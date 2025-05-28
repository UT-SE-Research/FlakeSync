package flakesync;

import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "flakedelay", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RunWithDelaysMojo extends FlakeSyncAbstractMojo {

    int[] delays = {100, 200, 400, 800, 1600, 3200, 6400, 12800};

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running RunWithDelaysMojo"));
        MojoExecutionException allExceptions = null;

        createWhiteList();

        for (int i = 0; i < delays.length; i++) {
            CleanSurefireExecution cleanExec = new CleanSurefireExecution(
                    this.surefire, this.originalArgLine, this.mavenProject,
                    this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, delays[i]);
            try {
                this.executeSurefireExecution(null, cleanExec);
            } catch (MojoExecutionException mee) {
                break;
            } catch (Throwable exception) {
                System.out.println(exception);
                throw new RuntimeException();
            }
        }
    }


    private MojoExecutionException executeSurefireExecution(MojoExecutionException allExceptions,
                                                            CleanSurefireExecution execution) throws MojoExecutionException,
            Throwable {
        execution.run();
        return allExceptions;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    private boolean createWhiteList() {
        System.out.println("Inside createWhiteList");
        File whitelist = new File(this.mavenProject.getBasedir() + "/.flakesync/whitelist.txt");
        File outputDir = new File(this.mavenProject.getBuild().getOutputDirectory());

        try {
            whitelist.createNewFile();
            FileWriter outputLocationsFile = new FileWriter(whitelist);
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            try (Stream<Path> paths = Files.walk(Paths.get(outputDir.toURI()))) {
                List<String> classNames = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.toString().contains("Tests"))
                        .map(path -> path.toString()
                                .replaceFirst(".*target/classes/", "")
                                .replace("/", ".")
                                .replace(".class", ""))
                        .collect(Collectors.toList());

                for (String location : classNames) {
                    bw.write(location);
                    bw.newLine();
                }
                bw.flush();
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return true;
    }
}
