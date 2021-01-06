package host.controllers;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import entities.Drive;
import generated.City;
import generated.PublishDriveGrpc;
import generated.User;
import host.PublishDriveServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.web.bind.annotation.*;
import repositories.DriveRepository;

import javax.xml.crypto.Data;
import java.util.Date;
import java.util.logging.Logger;

@RestController
public class DriveController {
    private static final Logger logger = Logger.getLogger(DriveController.class.getName());

    private static final DriveRepository repository = new DriveRepository();

    @PostMapping("/drives")
    Drive newRide(@RequestBody Drive newDrive) {
        String target = "localhost:7070";
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        PublishDriveGrpc.PublishDriveBlockingStub blockingStub = PublishDriveGrpc.newBlockingStub(channel);
        generated.Drive drive = getDrive(newDrive);
        Empty empty = blockingStub.redirectDrive(drive);
        logger.info("controller sent a publish drive request");
        return repository.save(newDrive);
    }

    private generated.Drive getDrive(Drive newDrive) {
        return generated.Drive.newBuilder()
                .setDriver(getUser(newDrive.getDriver()))
                .setStartingPoint(newDrive.getStartingPoint().getProtoType())
                .setEndingPoint(newDrive.getEndingPoint().getProtoType())
                .setDepartureDate(Timestamp.newBuilder().setSeconds(newDrive.getDepartureDate().getTime()).build())
                .setVacancies(newDrive.getVacancies())
                .setTaken(0)
                .build();
    }

    private generated.User getUser(entities.User user) {
        return User.newBuilder().setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setPhoneNumber(user.getPhoneNumber()).build();
    }
}
