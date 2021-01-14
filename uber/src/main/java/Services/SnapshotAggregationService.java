package Services;

import com.google.protobuf.Empty;
import generated.Path;
import generated.UberGrpc;
import host.ConfigurationManager;
import host.ReplicaManager;
import host.controllers.SnapshotController;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import protoSerializers.SnapshotSerializer;
import protoSerializers.UserSerializer;
import repositories.DriveRepository;
import repositories.PathRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class SnapshotAggregationService {
    private static final Logger logger = Logger.getLogger(SnapshotAggregationService.class.getName());
    private final SnapshotSerializer snapshotSerializer;
    private final ReplicaManager replicaManager;
    private final DriveRepository driveRepository;
    private final PathRepository pathRepository;

    public SnapshotAggregationService() {
        this.replicaManager = ReplicaManager.getInstance();
        this.driveRepository = DriveRepository.getInstance();
        this.pathRepository = PathRepository.getInstance();
        UserSerializer userSerializer = new UserSerializer();
        this.snapshotSerializer = new SnapshotSerializer(new DriveSerializer(userSerializer), new PathSerializer(userSerializer));
    }

    public List<SnapshotController.Snapshot> aggregateSnapshot() {
        List<Integer> leaders = null;
        try {
            leaders = replicaManager.getShardLeaders();
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        List<SnapshotController.Snapshot> snapshotList = new ArrayList<>();

        if (leaders.contains(ConfigurationManager.SERVER_ID)) {
            leaders.remove(ConfigurationManager.SERVER_ID);
            snapshotList.add(new SnapshotController.Snapshot(driveRepository.getAll(), pathRepository.getAll()));
        }

        leaders.forEach(serverId -> snapshotList.add(getSnapshotFromServer(serverId)));

        return snapshotList;
    }

    private SnapshotController.Snapshot getSnapshotFromServer(Integer dstServerId) {
//        int port = 7070 + serverId;
//        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        // local vs docker
        logger.info(String.format("server-%d is requesting snapshot from server-%d", ConfigurationManager.SERVER_ID, dstServerId));
        ManagedChannel channel = ManagedChannelBuilder.forAddress(String.format("server-%d", dstServerId), ConfigurationManager.GRPC_PORT).usePlaintext().build();

        try {
            UberGrpc.UberBlockingStub stub = UberGrpc.newBlockingStub(channel);
            return snapshotSerializer.deserialize(stub.getSnapshot(Empty.newBuilder().build()));
        } finally {
            channel.shutdown();
        }
    }

}
