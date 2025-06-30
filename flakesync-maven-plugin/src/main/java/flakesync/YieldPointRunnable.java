package flakesync;

import flakesync.common.ConfigurationDefaults;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Paths;

public class YieldPointRunnable implements Runnable {

    CleanSurefireExecution barrierPoint;
    boolean finishedTest;
    boolean testPass;
    boolean running;

    public YieldPointRunnable(Plugin surefire, String originalArgLine,
                              MavenProject mavenProject, MavenSession mavenSession,
                              BuildPluginManager pluginManager, File baseDir,
                              String localRepository, String testName,
                              int delay, String endLoc, String yieldingPoint,
                              int threshold) {

        barrierPoint = new CleanSurefireExecution(surefire, originalArgLine,
                mavenProject, mavenSession, pluginManager,
                Paths.get(baseDir.getAbsolutePath(),
                        ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                localRepository, testName, delay, endLoc, yieldingPoint, threshold);

        this.finishedTest = false;
        this.testPass = false;
        running = true;
    }

    @Override
    public void run() {
        while (running) {
            try {
                this.testPass = !executeSurefireExecution(null, barrierPoint);
                finishedTest = true;
            } catch (Throwable exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    public void terminate() {
        System.out.println("Terminate called");
        running = false;
    }

    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                             CleanSurefireExecution execution)
            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException exception) {
            return true;
        }
        return false;
    }
}
