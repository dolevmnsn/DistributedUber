package Services;

import entities.Drive;
import generated.SaveDriveRequest;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import host.RevisionManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import protoSerializers.DriveSerializer;

import java.util.List;
import java.util.logging.Logger;

public class DriveReplicationService {
    private static final Logger logger = Logger.getLogger(DriveReplicationService.class.getName());
    private final DriveSerializer driveSerializer;

    public DriveReplicationService(DriveSerializer driveSerializer) {
        this.driveSerializer = driveSerializer;
    }

    public void replicateToAllMembers(Drive newDrive) {
        List<Integer> members = null;
        try {
            members = ReplicaManager.getInstance().getShardMembers();
            // todo: remove this! only for testing!!
            // ----------------
            if (newDrive.getDriver().getFirstName().equals("testing_update")) {
                newDrive.getDriver().setFirstName("testing_update_changed");

                members.remove(0);
            }
            // ----------------
            synchronized (RevisionManager.getInstance()) {
                long updatedRevision = RevisionManager.getInstance().updateAndGet();
                newDrive.setRevision(updatedRevision);
                logger.info(String.format("replicating drive in the cluster. revision: %d", updatedRevision));
                // todo: in sync? to guarantee order in sending
                members.forEach(serverId -> sendDrive(newDrive, serverId, true));
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendDrive(Drive newDrive, int dstServerId, boolean replication) {
//        int port = 7070 + dstServerId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        logger.info(String.format("server-%d is sending drive to server-%d", ConfigurationManager.SERVER_ID, dstServerId));
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

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
