/*
The MIT License (MIT)
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
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package flakesync;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.DeltaDebugger;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "flakedeltadebug", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class DeltaDebugMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running FindTestsRunMojo"));
        MojoExecutionException allExceptions = null;

        LocationsDeltaDebug deltaDebug = new LocationsDeltaDebug(
                this.surefire, this.originalArgLine, this.mavenProject,
                this.mavenSession, this.pluginManager,
                this.baseDir,
                this.localRepository, this.testName, 200);

        //Generate list of all locations
        List<String> locations = generateLocsList();

        //Run delta debugging
        deltaDebug.deltaDebug(locations, locations.size());

    }

    private List<String> generateLocsList(){
        List<String> locsList = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File("/.flakedelay/Locations.txt")));
            String line = reader.readLine();
            while (line != null) {
                locsList.add(line);
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return locsList;
    }


    public void setTestName(String testName) {
        this.testName = testName;
    }

    private class LocationsDeltaDebug extends DeltaDebugger<String>{

        protected Plugin surefire;
        protected MavenProject mavenProject;
        protected MavenSession mavenSession;
        protected BuildPluginManager pluginManager;
        protected File baseDir;
        protected String testName;
        protected String localRepository;
        protected String originalArgLine;
        protected int delay;

        private LocationsDeltaDebug(Plugin surefire, String argLine, MavenProject mavenProject,
                                    MavenSession mavenSession, BuildPluginManager pluginManager,
                                    File baseDir, String localRepository, String testName,
                                    int delay){
            this.surefire = surefire;
            this.originalArgLine = argLine;
            this.mavenProject = mavenProject;
            this.mavenSession = mavenSession;
            this.pluginManager = pluginManager;
            this.baseDir = baseDir;
            this.localRepository = localRepository;
            this.testName = testName;
            this.delay = delay;
        }

        @Override
        public boolean checkValid(List<String> elements) {
            //First we need to write the elements to a temporary file for the agent to write to
            createTempFile(elements);

            DeltaDebugSurefireExecution cleanExec = new DeltaDebugSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                    this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, this.delay);
            return this.executeSurefireExecution(null, cleanExec);
        }

        private void createTempFile(List<String> elements) {
            File locsFile = new File("./.flakedelay/Locations_tmp.txt");
            try {
                FileWriter outputLocationsFile = new FileWriter(locsFile);
                BufferedWriter bw = new BufferedWriter(outputLocationsFile);

                for (String location : elements) {
                    bw.write(location);
                    bw.newLine();
                }
                bw.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                                                DeltaDebugSurefireExecution execution) {
            try {
                execution.run();
            } catch (MojoExecutionException ex) {
                return true;
            }
            return false;
        }
    }

}