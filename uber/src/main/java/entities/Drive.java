package entities;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Drive extends BaseEntity{

    @Getter @Setter
    private User driver;

    @Getter @Setter
    private City startingPoint;

    @Getter @Setter
    private City endingPoint;

    @Getter @Setter
    private Date departureDate;

    @Getter @Setter
    private int vacancies;

    @Getter @Setter
    private Long lastModified;

    private final List<User> passengers = new ArrayList<>(); // todo: fixed size

    @Getter @Setter
    private int permittedDeviation;

    public List<User> getPassengers() {
        return passengers;
    }

    public void addPassenger(User user, int index) {
        this.passengers.set(index, user);
    }
}
