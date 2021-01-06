package host;

import org.apache.zookeeper.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Omega implements Watcher {
    static ZooKeeper zk;
    static String shardId;
    static int serverId;
    static int elected;
    final Object lock = new Object();

    public Omega(String zkHost, String shard_id, int id) {
        try {
            zk = new ZooKeeper(zkHost, 3000, this);
            shardId = String.format("/%s", shard_id);
            serverId = id;
            elected = -1;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void propose() throws KeeperException, InterruptedException {
        if (zk.exists(shardId, true) == null) {
            zk.create(shardId, new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        zk.create(shardId + "/", String.valueOf(serverId).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    public void electLeader() throws KeeperException, InterruptedException {
        synchronized (lock) {
            List<String> children = zk.getChildren(shardId, true);
            Collections.sort(children);
            byte[] data = null;
            for (String leader : children) {
                data = zk.getData(shardId + "/" + leader, true , null);
                if (data != null) {
                    break;
                }
            }
            if (data != null) {
                elected = Integer.parseInt(new String(data));
            }
        }
    }

    public int getLeader() {
        synchronized (lock) {
            return elected;
        }
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
