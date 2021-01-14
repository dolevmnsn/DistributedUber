package host.controllers;

import Services.DriveReplicationService;
import entities.Drive;
import host.CityShardDistributor;
import host.ConfigurationManager;
import host.ReplicaManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import protoSerializers.DriveSerializer;
import protoSerializers.UserSerializer;
import repositories.DriveRepository;

import javax.annotation.PostConstruct;
import java.util.UUID;
import java.util.logging.Logger;

@RestController
public class DriveController {
    private final Logger logger = Logger.getLogger(DriveController.class.getName());

    private DriveReplicationService driveReplicationService;
    private DriveRepository repository;
    private ReplicaManager replicaManager;

    @PostConstruct
    public void initialize() {
        UserSerializer userSerializer = new UserSerializer();
        driveReplicationService = new DriveReplicationService(new DriveSerializer(userSerializer));
        this.repository = DriveRepository.getInstance();
        this.replicaManager = ReplicaManager.getInstance();
    }

    @PostMapping("/drives")
    Drive newDrive(@RequestBody Drive newDrive) {
        logger.info("got a drive publish request.");
        UUID uuid = UUID.randomUUID();
        newDrive.setId(uuid);

        int shardId = CityShardDistributor.getShardIdByCity(newDrive.getStartingPoint());
        int leader = -1;
        try {
            leader = replicaManager.getLeader(shardId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (leader == ConfigurationManager.SERVER_ID) {
            logger.info("I'm the leader. publishing the drive in my shard.");
            repository.save(newDrive);
            driveReplicationService.replicateToAllMembers(newDrive);
        } else {
            logger.info(String.format("I'm not the leader, sending the publish request to the relevant leader: %d", leader));
            driveReplicationService.sendDrive(newDrive, leader, false);
        }

        return newDrive;
    }
}
