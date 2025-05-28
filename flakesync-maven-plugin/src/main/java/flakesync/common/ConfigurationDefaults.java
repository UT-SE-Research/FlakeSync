package flakesync.common;

public class ConfigurationDefaults {

    public static final String PROPERTY_EXECUTION_ID = "flakesyncExecid";
    public static final String NO_EXECUTION_ID = "NoId";
    public static final String PROPERTY_DEFAULT_EXECUTION_ID = ConfigurationDefaults.NO_EXECUTION_ID;

    public static final String PROPERTY_RUN_ID = "flakesyncRunId";
    public static final String LATEST_RUN_ID = "LATEST";
    public static final String PROPERTY_DEFAULT_RUN_ID = ConfigurationDefaults.LATEST_RUN_ID;

    public static final String PROPERTY_FLAKESYNC_DIR = "flakesyncDir";
    public static final String DEFAULT_FLAKESYNC_DIR = ".flakesync";

    public static final String PROPERTY_FLAKESYNC_JAR_DIR = "flakesyncJarDir";
    public static final String DEFAULT_FLAKESYNC_JAR_DIR = ".flakesync";

    public static final String FAILURES_FILE = "failures";
    public static final String INVOCATIONS_FILE = "invocations";
    public static final String DEBUG_FILE = "debug";
    public static final String CONFIGURATION_FILE = "config";

    public static final int SEED_FACTOR = 0xA1e4;

    public static final String PROPERTY_LOGGING_LEVEL = "flakesyncLogging";
    public static final String DEFAULT_LOGGING_LEVEL = "CONFIG";

    public static final String CONCURRENT_METHODS_JAR =
        "/edu/utexas/ece/flakesync-core/1.0-SNAPSHOT/flakesync-core-1.0-SNAPSHOT.jar";
    public static final String BOUNDARY_SEARCH_JAR =
        "/edu/utexas/ece/localization-core/0.1-SNAPSHOT/localization-core-0.1-SNAPSHOT.jar";
    public static final String BARRIER_SEARCH_JAR =
        "/edu/utexas/ece/barrierSearch-core/1.0-SNAPSHOT/barrierSearch-core-1.0-SNAPSHOT.jar";
}
