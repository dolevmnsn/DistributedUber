package protoSerializers;


import com.google.protobuf.Timestamp;
import entities.City;
import entities.Drive;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DriveSerializer implements Serializer<entities.Drive, generated.Drive> {
    private final Serializer<entities.User, generated.User> userSerializer;

    public DriveSerializer(Serializer<entities.User, generated.User> userSerializer) {
        this.userSerializer = userSerializer;
    }

    @Override
    public generated.Drive serialize(entities.Drive drive) {
        return generated.Drive.newBuilder()
                .setId(drive.getId().toString())
                .setDriver(userSerializer.serialize(drive.getDriver()))
                .setStartingPoint(drive.getStartingPoint().getProtoType())
                .setEndingPoint(drive.getEndingPoint().getProtoType())
//                .setDepartureDate(Timestamp.newBuilder().setSeconds(drive.getDepartureDate().getTime()).build())
                .setDepartureDate(String.valueOf(drive.getDepartureDate().getTime()))
                .setVacancies(drive.getVacancies())
                .setTaken(drive.getTaken())
                .build();
    }

    @Override
    public entities.Drive deserialize(generated.Drive generatedDrive) {
//        long millis = TimeUnit.MILLISECONDS.convert(generatedDrive.getDepartureDate().getNanos(), TimeUnit.NANOSECONDS);

        Drive drive = new Drive();
        drive.setId(UUID.fromString(generatedDrive.getId()));
        drive.setDriver(userSerializer.deserialize(generatedDrive.getDriver()));
//        drive.setDepartureDate(new Date(millis));
        drive.setDepartureDate(new Date(Long.parseLong(generatedDrive.getDepartureDate())));
        drive.setStartingPoint(City.valueOf(generatedDrive.getStartingPoint().toString()));
        drive.setEndingPoint(City.valueOf(generatedDrive.getEndingPoint().toString()));
        drive.setVacancies(generatedDrive.getVacancies());
        drive.setPermittedDeviation(generatedDrive.getPermittedDeviation());

        return drive;
    }
}
