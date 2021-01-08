package host;

import com.google.protobuf.Empty;
import entities.Drive;
import generated.PublishDriveGrpc;
import generated.SaveDriveRequest;
import io.grpc.stub.StreamObserver;
import protoSerializers.DriveSerializer;
import repositories.DriveRepository;

import java.util.logging.Logger;

public class GRPCDriveService extends PublishDriveGrpc.PublishDriveImplBase {
    private static final Logger logger = Logger.getLogger(GRPCDriveService.class.getName());
    private final DriveReplicationService driveReplicationService;
    private final DriveSerializer driveSerializer;
    private final DriveRepository driveRepository;

    public GRPCDriveService(DriveReplicationService driveReplicationService, DriveSerializer driveSerializer) {
        this.driveRepository = DriveRepository.getInstance();
        this.driveReplicationService = driveReplicationService;
        this.driveSerializer = driveSerializer;
    }

    @Override
    public void saveDrive(SaveDriveRequest request, StreamObserver<Empty> responseObserver) {
        Drive newDrive = driveSerializer.deserialize(request.getDrive());
        driveRepository.save(newDrive);

        if (!request.getReplication()) { // I'm the leader
            driveReplicationService.replicateToAllMembers(newDrive);
        }

//        driveReplicationService.saveDrive(driveSerializer.deserialize(request.getDrive()), request.getReplication());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
