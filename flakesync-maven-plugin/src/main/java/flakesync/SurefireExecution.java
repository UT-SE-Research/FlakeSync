/*
The MIT License (MIT)
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman
Copyright (c) 2025 August Shi



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
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package flakesync;

import flakesync.Constants;
import flakesync.common.Configuration;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static flakesync.common.ConfigurationDefaults.CORE_JAR;

public class SurefireExecution {


    protected Xpp3Dom domNode;
    protected Plugin surefire;
    protected MavenProject mavenProject;
    protected MavenSession mavenSession;
    protected BuildPluginManager pluginManager;
    protected String flakesyncDir;
    protected String localRepository;
    protected Configuration configuration;

    protected int delay;

    enum PHASE {
        LOCATIONS_MINIMIZER,
        CRITICAL_POINT_SEARCH,
        BARRIER_POINT_SEARCH
    }

    private SurefireExecution(Plugin surefire, MavenProject mavenProject, MavenSession mavenSession,
                               BuildPluginManager pluginManager, String flakesyncDir, String localRepository, int delay) {
        this.surefire = surefire;
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.flakesyncDir = flakesyncDir;
        this.localRepository = localRepository;
        this.delay = delay;
        String executionId = "clean_" + Utils.getFreshExecutionId();
        this.configuration = new Configuration(executionId, flakesyncDir);
        this.domNode = this.applyFlakeSyncConfig((Xpp3Dom) surefire.getConfiguration(), executionId);
        //addAttributeToConfig(this.domNode, "forkedProcessTimeoutInSeconds", "7200");
    }

    public String getExecutionId() {
        return this.configuration.executionId;
    }

    public void run() throws Throwable {
        try {
            MojoExecutor.executeMojo(this.surefire, MojoExecutor.goal("test"), domNode,
                    MojoExecutor.executionEnvironment(this.mavenProject, this.mavenSession, this.pluginManager));
        } catch (MojoExecutionException mojoException) {
            if (mojoException.getCause() instanceof PluginExecutionException) {
                Logger.getGlobal().log(Level.INFO, "Surefire TIMED OUT when running tests for "
                        + this.configuration.executionId + " with delay: " + this.delay);
                throw mojoException.getCause();
            } else {
                Logger.getGlobal().log(Level.INFO, "Surefire failed when running tests for "
                        + this.configuration.executionId + " with delay: " + this.delay);
                throw new MojoExecutionException("escalating");
            }
        }
    }

    protected Xpp3Dom applyFlakeSyncConfig(Xpp3Dom configuration, String executionId) {
        //Xpp3Dom configNode = configuration;
        Xpp3Dom configNode = null;
        if (configNode == null) {
            configNode = new Xpp3Dom("configuration");
        }
        return setReportOutputDirectory(configNode, executionId);
    }

    protected Xpp3Dom setReportOutputDirectory(Xpp3Dom configNode, String executionId) {
        configNode = this.addAttributeToConfig(configNode, "reportsDirectory",
                Constants.getExecutionDir(this.mavenProject.getBasedir().toString(), executionId).toString());
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

    protected void setupArgline(PHASE phase, String originalArgLine) {
        String pathToJar = this.localRepository;
        // TODO: Encode path to agent in some final static variable for ease of access and potential changes to name/version
        String argLineToSet = "-javaagent:" + pathToJar + CORE_JAR;

        for (Xpp3Dom node : this.domNode.getChildren()) {
            if ("argLine".equals(node.getName()) && !node.getValue().contains(argLineToSet)) {
                String current = sanitizeAndRemoveEnvironmentVars(node.getValue());
                node.setValue(argLineToSet + " " + current);
            }
        }

        if (domNode.getChild("argLine") == null) {
            this.domNode.addChild(this.makeNode("argLine", argLineToSet));
        }


        // originalArgLine is the argLine set from Maven, not through the surefire config
        // if such an argLine exists, we modify that one also
        this.mavenProject.getProperties().setProperty("argLine", originalArgLine + " " + argLineToSet);
    }

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

    private boolean checkSysPropsDeprecated() {
        String[] split = this.surefire.getVersion().split("\\.");
        float version = Float.parseFloat(split[0]) + (Float.parseFloat(split[1]) / (10 * split[1].length()));
        return version > 2.20;
    }

    private void addAgentMode(String mode) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "agentmode", mode);
    }

    private void addTestName(String testName) {
        addAttributeToConfig(this.domNode, "test", testName);
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, ".test", testName);
    }

    private void addDelay(String delay) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "delay", delay);
    }

    private void addWhitelist(String testname) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "whitelist", String.valueOf(
                Constants.getWhitelistFilepath(testname)));
    }

    private void addCM(String testname) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "concurrentmethods", String.valueOf(
                Constants.getConcurrentMethodsFilepath(testname)));
    }

    private void addSearchMethodEL() {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "searchMethodEndLine", "search");
    }

    private void addSearchMethodMN() {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "searchForMethodName", "search");
    }

    private void addCodeToIntroVar(String line) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "CodeToIntroduceVariable", line);
    }

    private void addCollectST() {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "stackTraceCollect", "true");
    }

    private void addYieldPt(String yieldPt, int threshold) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "YieldingPoint", yieldPt);
        addAttributeToConfig(propertiesNode, "threshold", threshold + "");
    }

    private void addMonitorFlag() {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "executionMonitor", "flag");
    }

    private void addLocs(String testname) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "locations", String.valueOf(
                Constants.getAllLocationsFilepath(testname)));
    }

    private void addLocs(String testname, String locations) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "locations", locations);
    }

    private void addProvidedLocs(String testname) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "locations", String.valueOf(
                Constants.getWorkingLocationsFilepath(testname)));
    }

    private void addRootMethod(String testname) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, "rootMethod", String.valueOf(
                Constants.getRootMethodFilepath(testname)));
    }

    private void addMethodName(String property, String methodName) {
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        Xpp3Dom propertiesNode = addAttributeToConfig(this.domNode, properties, "").getChild(properties);
        addAttributeToConfig(propertiesNode, property, methodName);
    }

    private void addProcessTimeout(int delay) {
        addAttributeToConfig(this.domNode, "forkedProcessTimeoutInSeconds", String.valueOf(4 * delay));
    }

    public static class SurefireFactory {
        public static SurefireExecution createConcurrentMethodsExec(Plugin surefire, String originalArgLine,
                                                                     MavenProject mavenProject, MavenSession mavenSession,
                                                                     BuildPluginManager pluginManager, String flakesyncDir,
                                                                     String testName, String localRepository) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, 0);
            execution.addTestName(testName);
            execution.setupArgline(PHASE.LOCATIONS_MINIMIZER, originalArgLine);
            execution.addAgentMode("CONCURRENT_METHODS");

            return execution;
        }

        public static SurefireExecution createDelayAllExec(Plugin surefire, String originalArgLine,
                                                           MavenProject mavenProject, MavenSession mavenSession,
                                                           BuildPluginManager pluginManager, String flakesyncDir,
                                                           String localRepository, String testName, int delay) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addCM(testName);
            execution.addDelay(delay + "");
            execution.addWhitelist(testName);
            execution.setupArgline(PHASE.LOCATIONS_MINIMIZER, originalArgLine);
            execution.addAgentMode("ALL_LOCATIONS");

            return execution;
        }

        public static SurefireExecution createDownwardMvnExec(Plugin surefire, String originalArgLine,
                                                              MavenProject mavenProject, MavenSession mavenSession,
                                                              BuildPluginManager pluginManager, String flakesyncDir,
                                                              String localRepository, String testName, int delay,
                                                              String line) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addDelay(delay + "");
            execution.addSearchMethodEL();
            execution.addCodeToIntroVar(line);
            execution.setupArgline(PHASE.BARRIER_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("DOWNWARD_MVN");
            return execution;
        }

        public static SurefireExecution createBarrierSTExec(Plugin surefire, String originalArgLine,
                                                            MavenProject mavenProject, MavenSession mavenSession,
                                                            BuildPluginManager pluginManager, String flakesyncDir,
                                                            String localRepository, String testName, int delay,
                                                            String line) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addDelay(delay + "");
            execution.addCodeToIntroVar(line);
            execution.addCollectST();
            execution.setupArgline(PHASE.BARRIER_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("BARRIER_ST");
            return execution;
        }

        public static SurefireExecution createYieldExec1(Plugin surefire, String originalArgLine,
                                                        MavenProject mavenProject, MavenSession mavenSession,
                                                        BuildPluginManager pluginManager, String flakesyncDir,
                                                        String localRepository, String testName, int delay,
                                                        String startLoc, String yieldPt, int threshold) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addDelay(delay + "");
            execution.addCodeToIntroVar(startLoc);
            execution.addYieldPt(yieldPt, threshold);
            execution.setupArgline(PHASE.BARRIER_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("ADD_YIELD_PT1");
            return execution;
        }

        public static SurefireExecution createYieldExec2(Plugin surefire, String originalArgLine,
                                                         MavenProject mavenProject, MavenSession mavenSession,
                                                         BuildPluginManager pluginManager, String flakesyncDir,
                                                         String localRepository, String testName, int delay,
                                                         String startLoc, String yieldPt) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addDelay(delay + "");
            execution.addCodeToIntroVar(startLoc);
            execution.addYieldPt(yieldPt, 1);
            execution.addSearchMethodMN();
            execution.setupArgline(PHASE.BARRIER_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("ADD_YIELD_PT2");
            return execution;
        }

        public static SurefireExecution createExecMon(Plugin surefire, String originalArgLine,
                                                      MavenProject mavenProject, MavenSession mavenSession,
                                                      BuildPluginManager pluginManager, String flakesyncDir,
                                                      String localRepository, String testName, int delay,
                                                      String line) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addDelay(delay + "");
            execution.addCodeToIntroVar(line);
            execution.addMonitorFlag();
            execution.setupArgline(PHASE.BARRIER_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("EXEC_MONITOR");

            return execution;
        }

        public static SurefireExecution createDeltaDebugExec(Plugin surefire, String originalArgLine,
                                                             MavenProject mavenProject, MavenSession mavenSession,
                                                             BuildPluginManager pluginManager, String flakesyncDir,
                                                             String localRepository, String testName, int delay) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addProvidedLocs(testName);
            execution.addDelay(delay + "");
            execution.addWhitelist(testName);
            execution.setupArgline(PHASE.LOCATIONS_MINIMIZER, originalArgLine);
            execution.addAgentMode("DELTA_DEBUG");

            return execution;
        }

        public static SurefireExecution getDelayLocExec(Plugin surefire, String originalArgLine,
                                                  MavenProject mavenProject, MavenSession mavenSession,
                                                  BuildPluginManager pluginManager, String flakesyncDir,
                                                  String localRepository, String testName, int delay,
                                                  String locationsPath) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addLocs(testName, locationsPath);
            execution.addDelay(delay + "");
            execution.setupArgline(PHASE.CRITICAL_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("DELAY_INJECTION_BY_LOC");

            return execution;
        }

        public static SurefireExecution getRMAExec(Plugin surefire, String originalArgLine,
                                                        MavenProject mavenProject, MavenSession mavenSession,
                                                        BuildPluginManager pluginManager, String flakesyncDir,
                                                        String localRepository, String testName, int delay,
                                                        String methodName) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addRootMethod(testName);
            execution.addMethodName("methodOnly", methodName);
            execution.addDelay(delay + "");
            execution.setupArgline(PHASE.CRITICAL_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("ROOT_METHOD_ANALYSIS");

            return execution;
        }

        public static SurefireExecution getDelayMethodExec(Plugin surefire, String originalArgLine,
                                                        MavenProject mavenProject, MavenSession mavenSession,
                                                        BuildPluginManager pluginManager, String flakesyncDir,
                                                        String localRepository, String testName, int delay,
                                                        String locationsPath, String methodName) {
            SurefireExecution execution = new SurefireExecution(surefire, mavenProject, mavenSession, pluginManager,
                    flakesyncDir, localRepository, delay);
            execution.addTestName(testName);
            execution.addLocs(testName, locationsPath);
            execution.addMethodName("methodNameForDelayAtBeginning", methodName);
            execution.addDelay(delay + "");
            execution.setupArgline(PHASE.CRITICAL_POINT_SEARCH, originalArgLine);
            execution.addAgentMode("DELAY_INJECTION_BY_METHOD");

            return execution;
        }
    }
}
