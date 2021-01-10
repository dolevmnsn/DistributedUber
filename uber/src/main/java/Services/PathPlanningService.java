package Services;

import entities.City;
import entities.Drive;
import entities.Path;
import repositories.DriveRepository;

import java.awt.*;
import java.util.AbstractMap;
import java.util.UUID;

public class PathPlanningService {
    private final DriveRepository driveRepository;
    private final PathReplicationService pathReplicationService;

    public PathPlanningService(PathReplicationService pathReplicationService) {
        this.driveRepository = DriveRepository.getInstance();
        this.pathReplicationService = pathReplicationService;
    }

    public Path planPath(Path path) {
        // plan in shard
        path.getRides().forEach((src_dst, id) -> {
            if (id == null) {
                path.getRides().put(src_dst, findRideForSegment(src_dst, path));
            }
        });

        // todo: plan with other shards. now only inside the shard.
//        if (path.getRides().containsValue(null)) {
//            pathReplicationService.sendPlanRequest(path);
//        }

        if (!path.getRides().containsValue(null)) {
            path.setSatisfied(true);
        }

        return path;
    }

    private UUID findRideForSegment(AbstractMap.SimpleEntry<City, City> src_dst, Path path) {
        for (Drive drive : driveRepository.getAll()) {
            if (satisfiesSegment(drive, src_dst, path)) {
                // todo: update drive taken seats
//                drive.increaseTaken();
                return drive.getId();
            }
        }

        return null;
    }

    private boolean satisfiesSegment(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst, Path path) {
        boolean isNotSameUser = !drive.getDriver().equals(path.getPassenger());
        boolean sameDate = drive.getDepartureDate().equals(path.getDepartureDate());
        // TODO: calculate right condition. now only from sec to dst.
//        boolean isNotPassDeviation = maxDeviation(drive, src_dst) <= drive.getPermittedDeviation();
//        return isNotSameUser && isNotPassDeviation;
        return isNotSameUser && sameDate &&
                drive.getStartingPoint().equals(src_dst.getKey()) && drive.getEndingPoint().equals(src_dst.getValue());
    }

    private double maxDeviation(Drive drive, AbstractMap.SimpleEntry<City, City> src_dst) {
        Point driveSrc = drive.getStartingPoint().getLocation();
        Point driveDst = drive.getEndingPoint().getLocation();
        Point segmentSrc = src_dst.getKey().getLocation();
        Point segmentDst = src_dst.getValue().getLocation();

        double deviationToSegSrc = distance(driveSrc, driveDst, segmentSrc);
        double deviationToSegDst = distance(driveSrc, driveDst, segmentDst);

        return Math.max(deviationToSegSrc, deviationToSegDst);
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
}
