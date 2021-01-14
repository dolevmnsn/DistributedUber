package Services;

import entities.Path;
import generated.SavePathRequest;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import protoSerializers.PathSerializer;

import java.util.List;
import java.util.logging.Logger;

public class PathReplicationService {
    private static final Logger logger = Logger.getLogger(PathReplicationService.class.getName());
    private final PathSerializer pathSerializer;
    private final ReplicaManager replicaManager;

    public PathReplicationService(PathSerializer pathSerializer) {
        this.pathSerializer = pathSerializer;
        this.replicaManager = ReplicaManager.getInstance();
    }

    public void replicateToAllMembers(Path newPath) {
        List<Integer> members = null;
        try {
            members = replicaManager.getShardMembers();
            members.forEach(serverId -> sendPath(newPath, serverId, true));
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Path sendPath(Path newPath, int dstServerId, boolean replication) {
//        int port = 7070 + serverId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        logger.info(String.format("server-%d is sending path to server-%d", ConfigurationManager.SERVER_ID, dstServerId));
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        generated.Path path;
        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            path = stub.savePath(buildSavePathRequest(newPath, replication));
        } finally {
            channel.shutdown();
        }

        return pathSerializer.deserialize(path);
    }

    private generated.SavePathRequest buildSavePathRequest(Path newPath, boolean replication) {
        generated.Path path = pathSerializer.serialize(newPath);
        return SavePathRequest.newBuilder().setReplication(replication).setPath(path).build();
    }

//    public Path sendPlanRequest(Path path) {
//        try {
//            List<Integer> shardLeaders = replicaManager.getShardLeaders().stream()
//                    .filter(leader -> !leader.equals(ConfigurationManager.SERVER_ID))
//                    .collect(Collectors.toList());
//        } catch (KeeperException | InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        // send grpc messages
//
//        return null;
//    }
}
