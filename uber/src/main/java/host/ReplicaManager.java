package host;

import Services.DriveReplicationService;
import Services.UpdatesService;
import com.google.common.collect.Sets;
import entities.Drive;
import lombok.SneakyThrows;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import protoSerializers.DriveSerializer;
import protoSerializers.UserSerializer;
import repositories.DriveRepository;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.apache.zookeeper.Watcher.Event.EventType.NodeDeleted;

public class ReplicaManager implements Watcher {
    private static final Logger logger = Logger.getLogger(ReplicaManager.class.getName());

    private static ReplicaManager INSTANCE;
    static ZooKeeper zk;
    static int shardId;
    static int serverId;
    static boolean isLeader;
    static int replicaVersion;
    final Object lock = new Object();
    DriveReplicationService driveReplicationService;


    private ReplicaManager() {
        try {
            zk = new ZooKeeper(ConfigurationManager.ZK_HOST, 10000, this);
            shardId = ConfigurationManager.SHARD_ID;
            serverId = ConfigurationManager.SERVER_ID;
            isLeader = false;
            replicaVersion = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows
    public static ReplicaManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ReplicaManager();
            while (true) {
                try {
                    INSTANCE.registerServer(); // register server to the shard
                    INSTANCE.electLeader(); // elect a shard leader
                    break;
                } catch (KeeperException | InterruptedException e) {
                    e.printStackTrace();
                    logger.info("Failed registering to zookeeper, trying again");
                    Thread.sleep(1000);
                }
            }
            INSTANCE.driveReplicationService = new DriveReplicationService(new DriveSerializer(new UserSerializer()));
        }
        return INSTANCE;
    }

    // check if the zNode exists, is not create it
//    @SneakyThrows
    private void checkZNode(String path, boolean watch, byte[] data, List<ACL> acl, CreateMode createMode) throws KeeperException, InterruptedException {
        if (zk.exists(path, watch) == null) {
            zk.create(path, data, acl, createMode);
        }
    }

    //    @SneakyThrows
    private void registerServer() throws KeeperException, InterruptedException {
        String path = String.format("/%d", shardId);

        checkZNode(path, false, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        checkZNode(path + "/election", false, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        checkZNode(path + "/members", false, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        zk.create(path + "/election/", String.valueOf(serverId).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);

        zk.create(path + "/members/" + serverId, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }

    /*
        Elect the leader by picking the ZNode with the smallest sqno in the election folder,
        called at initialization and every time the current leader fails.
    */
    private void electLeader() throws KeeperException, InterruptedException {
        synchronized (lock) {
            String path = String.format("/%d", shardId);
            List<String> children = zk.getChildren(path + "/election", false); // watch
            Collections.sort(children);
            byte[] data = null;
            for (String leader : children) {
                data = zk.getData(path + "/election/" + leader, new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        try {
                            electLeader();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, null);
                if (data != null) {
                    int elected_leader = Integer.parseInt(new String(data));
                    if (elected_leader == serverId) { // I'm the leader
                        isLeader = true;
                        // write the leader id into dedicated folder
                        Stat s = zk.exists(path + "/election/leader", false); // watch
                        if (s == null) {
                            zk.create(path + "/election/leader", data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                        } else {
                            zk.setData(path + "/election/leader", data, s.getVersion());
                        }
                    }
                    break;
                }
            }
            if (isLeader) {
                UpdatesService.getInstance().sendUpdateRequest();
            }
        }
    }

    // get the current leader of the shard with shard_id
//    @SneakyThrows
    public int getLeader(int shard_id) throws KeeperException, InterruptedException {
        String path = String.format("/%s/election/leader", shard_id);
        byte[] data = zk.getData(path, false, null);
        return Integer.parseInt(new String(data));
    }

    //    @SneakyThrows
    public List<Integer> getShardMembers() throws KeeperException, InterruptedException {
        String path = String.format("/%s/members", shardId);
        List<String> children = zk.getChildren(path, false);
        return children.stream().map(Integer::parseInt).filter(c -> c != serverId).collect(Collectors.toList());
    }

    //    @SneakyThrows
    public List<Integer> getShardLeaders() throws KeeperException, InterruptedException {
        List<Integer> leaders = new ArrayList<>();
        for (int s = 1; s <= ConfigurationManager.NUM_OF_SHARDS; s++) {
            leaders.add(getLeader(s));
        }
        return leaders;
    }

//    // get the final status of the transaction
//    @SneakyThrows
//    public String get2PCStatus(UUID id){
//        String path = String.format("/%d/shared-txn/%s", shardId, id);
//        byte [] decision = zk.getData(path + "/decision", false, null);
//        // set watch inorder to delete the transaction
//        //zk.getChildren(path, new TPCDeleteTxn(id.toString()));
//        return new String(decision);
//    }

    @SneakyThrows
    public Map<Integer, String> get2PCResponses(UUID id, List<Integer> shards){
        String path = String.format("/%d/shared-txn/%s", shardId, id);
        Map<Integer, String> responses = new HashMap<>();
        for (Integer shard : shards) {
            byte[] data = zk.getData(path + "/" + shard, false, null);
            responses.put(shard, data == null ? null : new String(data));
        }
        return responses;
    }

    public class TPCDeleteTxn implements Watcher{
        String txnId;
        CountDownLatch finishSignal;

        public TPCDeleteTxn(String id, CountDownLatch finishSignal) {
            txnId = id;
            this.finishSignal = finishSignal;
        }

        @SneakyThrows
        public void process(WatchedEvent event) {
            String path = String.format("/%d/shared-txn/%s", shardId, txnId);
            try {
                List<String> children = zk.getChildren(path, this);
                // only "decision" node left
                if (children.size() <= 1) {
                    zk.delete(path + "/decision", -1);
                    zk.delete(path, -1);
                    finishSignal.countDown();
                }
            }
            catch (Exception e){
                finishSignal.countDown();
            }
        }
    }

    @SneakyThrows
    public void write2PCResult(UUID txnId, byte [] status, CountDownLatch finishSignal){
        String path = String.format("/%d/shared-txn/%s", shardId, txnId);
        zk.setData(path + "/decision", status, -1);
        // set watch inorder to delete the transaction
        try {
            List<String> children = zk.getChildren(path, new TPCDeleteTxn(txnId.toString(), finishSignal));
            if (children.size() <= 1) {

                // only the decision node left
                zk.delete(path + "/decision", -1);
                zk.delete(path, -1);
                finishSignal.countDown();
            }
        }
        catch (Exception e){
            finishSignal.countDown();
        }
    }

    // create the zk nodes before sending path planning request
    @SneakyThrows
    public void initiate2PC(UUID id){
        String path = String.format("/%d/shared-txn", shardId);
        checkZNode(path, false, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        checkZNode(path + "/" + id.toString(), false, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zk.create(path + "/" + id.toString() + "/decision", new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        if (zk.exists(path + "/" + id.toString() + "/decision", false) == null) {
            zk.create(path + "/" + id.toString() + "/decision" , new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }
        else{
            zk.setData(path + "/" + id.toString() + "/decision", new byte[]{}, -1);
        }
//            zk.getChildren(path + "/" + id.toString(), new TPCWatcher(finishSignal, id.toString()));
    }

//    public class TPCWatcher implements Watcher{
//        Set <String> committed;
//        CountDownLatch finishSignal;
//        String txnId;
//
//        public TPCWatcher(CountDownLatch finishSignal, String id){
//            committed = new HashSet<>();
//            this.finishSignal = finishSignal;
//            txnId = id;
//        }
//
//        @SneakyThrows
//        public void process(WatchedEvent event){
//            String path = String.format("/%d/shared-txn/%s", shardId, txnId);
//            Set<String> children = new HashSet<>(zk.getChildren(path, false));
//            if (!Sets.difference(committed, children).isEmpty()){
//                // some process failed before we reached a decision
//                zk.setData(path + "/decision", "ABORT".getBytes(), -1);
//                finishSignal.countDown();
//            }
//            for(String c : Sets.difference(children, committed)){
//                byte[] data = zk.getData(path + "/" + c, false, null);
//                String msg = new String(data);
//                if (msg.equals("COMMIT")){
//                    committed.add(new String(data));
//                }
//                else if (msg.equals("ABORT")){
//                    zk.setData(path + "/decision", "ABORT".getBytes(), -1);
//                    finishSignal.countDown();
//                    return;
//                }
//            }
//            if(committed.size() == ConfigurationManager.NUM_OF_SHARDS - 1){
//                zk.setData(path + "/decision", "COMMIT".getBytes(), -1);
//                finishSignal.countDown();
//            }
//            else{
//                zk.getChildren(path, this);
//            }
//        }
//    }

    @SneakyThrows
    public void response2PC(UUID pathId, int senderShardId, byte[] response, List<UUID> drives){
        String path = String.format("/%d/shared-txn/%s/", senderShardId, pathId.toString());
        zk.create(path + shardId, response, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        String msg = new String(response);
        // wait to see if the transaction is declared committed
        zk.exists(path + "decision",
                new TPCResponseWatcher(drives, pathId.toString(), senderShardId, msg.equals("COMMIT")));
    }

    public class TPCResponseWatcher implements Watcher{
        List <UUID> drives;
        String txnId;
        int senderShardId;
        boolean committed;

        public TPCResponseWatcher(List <UUID> drives, String txnId, int senderShardId, boolean committed){
            this.drives = drives;
            this.txnId = txnId;
            this.senderShardId = senderShardId;
            this.committed = committed;
        }

        @SneakyThrows
        public void process(WatchedEvent event){
            String path = String.format("/%d/shared-txn/%s/", senderShardId, txnId);
            if (committed) {
                if (event.getType() == NodeDeleted) {
                    logger.info("NodeDeleted");
                    // the transaction leader failed before declaring commit/abort
                    DriveRepository.getInstance().releaseDrives(drives);
                } else {
                    byte[] data = zk.getData(path + "decision", false, null);
                    String msg = new String(data);
                    if (msg.equals("ABORT")) {
                        DriveRepository.getInstance().releaseDrives(drives);
                    } else if (msg.equals("COMMIT")) {
                        for (UUID driveId : drives) {
                            Drive drive = DriveRepository.getInstance().getDrive(driveId);
                            logger.info(String.format("updating drive with taken seats: %d", drive.getTaken()));
                            driveReplicationService.replicateToAllMembers(drive);
                        }
                    }
                }
            }
            zk.delete(path + shardId, -1);
        }
    }

    public void process(WatchedEvent watchedEvent) {
        final Event.EventType eventType = watchedEvent.getType();
        if (eventType == Event.EventType.None) {
            // connection loss
        }
    }

}
