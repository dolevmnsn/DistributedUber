package host;

import lombok.SneakyThrows;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ReplicaManager implements Watcher {
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


    private ReplicaManager() {
        try {
            zk = new ZooKeeper(ConfigurationManager.ZK_HOST, 3000, this);
            shardId = ConfigurationManager.SHARD_ID;
            serverId = ConfigurationManager.SERVER_ID;
            isLeader = false;
            replicaVersion = 0;

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

    public void process(WatchedEvent watchedEvent) {
        final Event.EventType eventType = watchedEvent.getType();
        if (eventType == Event.EventType.None) {
            // connection loss
        }
    }

}
