package flakesync;

import flakesync.common.ConfigurationDefaults;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;

import java.nio.file.Paths;

public class SurefireFactory {
    public static SurefireExecution createConcurrentMethodsExec(Plugin surefire, String originalArgLine,
                                                         MavenProject mavenProject, MavenSession mavenSession,
                                                         BuildPluginManager pluginManager, String flakesyncDir,
                                                         String testName, String localRepository) {

        SurefireExecution execution = new SurefireExecution(surefire, originalArgLine, mavenProject,
                mavenSession, pluginManager, flakesyncDir, testName, localRepository);

        return execution;
    }
}
