/*
The MIT License (MIT)
Copyright (c) 2025 August Shi
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman



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

import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
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

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;


@Execute(phase = LifecyclePhase.TEST_COMPILE)
public abstract class FlakeSyncAbstractMojo extends AbstractMojo {

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

    @Parameter(defaultValue = "${settings.localRepository}")
    protected String localRepository;

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
