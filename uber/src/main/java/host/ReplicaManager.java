package host;

import Services.DriveReplicationService;
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
import java.util.concurrent.locks.ReentrantLock;
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
    //static Object lastOp;
    final Object lock = new Object();
    //static int shardSize;
    //static int numOfShards;
    //public static List<Integer> members;
    DriveReplicationService driveReplicationService;


    private ReplicaManager() {
        try {
            zk = new ZooKeeper(ConfigurationManager.ZK_HOST, 3000, this);
            shardId = ConfigurationManager.SHARD_ID;
            serverId = ConfigurationManager.SERVER_ID;
            isLeader = false;
            replicaVersion = 0;
            driveReplicationService = new DriveReplicationService(new DriveSerializer(new UserSerializer()));

            registerServer(); // register server to the shard
            electLeader(); // elect a shard leader

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ReplicaManager getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new ReplicaManager();
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
                            // Optional<Drive> newestDriveTimestamp = DriveRepository.getInstance().getAll().stream().max(Comparator.comparing(Drive::getLastModified));
                            // newestDriveTimestamp.orElseGet(() -> new Drive().)
                            // Long newestPathTimestamp = PathRepository.getInstance().getAll().stream().map(Path::getLastModified).max();
                            // todo: write to zk max(drive, path)
                            electLeader();
                        }
                        catch (Exception e){
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
                        }
                        else {
                            zk.setData(path + "/election/leader", data, s.getVersion());
                        }
                    }
                    break;
                }
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

    // get the final status of the transaction
    @SneakyThrows
    public String get2PCStatus(UUID id){
        String path = String.format("/%d/shared-txn/%s", shardId, id);
        byte [] decision = zk.getData(path + "/decision", false, null);
        // set watch inorder to delete the transaction
        //zk.getChildren(path, new TPCDeleteTxn(id.toString()));
        return new String(decision);
    }

    // abort the 2pc transaction
    @SneakyThrows
    public void abort2PC(UUID txnId){
        String path = String.format("/%d/shared-txn/%s", shardId, txnId);
        zk.setData(path + "decision", "ABORT".getBytes(), -1);
        // set watch inorder to delete the transaction
        //zk.getChildren(path, new TPCDeleteTxn(txnId.toString()));
    }

    // create the zk nodes before sending respone from ther shards
    @SneakyThrows
    public void initiate2PC(UUID id, CountDownLatch finishSignal){
        String path = String.format("/%d/shared-txn", shardId);
        checkZNode(path, false, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zk.create(path + "/" + id.toString(), new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zk.create(path + "/" + id.toString() + "/decision" , new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        zk.getChildren(path + "/" + id.toString(), new TPCWatcher(finishSignal, id.toString()));
    }

    public class TPCWatcher implements Watcher{
        Set <String> committed;
        CountDownLatch finishSignal;
        String txnId;

        public TPCWatcher(CountDownLatch finishSignal, String id){
            committed = new HashSet<>();
            this.finishSignal = finishSignal;
            txnId = id;
        }

        @SneakyThrows
        public void process(WatchedEvent event){
            String path = String.format("/%d/shared-txn/%s", shardId, txnId);
            Set<String> children = new HashSet<>(zk.getChildren(path, false));
            if (!Sets.difference(committed, children).isEmpty()){
                // some process failed before we reached a decision
                zk.setData(path + "/decision", "ABORT".getBytes(), -1);
                finishSignal.countDown();
            }
            for(String c : Sets.difference(children, committed)){
                byte[] data = zk.getData(path + "/" + c, false, null);
                String msg = new String(data);
                if (msg.equals("COMMIT")){
                    committed.add(new String(data));
                }
                else if (msg.equals("ABORT")){
                    zk.setData(path + "/decision", "ABORT".getBytes(), -1);
                    finishSignal.countDown();
                    return;
                }
            }
            if(committed.size() == ConfigurationManager.NUM_OF_SHARDS - 1){
                zk.setData(path + "/decision", "COMMIT".getBytes(), -1);
                finishSignal.countDown();
            }
            else{
                zk.getChildren(path, this);
            }
        }
    }

    public class TPCDeleteTxn implements Watcher{
        String txnId;

        public TPCDeleteTxn(String id) {
            txnId = id;
        }

        @SneakyThrows
        public void process(WatchedEvent event) {
            String path = String.format("/%d/shared-txn/%s", shardId, txnId);
            List<String> children = zk.getChildren(path, false);
            // only "decision" node left
            if(children.size() == 1){
                zk.delete(path + "/decision",-1);
                zk.delete(path,-1);
            }
            // continue waiting
            else {
                zk.getChildren(path, true);
            }
        }
    }

    @SneakyThrows
    public void response2PC(UUID pathId, int senderShardId, byte[] response, List<UUID> drives){
        String path = String.format("/%d/shared-txn/%s/", senderShardId, pathId.toString());
        zk.create(path + shardId , response, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        String msg = new String(response);
        // wait to see if the transaction is declared committed
        if (msg.equals("COMMIT")){
            zk.exists(path + "decision", new TPCResponseWatcher(drives));
        }
    }

    public class TPCResponseWatcher implements Watcher{
        List <UUID> drives;

        public TPCResponseWatcher(List <UUID> drives){
            this.drives = drives;
        }

        @SneakyThrows
        public void process(WatchedEvent event){
            logger.info("process");
            if (event.getType() == NodeDeleted){
                logger.info("NodeDeleted");
                // the transaction leader failed before declaring commit/abort
                DriveRepository.getInstance().releaseDrives(drives);
            }
            else {
                logger.info("ABORT OR COMMIT");
                byte[] data = zk.getData(event.getPath(), false, null);
                String msg = new String(data);
                if (msg.equals("ABORT")) {
                    DriveRepository.getInstance().releaseDrives(drives);
                } else if (msg.equals("COMMIT")) {
                    logger.info("wrote COMMIT");
                    for (UUID drive : drives) {
                        Drive drive1 = DriveRepository.getInstance().getDrive(drive);
                        logger.info(String.format("updating drive with taken seats: %d", drive1.getTaken()));
                        driveReplicationService.replicateToAllMembers(drive1);
                    }
                }
            }
        }
    }

    public void process(WatchedEvent watchedEvent) {
        final Event.EventType eventType = watchedEvent.getType();
        if (eventType == Event.EventType.None) {
            // connection loss
        }
    }

}
