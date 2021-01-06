package entities;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Path {
//    private static long counter = 0;
//    private static final Long server_id = Long.valueOf(System.getenv("SERVER_ID"));
//
//    private static Pair<Long, Long> getSerialNumber() {
//        Pair<Long, Long> serialNumber = new Pair<>(server_id, counter);
//        counter++;
//        return serialNumber;
//    }

    @Getter
    private final Pair<Long, Long> SN;

    @Getter @Setter
    private User passenger;

    private final List<City> cities;

    @Getter @Setter
    private boolean satisfied = false;

    @Getter
    private Map<Pair<City, City>, Drive> rides = new HashMap<>();

    public Path(Pair<Long, Long> SN, String firstName, String lastName, String phoneNumber, List<City> cities) {
        this.SN = SN;
        this.passenger = new User(firstName, lastName, phoneNumber);
        this.cities = new ArrayList<>(cities);
    }

    public void addRide(City src, City dst, Drive ride) {
        rides.put(new Pair<>(src, dst), ride);
    }
}
