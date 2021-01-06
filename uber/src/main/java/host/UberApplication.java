package host;

import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class UberApplication {

    public static void main(String[] args) throws Exception {

        SpringApplication.run(UberApplication.class, args);
        PublishDriveServer publishDriveServer = new PublishDriveServer(Integer.parseInt(System.getenv("PUBLISH_DRIVE_SERVER_PORT")));
        publishDriveServer.start();
        System.out.println("Server started");
        publishDriveServer.blockUntilShutdown();
//        ServerBuilder.forPort(Integer.parseInt(System.getenv("PORT")));
//        RouteGuideServer server = new RouteGuideServer(8980);
//        server.start();
//        System.out.println("Server started");
//        server.blockUntilShutdown();
    }

    public static void initServer() {

    }
}
