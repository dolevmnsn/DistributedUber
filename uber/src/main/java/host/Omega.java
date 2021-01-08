package host;

import entities.Drive;
import entities.Path;
import lombok.SneakyThrows;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import repositories.DriveRepository;
import repositories.PathRepository;
import sun.font.TrueTypeFont;

import java.io.IOException;
import java.util.*;

public class Omega implements Watcher {
    static ZooKeeper zk;
    static int shardId;
    static int serverId;
    static boolean isLeader;
    static String path;
    static int shardSize;
    static int numOfShards;
    public static List<Boolean> members; // all shard members are up at the beginning

    // do we need this?
    public static List<Integer> shardLeaders; // shard_id[1,...,NUM_OF_SHARDS] -> leader_id
    final Object lock = new Object();

    public Omega(String zkHost, int shard_id, int server_id) {
        try {
            zk = new ZooKeeper(zkHost, 3000, this);
            shardId = shard_id;
            serverId = server_id;
            path = String.format("/%d", shard_id);
            isLeader = false;
            shardSize = Integer.parseInt(System.getenv("SHARD_SIZE"));
            numOfShards = Integer.parseInt(System.getenv("NUM_OF_SHARDS"));

            members = new ArrayList<Boolean>(Collections.nCopies(shardSize, Boolean.TRUE));
            shardLeaders = new ArrayList<Integer>(Collections.nCopies(numOfShards, -1));

            register();
            electLeader();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
        Create the shard folder is needed and add a znode representing the server
     */
    public void register() throws KeeperException, InterruptedException {
        if (zk.exists(path, false) == null) { // watch
            zk.create(path, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (zk.exists(path + "/election", false) == null) { // watch
            zk.create(path + "/election", new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        if (zk.exists(path + "/members", false) == null) { // watch
            zk.create(path + "/members", new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        zk.create(path + "/election/", String.valueOf(serverId).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);

        zk.create(path + "/members/" + serverId, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }

    /*
        Elect the leader by picking the znode with the smallest sqno in the shard folder.
        If I'm the elected leader, start managing inner shard membership and global shard membership.
        Called at initialization and every time the current leader fails.
    */
    public void electLeader() throws KeeperException, InterruptedException {
        synchronized (lock) {
            List<String> children = zk.getChildren(path + "/election", false); // watch
            Collections.sort(children);
            byte[] data = null;
            for (String leader : children) {
                data = zk.getData(path + "/election/" + leader, new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        try {
//                            Optional<Drive> newestDriveTimestamp = DriveRepository.getInstance().getAll().stream().max(Comparator.comparing(Drive::getLastModified));
//                            newestDriveTimestamp.orElseGet(() -> new Drive().)
//                            Long newestPathTimestamp = PathRepository.getInstance().getAll().stream().map(Path::getLastModified).max();
                            // todo: write to zk max(drive, path)
                            electLeader();
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }, null);
                if (data != null) {
                    break;
                }
            }
            if (data != null) {
                int leader = Integer.parseInt(new String(data));
                shardLeaders.add(shardId, leader);

                // I'm the leader
                if (leader == serverId){
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
                updateMembers();
            }
        }
    }

    public void updateMembers() throws KeeperException, InterruptedException {
        List<String> children = zk.getChildren(path + "/members", new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() != Event.EventType.None){
                    try {
                        updateMembers();
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        });

        // do this smarter? make sure how we use members list
        for(int i = 0 ; i < shardSize ; i++){
            if (members.get(i) && !children.contains(String.valueOf(i+1))){
                members.set(i, false);
            }
        }
    }

    /* get the current leader of the shard with shard_id */
    public static int getLeader(int shard_id) throws KeeperException, InterruptedException {
//        synchronized (lock) {
//            return shardLeaders.get(shard_id);
//        }
        // can we assume this exist?
        byte[] data = zk.getData(String.format("/%s/election/leader", shard_id), false, null);
        return Integer.parseInt(new String(data));
    }

    /* Only for the shard leader to use. Publish an operation to all the shard members */
    public static void commitOperation(Object o) {

    }

    public void process(WatchedEvent watchedEvent) {
        final Event.EventType eventType = watchedEvent.getType();
        if (eventType == Event.EventType.None) {
            // connection loss
        }
    }

}
