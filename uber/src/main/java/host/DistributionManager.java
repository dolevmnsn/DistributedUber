//package host;
//
//import org.apache.zookeeper.*;
//
//import java.io.IOException;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//
//public class DistributionManager {
//    private static ZooKeeper zk;
//    public static long shardId;
//    public static long serverId;
//
//    public static boolean elected;
//    public static long leaderId;
//    public static List<Long> members;
//    public static Map<Long, Long> shardLeaders; // shard_id -> leader_id
//
//    final Object lock = new Object();
//
//    public DistributionManager() {
//        try {
//            zk = new ZooKeeper(System.getenv("ZK_HOST"), 3000, null);
//            shardId = Long.parseLong(System.getenv("SHARD_ID"));
//            serverId = Long.parseLong(System.getenv("SERVER_ID"));
//            elected = false;
//
//            if (zk.exists(String.format("/%d", shardId), false) == null) {
//                zk.create(String.format("/%d", shardId), new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
//            }
//            zk.create(String.format("/%d/", shardId), String.valueOf(serverId).getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
//                    CreateMode.EPHEMERAL_SEQUENTIAL);
//
//        } catch (IOException | KeeperException | InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public void electLeader() throws KeeperException, InterruptedException {
//        synchronized (lock) {
//            List<String> children = zk.getChildren(String.format("/%d", shardId), false);
//            Collections.sort(children);
//            byte[] data = null;
//            for (String leader : children) {
//                data = zk.getData(String.format("/%d/%s", shardId, leader), true , null);
//                if (data != null) {
//                    break;
//                }
//            }
//            if (data != null) {
//                elected = Integer.parseInt(new String(data));
//            }
//        }
//    }
//
//    private static class ShardWatcher implements Watcher {
//
//        @Override
//        public void process(WatchedEvent event) {
//
//        }
//    }
//
//    private static class ServerWatcher implements Watcher {
//
//        @Override
//        public void process(WatchedEvent event) {
//
//        }
//    }
//}
