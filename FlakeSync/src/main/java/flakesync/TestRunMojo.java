package flakesync;

import edu.illinois.cs.testrunner.coreplugin.TestRunner;
import edu.illinois.cs.testrunner.util.ProjectWrapper;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.file.Files;


/**
 * Says "Hi" to the user.
 *
 */
@Mojo(name = "run_tests")
public class TestRunMojo extends FlakeSyncAbstractMojo
{
    @Override
    public void execute() {
        super.execute();
        try {
            Files.deleteIfExists(PathManager.errorPath());
            Files.createDirectories(PathManager.cachePath());
            Files.createDirectories(PathManager.detectionResults());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        TestRunner runner = new TestRunner();
        runner.execute((ProjectWrapper)(mavenProject));

        System.out.println("Tests executed successfully");

    }
}
