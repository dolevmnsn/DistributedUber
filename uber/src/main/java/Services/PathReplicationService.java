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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class PathReplicationService {
    private final PathSerializer pathSerializer;
    private final ReplicaManager replicaManager;

    public PathReplicationService(PathSerializer pathSerializer) {
        this.pathSerializer = pathSerializer;
        this.replicaManager = ReplicaManager.getInstance();
    }

    public void replicateToAllMembers(Path newPath) {
//        List<Integer> members = Collections.singletonList(2); // todo: get real members excluding myself
//        List<Integer> members = Collections.emptyList();
        List<Integer> members = null;
        try {
            members = replicaManager.getShardMembers();
            members.forEach(serverId -> sendPath(newPath, serverId, true));
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendPath(Path newPath, int serverId, boolean replication) {
        int port = 7070 + serverId; // todo: delete
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
//        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", serverId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            stub.savePath(buildSavePathRequest(newPath, replication));
        } finally {
            channel.shutdown();
        }
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
