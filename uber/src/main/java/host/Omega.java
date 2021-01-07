package host;

import org.apache.zookeeper.*;
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
    public static List<Integer> shardLeaders; // shard_id[1,...,NUM_OF_SHARDS] -> leader_id
    final Object lock = new Object();

    public Omega(String zkHost, int shard_id, int server_id) {
        try {
            zk = new ZooKeeper(zkHost, 3000, this);
            shardId = shard_id;
            shardId = server_id;
            path = String.format("/%d", shard_id);
            isLeader = false;
            shardSize = Integer.parseInt(System.getenv("SHARD_SIZE"));
            numOfShards = Integer.parseInt(System.getenv("NUM_OF_SHARDS"));

            members = new ArrayList<Boolean>(Collections.nCopies(shardSize, Boolean.TRUE));
            shardLeaders = new ArrayList<Integer>(Collections.nCopies(numOfShards, -1));

            propose();
            electLeader();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
        Create the shard folder is needed and add a znode representing the server
     */
    public void propose() throws KeeperException, InterruptedException {
        if (zk.exists(path, true) == null) {
            zk.create(path, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        zk.create(path + "/", String.valueOf(serverId).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    /*
        Elect the leader by picking the znode with the smallest sqno in the shard folder.
        If I'm the elected leader, start managing inner shard membership and global shard membership.
        Called at initialization and every time the current leader fails.
    */
    public void electLeader() throws KeeperException, InterruptedException {
        synchronized (lock) {
            List<String> children = zk.getChildren(path, true);
            Collections.sort(children);
            byte[] data = null;
            for (String leader : children) {
                data = zk.getData(path + "/" + leader, true , null);
                if (data != null) {
                    break;
                }
            }
            if (data != null) {
                shardLeaders.add(shardId, Integer.parseInt(new String(data)));
            }
        }
    }

    /* get the current leader of the shard with shard_id */
    public int getLeader(int shard_id) {
        synchronized (lock) {
            return shardLeaders.get(shard_id);
        }
    }

    /* Only for the shard leader to use. Publish an operation to all the shard members */
    public static void commitOperation(Object o){

    }

    public void process(WatchedEvent watchedEvent) {
        final Event.EventType eventType = watchedEvent.getType();
        try {
            electLeader();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
