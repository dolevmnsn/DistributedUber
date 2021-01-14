package host.controllers;

import Services.DriveReplicationService;
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

    public static class PathWrapper {
        @Getter
        @Setter
        private User passenger;

        @Getter @Setter
        private Date departureDate;

        @Getter @Setter
        private List<City> cities = new ArrayList<>();
    }

    private PathReplicationService pathReplicationService;
    private PathPlanningService pathPlanningService;
    private PathRepository pathRepository;
    private ReplicaManager replicaManager;

    @PostConstruct
    public void initialize() {
        UserSerializer userSerializer = new UserSerializer();
        DriveSerializer driveSerializer = new DriveSerializer(userSerializer);
        PathSerializer pathSerializer = new PathSerializer(userSerializer);
        DriveReplicationService driveReplicationService = new DriveReplicationService(driveSerializer);
        this.pathPlanningService = new PathPlanningService(pathSerializer, driveSerializer, driveReplicationService);
        this.pathReplicationService = new PathReplicationService(pathSerializer);
        this.pathRepository = PathRepository.getInstance();
        this.replicaManager = ReplicaManager.getInstance();
    }

    @PostMapping("/paths")
    Path newPath(@RequestBody PathWrapper newPathWrapper) {
        logger.info("got a path planning request.");
        Path newPath = new Path(newPathWrapper.getPassenger(), newPathWrapper.getDepartureDate(), newPathWrapper.getCities());
        UUID uuid = UUID.randomUUID();
        newPath.setId(uuid);

        int shardId = CityShardDistributor.getShardIdByCity(newPath.getCities().get(0));
        int leader = -1;
        try {
            leader = replicaManager.getLeader(shardId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Path plannedPath = null;
        if (leader == ConfigurationManager.SERVER_ID) { // I'm the leader
            logger.info("I'm the leader. trying to plan path. (controller)");
            plannedPath = pathPlanningService.planPath(newPath);
            if (plannedPath.isSatisfied()) {
                logger.info("path was satisfied. saving and sending to the rest of the shard (controller)");
                pathRepository.save(plannedPath);
                pathReplicationService.replicateToAllMembers(plannedPath);
            }
        } else { // send to leader
            logger.info(String.format("I'm not the leader. sending the path to the relevant leader: %d", leader));
            plannedPath = pathReplicationService.sendPath(newPath, leader, false);
        }

        return plannedPath;
    }
}
