package Services;

import com.google.protobuf.Empty;
import entities.Drive;
import entities.Path;
import generated.SaveDriveRequest;
import generated.SavePathRequest;
import generated.Snapshot;
import generated.UberGrpc;
import host.ConfigurationManager;
import io.grpc.stub.StreamObserver;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import repositories.DriveRepository;
import repositories.PathRepository;

import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GrpcService extends UberGrpc.UberImplBase {
    private static final Logger logger = Logger.getLogger(GrpcService.class.getName());
    private final DriveReplicationService driveReplicationService;
    private final DriveSerializer driveSerializer;
    private final DriveRepository driveRepository;
    private final PathReplicationService pathReplicationService;
    private final PathSerializer pathSerializer;
    private final PathRepository pathRepository;
    private final PathPlanningService pathPlanningService;
    private final SnapshotAggregationService snapshotAggregationService;

    public GrpcService(DriveReplicationService driveReplicationService, DriveSerializer driveSerializer,
                       PathReplicationService pathReplicationService, PathSerializer pathSerializer,
                       PathPlanningService pathPlanningService, SnapshotAggregationService snapshotAggregationService) {
        this.driveRepository = DriveRepository.getInstance();
        this.driveReplicationService = driveReplicationService;
        this.driveSerializer = driveSerializer;
        this.pathRepository = PathRepository.getInstance();
        this.pathReplicationService = pathReplicationService;
        this.pathSerializer = pathSerializer;
        this.pathPlanningService = pathPlanningService;
        this.snapshotAggregationService = snapshotAggregationService;
    }

    @Override
    public void saveDrive(SaveDriveRequest request, StreamObserver<Empty> responseObserver) {
        Drive newDrive = driveSerializer.deserialize(request.getDrive());
        logger.info(String.format("server-%d is saving new drive (grpc)", ConfigurationManager.SERVER_ID));
        driveRepository.save(newDrive);

        if (!request.getReplication()) { // I'm the leader
            driveReplicationService.replicateToAllMembers(newDrive);
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void savePath(SavePathRequest request, StreamObserver<Empty> responseObserver) {
        Path newPath = pathSerializer.deserialize(request.getPath());
        logger.info(String.format("server-%d is saving new path", ConfigurationManager.SERVER_ID));
        pathRepository.save(newPath);

        if (!request.getReplication()) { // I'm the leader
            Path plannedPath = pathPlanningService.planPath(newPath);
            pathReplicationService.replicateToAllMembers(plannedPath);
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getSnapshot(com.google.protobuf.Empty request, io.grpc.stub.StreamObserver<generated.Snapshot> responseObserver) {
        Snapshot snapshot = Snapshot.newBuilder()
                .addAllDrives(driveRepository.getAll().stream().map(driveSerializer::serialize).collect(Collectors.toList()))
                .addAllPaths(pathRepository.getAll().stream().map(pathSerializer::serialize).collect(Collectors.toList()))
                .build();

        responseObserver.onNext(snapshot);
        responseObserver.onCompleted();
    }
}
