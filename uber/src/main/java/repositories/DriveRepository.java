package repositories;

import entities.Drive;

import java.util.ArrayList;
import java.util.List;

public class DriveRepository {
    private static DriveRepository INSTANCE;
    private final List<Drive> drives;

    private DriveRepository() {
        drives = new ArrayList<>();
    }

    public static DriveRepository getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new DriveRepository();
        }

        return INSTANCE;
    }

    public void save(Drive newDrive) {
        drives.add(newDrive);
    }

    public List<Drive> getAll() { return drives; }
}
