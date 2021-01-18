package repositories;

import entities.Drive;
import entities.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PathRepository {
    private static PathRepository INSTANCE;
    private final List<Path> paths;

    private PathRepository() {
        paths = new ArrayList<>();
    }

    public synchronized static PathRepository getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new PathRepository();
        }

        return INSTANCE;
    }

    public synchronized void save(Path path) {
        paths.add(path);
    }

    public synchronized List<Path> getAll() { return paths; }

    public synchronized List<Path> getPathsSinceRevision(long revision) {
        return paths.stream()
                .filter(path -> path.getRevision() > revision)
                .collect(Collectors.toList());
    }
}
