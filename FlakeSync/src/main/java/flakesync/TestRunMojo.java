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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "flakesync", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class TestRunMojo extends FlakeSyncAbstractMojo {

    private List<CleanSurefireExecution> executions = new LinkedList<>();
    private ArrayList<CleanSurefireExecution> executionsWithoutShuffling =
            new ArrayList<CleanSurefireExecution>();
    private Path runFilePath = null;

    public void setFilePath(Path value) {
        this.runFilePath = this.runFilePath == null ? value : this.runFilePath;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("The test name is: " + this.testName));
        MojoExecutionException allExceptions = null;

        // If we add clean exceptions to allExceptions then the build fails if anything fails without nondex.
        // Everything in nondex-test is expected to fail without nondex so we throw away the result here.
        for (int j = 0; j < this.numRunsWithoutShuffling; j++) {
            CleanSurefireExecution cleanExec = new CleanSurefireExecution(
                    this.surefire, this.originalArgLine, this.mavenProject,
                    this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_NONDEX_DIR).toString(),
                    this.testName);
            this.executeSurefireExecution(allExceptions, cleanExec);
        }

        for (CleanSurefireExecution cleanExec : this.executionsWithoutShuffling) {
            setFilePath(cleanExec.getConfiguration().getRunFilePath());
            this.writeCurrentRunInfo(cleanExec);
        }

    }


    private MojoExecutionException executeSurefireExecution(MojoExecutionException allExceptions,
                                                            CleanSurefireExecution execution) {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            return (MojoExecutionException) Utils.linkException(ex, allExceptions);
        }
        return allExceptions;
    }

    private void writeCurrentRunInfo(CleanSurefireExecution execution) {
        try {
            Files.write(this.runFilePath,
                    (execution.getConfiguration().executionId + String.format("%n")).getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            Logger.getGlobal().log(Level.SEVERE, "Cannot write execution id to current run file", ex);
        }
    }

    public void setTestName(String[] testName) {
        // we can do something more with provided parameter
        System.out.println("Huzzah " + Arrays.toString(testName));
        this.testName = testName[0];
    }

}