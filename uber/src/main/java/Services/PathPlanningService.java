package Services;

import entities.City;
import entities.Drive;
import entities.Path;
import generated.PathOptionsResponse;
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
    private final PathSerializer pathSerializer;
    private final DriveSerializer driveSerializer;
    private final DriveReplicationService driveReplicationService;

    //private final PathReplicationService pathReplicationService;


    public PathPlanningService(PathSerializer pathSerializer, DriveSerializer driveSerializer, DriveReplicationService driveReplicationService) {
        this.pathSerializer = pathSerializer;
        this.driveSerializer = driveSerializer;
        this.driveReplicationService = driveReplicationService;
        //this.pathReplicationService = pathReplicationService;
    }

    public Path planPath(Path path){
        logger.info("plan path transaction: " + path.getId());
        int attempt = 0;
        while(attempt < ConfigurationManager.PLAN_PATH_RETRIES && !path.isSatisfied()) {
            attempt = attempt + 1;
            // wait a random time
            if(attempt > 1){
                try {
                    double time = Math.random() * 100;
                    logger.info("retry path planning " + attempt + ", waiting " + time + " ms");
                    Thread.sleep((long) (time));
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            // get path options from the other shards
            Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> drives = sendPlanRequest(path);
            // add inner shard drives to path options
            DriveRepository.getInstance().getPathOptions(path).forEach((src_dst, l) ->
                    drives.get(src_dst).addAll(l));

            for (List<Drive> l : drives.values()) {
                if (l.isEmpty()) {
                    // the path cannot be satisfied, no retry
                    return path;
                }
            }

            // get a mapping of the drives in the path by shards
            List<List<UUID>> drivesPerShard = new ArrayList<>();
            IntStream.range(0, ConfigurationManager.NUM_OF_SHARDS).
                    forEach(i -> drivesPerShard.add(new ArrayList<>()));

            // randomly assign drives to path
            Random rand = new Random();
            Path finalPath = path; // due to a weird error
            path.getRides().forEach((src_dst, id) -> {
                Drive drive = drives.get(src_dst).get(rand.nextInt(drives.get(src_dst).size()));
                finalPath.getRides().put(src_dst, drive.getId());
                drivesPerShard.get(getShardIdByCity(drive.getStartingPoint()) - 1).add(drive.getId());
            });

//            if (path.getRides().containsValue(null)) {
//                logger.info("no drives to satisfy the path");
//                continue;
//            } // the path cannot be satisfied

            // reserve the drives belonging to this shard
            if (!DriveRepository.getInstance().reserveDrives(drivesPerShard.get(ConfigurationManager.SHARD_ID - 1))) {
                logger.info("cannot reserve drives to satisfy the path");
                return path; // the path cannot be satisfied
            }

            // all the drives in the path belongs to this shard, no need for 2pc
            if (drivesPerShard.get(ConfigurationManager.SHARD_ID - 1).size() == path.getCities().size() - 1) {
                logger.info("inner shard path planning");
                path.setSatisfied(true);
                // publish that the drives are taken to shard
                path.getRides().values().forEach(driveId ->
                        driveReplicationService.replicateToAllMembers(DriveRepository.getInstance().getDrive(driveId))
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
        }
        return path;
    }

    @SneakyThrows
    public Path TPCTxn(Path path, List<List<UUID>> drivesPerShard){
        List<Integer> participatingShards = IntStream.range(1, ConfigurationManager.NUM_OF_SHARDS + 1).
                filter(i -> !drivesPerShard.get(i - 1).isEmpty() && i !=ConfigurationManager.SHARD_ID)
                .boxed().collect(Collectors.toList());

        logger.info("participating shards: "+ participatingShards);

        // create the zNodes for the transaction
        ReplicaManager.getInstance().initiate2PC(path.getId());
        CountDownLatch finishSignal = new CountDownLatch(1);

        if (!sendPathApprovalRequest(path, drivesPerShard, participatingShards)){
            // some leader didn't get the message
            ReplicaManager.getInstance().write2PCResult(path.getId(), "ABORT".getBytes(), finishSignal);
            DriveRepository.getInstance().releaseDrives(drivesPerShard.get(ConfigurationManager.SHARD_ID - 1));
        }
        else {
            Map<Integer, String> responses = ReplicaManager.getInstance().get2PCResponses(path.getId(), participatingShards);
            if (responses.containsValue(null) || responses.containsValue("ABORT")) {
                ReplicaManager.getInstance().write2PCResult(path.getId(), "ABORT".getBytes(), finishSignal);
                DriveRepository.getInstance().releaseDrives(drivesPerShard.get(ConfigurationManager.SHARD_ID - 1));
            } else {
                ReplicaManager.getInstance().write2PCResult(path.getId(), "COMMIT".getBytes(), finishSignal);
                path.setSatisfied(true);
                drivesPerShard.get(ConfigurationManager.SHARD_ID - 1).forEach(driveId ->
                        driveReplicationService.replicateToAllMembers(DriveRepository.getInstance().getDrive(driveId))
                );
            }
        }
        logger.info("waiting for everyone to see the txn");
        finishSignal.await();
        return path;
    }

    public Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> sendPlanRequest(Path path) {
        try {
            List<Integer> shardLeaders = ReplicaManager.getInstance().getShardLeaders().stream()
                    .filter(leader -> !leader.equals(ConfigurationManager.SERVER_ID))
                    .collect(Collectors.toList());

            Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> pathOptions = new LinkedHashMap<>();
            path.getRides().keySet().forEach(k -> pathOptions.put(k, new ArrayList<>()));

            for (Integer l : shardLeaders){
                getPathOptions(path, l).forEach((k,v) ->
                        pathOptions.get(k).addAll(v));
            }
            return pathOptions;

        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> getPathOptions(Path newPath, int dstServerId) {
//        int port = 7070 + dstServerId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        logger.info(String.format("server-%d is requesting path options from server-%d", ConfigurationManager.SERVER_ID, dstServerId));
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            generated.PathOptionsResponse response = stub.getPathOptions(pathSerializer.serialize(newPath));

            Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> pathOptions = new HashMap<>();

            for(PathOptionsResponse.PathOption option :  response.getOptionList()){
                City src = City.valueOf(option.getSegment().getStartingPoint().toString());
                City dst = City.valueOf(option.getSegment().getEndingPoint().toString());
                AbstractMap.SimpleEntry<City, City> key = new AbstractMap.SimpleEntry<>(src, dst);
                if(pathOptions.containsKey(key)){
                    pathOptions.get(key).add(driveSerializer.deserialize(option.getDrive()));
                }
                else{
                    List<Drive> drives = new ArrayList<>();
                    drives.add(driveSerializer.deserialize(option.getDrive()));
                    pathOptions.put(key, drives);
                }
            }
            return pathOptions;

        } finally {
            channel.shutdown();
        }
    }

    public boolean sendPathApprovalRequest(Path path, List<List<UUID>> drivesPerShard, List<Integer> shards) {
        boolean success = true;
        try {
            for (Integer shard : shards) {
                int leader = ReplicaManager.getInstance().getLeader(shard);
                if (!sendPathApproval(drivesPerShard.get(shard - 1), path.getId(), leader)){
                    success = false;
                    break;
                }
            }
        } catch (Exception e) { // delivery of the message failed
            e.printStackTrace();
        }

        return success;
    }

    public boolean sendPathApproval(List<UUID> drives, UUID pathID, int dstServerId) {
//        int port = 7070 + dstServerId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        logger.info(String.format("server-%d is sending path approval to server-%d", ConfigurationManager.SERVER_ID, dstServerId));
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

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