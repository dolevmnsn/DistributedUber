package host;

import entities.Drive;
import generated.PublishDriveGrpc;
import generated.SaveDriveRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import protoSerializers.DriveSerializer;
import protoSerializers.UserSerializer;
import repositories.DriveRepository;

import java.util.Collections;
import java.util.List;

public class DriveReplicationService {
    private final DriveRepository repository;
    private final DriveSerializer driveSerializer;

    public DriveReplicationService(DriveSerializer driveSerializer) {
        this.repository = DriveRepository.getInstance();
        this.driveSerializer = driveSerializer;
    }

    public void replicateToAllMembers(Drive newDrive) {
        //List<Integer> members = Collections.singletonList(2); // todo: get real members excluding myself
        List<Integer> members = ReplicaManager.getInstance().getShardMembers();
        members.forEach(serverId -> sendDrive(newDrive, serverId, true));
    }

    public void sendDrive(Drive newDrive, int serverId, boolean replication) {
        int port = 7070 + serverId; // todo: delete
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
//        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", serverId), ConfigurationManager.DRIVE_GRPC_PORT).usePlaintext().build();

        try {
            PublishDriveGrpc.PublishDriveBlockingStub stub = PublishDriveGrpc.newBlockingStub(channel);
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
