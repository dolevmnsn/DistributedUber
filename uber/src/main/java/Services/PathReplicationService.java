package Services;

import entities.Path;
import generated.SavePathRequest;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import host.RevisionManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import protoSerializers.PathSerializer;

import java.util.List;
import java.util.logging.Logger;

public class PathReplicationService {
    private static final Logger logger = Logger.getLogger(PathReplicationService.class.getName());
    private final PathSerializer pathSerializer;

    public PathReplicationService(PathSerializer pathSerializer) {
        this.pathSerializer = pathSerializer;
    }

    public void replicateToAllMembers(Path newPath) {
        List<Integer> members = null;
        try {
            members = ReplicaManager.getInstance().getShardMembers();
            // todo: remove this! only for testing!!
            // ----------------
            if (newPath.getPassenger().getFirstName().equals("testing_update")) {
                newPath.getPassenger().setFirstName("testing_update_changed");
                members.remove(0);
            }
            // ----------------
            synchronized (RevisionManager.getInstance()) {
                long updatedRevision = RevisionManager.getInstance().updateAndGet();
                newPath.setRevision(updatedRevision);
                logger.info(String.format("replicating path in the cluster. revision: %d", updatedRevision));
                // todo: in sync? to guarantee order in sending
                members.forEach(serverId -> sendPath(newPath, serverId, true));
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Path sendPath(Path newPath, int dstServerId, boolean replication) {
//        int port = 7070 + dstServerId;
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
//            List<Integer> shardLeaders = ReplicaManager.getInstance().getShardLeaders().stream()
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
