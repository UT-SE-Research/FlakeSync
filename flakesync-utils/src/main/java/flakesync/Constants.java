package flakesync;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final String DEFAULT_FLAKESYNC_DIR = ".flakesync";

    //Agent Input Files
    public static final String CONCURRENT_METHODS_FILE = "ResultMethods.txt";
    public static final String LOCATIONS_FILE = "Locations.txt";
    public static final String WHITELIST_FILE = "whitelist.txt";
    public static final String BARRIER_SEARCH_RESULTS_DIR = "Results-BarrierSearch";
    public static final String BARRIER_POINTS_FILE = "BarrierPoints.csv";


    public static void createExecutionDirIfNeeded(String executionId) {
        Paths.get(DEFAULT_FLAKESYNC_DIR, executionId).toFile().mkdirs();
    }

    public static Path getExecutionDir(String executionId) {
        return Paths.get(DEFAULT_FLAKESYNC_DIR, executionId);
    }

    public static Path getConcurrentMethodsFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + CONCURRENT_METHODS_FILE;
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getWhitelistFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + WHITELIST_FILE;
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getAllLocationsFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + LOCATIONS_FILE;
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getBarrierPointsResultsFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + BARRIER_POINTS_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, BARRIER_SEARCH_RESULTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, BARRIER_SEARCH_RESULTS_DIR, fileName);
    }
}