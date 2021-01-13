package Services;

import entities.City;
import entities.Drive;
import entities.Path;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
        drives.addAll(driveRepository.getAll());
        Collections.shuffle(drives);

        path.getRides().forEach((src_dst, id) -> {
            if (id == null) {
                path.getRides().put(src_dst, findRideForSegment(src_dst, path, drives));
            }
        });

        if (path.getRides().containsValue(null)) {
            return path;
        } // the path cannot be satisfied

        // get drives relevant to shard
        List<UUID> drivesInShard = new ArrayList<>();
        path.getRides().forEach((src_dst, id) -> {
            if (ConfigurationManager.SHARD_ID == getShardIdByCity(src_dst.getKey())){
                drivesInShard.add(id);
            }
        });

        // reserve the places in the shard
        List<UUID> visited = new ArrayList<>();
        boolean success = true;
        for (UUID id : drivesInShard){
            if(!driveRepository.getDrive(id).increaseTaken()){
                for(UUID visitedId : visited){ // undo
                    driveRepository.getDrive(visitedId).decreaseTaken();
                }
                success = false;
                break;
            }
            logger.info("increased taken");
            visited.add(id);
        }

        if(!success) {
            return path;
        }

        try {
            final CountDownLatch connectedSignal = new CountDownLatch(1);
            replicaManager.initiate2PC(path.getId(), connectedSignal);

            if (!sendPathApprovalRequest(path)){ // some leader didn't get the message
                replicaManager.finish2PC(path.getId(), "ABORT".getBytes());
                return path;
                }
            connectedSignal.await();

            if (replicaManager.get2PCStatus()){ // all the shards committed
                replicaManager.finish2PC(path.getId(), "COMMIT".getBytes());
                path.setSatisfied(true);
                path.getRides().values().forEach(driveId ->
                        driveReplicationService.replicateToAllMembers(driveRepository.getDrive(driveId))
                );
            }
            else{
                replicaManager.finish2PC(path.getId(), "ABORT".getBytes());
                for (UUID id : drivesInShard){ // rollback
                    driveRepository.getDrive(id).decreaseTaken();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    private UUID findRideForSegment(AbstractMap.SimpleEntry<City, City> src_dst, Path path, List<Drive> drives) {
        for (Drive drive : drives) {
            if (satisfiesSegment(drive, src_dst, path)) {
                // todo: update drive taken seats
                return drive.getId();
            }
        }
        return null;
    }

    private boolean satisfiesSegment(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst, Path path) {
        boolean isNotSameUser = !drive.getDriver().equals(path.getPassenger());
        boolean sameDate = drive.getDepartureDate().equals(path.getDepartureDate());
        // TODO: calculate right condition. now only from sec to dst.
//        boolean isNotPassDeviation = maxDeviation(drive, src_dst) <= drive.getPermittedDeviation();
//        return isNotSameUser && sameDate && isNotPassDeviation;
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

    public boolean sendPathApprovalRequest(Path path) {
        List<List<UUID>> drivesPerShard = new ArrayList<>();
        IntStream.range(0, ConfigurationManager.NUM_OF_SHARDS).
                forEach(i -> drivesPerShard.add(new ArrayList<>()));
        path.getRides().forEach((src_dst, id) -> {
            List<UUID> l = drivesPerShard.get(getShardIdByCity(src_dst.getKey()) - 1);
            l.add(id);
        });

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

