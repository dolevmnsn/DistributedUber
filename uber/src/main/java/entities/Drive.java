package entities;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

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
    private Integer vacancies;

    @Getter @Setter
    private Integer taken = 0;

    @Getter @Setter
    private int permittedDeviation;

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
