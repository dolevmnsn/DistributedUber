package Services;

import entities.Drive;
import generated.SaveDriveRequest;
import generated.UberGrpc;
import host.ReplicaManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import protoSerializers.DriveSerializer;

import java.util.Collections;
import java.util.List;

public class DriveReplicationService {
    private final DriveSerializer driveSerializer;

    public DriveReplicationService(DriveSerializer driveSerializer) {
        this.driveSerializer = driveSerializer;
    }

    public void replicateToAllMembers(Drive newDrive) {
//        List<Integer> members = Collections.singletonList(2); // todo: get real members excluding myself
//        List<Integer> members = Collections.emptyList();
        List<Integer> members = null;
        try {
            members = ReplicaManager.getInstance().getShardMembers();
            members.forEach(serverId -> sendDrive(newDrive, serverId, true));
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendDrive(Drive newDrive, int serverId, boolean replication) {
        int port = 7070 + serverId; // todo: delete
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
//        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", serverId), ConfigurationManager.DRIVE_GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            stub.saveDrive(buildSaveDriveRequest(newDrive, replication));
        } finally {
            channel.shutdown();
        }
    }

    private generated.SaveDriveRequest buildSaveDriveRequest(Drive newDrive, boolean replication) {
        generated.Drive drive = driveSerializer.serialize(newDrive);
        return SaveDriveRequest.newBuilder().setReplication(replication).setDrive(drive).build();
    }
}
