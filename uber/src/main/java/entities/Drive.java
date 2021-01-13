package entities;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
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
//    @JsonFormat(pattern="yyyy-MM-dd")
    private Date departureDate;

    @Getter @Setter
    private Integer vacancies;

    @Getter @Setter
    private Integer taken = 0;

//    @Getter @Setter
//    private List<User> passengers = new ArrayList<>(); // todo: fixed size

    @Getter @Setter
    private int permittedDeviation;

//    public List<User> getPassengers() {
//        return passengers;
//    }

//    public void addPassenger(User user, int index) {
//        this.passengers.set(index, user);
//    }

    public boolean increaseTaken() {
        if (taken + 1 <= vacancies) {
            taken++;
            return true;
        }

        return false;
    }

    public boolean decreaseTaken() {
        if (taken - 1 >= 0) {
            taken--;
            return true;
        }

        return false;
    }
}
