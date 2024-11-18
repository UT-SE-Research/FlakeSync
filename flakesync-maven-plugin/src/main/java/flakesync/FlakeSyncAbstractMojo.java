package flakesync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
import flakesync.common.Mode;
import flakesync.instrumentation.Instrumenter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;


@Execute(phase = LifecyclePhase.TEST_COMPILE)
public abstract class FlakeSyncAbstractMojo extends AbstractMojo {

    // NonDex Mojo specific properties
    /**
     * The seed that is used for randomization during shuffling.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_SEED, defaultValue = ConfigurationDefaults.DEFAULT_SEED_STR)
    protected int seed;

    /**
     * The degree/level of shuffling that should be carried out.
     * Section III.B in http://mir.cs.illinois.edu/~awshi2/publications/ICST2016.pdf)
     * The options are:
     * FULL: shuffle on each invocation of methods with nondeterministic specs
     * ONE: only shuffle on the first invocation of an object
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_MODE, defaultValue = ConfigurationDefaults.DEFAULT_MODE_STR)
    protected Mode mode;

    /**
     * Regex filter used to whitelist the sources of non-determinism to explore.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_FILTER, defaultValue = ConfigurationDefaults.DEFAULT_FILTER)
    protected String filter;

    /**
     * Starting point of the range of invocations to use during the debug phase.
     * The minimum value is 0 and the maximum can be obtained from first running
     * the nondex phase. If start and end have the same value, then only the
     * invocation at that value is shuffled.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_START, defaultValue = ConfigurationDefaults.DEFAULT_START_STR)
    protected long start;

    /**
     * Ending point of the range of invocations to use during the debug phase.
     * The minimum value is 0 and the maximum can be obtained from first running
     * the nondex phase. If start and end have the same value, then only the
     * invocation at that value is shuffled.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_END, defaultValue = ConfigurationDefaults.DEFAULT_END_STR)
    protected long end;

    /**
     * The number of clean test runs without NonDex shuffling.
     * This feature may be useful if tests are flaky (NOD / NIO) by themselves.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_NUM_RUNS_WITHOUT_SHUFFLING,
            defaultValue = ConfigurationDefaults.DEFAULT_NUM_RUNS_WITHOUT_SHUFFLING_STR)
    protected int numRunsWithoutShuffling;

    /**
     * The number of seeds to use for shuffle. NonDex will obtain other seeds
     * apart from the specified (or default) seed from the current run and
     * some internal factor.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_NUM_RUNS,
            defaultValue = ConfigurationDefaults.DEFAULT_NUM_RUNS_STR)
    protected int numRuns;

    /**
     * Set this to "true" to rerun multiple times with only the specified (or
     * default) seed. The number of reruns is equal to numRuns.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_RERUN,
            defaultValue = ConfigurationDefaults.DEFAULT_RERUN_STR)
    protected boolean rerun;

    /**
     * Unique ID for the current nondex execution.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_EXECUTION_ID,
            defaultValue = ConfigurationDefaults.PROPERTY_DEFAULT_EXECUTION_ID)
    protected String executionId;

    /**
     * Select which run to perform debugging on. Default is the latest.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_RUN_ID,
            defaultValue = ConfigurationDefaults.PROPERTY_DEFAULT_RUN_ID)
    protected String runId;

    /**
     * Same as the levels defined in java.util.logging.Level.
     */
    @Parameter(property = ConfigurationDefaults.PROPERTY_LOGGING_LEVEL,
            defaultValue = ConfigurationDefaults.DEFAULT_LOGGING_LEVEL)
    protected String loggingLevel;

    // Generic properties
    @Parameter(property = "project")
    protected MavenProject mavenProject;
    @Parameter(defaultValue = "${project.build.directory}")
    protected String projectBuildDir;
    @Parameter(defaultValue = "${basedir}")
    protected File baseDir;
    @Parameter(property = "goal", alias = "mojo")
    protected String goal;

    @Parameter(property = "flakesync.testName", required = true)
    protected String testName;

    @Component
    protected MavenSession mavenSession;
    @Component
    protected BuildPluginManager pluginManager;

    protected Plugin surefire;

    protected String originalArgLine;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Logger.getGlobal().setLoggingLevel(Level.parse(this.loggingLevel));
        String rtPathStr = "";
        if (Utils.checkJDK8()) {
            Path rtPath;
            rtPath = Utils.getRtJarLocation();
            if (rtPath == null) {
                Logger.getGlobal().log(Level.SEVERE, "Cannot find the rt.jar!");
                throw new MojoExecutionException("Cannot find the rt.jar!");
            }
            rtPathStr = rtPath.toString();
        }

        this.surefire = this.lookupPlugin("org.apache.maven.plugins:maven-surefire-plugin");

        if (this.surefire == null) {
            Logger.getGlobal().log(Level.SEVERE, "Surefire is not explicitly declared in your pom.xml; "
                    + "we will use version 2.20, but you may want to change that.");
            this.surefire = getSureFirePlugin();
        }


        Properties localProperties = this.mavenProject.getProperties();
        this.originalArgLine = localProperties.getProperty("argLine", "");
        System.out.println("This is the original argline: " + this.originalArgLine);

    }

    private Plugin lookupPlugin(String paramString) {
        List<Plugin> localList = this.mavenProject.getBuildPlugins();
        Iterator<Plugin> localIterator = localList.iterator();
        while (localIterator.hasNext()) {
            Plugin localPlugin = localIterator.next();
            if (paramString.equalsIgnoreCase(localPlugin.getKey())) {
                return localPlugin;
            }
        }
        return null;
    }

    private Plugin getSureFirePlugin() {
        Plugin surefire = new Plugin();
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setArtifactId("maven-surefire-plugin");
        surefire.setVersion("2.20");
        return surefire;
    }




}
