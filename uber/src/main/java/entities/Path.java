package entities;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class Path extends BaseEntity{

    @Getter @Setter
    private User passenger;

    @Getter @Setter
    private Date departureDate;

    @Getter @Setter
    private List<City> cities;

    @Getter @Setter
    private boolean satisfied = false;

    @Getter @Setter
    private Map<AbstractMap.SimpleEntry<City, City>, UUID> rides = new LinkedHashMap<>();

    public Path(User passenger, Date departureDate, List<City> cities) {
        this.passenger = passenger;
        this.departureDate = departureDate;
        this.cities = cities;
        for (int i = 0; i < cities.size()-1; i++) {
            this.rides.put(new AbstractMap.SimpleEntry<>(cities.get(i), cities.get(i+1)), null);
        }
    }

    public Path() {

    }

    public void addRide(City src, City dst, Drive ride) {
        rides.put(new AbstractMap.SimpleEntry<>(src, dst), ride.getId());
    }

}
