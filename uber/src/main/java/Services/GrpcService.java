package Services;

import com.google.protobuf.Empty;
import entities.Drive;
import entities.Path;
import generated.*;
import host.ConfigurationManager;
import host.ReplicaManager;
import io.grpc.stub.StreamObserver;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import repositories.DriveRepository;
import repositories.PathRepository;

import java.util.*;
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
    public void savePath(SavePathRequest request, io.grpc.stub.StreamObserver<generated.Path> responseObserver) {
        Path newPath = pathSerializer.deserialize(request.getPath());

        Path returnValuePath = newPath;
        if (request.getReplication()) {
            logger.info("saving a new path as a replication (grpc)");
            pathRepository.save(newPath);
        } else { // I'm the leader
            logger.info("I'm the leader. trying to plan path. (grpc)");
            Path plannedPath = pathPlanningService.planPath(newPath);
            if (plannedPath.isSatisfied()) {
                logger.info("path was satisfied. saving and sending to the rest of the shard (grpc)");
                pathRepository.save(plannedPath);
                pathReplicationService.replicateToAllMembers(plannedPath);
            }
            returnValuePath = plannedPath;
        }

        responseObserver.onNext(pathSerializer.serialize(returnValuePath));
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

    @Override
    public void getPathOptions(generated.Path path, StreamObserver<generated.PathOptionsResponse> responseObserver) {
        Path path1 = pathSerializer.deserialize(path);
        Map<AbstractMap.SimpleEntry<entities.City, entities.City>, List<Drive>> pathOptions =
                driveRepository.getPathOptions(path1);

        PathOptionsResponse.Builder builder = PathOptionsResponse.newBuilder();
        for (Map.Entry<AbstractMap.SimpleEntry<entities.City, entities.City>, List<Drive>> entry : pathOptions.entrySet())
            for(Drive drive : entry.getValue()){

                PathOptionsResponse.Segment segment = PathOptionsResponse.Segment.newBuilder()
                        .setStartingPoint(entry.getKey().getKey().getProtoType())
                        .setEndingPoint(entry.getKey().getValue().getProtoType())
                        .build();

                PathOptionsResponse.PathOption option = PathOptionsResponse.PathOption.newBuilder()
                        .setDrive(driveSerializer.serialize(drive))
                        .setSegment(segment)
                        .build();

                builder.addOption(option);
            }
        PathOptionsResponse options = builder.build();

        responseObserver.onNext(options);
        responseObserver.onCompleted();
    }

    @Override
    public void pathApproval(generated.PathApprovalRequest approvalRequest, StreamObserver<Empty> responseObserver) {

        List<UUID> drives= approvalRequest.getDriveIdList().stream().
                map(UUID::fromString).collect(Collectors.toList());

        boolean success = driveRepository.reserveDrives(drives);

        ReplicaManager replicaManager = ReplicaManager.getInstance();
        UUID pathId = UUID.fromString(approvalRequest.getPathId());
        int shard = approvalRequest.getShard();
        byte [] response = success ? "COMMIT".getBytes() : "ABORT".getBytes();
        replicaManager.response2PC(pathId, shard, response, drives);

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
