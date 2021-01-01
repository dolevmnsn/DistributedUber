package host.controllers;

import entities.Ride;
import org.springframework.web.bind.annotation.*;
import repositories.RideRepository;

import java.util.List;

public class RideController {

    private static final RideRepository repository = new RideRepository();

    @PostMapping("/rides")
    Ride newRide(@RequestBody Ride newRide) {
        return repository.save(newRide);
    }
}
