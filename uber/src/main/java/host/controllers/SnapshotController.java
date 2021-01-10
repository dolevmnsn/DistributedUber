package host.controllers;

import Services.SnapshotAggregationService;
import entities.Drive;
import entities.Path;
import host.ReplicaManager;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import repositories.DriveRepository;
import repositories.PathRepository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class SnapshotController {
    private ReplicaManager replicaManager;
    private PathRepository pathRepository;
    private DriveRepository driveRepository;
    private SnapshotAggregationService snapshotAggregationService;


    @PostConstruct
    public void initialize() {
        this.replicaManager = ReplicaManager.getInstance();
        this.driveRepository = DriveRepository.getInstance();
        this.pathRepository = PathRepository.getInstance();
        this.snapshotAggregationService = new SnapshotAggregationService();
    }

    public static class Snapshot {
        @Getter @Setter
        private List<Drive> drives;

        @Getter @Setter
        public List<Path> paths;

        public Snapshot(List<Drive> drives, List<Path> paths) {
            this.drives = new ArrayList<>(drives);
            this.paths = new ArrayList<>(paths);
        }
    }

    @GetMapping("/snapshot")
    Snapshot getSnapshot() {
        List<Snapshot> snapshotList = new ArrayList<>(snapshotAggregationService.aggregateSnapshot());

        return new Snapshot(snapshotList.stream().map(Snapshot::getDrives).flatMap(List::stream).collect(Collectors.toList()),
                snapshotList.stream().map(Snapshot::getPaths).flatMap(List::stream).collect(Collectors.toList()));
    }


}
