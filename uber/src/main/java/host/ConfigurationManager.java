package host;

public abstract class ConfigurationManager {
    public static Integer SERVER_ID = Integer.valueOf(System.getenv("SERVER_ID"));
    public static Integer SHARD_ID = Integer.valueOf(System.getenv("SHARD_ID"));
    public static String ZK_HOST = System.getenv("ZK_HOST");
    public static Integer GRPC_PORT = Integer.valueOf(System.getenv("GRPC_PORT") != null ? System.getenv("GRPC_PORT") : "7070");
    public static Integer NUM_OF_SHARDS = Integer.valueOf(System.getenv("NUM_OF_SHARDS") != null ? System.getenv("NUM_OF_SHARDS") : "2");
    public static Integer PLAN_PATH_RETRIES = Integer.valueOf(System.getenv("PLAN_PATH_RETRIES") != null ? System.getenv("PLAN_PATH_RETRIES") : "10");
}
