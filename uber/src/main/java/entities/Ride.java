package entities;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Ride {
    private static class User {
        private String firstName;
        private String lastName;
        private String phoneNumber;


        public User(String firstName, String lastName, String phoneNumber) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.phoneNumber = phoneNumber;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return Objects.equals(firstName, user.firstName) && Objects.equals(lastName, user.lastName) && Objects.equals(phoneNumber, user.phoneNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstName, lastName, phoneNumber);
        }
        @Override
        public String toString() {
            return "User{" +
                    "firstName='" + firstName + '\'' +
                    ", lastName='" + lastName + '\'' +
                    ", phoneNumber='" + phoneNumber + '\'' +
                    '}';
        }

    }

    private User driver;
    private City startingPoint;
    private City endingPoint;
    private Date departureDate;
    private int vacancies;
    private final List<User> passengers;
    private int permittedDeviation;

    public Ride(String firstName, String lastName, String phoneNumber, City startingPoint, City endingPoint, Date departureDate, int vacancies, int permittedDeviation) {
        this.driver = new User(firstName, lastName, phoneNumber);
        this.startingPoint = startingPoint;
        this.endingPoint = endingPoint;
        this.departureDate = departureDate;
        this.vacancies = vacancies;
        this.passengers = Arrays.asList(new User[vacancies]); // limited size list
        this.permittedDeviation = permittedDeviation;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

    public City getStartingPoint() {
        return startingPoint;
    }

    public void setStartingPoint(City startingPoint) {
        this.startingPoint = startingPoint;
    }

    public City getEndingPoint() {
        return endingPoint;
    }

    public void setEndingPoint(City endingPoint) {
        this.endingPoint = endingPoint;
    }

    public Date getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(Date departureDate) {
        this.departureDate = departureDate;
    }

    public int getVacancies() {
        return vacancies;
    }

    public void setVacancies(int vacancies) {
        this.vacancies = vacancies;
    }

    public int getPermittedDeviation() {
        return permittedDeviation;
    }

    public void setPermittedDeviation(int permittedDeviation) {
        this.permittedDeviation = permittedDeviation;
    }

    public List<User> getPassengers() {
        return passengers;
    }

    public void addPassenger(User user, int index) {
        this.passengers.set(index, user);
    }
}
