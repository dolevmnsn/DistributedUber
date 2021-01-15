package GRPCServers;

import Services.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import protoSerializers.UserSerializer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class GrpcServer {
    private static final Logger logger = Logger.getLogger(GrpcServer.class.getName());

    private final int port;
    private final Server server;

    public GrpcServer(int port) throws IOException {
        this(ServerBuilder.forPort(port), port);
    }

    /**
     * Create a RouteGuide server using serverBuilder as a base and features as data.
     */
    public GrpcServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        UserSerializer userSerializer = new UserSerializer();
        DriveSerializer driveSerializer = new DriveSerializer(userSerializer);
        DriveReplicationService driveReplicationService = new DriveReplicationService(driveSerializer);
        PathSerializer pathSerializer = new PathSerializer(userSerializer);
        PathReplicationService pathReplicationService = new PathReplicationService(pathSerializer);
        PathPlanningService pathPlanningService = new PathPlanningService(pathSerializer, driveSerializer, driveReplicationService);
        server = serverBuilder.addService(new GrpcService(driveReplicationService, driveSerializer, pathReplicationService, pathSerializer, pathPlanningService))
                .build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        try {
            server.start();
        }catch (Exception e){
            e.printStackTrace();
        }
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    GrpcServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
