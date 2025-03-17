/*The MIT License (MIT)
Copyright (c) 2015 Alex Gyori
Copyright (c) 2022 Kaiyao Ke
Copyright (c) 2015 Owolabi Legunsen
Copyright (c) 2015 Darko Marinov
Copyright (c) 2015 August Shi


Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.*/


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

import static flakesync.common.ConfigurationDefaults.*;

public class CleanSurefireExecution {

    protected Configuration configuration;
    protected final String executionId;

    protected Plugin surefire;
    protected MavenProject mavenProject;
    protected MavenSession mavenSession;
    protected BuildPluginManager pluginManager;
    protected String testName;
    protected String localRepository;
    protected String originalArgLine;


    protected int delay;
    protected String pathToLocations;
    protected String methodName;
    protected String startLine;
    protected String yieldingPoint;
    protected int threshold;

    protected PHASE phase;


    enum PHASE{
        LOCATIONS_MINIMIZER,
        CRITICAL_POINT_SEARCH,
        BARRIER_POINT_SEARCH
    }

    enum TYPE {
        CONCURRENT_METHODS,
        ALL_LOCATIONS,
        DELTA_DEBUG,
        GET_STACK_TRACE,
        ROOT_METHOD_ANALYSIS,
        DELAY_INJECTION,
        METHOD_END_LINE,
        DOWNWARD_MAVEN_EXEC,
        ADD_BARRIER_POINT,
        BARRIER_STACKTRACE,
        ADD_BARRIER_POINT_2,
        EXECUTION_MONITOR
    }

    protected Xpp3Dom domNode;

    protected CleanSurefireExecution(Plugin surefire, String originalArgLine, String executionId,
                                     MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager pluginManager,
                                     String nondexDir, String testName, String localRepository) {
        this.executionId = executionId;
        this.surefire = surefire;
        this.testName = testName;
        this.originalArgLine = sanitizeAndRemoveEnvironmentVars(originalArgLine);
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.configuration = new Configuration(executionId, nondexDir, testName);
        this.localRepository = localRepository;

    }

    //MOJO FindTestsRunMojo: For finding concurrent methods and number of threads running
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                  MavenSession mavenSession, BuildPluginManager pluginManager, String nondexDir, String testName, String localRepository) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                nondexDir, testName, localRepository);

        this.phase = PHASE.LOCATIONS_MINIMIZER;

        System.out.println("Inside run in CleanSurefireExecution");

        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(TYPE.CONCURRENT_METHODS);
    }

    //MOJO RunWithDelaysMojo: For getting complete list of locations(not minimal)
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                    MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository, String testName, int delay) {

        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        this.delay = delay;
        this.phase = PHASE.LOCATIONS_MINIMIZER;

        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(TYPE.ALL_LOCATIONS);

    }

    //MOJO DeltaDebugMojo(0), MOJO CritSearchMojo: Generate stack trace(1), Generate stack trace barrier point(2)
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                  MavenSession mavenSession, BuildPluginManager pluginManager,
                                  String flakesyncDir, String localRepository, String testName, int delay,
                                  String pathToLocations, int generateStacktrace) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        this.delay = delay;
        this.pathToLocations = pathToLocations;

        if (generateStacktrace == 0) {
            this.phase = PHASE.CRITICAL_POINT_SEARCH;
            this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
            this.setupArgline(TYPE.GET_STACK_TRACE);
        }else if (generateStacktrace == 1) {
            this.phase = PHASE.LOCATIONS_MINIMIZER;
            this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
            this.setupArgline(TYPE.DELTA_DEBUG);
        } else if (generateStacktrace == 2) {
            this.phase = PHASE.BARRIER_POINT_SEARCH;
            this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
            this.setupArgline(TYPE.BARRIER_STACKTRACE);
        }
    }

    //MOJO CritSearchMojo: Delay Injection
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                        MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository,
                                        String testName, int delay, String pathToLocations, String methodName, boolean barrier) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        if(!barrier) {
            this.phase = PHASE.CRITICAL_POINT_SEARCH;

            this.delay = delay;
            this.pathToLocations = pathToLocations;
            this.methodName = methodName;

            this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
            this.setupArgline(TYPE.DELAY_INJECTION);
        } else {
            this.phase = PHASE.BARRIER_POINT_SEARCH;

            this.delay = delay;
            this.startLine = pathToLocations;
            this.yieldingPoint = methodName;

            this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
            this.setupArgline(TYPE.ADD_BARRIER_POINT_2);


        }
    }

    //MOJO CritSearchMojo: Root Method Analysis
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                               MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository,
                                               String testName, int delay, String methodName) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        this.phase = PHASE.CRITICAL_POINT_SEARCH;

        this.delay = delay;
        this.methodName = methodName;

        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(TYPE.ROOT_METHOD_ANALYSIS);
    }

    //MOJO BarrierPointMojo: Downward Maven
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                  MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository,
                                  String testName, int delay, String startLine, boolean threshold) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        this.phase = PHASE.BARRIER_POINT_SEARCH;

        this.delay = delay;
        this.startLine = startLine;

        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(TYPE.DOWNWARD_MAVEN_EXEC);
    }

    //MOJO BarrierPointMojo: Instrument/add barrier point
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                  MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository,
                                  String testName, int delay, String startLine, String yieldingPoint, int threshold) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        this.phase = PHASE.BARRIER_POINT_SEARCH;

        this.delay = delay;
        this.startLine = startLine;
        this.yieldingPoint = yieldingPoint;
        this.threshold = threshold;

        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(TYPE.ADD_BARRIER_POINT);
    }

    //MOJO BarrierPointMojo: Execution Monitor
    public CleanSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                  MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository,
                                  String testName, int delay, int upperBoundary) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir, testName, localRepository);

        this.phase = PHASE.BARRIER_POINT_SEARCH;

        this.delay = delay;
        this.startLine = upperBoundary+"";

        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(TYPE.EXECUTION_MONITOR);
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

    public void run() throws MojoExecutionException {
        try {
            //System.out.println("Running surefire execution" + this.surefire + " "
            //+ this.mavenProject + " " + this.mavenSession + " " +
            //        this.pluginManager);
            MojoExecutor.executeMojo(this.surefire, MojoExecutor.goal("test"), domNode,
                    MojoExecutor.executionEnvironment(this.mavenProject, this.mavenSession, this.pluginManager));
        } catch (MojoExecutionException mojoException) {
            Logger.getGlobal().log(Level.INFO, "Surefire failed when running tests for " + this.configuration.executionId + "with delay: " + this.delay);
            throw new MojoExecutionException("escalating");
        }
    }

    private boolean checkSysPropsDeprecated() {
        System.out.println("Checking system properties deprecated");
        String[] split = this.surefire.getVersion().split("\\.");
        System.out.println(split[0]);
        float f = Float.parseFloat(split[0]) + (Float.parseFloat(split[1]) / (10 * split[1].length()));
        System.out.println("here's the version as a float: " + f);
        return f > 2.20;
    }

    protected void setupArgline(TYPE mode) {
        // create the flakeSync-delay argLine for surefire based on the current configuration
        // this adds things like where to save test reports, what directory NonDex
        // should store results in, what seed and mode should be used.

        this.domNode.addChild((this.makeNode("test", this.testName)));

        String pathToJar = this.localRepository;
        // TODO: Encode path to agent in some final static variable for ease of access and potential changes to name/version
        String argLineToSet = "-javaagent:" + pathToJar;
        if(this.phase == PHASE.LOCATIONS_MINIMIZER){
            argLineToSet += CONCURRENT_METHODS_JAR;
        } else if(this.phase == PHASE.CRITICAL_POINT_SEARCH){
            argLineToSet += BOUNDARY_SEARCH_JAR;
        } else if(this.phase == PHASE.BARRIER_POINT_SEARCH){
            argLineToSet += BARRIER_SEARCH_JAR;
        }

        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        System.out.println(properties + "******************");

        boolean added = false;
        for (Xpp3Dom node : this.domNode.getChildren()) {
            if ("argLine".equals(node.getName()) && !node.getValue().contains(argLineToSet)) {
                Logger.getGlobal().log(Level.INFO, "Adding argLine to existing argLine specified by the project");
                String current = sanitizeAndRemoveEnvironmentVars(node.getValue());
                node.setValue(argLineToSet + " " + current);
                added = true;
            }

            if(properties.equals(node.getName())){
                if(mode != TYPE.CONCURRENT_METHODS) {
                    if(node.getChild("delay") == null) node.addChild(this.makeNode("delay", delay + ""));
                    else node.getChild("delay").setValue( this.delay + "");
                }
                if(mode == TYPE.ALL_LOCATIONS) {
                    if(node.getChild("concurrentmethods") == null) node.addChild(this.makeNode("concurrentmethods", "./.flakesync/ResultMethods.txt"));
                    else node.getChild("concurrentmethods").setValue("./.flakesync/ResultMethods.txt");
                    if(node.getChild("whitelist") == null) node.addChild(this.makeNode("whitelist", "./.flakesync/whitelist.txt"));
                    else node.getChild("whitelist").setValue("./.flakesync/whitelist.txt");
                } else if(mode == TYPE.DELTA_DEBUG) {
                    if(node.getChild("concurrentmethods") == null) node.addChild(this.makeNode("concurrentmethods", "./.flakesync/ResultMethods.txt"));
                    else node.getChild("concurrentmethods").setValue("./.flakesync/ResultMethods.txt");
                    if(node.getChild("whitelist") == null) node.addChild(this.makeNode("whitelist", "./.flakesync/whitelist.txt"));
                    else node.getChild("whitelist").setValue("./.flakesync/whitelist.txt");
                    if (node.getChild("concurrentmethods") == null) node.addChild(this.makeNode("concurrentmethods", "./.flakesync/ResultMethods.txt"));
                    else node.getChild("concurrentmethods").setValue("./.flakesync/ResultMethods.txt");
                    if (node.getChild("locations") == null) node.addChild(this.makeNode("locations", this.pathToLocations));
                    else node.getChild("locations").setValue(pathToLocations);
                } else if(mode == TYPE.GET_STACK_TRACE) {
                    if (node.getChild("locations") == null) node.addChild(this.makeNode("locations", this.pathToLocations));
                    else node.getChild("locations").setValue(this.pathToLocations);
                } else if(mode == TYPE.DELAY_INJECTION) {
                    if (node.getChild("locations") == null) node.addChild(this.makeNode("locations", this.pathToLocations));
                    else node.getChild("locations").setValue(this.pathToLocations);
                    if (node.getChild("methodNameForDelayAtBegining") == null) node.addChild(this.makeNode("methodNameForDelayAtBeginning", this.methodName));
                    else node.getChild("methodNameForDelayAtBeginning").setValue(this.methodName);
                } else if(mode == TYPE.ROOT_METHOD_ANALYSIS) {
                    if (node.getChild("rootMethod") == null) node.addChild(this.makeNode("rootMethod", "./" + DEFAULT_FLAKESYNC_DIR + "/Locations/Root.txt"));
                    else node.getChild("rootMethod").setValue("./" + DEFAULT_FLAKESYNC_DIR + "/Locations/Root.txt");
                    if (node.getChild("methodOnly") == null) node.addChild(this.makeNode("methodOnly", this.methodName));
                    else node.getChild("methodOnly").setValue(this.methodName);
                } else if(mode == TYPE.DOWNWARD_MAVEN_EXEC) {
                    if(node.getChild("searchMethodEndLine") == null) node.addChild(this.makeNode("searchMethodEndLine", "search"));
                    else node.getChild("searchMethodEndLine").setValue("search");
                    if(node.getChild("CodeToIntroduceVariable") == null) node.addChild(this.makeNode("CodeToIntroduceVariable", this.startLine));
                    else node.getChild("CodeToIntroduceVariable").setValue(this.startLine);
                } else if(mode == TYPE.ADD_BARRIER_POINT) {
                    if(node.getChild("CodeToIntroduceVariable") == null) node.addChild(this.makeNode("CodeToIntroduceVariable", this.startLine));
                    else node.getChild("CodeToIntroduceVariable").setValue(this.startLine);
                    if(node.getChild("YieldingPoint") == null) node.addChild(this.makeNode("YieldingPoint", this.yieldingPoint));
                    else node.getChild("YieldingPoint").setValue(this.yieldingPoint);
                    if(node.getChild("threshold") == null) node.addChild(this.makeNode("threshold", this.threshold + ""));
                    else node.getChild("threshold").setValue(this.threshold + "");
                } else if(mode == TYPE.BARRIER_STACKTRACE) {
                    if(node.getChild("CodeToIntroduceVariable") == null) node.addChild(this.makeNode("CodeToIntroduceVariable", this.pathToLocations));
                    else node.getChild("CodeToIntroduceVariable").setValue(this.pathToLocations);
                    if(node.getChild("stackTraceCollect") == null) node.addChild(this.makeNode("stackTraceCollect", "true"));
                    else node.getChild("stackTraceCollect").setValue("true");
                }else if(mode == TYPE.ADD_BARRIER_POINT) {
                    if (node.getChild("CodeToIntroduceVariable") == null) node.addChild(this.makeNode("CodeToIntroduceVariable", this.startLine));
                    else node.getChild("CodeToIntroduceVariable").setValue(this.startLine);
                    if (node.getChild("YieldingPoint") == null) node.addChild(this.makeNode("YieldingPoint", this.yieldingPoint));
                    else node.getChild("YieldingPoint").setValue(this.yieldingPoint);
                }else if(mode == TYPE.EXECUTION_MONITOR) {
                    if (node.getChild("CodeToIntroduceVariable") == null) node.addChild(this.makeNode("CodeToIntroduceVariable", this.startLine));
                    else node.getChild("CodeToIntroduceVariable").setValue(this.startLine);
                    if (node.getChild("YieldingPoint") == null) node.addChild(this.makeNode("YieldingPoint", ""));
                    else node.getChild("YieldingPoint").setValue("");
                    if (node.getChild("executionMonitor") == null) node.addChild(this.makeNode("YieldingPoint", "flag"));
                    else node.getChild("executionMonitor").setValue("flag");
                }
            }
        }
        if (!added) {
            Logger.getGlobal().log(Level.INFO, "Creating new argline for Surefire: *" + argLineToSet + "*");
            this.domNode.addChild(this.makeNode("argLine", argLineToSet));
        }

        // originalArgLine is the argLine set from Maven, not through the surefire config
        // if such an argLine exists, we modify that one also
        this.mavenProject.getProperties().setProperty("argLine",
                this.originalArgLine + " " + argLineToSet);
    }

    protected Xpp3Dom applyFlakeSyncConfig(Xpp3Dom configuration) {
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
