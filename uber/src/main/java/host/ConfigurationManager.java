package host;

public abstract class ConfigurationManager {
    public static Integer SERVER_ID = Integer.valueOf(System.getenv("SERVER_ID"));
//    public static Integer _PORT = Integer.valueOf(System.getenv("GRPC_PORT") != null ? System.getenv("GRPC_PORT") : "7070");
    public static Integer DRIVE_GRPC_PORT = Integer.valueOf(System.getenv("DRIVE_GRPC_PORT") != null ? System.getenv("DRIVE_GRPC_PORT") : "7070");
    public static Integer NUM_OF_SHARDS = Integer.valueOf(System.getenv("NUM_OF_SHARDS") != null ? System.getenv("NUM_OF_SHARDS") : "2");
}
