package protoSerializers;

import generated.Snapshot;
import host.controllers.SnapshotController;

import java.util.stream.Collectors;

public class SnapshotSerializer implements Serializer<SnapshotController.Snapshot, Snapshot>{
    private final Serializer<entities.Drive, generated.Drive> driveSerializer;
    private final Serializer<entities.Path, generated.Path> pathSerializer;

    public SnapshotSerializer(Serializer<entities.Drive, generated.Drive> driveSerializer,
                              Serializer<entities.Path, generated.Path> pathSerializer) {
        this.driveSerializer = driveSerializer;
        this.pathSerializer = pathSerializer;
    }

    @Override
    public Snapshot serialize(SnapshotController.Snapshot snapshot) {
        return Snapshot.newBuilder()
                .addAllDrives(snapshot.getDrives().stream().map(driveSerializer::serialize).collect(Collectors.toList()))
                .addAllPaths(snapshot.getPaths().stream().map(pathSerializer::serialize).collect(Collectors.toList()))
                .build();
    }

    @Override
    public SnapshotController.Snapshot deserialize(Snapshot generated) {
        return new SnapshotController.Snapshot(
                generated.getDrivesList().stream().map(driveSerializer::deserialize).collect(Collectors.toList()),
                generated.getPathsList().stream().map(pathSerializer::deserialize).collect(Collectors.toList())
        );
    }
}
