package flakesync;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.nio.file.Paths;

@Mojo(name = "concurrentfind", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class FindTestsRunMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running FindTestsRunMojo"));
        MojoExecutionException allExceptions = null;

        try {
            // If we add clean exceptions to allExceptions then the build fails if anything fails without nondex.
            // Everything in nondex-test is expected to fail without nondex so we throw away the result here.
            CleanSurefireExecution cleanExec = new CleanSurefireExecution(
                    this.surefire, this.originalArgLine, this.mavenProject,
                    this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.testName, this.localRepository);
            this.executeSurefireExecution(null, cleanExec);
        } catch (Throwable exception) {
            System.out.println(exception);
            throw new RuntimeException();
        }


    }


    private MojoExecutionException executeSurefireExecution(MojoExecutionException allExceptions,
                                                            CleanSurefireExecution execution)
                                                            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            return (MojoExecutionException) Utils.linkException(ex, allExceptions);
        }
        return allExceptions;
    }


    public void setTestName(String testName) {
        this.testName = testName;
    }

}