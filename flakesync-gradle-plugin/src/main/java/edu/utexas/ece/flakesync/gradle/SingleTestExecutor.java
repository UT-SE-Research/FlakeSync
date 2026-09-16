package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.launcher.SingleTestLauncher;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SingleTestExecutor {

    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private static final String LAUNCHER_MAIN_CLASS = "edu.utexas.ece.flakesync.launcher.SingleTestLauncher";

    private final File agentJar;
    private final FileCollection testClasspath;
    private final File workingDir;
    private final List<String> targetJvmArgs;
    private final Map<String, String> targetSystemProperties;

    public SingleTestExecutor(File agentJar, FileCollection testClasspath, File workingDir) {
        this(agentJar, testClasspath, workingDir, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * @param targetJvmArgs the target project's own {@code test} task jvmArgs (e.g. heap sizing,
     *         assertion flags) — preserved so the forked JVM behaves like the project's own test
     *         execution, mirroring how the Maven plugin preserves Surefire's existing argLine.
     * @param targetSystemProperties the target project's own {@code test} task system properties
     *         (e.g. ones its tests depend on for setup or extension registration).
     */
    public SingleTestExecutor(File agentJar, FileCollection testClasspath, File workingDir,
                               List<String> targetJvmArgs, Map<String, String> targetSystemProperties) {
        this.agentJar = agentJar;
        this.testClasspath = testClasspath;
        this.workingDir = workingDir;
        this.targetJvmArgs = targetJvmArgs;
        this.targetSystemProperties = targetSystemProperties;
    }

    /**
     * Returns true if the forked execution reproduced the target failure, false if it passed cleanly.
     */
    public boolean run(TestExecutionRequest request) {
        Process process = start(request);

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        startDrainThread(process.getErrorStream(), stderr);
        startDrainThread(process.getInputStream(), new ByteArrayOutputStream());

        boolean finished;
        try {
            finished = process.waitFor(request.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new GradleException("Interrupted while waiting for " + request.getTestName(), ie);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new TestExecutionTimeoutException("Execution for " + request.getTestName()
                    + " timed out after " + request.getTimeoutSeconds() + "s");
        }

        int exitValue = process.exitValue();
        if (exitValue == SingleTestLauncher.EXIT_PASSED) {
            return false;
        } else if (exitValue == SingleTestLauncher.EXIT_FAILED) {
            return true;
        } else {
            throw new GradleException("SingleTestLauncher could not run " + request.getTestName()
                    + " (exit " + exitValue + "): " + stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private Process start(TestExecutionRequest request) {
        String classpath = agentJar.getAbsolutePath() + File.pathSeparator + testClasspath.getAsPath();
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.addAll(targetJvmArgs);
        command.add("-javaagent:" + agentJar.getAbsolutePath());
        for (Map.Entry<String, String> property : targetSystemProperties.entrySet()) {
            command.add("-D" + property.getKey() + "=" + property.getValue());
        }
        // flakesync's own properties are added last so they win over same-named target properties.
        for (Map.Entry<String, String> property : request.getSystemProperties().entrySet()) {
            command.add("-D" + property.getKey() + "=" + property.getValue());
        }
        command.add("-cp");
        command.add(classpath);
        command.add(LAUNCHER_MAIN_CLASS);
        command.add(request.getTestName());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        try {
            return builder.start();
        } catch (IOException ioe) {
            throw new GradleException("Failed to launch test JVM for " + request.getTestName(), ioe);
        }
    }

    private static void startDrainThread(InputStream in, OutputStream out) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int read;
            try {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // stream closed because the process ended or was destroyed
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
