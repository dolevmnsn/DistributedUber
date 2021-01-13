package repositories;

import entities.Drive;
import java.util.*;
import java.util.logging.Logger;

public class DriveRepository {
    private static final Logger logger = Logger.getLogger(DriveRepository.class.getName());

    private static DriveRepository INSTANCE;
    private final Map<UUID, Drive> drives;
    private final Map<UUID, Drive> fullDrives;

    private DriveRepository() {
        drives = new LinkedHashMap<>();
        fullDrives = new LinkedHashMap<>();
    }

    public static DriveRepository getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new DriveRepository();
        }

        return INSTANCE;
    }

    public void save(Drive newDrive) {
        drives.put(newDrive.getId(), newDrive);
    }

    public List<Drive> getAll() { return new ArrayList<>(drives.values()); }

    public Drive getDrive(UUID id){
        return drives.get(id);
    }

    public void updateFullDrive(UUID id){
        Drive drive = drives.remove(id);
        if (drive != null){
            fullDrives.put(id, drive);
        }
    }
}
