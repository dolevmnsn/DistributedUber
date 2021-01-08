package host;

import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DistributionManager {
    public static boolean elected;
    public static long leaderId;
    public static List<Long> members;
    public static Map<Long, Long> shardLeaders; // shard_id -> leader_id

}
