package host.controllers;

import Services.SnapshotAggregationService;
import entities.Drive;
import entities.Path;
import host.RevisionManager;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import repositories.DriveRepository;
import repositories.PathRepository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class SnapshotController {
    private SnapshotAggregationService snapshotAggregationService;


    @PostConstruct
    public void initialize() {
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

        List<Drive> drives = snapshotList.stream()
                .map(Snapshot::getDrives)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(Drive::getId))
                .collect(Collectors.toList());
        List<Path> paths = snapshotList.stream()
                .map(Snapshot::getPaths)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(Path::getId))
                .collect(Collectors.toList());

        return new Snapshot(drives, paths);
    }

    @GetMapping("/revision")
    long getRevision() {
        return RevisionManager.getInstance().getRevision();
    }

    @GetMapping("/localMemory")
    Snapshot getLocalMemory() {
        return new Snapshot(DriveRepository.getInstance().getAll(), PathRepository.getInstance().getAll());
    }
}
