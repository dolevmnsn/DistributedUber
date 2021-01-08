package entities;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Path extends BaseEntity{

    @Getter @Setter
    private User passenger;

    @Getter
    private final List<City> cities = new ArrayList<>();

    @Getter @Setter
    private boolean satisfied = false;

    @Getter
    private final Map<Pair<City, City>, Drive> rides = new HashMap<>();


    public void addRide(City src, City dst, Drive ride) {
        rides.put(new Pair<>(src, dst), ride);
    }
}
