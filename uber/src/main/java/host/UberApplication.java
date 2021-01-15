package host;

import GRPCServers.GrpcServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UberApplication {

    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(UberApplication.class, args);
        ReplicaManager.getInstance(); // init
        GrpcServer grpcServer = new GrpcServer(ConfigurationManager.GRPC_PORT);
        grpcServer.start();
        System.out.println("Server started");
        grpcServer.blockUntilShutdown();
//        ServerBuilder.forPort(Integer.parseInt(System.getenv("PORT")));
//        RouteGuideServer server = new RouteGuideServer(8980);
//        server.start();
//        System.out.println("Server started");
//        server.blockUntilShutdown();
    }

    public static void initServer() {

    }
}
