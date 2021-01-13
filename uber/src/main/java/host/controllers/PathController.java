package host.controllers;

import Services.PathPlanningService;
import Services.PathReplicationService;
import entities.City;
import entities.Path;
import entities.User;
import host.CityShardDistributor;
import host.ConfigurationManager;
import host.ReplicaManager;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import protoSerializers.DriveSerializer;
import protoSerializers.PathSerializer;
import protoSerializers.UserSerializer;
import repositories.PathRepository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@RestController
public class PathController {
    private final Logger logger = Logger.getLogger(PathController.class.getName());

    private PathReplicationService pathReplicationService;
    private PathPlanningService pathPlanningService;
    private PathRepository pathRepository;
    private ReplicaManager replicaManager;


    public static class PathWrapper {
        @Getter
        @Setter
        private User passenger;

        @Getter @Setter
        private Date departureDate;

        @Getter @Setter
        private List<City> cities = new ArrayList<>();
    }

    @PostConstruct
    public void initialize() {
        UserSerializer userSerializer = new UserSerializer();
        DriveSerializer driveSerializer = new DriveSerializer(userSerializer);
        PathSerializer pathSerializer = new PathSerializer(userSerializer);
        this.pathPlanningService = new PathPlanningService(pathSerializer, driveSerializer);
        this.pathReplicationService = new PathReplicationService(pathSerializer);
        this.pathRepository = PathRepository.getInstance();
        this.replicaManager = ReplicaManager.getInstance();
    }

    @PostMapping("/paths")
    Path newPath(@RequestBody PathWrapper newPathWrapper) {
        Path newPath = new Path(newPathWrapper.getPassenger(), newPathWrapper.getDepartureDate(), newPathWrapper.getCities());
        UUID uuid = UUID.randomUUID();
        newPath.setId(uuid);

        Integer shardId = CityShardDistributor.getShardIdByCity(newPath.getCities().get(0));
//        int leader = 1; // todo: get real leader
        int leader = -1;
        try {
            leader = replicaManager.getLeader(shardId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (leader == ConfigurationManager.SERVER_ID) {
            Path plannedPath = pathPlanningService.planPath(newPath);

            logger.info(String.format("server-%d saved new path", ConfigurationManager.SERVER_ID));
            pathRepository.save(plannedPath);
            pathReplicationService.replicateToAllMembers(newPath);
        } else {
            pathReplicationService.sendPath(newPath, leader, false);
        }

        return newPath;
    }
}
