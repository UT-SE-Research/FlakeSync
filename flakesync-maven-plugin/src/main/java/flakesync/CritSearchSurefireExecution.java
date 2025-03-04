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

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CritSearchSurefireExecution {

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
    protected String locations;

    protected CritSearchSurefireExecution(Plugin surefire, String originalArgLine, String executionId,
                                          MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager pluginManager,
                                          String flakesyncDir, String localRepository, String testName, int delay, String locations) {
        this.executionId = executionId;
        this.surefire = surefire;
        this.originalArgLine = sanitizeAndRemoveEnvironmentVars(originalArgLine);
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.configuration = new Configuration(executionId, flakesyncDir, testName);
        this.localRepository = localRepository;
        this.testName = testName;
        this.delay = delay;
        this.locations = locations;

    }

    public CritSearchSurefireExecution(Plugin surefire, String originalArgLine, MavenProject mavenProject,
                                       MavenSession mavenSession, BuildPluginManager pluginManager, String flakesyncDir, String localRepository,
                                       String testName, int delay, String locations) {
        this(surefire, originalArgLine, "clean_" + Utils.getFreshExecutionId(), mavenProject, mavenSession, pluginManager,
                flakesyncDir,localRepository, testName, delay, locations);
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

    public void run() throws MojoExecutionException {
        System.out.println("Inside run in CritSearchSurefireExecution "+ this.delay);
        Xpp3Dom origNode = null;
        if (this.surefire.getConfiguration() != null) {
            origNode = new Xpp3Dom((Xpp3Dom) this.surefire.getConfiguration());
        }
        System.out.println("Created node");
        Xpp3Dom domNode = this.applyFlakesyncConfig((Xpp3Dom) this.surefire.getConfiguration());
        this.setupArgline(domNode);
        this.setupArgs(domNode);
        System.out.println("Setup args worked");
        Logger.getGlobal().log(Level.FINE, "Config node passed: " + domNode.toString());
        Logger.getGlobal().log(Level.FINE, this.mavenProject + "\n" + this.mavenSession + "\n" + this.pluginManager);
        Logger.getGlobal().log(Level.FINE, "Surefire config: " + this.surefire + "  " + MojoExecutor.goal("test")
                + " " + domNode + " "
                + MojoExecutor.executionEnvironment(this.mavenProject, this.mavenSession,
                this.pluginManager));

        //runs mvn clean install
        //install();

        MojoExecutor.executeMojo(this.surefire, MojoExecutor.goal("test"),
                domNode,
                MojoExecutor.executionEnvironment(this.mavenProject, this.mavenSession, this.pluginManager));
    }

    protected void install() {
        // TODO: Maybe support custom command lines/options?
        final InvocationRequest request = new DefaultInvocationRequest();
        request.setGoals(Arrays.asList("clean", "install"));
        request.setPomFile(this.mavenProject.getFile());
        request.setProperties(new Properties());
        request.getProperties().setProperty("skipTests", "true");
        request.getProperties().setProperty("rat.skip", "true");
        /*request.getProperties().setProperty("dependency-check.skip", "true");
        request.getProperties().setProperty("enforcer.skip", "true");
        request.getProperties().setProperty("checkstyle.skip", "true");
        request.getProperties().setProperty("maven.javadoc.skip", "true");
        request.getProperties().setProperty("maven.source.skip", "true");
        request.getProperties().setProperty("gpg.skip", "true");*/
        request.setUpdateSnapshots(false);

        ByteArrayOutputStream baosOutput = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(baosOutput);
        request.setOutputHandler(new PrintStreamHandler(outputStream, true));
        ByteArrayOutputStream baosError = new ByteArrayOutputStream();
        PrintStream errorStream = new PrintStream(baosError);
        request.setErrorHandler(new PrintStreamHandler(errorStream, true));

        try {
            final Invoker invoker = new DefaultInvoker();
            final InvocationResult result = invoker.execute(request);

            if (result.getExitCode() != 0) {
                // Print out the contents of the output/error streamed out during evocation, if not suppressed
                //if (!suppressOutput) {
                    //Logger.getGlobal().log(Level.SEVERE, baosOutput.toString());
                   // Logger.getGlobal().log(Level.SEVERE, baosError.toString());
                //}

                if (result.getExecutionException() == null) {
                    throw new RuntimeException("Compilation failed with exit code " + result.getExitCode() + " for an unknown reason");
                } else {
                    throw new RuntimeException(result.getExecutionException());
                }
            }
        } catch (MavenInvocationException mie) {
            throw new RuntimeException(mie);
        }
    }

    protected void setupArgline(Xpp3Dom configNode) {
        // create the boundaryPoint argLine for surefire based on the current configuration
        // this adds things like where to save test reports, what directory flakesync
        // should store results in, what seed and mode should be used.

        String pathToJar = this.localRepository;
        // TODO: Encode path to agent in some final static variable for ease of access and potential changes to name/version
        String argLineToSet = "-javaagent:" + pathToJar + "/edu/utexas/ece/localization-core/0.1-SNAPSHOT/localization-core-0.1-SNAPSHOT.jar";

        boolean added = false;
        for (Xpp3Dom config : configNode.getChildren()) {
            if ("argLine".equals(config.getName())) {
                Logger.getGlobal().log(Level.INFO, "Adding boundaryPoint argLine to existing argLine specified by the project");
                String current = sanitizeAndRemoveEnvironmentVars(config.getValue());
                config.setValue(argLineToSet + " " + current);
                added = true;
                break;
            }
        }
        if (!added) {
            Logger.getGlobal().log(Level.INFO, "Creating new argline for Surefire: *" + argLineToSet + "*");
            configNode.addChild(this.makeNode("argLine", argLineToSet));
        }

        // originalArgLine is the argLine set from Maven, not through the surefire config
        // if such an argLine exists, we modify that one also
        this.mavenProject.getProperties().setProperty("argLine",
                this.originalArgLine + " " + argLineToSet);
        System.out.println("argline: " + this.mavenProject.getProperties().getProperty("argLine"));
    }

    private boolean checkSysPropsDeprecated() {
        System.out.println("Checking system properties deprecated");
        String[] split = this.surefire.getVersion().split("\\.");
        System.out.println(split[0]);
        float f = Float.parseFloat(split[0]) + (Float.parseFloat(split[1])/(10*split[1].length()));
        System.out.println("here's the version as a float: " + f);
        return f > 2.20;
    }

    protected void setupArgs(Xpp3Dom configNode) {

        //Add the test name
        System.out.println("Inside setup args");
        configNode.addChild((this.makeNode("test", this.testName)));
        String properties = (!checkSysPropsDeprecated()) ? ("systemPropertyVariables") : ("systemProperties");
        System.out.println("properties: " + properties);

        for (Xpp3Dom node : configNode.getChildren()) {
            if (properties.equals(node.getName())) {
                Xpp3Dom sysPropVarsNode = node;
                boolean addedDelay = false;
                boolean addedCM = false;
                boolean addedWL = false;
                boolean addedL = false;
                for(Xpp3Dom node2 : sysPropVarsNode.getChildren()) {
                    if(node2.getName().equals("delay")) {
                        node2.setValue(this.delay+"");
                        addedDelay = true;
                    }
                     if(node2.getName().equals("locations")) {
                         node2.setValue("."+this.locations);
                         addedWL = true;
                     }
                }
                if(!addedDelay) sysPropVarsNode.addChild(this.makeNode("delay", this.delay+""));
                if(!addedL) sysPropVarsNode.addChild(this.makeNode("locations", "."+this.locations));
            }
        }
    }

    protected Xpp3Dom applyFlakesyncConfig(Xpp3Dom configuration) {
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
