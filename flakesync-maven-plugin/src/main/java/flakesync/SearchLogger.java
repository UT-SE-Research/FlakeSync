package flakesync;

import flakesync.common.ConfigurationDefaults;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Appends one row per examined search candidate to
 * .flakesync/&lt;test&gt;-CandidateOrder.csv
 * Columns: phase,group,order,candidate,result,runs,elapsed_ms
 */
public final class SearchLogger {
    private static final long T0 = System.nanoTime();

    private SearchLogger() {
    }

    public static synchronized void log(String baseDir, String testName, String phase, String group,
                                        int order, String candidate, String result, int runs) {
        Path logPath = Paths.get(baseDir, ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR,
                testName.replace("#", ".") + "-CandidateOrder.csv");
        try {
            boolean isNew = !Files.exists(logPath);
            try (BufferedWriter bw = Files.newBufferedWriter(logPath, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (isNew) {
                    bw.write("phase,group,order,candidate,result,runs,elapsed_ms");
                    bw.newLine();
                }
                long ms = (System.nanoTime() - T0) / 1_000_000;
                bw.write(phase + "," + group + "," + order + "," + candidate + "," + result + ","
                        + runs + "," + ms);
                bw.newLine();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}