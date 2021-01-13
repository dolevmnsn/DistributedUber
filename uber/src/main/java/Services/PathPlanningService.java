package Services;

import entities.City;
import entities.Drive;
import entities.Path;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.SneakyThrows;
import org.apache.zookeeper.KeeperException;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import repositories.DriveRepository;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static host.CityShardDistributor.getShardIdByCity;

public class PathPlanningService {
    private static final Logger logger = Logger.getLogger(PathPlanningService.class.getName());
    private final DriveRepository driveRepository;
    private final PathSerializer pathSerializer;
    private final DriveSerializer driveSerializer;
    private final DriveReplicationService driveReplicationService;
    private final ReplicaManager replicaManager;

    //private final PathReplicationService pathReplicationService;


    public PathPlanningService(PathSerializer pathSerializer, DriveSerializer driveSerializer, DriveReplicationService driveReplicationService) {
        this.driveRepository = DriveRepository.getInstance();
        this.pathSerializer = pathSerializer;
        this.driveSerializer = driveSerializer;
        this.replicaManager = ReplicaManager.getInstance();
        this.driveReplicationService = driveReplicationService;
        //this.pathReplicationService = pathReplicationService;
    }

    public Path planPath(Path path){

        // repeat several times in case of a race on the drives
        List<Drive> drives = sendPlanRequest(path);
        drives.addAll(driveRepository.getAll()); //get rides that have vacant seats

        Map<AbstractMap.SimpleEntry<City, City>, List<UUID>> drivesPerSeg = new LinkedHashMap<>();

        // list of potential drives for every segment
        for (AbstractMap.SimpleEntry<City, City> src_dst : path.getRides().keySet()) {
            List<UUID> segDrives = findRidesForSegment(src_dst, path, drives);
            if (segDrives.isEmpty()){
                return path; // the path cannot be satisfied
            }
            drivesPerSeg.put(src_dst, segDrives);
        }

        // randomly assign drives to path
        Random rand = new Random();
        Path finalPath = path; // due to a weird error
        path.getRides().forEach((src_dst, id) -> {
            if (id == null) { // is this needed?
                finalPath.getRides().put(src_dst,
                        drivesPerSeg.get(src_dst).get(rand.nextInt(drivesPerSeg.get(src_dst).size())));
            }
        });

        if (path.getRides().containsValue(null)) {
            logger.info("no drives to satisfy the path");
            return path;
        } // the path cannot be satisfied

        // get a mapping of the drives in the path by shards
        List<List<UUID>> drivesPerShard = pathDrivesPerShard(path);

        // reserve the drives belonging to this shard
        if(!driveRepository.reserveDrives(drivesPerShard.get(ConfigurationManager.SHARD_ID - 1))){
            return path; // the path cannot be satisfied
        }

        // all the drives in the path belongs to this shard, no need for 2pc
        if (drivesPerShard.get(ConfigurationManager.SHARD_ID - 1).size() == path.getCities().size() - 1){
            logger.info("inner shard path planning");
            path.setSatisfied(true);
            // publish that the drives are taken to shard
            path.getRides().values().forEach(driveId ->
                    driveReplicationService.replicateToAllMembers(driveRepository.getDrive(driveId))
            );
        }
        // initiate 2pc transaction
        else {
            try {
                path = TPCTxn(path, drivesPerShard);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return path; // what to return?
    }

    @SneakyThrows
    public Path TPCTxn(Path path, List<List<UUID>> drivesPerShard){
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        // create the zNodes for the transaction
        replicaManager.initiate2PC(path.getId(), connectedSignal);

        if (!sendPathApprovalRequest(path, drivesPerShard)) {
            // some leader didn't get the message
            replicaManager.abort2PC(path.getId());
            return path;
        }
        // wait for all the processes to write their response
        connectedSignal.await();

        // read the transaction status from zookeeper
        String decision = replicaManager.get2PCStatus(path.getId());
        if (decision.equals("COMMIT")) { // all the shards committed
            path.setSatisfied(true);
            // publish that the drives are taken to shard
            drivesPerShard.get(ConfigurationManager.SHARD_ID - 1).forEach(driveId ->
                    driveReplicationService.replicateToAllMembers(driveRepository.getDrive(driveId))
            );
        } else {
            // release all the reserved drives
            driveRepository.releaseDrives(drivesPerShard.get(ConfigurationManager.SHARD_ID - 1));
        }
        return path;
    }

    public List<Drive> sendPlanRequest(Path path) {
        try {
            List<Integer> shardLeaders = replicaManager.getShardLeaders().stream()
                    .filter(leader -> !leader.equals(ConfigurationManager.SERVER_ID))
                    .collect(Collectors.toList());

            return shardLeaders.stream().map(l -> getPathOptions(path, l)).
                    flatMap(Collection::stream).
                    collect(Collectors.toList());

        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<Drive> getPathOptions(Path newPath, int serverId) {
        int port = 7070 + serverId; // todo: delete
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
//        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", serverId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            generated.PathOptionsResponse response = stub.getPathOptions(pathSerializer.serialize(newPath));
            return response.getDrivesList().stream().map(driveSerializer::deserialize).collect(Collectors.toList());
        } finally {
            channel.shutdown();
        }
    }

    private List<UUID> findRidesForSegment(AbstractMap.SimpleEntry<City, City> src_dst, Path path, List<Drive> drives) {
        List<UUID> matchingDrives = new ArrayList<>();
        for (Drive drive : drives) {
            if (satisfiesSegment(drive, src_dst, path)) {
                matchingDrives.add(drive.getId());
            }
        }
        return matchingDrives;
    }

//    private UUID findRideForSegment(AbstractMap.SimpleEntry<City, City> src_dst, Path path, List<Drive> drives) {
//        for (Drive drive : drives) {
//            if (satisfiesSegment(drive, src_dst, path)) {
//                // todo: update drive taken seats
//                return drive.getId();
//            }
//        }
//        return null;
//    }

    private boolean satisfiesSegment(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst, Path path) {
        boolean isNotSameUser = !drive.getDriver().equals(path.getPassenger());
        boolean sameDate = drive.getDepartureDate().equals(path.getDepartureDate());
        // TODO: calculate right condition. now only from sec to dst.
//        boolean isNotPassDeviation = maxDeviation(drive, src_dst) <= drive.getPermittedDeviation();
//        return isNotSameUser && isNotPassDeviation;
        return isNotSameUser && sameDate &&
                drive.getStartingPoint().equals(src_dst.getKey()) && drive.getEndingPoint().equals(src_dst.getValue());
    }

    private double maxDeviation(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst) {
        Point driveSrc = drive.getStartingPoint().getLocation();
        Point driveDst = drive.getEndingPoint().getLocation();
        Point segmentSrc = src_dst.getKey().getLocation();
        Point segmentDst = src_dst.getValue().getLocation();

        double deviationToSegSrc = distance(driveSrc, driveDst, segmentSrc);
        double deviationToSegDst = distance(driveSrc, driveDst, segmentDst);

        return Math.max(deviationToSegSrc, deviationToSegDst);
    }

    private double distance(Point point1, Point point2, Point point0) {
        int x2_x1 = point2.x - point1.x;
        int y1_y0 = point1.y - point0.y;
        int x1_x0 = point1.x - point0.x;
        int y2_y1 = point2.y - point1.y;

        int numerator = Math.abs(x2_x1 * y1_y0 - x1_x0 * y2_y1);
        double denominator = Math.sqrt(Math.pow(x2_x1, 2) + Math.pow(y2_y1, 2));
        return (numerator / denominator);
    }

    public List<List<UUID>> pathDrivesPerShard(Path path){
        List<List<UUID>> drivesPerShard = new ArrayList<>();
        IntStream.range(0, ConfigurationManager.NUM_OF_SHARDS).
                forEach(i -> drivesPerShard.add(new ArrayList<>()));
        path.getRides().forEach((src_dst, id) -> {
            List<UUID> l = drivesPerShard.get(getShardIdByCity(src_dst.getKey()) - 1);
            l.add(id);
        });
        return drivesPerShard;
    }

    public boolean sendPathApprovalRequest(Path path, List<List<UUID>> drivesPerShard) {
        boolean success = true;
        try {
            for (int shard = 1; shard <= ConfigurationManager.NUM_OF_SHARDS; shard++) {
                if (shard == ConfigurationManager.SHARD_ID) {
                    continue;
                }
                int leader = replicaManager.getLeader(shard);
                if (!sendPathApproval(drivesPerShard.get(shard-1), path.getId(), leader)){
                    success = false;
                    break;
                }
            }
        } catch (Exception e) { // delivery of the message failed
            e.printStackTrace();
        }

        return success;
    }

    public boolean sendPathApproval(List<UUID> drives, UUID pathID, int serverId) {
        int port = 7070 + serverId; // todo: delete
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
//        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", serverId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            generated.PathApprovalRequest pathApprovalRequest =  generated.PathApprovalRequest.newBuilder()
                    .setShard(ConfigurationManager.SHARD_ID)
                    .setPathId(pathID.toString())
                    .addAllDriveId(drives.stream().map(d -> d.toString()).collect(Collectors.toList()))
                    .build();
            stub.pathApproval(pathApprovalRequest);
            return true;
        } catch (Exception e){
            return false;
        }
        finally {
            channel.shutdown();
        }
    }
}

