package flakesync;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final String DEFAULT_FLAKESYNC_DIR = ".flakesync";

    // Agent Input Files
    public static final String CONCURRENT_METHODS_FILE = "ResultMethods.txt";

    public static final String LOCATIONS_FILE = "Locations.txt";
    public static final String LOCATIONS_MIN_FILE = "Locations_minimized.txt";
    public static final String LOCATIONS_TEMP_FILE = "Locations_tmp.txt";
    public static final String WHITELIST_FILE = "whitelist.txt";

    public static final String ROOTS_DIR = "Locations";
    public static final String LINES_DIR = "Lines";
    public static final String STACKTRACE_FILE = "StackTrace.txt";
    public static final String ROOT_METHOD_FILE = "Root.txt";
    public static final String METHOD_START_END_FILE = "MethodStartAndEndLine.txt";
    public static final String CRIT_SEARCH_RESULTS_DIR = "Results-CritSearch";
    public static final String RMA_RESULTS_FILE = "RootMethods.csv";
    public static final String CRIT_POINTS_FILE = "CriticalPoints.csv";

    public static final String SEARCH_METHOD_END_FILE = "SearchedMethodEndLine.txt";
    public static final String SEARCH_METHOD_AND_FILE = "SearchedMethodANDLine.txt";
    public static final String THRESHOLD_FILE = "ExecutionMonitor.txt";
    public static final String YIELD_RESULT_FILE = "FlagDelayANDUpdateANDYielding.txt";
    public static final String BARRIER_SEARCH_RESULTS_DIR = "Results-BarrierSearch";
    public static final String BARRIER_POINTS_FILE = "BarrierPoints.csv";

    public static void createExecutionDirIfNeeded(String executionId) {
        Paths.get(DEFAULT_FLAKESYNC_DIR, executionId).toFile().mkdirs();
    }

    public static Path getExecutionDir(String baseDir, String executionId) {
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, executionId);
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

    public static Path getCritPointsResultsFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + CRIT_POINTS_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, CRIT_SEARCH_RESULTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, CRIT_SEARCH_RESULTS_DIR, fileName);
    }

    public static Path getBarrierPointsResultsFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + BARRIER_POINTS_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, BARRIER_SEARCH_RESULTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, BARRIER_SEARCH_RESULTS_DIR, fileName);
    }

    public static Path getSearchMethodEndLineFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + SEARCH_METHOD_END_FILE;
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getSearchMethodANDLineFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + SEARCH_METHOD_AND_FILE;
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getThresholdFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + THRESHOLD_FILE;
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getYieldResultFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + YIELD_RESULT_FILE;
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getMinLocationsFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + LOCATIONS_MIN_FILE;
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getWorkingLocationsFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + LOCATIONS_TEMP_FILE;
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getStackTraceFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + STACKTRACE_FILE;
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, fileName);
    }

    public static Path getRootMethodFilepath(String testName) {
        String fileName = testName.replace("#", ".") + "-" + ROOT_METHOD_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(DEFAULT_FLAKESYNC_DIR, ROOTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, ROOTS_DIR, fileName);
    }

    public static Path getIndRootFilepath(String baseDir, String testName, int idx) {
        String fileName = testName.replace("#", ".") + "-" + ROOT_METHOD_FILE;
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, ROOTS_DIR, "Root-" + idx + ".txt");
    }

    public static Path getIndLocFilepath(String baseDir, String testName, int threadID, int idx) {
        String fileName = testName.replace("#", ".") + "-loc-" + threadID + "-" + idx + ".txt";
        File rootsDir = new File(String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, ROOTS_DIR)));
        rootsDir.mkdirs();
        File linesDir = new File(String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, ROOTS_DIR, LINES_DIR)));
        linesDir.mkdirs();
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, ROOTS_DIR, LINES_DIR, fileName);
    }

    public static String getMethodStartEndLineFile(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + METHOD_START_END_FILE;
        return String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, fileName));
    }

    public static Path getRootMethodResultsFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + RMA_RESULTS_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(baseDir, CRIT_SEARCH_RESULTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, CRIT_SEARCH_RESULTS_DIR, fileName);
    }

    public static Path getCritPointsResultsFilepath(String baseDir, String testName) {
        String fileName = testName.replace("#", ".") + "-" + CRIT_POINTS_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, CRIT_SEARCH_RESULTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(baseDir, DEFAULT_FLAKESYNC_DIR, CRIT_SEARCH_RESULTS_DIR, fileName);
    }
}
