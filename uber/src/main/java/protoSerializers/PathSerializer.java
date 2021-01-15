package protoSerializers;

import entities.City;
import entities.Path;
import generated.Ride;

import java.util.*;
import java.util.stream.Collectors;

public class PathSerializer implements Serializer<Path, generated.Path>{
    private final Serializer<entities.User, generated.User> userSerializer;

    public PathSerializer(Serializer<entities.User, generated.User> userSerializer) {
        this.userSerializer = userSerializer;
    }

    @Override
    public generated.Path serialize(Path path) {
        return generated.Path.newBuilder()
                .setId(path.getId().toString())
                .setRevision(path.getRevision())
                .setPassenger(userSerializer.serialize(path.getPassenger()))
                .setDepartureDate(String.valueOf(path.getDepartureDate().getTime()))
                .addAllCities(path.getCities().stream().map(City::getProtoType).collect(Collectors.toList()))
                .setSatisfied(path.isSatisfied())
                .addAllRides(serializeRides(path.getRides()))
                .build();
    }

    @Override
    public entities.Path deserialize(generated.Path generatedPath) {
        Path path = new Path();
        path.setId(UUID.fromString(generatedPath.getId()));
        path.setRevision(generatedPath.getRevision());
        path.setPassenger(userSerializer.deserialize(generatedPath.getPassenger()));
        path.setDepartureDate(new Date(Long.parseLong(generatedPath.getDepartureDate())));
        path.setSatisfied(generatedPath.getSatisfied());
        path.setCities(generatedPath.getCitiesList().stream().map(city -> City.valueOf(city.toString())).collect(Collectors.toList()));
        path.setRides(deserializeRides(generatedPath.getRidesList()));

        return path;
    }

    private Map<AbstractMap.SimpleEntry<City, City>, UUID> deserializeRides(List<Ride> genRidesList) {
        Map<AbstractMap.SimpleEntry<City, City>, UUID> rides = new LinkedHashMap<>();

        genRidesList.forEach(genRide ->
                rides.put(new AbstractMap.SimpleEntry<>(City.valueOf(genRide.getSrc().toString()), City.valueOf(genRide.getDst().toString())),
                        genRide.getDriveId().equals("0") ? null : UUID.fromString(genRide.getDriveId())));

        return rides;
    }

    private List<Ride> serializeRides (Map<AbstractMap.SimpleEntry<City, City>, UUID> rides) {
        List<Ride> genRides = new ArrayList<>();

        rides.forEach((stc_dst, driveId) -> genRides.add(serializeRide(stc_dst, driveId)));

        return genRides;
    }

    private Ride serializeRide(AbstractMap.SimpleEntry<City, City> stc_dst, UUID driveId) {
        return Ride.newBuilder()
                .setSrc(stc_dst.getKey().getProtoType())
                .setDst(stc_dst.getValue().getProtoType())
                .setDriveId(driveId != null ? driveId.toString() : "0")
                .build();
    }
}
