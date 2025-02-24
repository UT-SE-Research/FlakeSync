package flakesync;

import flakesync.common.Configuration;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownWardMvnSurefireExecution {
    protected Configuration configuration;
    protected final String executionId;

    protected Plugin surefire;
    protected MavenProject mavenProject;
    protected MavenSession mavenSession;
    protected BuildPluginManager pluginManager;
    protected String testName;
    protected String localRepository;
    protected String originalArgLine;



    protected DownWardMvnSurefireExecution(Plugin surefire, String originalArgLine, String executionId,
                                     MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager pluginManager,
                                     String flakesyncDir, String testName, String localRepository) {
        this.executionId = executionId;
        this.surefire = surefire;
        this.testName = testName;
        this.originalArgLine = sanitizeAndRemoveEnvironmentVars(originalArgLine);
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.configuration = new Configuration(executionId, flakesyncDir, testName);
        this.localRepository = localRepository;

    }

    protected DownWardMvnSurefireExecution(Plugin surefire, String originalArgLine, String executionId,
                                     MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager pluginManager,
                                     String flakesyncDir, String localRepository, int delays) {
        this.executionId = executionId;
        this.surefire = surefire;
        this.originalArgLine = sanitizeAndRemoveEnvironmentVars(originalArgLine);
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.configuration = new Configuration(executionId, flakesyncDir, testName);
        this.localRepository = localRepository;

    }

    public DownWardMvnSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                  MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String testName,
                                        String localRepository) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

    public void run(int mode) throws MojoExecutionException {
        System.out.println("Inside run in DownWardMvnSurefireExecution");
        Xpp3Dom origNode = null;
        if (this.surefire.getConfiguration() != null) {
            origNode = new Xpp3Dom((Xpp3Dom) this.surefire.getConfiguration());
        }
        try {
            Xpp3Dom domNode = this.applyNonDexConfig((Xpp3Dom) this.surefire.getConfiguration());
            //this.addAttributeToConfig(domNode, "")
            this.setupArgline(domNode, mode);
            this.setupTest(domNode);
            Logger.getGlobal().log(Level.FINE, "Config node passed: " + domNode.toString());
            Logger.getGlobal().log(Level.FINE, this.mavenProject + "\n" + this.mavenSession + "\n" + this.pluginManager);
            Logger.getGlobal().log(Level.FINE, "Surefire config: " + this.surefire + "  " + MojoExecutor.goal("test")
                    + " " + domNode + " "
                    + MojoExecutor.executionEnvironment(this.mavenProject, this.mavenSession,
                    this.pluginManager));
            MojoExecutor.executeMojo(this.surefire, MojoExecutor.goal("test"),
                    domNode,
                    MojoExecutor.executionEnvironment(this.mavenProject, this.mavenSession, this.pluginManager));
        } catch (MojoExecutionException mojoException) {
            Logger.getGlobal().log(Level.INFO, "Surefire failed when running tests for " + this.configuration.executionId);
        }
    }

    protected void setupArgline(Xpp3Dom configNode, int mode) {
        // create the flakeSync-delay argLine for surefire based on the current configuration
        // this adds things like where to save test reports, what directory NonDex
        // should store results in, what seed and mode should be used.

        String pathToJar = this.localRepository;
        // TODO: Encode path to agent in some final static variable for ease of access and potential changes to name/version
        String argLineToSet = "-javaagent:" + pathToJar + "/edu/utexas/ece/barrierSearch-core/0.1-SNAPSHOT/barrierSearch-core-0.1-SNAPSHOT.jar";

        boolean addedAL = false;
        boolean addedSML = false;
        boolean addedCTV = false;
        boolean addedYP = false;
        boolean addedT = false;
        for (Xpp3Dom config : configNode.getChildren()) {
            if ("argLine".equals(config.getName())) {
                Logger.getGlobal().log(Level.INFO, "Adding flakeSync-delay argLine to existing argLine specified by the project");
                String current = sanitizeAndRemoveEnvironmentVars(config.getValue());
                config.setValue(argLineToSet + " " + current);
                addedAL = true;
                break;
            }
            if ("searchMethodEndLine".equals(config.getName()) && mode == 1) {
                config.setValue("search");
                addedSML = true;
                break;
            }
            if ("CodeToIntroduceVariable".equals(config.getName())) {
                config.setValue(""); // change to starting boundary
                addedCTV = true;
                break;
            }
            if ("YieldingPoint".equals(config.getName()) && mode == 2) {
                config.setValue(""); // change to yielding point
                addedYP = true;
                break;
            }
            if ("threshold".equals(config.getName()) && mode == 2) {
                config.setValue(""); // change to threshold
                addedT = true;
                break;
            }
        }
        if (!addedAL) {
            Logger.getGlobal().log(Level.INFO, "Creating new argline for Surefire: *" + argLineToSet + "*");
            configNode.addChild(this.makeNode("argLine", argLineToSet));
        }
        if (!addedSML && mode == 1) {
            configNode.addChild(this.makeNode("searchMethodEndLine", "search"));
        }
        if (!addedSML) {
            configNode.addChild(this.makeNode("CodeToIntroduceVariable", "search"));
        }
        if (!addedSML && mode == 2) {
            configNode.addChild(this.makeNode("searchMethodEndLine", "search"));
        }
        if (!addedSML && mode == 2) {
            configNode.addChild(this.makeNode("searchMethodEndLine", "search"));
        }

        // originalArgLine is the argLine set from Maven, not through the surefire config
        // if such an argLine exists, we modify that one also
        this.mavenProject.getProperties().setProperty("argLine",
                this.originalArgLine + " " + argLineToSet);
    }

    protected void setupTest(Xpp3Dom configNode) {
        configNode.addChild((this.makeNode("test", this.testName)));
    }

    protected Xpp3Dom applyNonDexConfig(Xpp3Dom configuration) {
        Xpp3Dom configNode = configuration;
        if (configNode == null) {
            configNode = new Xpp3Dom("configuration");
        }

        return setReportOutputDirectory(configNode);
    }

    protected Xpp3Dom setReportOutputDirectory(Xpp3Dom configNode) {
        configNode = this.addAttributeToConfig(configNode, "reportsDirectory",
                this.configuration.getExecutionDir().toString());
        configNode = this.addAttributeToConfig(configNode, "disableXmlReport", "false");
        return configNode;
    }

    private Xpp3Dom addAttributeToConfig(Xpp3Dom configNode, String nodeName, String value) {
        for (Xpp3Dom config : configNode.getChildren()) {
            if (nodeName.equals(config.getName())) {
                config.setValue(value);
                return configNode;
            }
        }

        configNode.addChild(this.makeNode(nodeName, value));
        return configNode;
    }

    protected Xpp3Dom makeNode(String nodeName, String value) {
        Xpp3Dom node = new Xpp3Dom(nodeName);
        node.setValue(value);
        return node;
    }

    // removes all substring matching the format of a maven property
    // when this method is invoked Maven should have resolved all properties that are defined
    // if any property is present it means it couldn't be resolved so this will remove it
    protected static String sanitizeAndRemoveEnvironmentVars(String toSanitize) {
        String pattern = "\\$\\{([A-Za-z0-9\\.\\-]+)\\}";
        Pattern expr = Pattern.compile(pattern);
        Matcher matcher = expr.matcher(toSanitize);
        while (matcher.find()) {
            Pattern subexpr = Pattern.compile(Pattern.quote(matcher.group(0)));
            toSanitize = subexpr.matcher(toSanitize).replaceAll("");
        }
        return toSanitize.trim();
    }
}
