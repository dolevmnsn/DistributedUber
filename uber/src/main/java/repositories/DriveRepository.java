package repositories;

import entities.City;
import entities.Drive;
import entities.Path;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DriveRepository {
    private static final Logger logger = Logger.getLogger(DriveRepository.class.getName());

    private static DriveRepository INSTANCE;
    private final Map<UUID, Drive> drives;
    private final Map<UUID, Drive> fullDrives;

    private DriveRepository() {
        drives = new HashMap<>();
        fullDrives = new HashMap<>();
    }

    public static DriveRepository getInstance() {
        if(INSTANCE == null) {
            INSTANCE = new DriveRepository();
        }

        return INSTANCE;
    }

    public synchronized void save(Drive newDrive) {
        drives.put(newDrive.getId(), newDrive);
    }

    public synchronized List<Drive> getAll() { return new ArrayList<>(drives.values()); }

    public synchronized Drive getDrive(UUID id){
        return drives.get(id);
    }

    public synchronized boolean reserveDrives(List<UUID> drives){
        List<UUID> visited = new ArrayList<>();
        for (UUID id : drives){
            if(!getDrive(id).increaseTaken()){
                for(UUID visitedId : visited){ // undo
                    getDrive(visitedId).decreaseTaken();
                }
                return false;
            }
            visited.add(id);
        }
        return true;
    }

    public synchronized void releaseDrives(List<UUID> drives){
        for (UUID id : drives){
            getDrive(id).decreaseTaken();
        }
    }

    public synchronized Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> getPathOptions(Path path) {
        Map<AbstractMap.SimpleEntry<City, City>, List<Drive>> pathOptions = new LinkedHashMap();
        path.getRides().keySet().forEach(src_dst ->
                pathOptions.put(src_dst, new ArrayList<>()));

        // make sure we send each segment only once
        List<AbstractMap.SimpleEntry<City, City>> segments = new ArrayList<>(path.getRides().keySet());
        Collections.shuffle(segments);
        for (Drive drive : drives.values()) {
            for (AbstractMap.SimpleEntry<City, City> src_dst : segments) {
                if (satisfiesSegment(drive, src_dst, path)) {
                    pathOptions.get(src_dst).add(drive);
                    break;
                }
            }
        }
        return pathOptions;
    }

/*
    private List<Drive> findRidesForSegment(AbstractMap.SimpleEntry<City, City> src_dst, Path path) {
        List<Drive> matchingDrives = new ArrayList<>();
        for (Drive drive : drives.values()) {
            if (satisfiesSegment(drive, src_dst, path)) {
                matchingDrives.add(drive);
            }
        }
        return matchingDrives;
    }
*/

    private boolean satisfiesSegment(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst, Path path) {
        boolean isNotSameUser = !drive.getDriver().equals(path.getPassenger());
        boolean sameDate = drive.getDepartureDate().equals(path.getDepartureDate());
        boolean vacant = drive.getTaken() < drive.getVacancies();
        boolean isNotPassDeviation = doesNotPassDeviation(drive, src_dst);
        return isNotSameUser && sameDate && vacant && isNotPassDeviation;
    }

    private boolean doesNotPassDeviation(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst) {
        Point driveSrc = drive.getStartingPoint().getLocation();
        Point driveDst = drive.getEndingPoint().getLocation();
        Point segmentSrc = src_dst.getKey().getLocation();
        Point segmentDst = src_dst.getValue().getLocation();
        double pd = drive.getPermittedDeviation();

        boolean deviationToSegSrc = driveDst.equals(segmentDst) && distance(driveSrc, driveDst, segmentSrc) <= pd;
        boolean deviationToSegDst = driveSrc.equals(segmentSrc) && distance(driveSrc, driveDst, segmentDst) <= pd;

        return deviationToSegSrc || deviationToSegDst;
    }

    private double distance(Point point1, Point point2, Point point0) {
        int x2_x1 = point2.x - point1.x;
        int y1_y0 = point1.y - point0.y;
        int x1_x0 = point1.x - point0.x;
        int y2_y1 = point2.y - point1.y;

        int numerator = Math.abs(x2_x1 * y1_y0 - x1_x0 * y2_y1);
        double denominator = Math.sqrt(Math.pow(x2_x1, 2) + Math.pow(y2_y1, 2));
        return (numerator / denominator);
    }

//    public void updateFullDrive(UUID id){
//        Drive drive = drives.remove(id);
//        if (drive != null){
//            fullDrives.put(id, drive);
//        }
//    }

    public List<Drive> getDrivesSinceRevision(long revision) {
        return drives.values().stream()
                .filter(drive -> drive.getRevision() > revision)
                .collect(Collectors.toList());
    }
}
