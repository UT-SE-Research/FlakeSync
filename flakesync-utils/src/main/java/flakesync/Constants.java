package flakesync;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final String DEFAULT_FLAKESYNC_DIR = ".flakesync";

    //Agent Input Files
    public static final String CONCURRENT_METHODS_FILE = "ResultMethods.txt";
    public static final String LOCATIONS_FILE = "Locations.txt";
    public static final String LOCATIONS_MIN_FILE = "Locations_minimized.txt";
    public static final String LOCATIONS_TEMP_FILE = "Locations_temp.txt";
    public static final String ROOTS_DIR = "Locations";
    public static final String STACKTRACE_FILE = "StackTrace.txt";
    public static final String ROOT_METHOD_FILE = "Root.txt";
    public static final String WHITELIST_FILE = "whitelist.txt";



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

    public static Path getRootMethodFilepath(String testname) {
        String fileName = testname.replace("#", ".") + "-" + ROOT_METHOD_FILE;
        File rootsDir = new File(String.valueOf(Paths.get(DEFAULT_FLAKESYNC_DIR, ROOTS_DIR)));
        if(!rootsDir.exists()) {
            rootsDir.mkdirs();
        }
        return Paths.get(".", DEFAULT_FLAKESYNC_DIR, ROOTS_DIR, fileName);
    }
}