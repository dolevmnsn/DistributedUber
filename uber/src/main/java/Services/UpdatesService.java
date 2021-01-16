package Services;

import com.google.protobuf.Empty;
import entities.Drive;
import entities.Path;
import generated.GeneralUpdateRequest;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import host.RevisionManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import protoSerializers.UserSerializer;
import repositories.DriveRepository;
import repositories.PathRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UpdatesService {
    private static final Logger logger = Logger.getLogger(UpdatesService.class.getName());
    private static UpdatesService INSTANCE;
    private final DriveReplicationService driveReplicationService;
    private final PathReplicationService pathReplicationService;

    public UpdatesService() {
        this.driveReplicationService = new DriveReplicationService(new DriveSerializer(new UserSerializer()));
        this.pathReplicationService = new PathReplicationService(new PathSerializer(new UserSerializer()));
    }

    public static synchronized UpdatesService getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new UpdatesService();
        }
        return INSTANCE;
    }

    public void sendUpdateRequest() {
        // I'm the leader and I ask for revisions from all the other servers
        // and then send them to the most updated server (unless it's me) and ask to update
        logger.info(String.format("I'm the new leader: %d. requesting updates.", ConfigurationManager.SERVER_ID));
        Map<Integer, Long> serverRevisions = new HashMap<>();
        try {
            List<Integer> members = ReplicaManager.getInstance().getShardMembers();
            // add myself:
            serverRevisions.put(ConfigurationManager.SERVER_ID, RevisionManager.getInstance().getRevision());
            // ask revisions from the rest:
            members.forEach(serverId -> serverRevisions.put(serverId, getRevisionFromServer(serverId)));

            if (serverRevisions.isEmpty()) {
                return;
            }

            serverRevisions.forEach((sId, rev) -> logger.info(String.format("server: %d has revision: %d", sId, rev)));

            Map.Entry<Integer, Long> mostUpdated = getMostUpdatedServer(serverRevisions);

            logger.info(String.format("The most updated server is: %d with revision: %d", mostUpdated.getKey(), mostUpdated.getValue()));

            logger.info("removing updated servers:");
            while (serverRevisions.values().remove(mostUpdated.getValue())); // remove updated servers

            if (serverRevisions.isEmpty()) {
                return;
            }

            serverRevisions.forEach((sId, rev) -> logger.info(String.format("server: %d has revision: %d", sId, rev)));

            if (ConfigurationManager.SERVER_ID.equals(mostUpdated.getKey())) { // I'm the most updated
                sendUpdateToServers(serverRevisions);
            } else {
                sendUpdateRequestFromMostUpdated(serverRevisions, mostUpdated.getKey());
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendUpdateToServers(Map<Integer, Long> serverRevisions) {
        serverRevisions.forEach((serverId, revision) -> {
            List<Drive> drives = DriveRepository.getInstance().getDrivesSinceRevision(revision).stream()
                    .sorted(Comparator.comparing(Drive::getRevision))
                    .collect(Collectors.toList());
            List<Path> paths = PathRepository.getInstance().getPathsSinceRevision(revision).stream()
                    .sorted(Comparator.comparing(Path::getRevision))
                    .collect(Collectors.toList());

            drives.forEach(drive -> driveReplicationService.sendDrive(drive, serverId, true));
            paths.forEach(path -> pathReplicationService.sendPath(path, serverId, true));
        });
    }

    private void sendUpdateRequestFromMostUpdated(Map<Integer, Long> serverRevisions, Integer dstServerId) {
        GeneralUpdateRequest.Builder generalUpdateRequest = GeneralUpdateRequest.newBuilder();
        serverRevisions.forEach((serverId, revision) ->
                generalUpdateRequest.addServerUpdate(GeneralUpdateRequest.ServerUpdate.newBuilder()
                        .setServerId(serverId).setRevision(revision).build()));

//        int port = 7070 + dstServerId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            stub.updateRequest(generalUpdateRequest.build());
        } finally {
            channel.shutdown();
        }
    }

    private Map.Entry<Integer, Long> getMostUpdatedServer(Map<Integer, Long> serverRevisions) {
        Map.Entry<Integer, Long> mostUpdatedServer = null;

        for (Map.Entry<Integer, Long> entry : serverRevisions.entrySet()) {
            if (mostUpdatedServer == null || entry.getValue().compareTo(mostUpdatedServer.getValue()) > 0) {
                mostUpdatedServer = entry;
            }
        }
        return mostUpdatedServer;
    }

    private long getRevisionFromServer(Integer dstServerId) {
//        int port = 7070 + dstServerId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            return stub.getState(Empty.newBuilder().build()).getRevision();
        } finally {
            channel.shutdown();
        }
    }
}
