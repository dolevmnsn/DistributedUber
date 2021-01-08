package repositories;

import entities.Drive;
import entities.Path;

import java.util.ArrayList;
import java.util.List;

public class PathRepository {
    private static PathRepository INSTANCE;
    private final List<Path> paths;

    private PathRepository() {
        paths = new ArrayList<>();
    }

    public static PathRepository getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new PathRepository();
        }

        return INSTANCE;
    }

    public void save(Path path) {
        paths.add(path);
    }

    public List<Path> getAll() { return paths; }
}
