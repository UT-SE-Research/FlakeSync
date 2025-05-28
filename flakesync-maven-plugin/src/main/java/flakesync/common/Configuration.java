package flakesync.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class Configuration {

    public final String executionId;

    public final boolean shouldPrintStackTrace;

    public final String flakesyncDir;
    public final String flakesyncJarDir;

    public final String testName;

    public final Level loggingLevel;

    public Configuration(String flakesyncDir,
            String flakesyncJarDir, String testName, String executionId, Level loggingLevel) {
        this(flakesyncDir, flakesyncJarDir, testName, executionId, loggingLevel, false);
    }

    public Configuration(String flakesyncDir,
            String flakesyncJarDir, String testName, String executionId, Level loggingLevel, boolean printStackTrace) {
        this.flakesyncDir = flakesyncDir;
        this.flakesyncJarDir = flakesyncJarDir;
        this.testName = testName;
        this.executionId = executionId;
        this.shouldPrintStackTrace = printStackTrace;
        this.loggingLevel = loggingLevel;
        this.createExecutionDirIfNeeded();
    }

    public Configuration(String executionId, String flakesyncDir, String testName) {
        this(flakesyncDir, ConfigurationDefaults.DEFAULT_FLAKESYNC_JAR_DIR,
                testName, executionId, Logger.getGlobal().getLoggingLevel());

    }

    public void createflakesyncDirIfNeeded() {
        new File(this.flakesyncDir).mkdir();
    }

    @Override
    public String toString() {
        String[] props = new String[] {
            ConfigurationDefaults.PROPERTY_FLAKESYNC_DIR + "=" + this.flakesyncDir,
            ConfigurationDefaults.PROPERTY_FLAKESYNC_JAR_DIR + "=" + this.flakesyncJarDir,
            ConfigurationDefaults.PROPERTY_EXECUTION_ID + "=" + this.executionId,
            ConfigurationDefaults.PROPERTY_LOGGING_LEVEL + "=" + this.loggingLevel,
            "test=" + (this.testName == null ? "" : this.testName)};
        return String.join(String.format("%n"), props);
    }

    public void createExecutionDirIfNeeded() {
        Paths.get(this.flakesyncDir, this.executionId).toFile().mkdirs();
    }

    public Path getExecutionDir() {
        return Paths.get(this.flakesyncDir, this.executionId);
    }

    public Path getResultMethodsFile() {
        return Paths.get(".", this.flakesyncDir, ConfigurationDefaults.CONCURRENT_METHODS_FILE);
    }

    public Path fullLocationsFile() {
        return Paths.get(".", this.flakesyncDir, ConfigurationDefaults.LOCATIONS_FILE);
    }

    public Path whitelistFile() {
        return Paths.get(".", this.flakesyncDir, ConfigurationDefaults.WHITELIST_FILE);
    }
}
