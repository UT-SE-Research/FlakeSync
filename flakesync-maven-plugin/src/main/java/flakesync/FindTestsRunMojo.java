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
            CleanSurefireExecution cleanExec = new CleanSurefireExecution(
                    this.surefire, this.originalArgLine, this.mavenProject,
                    this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(),
                            ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.testName, this.localRepository);
            this.executeSurefireExecution(null, cleanExec);
        } catch (Throwable exception) {
            System.out.println("Error executing test: The test did not run");
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
}