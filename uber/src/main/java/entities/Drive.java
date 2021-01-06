package entities;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Drive {
    private static long counter = 0;
    private static final Long server_id = Long.valueOf(System.getenv("SERVER_ID"));

    private static Pair<Long, Long> getSerialNumber() {
        Pair<Long, Long> serialNumber = new Pair<>(server_id, counter);
        counter++;
        return serialNumber;
    }

    @Getter
    private Pair<Long, Long> SN;

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

    private final List<User> passengers;

    @Getter @Setter
    private int permittedDeviation;

    public Drive(String firstName, String lastName, String phoneNumber, City startingPoint, City endingPoint, Date departureDate, int vacancies, int permittedDeviation) {
        this.SN = getSerialNumber();
        this.driver = new User(firstName, lastName, phoneNumber);
        this.startingPoint = startingPoint;
        this.endingPoint = endingPoint;
        this.departureDate = departureDate;
        this.vacancies = vacancies;
        this.passengers = Arrays.asList(new User[vacancies]); // limited size list
        this.permittedDeviation = permittedDeviation;
    }

    public List<User> getPassengers() {
        return passengers;
    }

    public void addPassenger(User user, int index) {
        this.passengers.set(index, user);
    }
}
