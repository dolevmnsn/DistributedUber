package host;

import com.google.protobuf.Empty;
import generated.Drive;
import generated.PublishDriveGrpc;
import io.grpc.stub.StreamObserver;

import java.util.logging.Logger;

public class PublishDriveService extends PublishDriveGrpc.PublishDriveImplBase {
    private static final Logger logger = Logger.getLogger(PublishDriveService.class.getName());

    @Override
    public void redirectDrive(Drive request, StreamObserver<Empty> responseObserver) {
        // TODO: add a new drive to zk and return ack
        logger.info("got a redirectDrive message");
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
