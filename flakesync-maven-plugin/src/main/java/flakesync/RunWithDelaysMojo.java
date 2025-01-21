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


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "flakedelay", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RunWithDelaysMojo extends FlakeSyncAbstractMojo {

    int[] delays = {100, 200, 400 , 800, 1600, 3200, 6400, 12800};

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running RunWithDelaysMojo"));
        MojoExecutionException allExceptions = null;

        createWhiteList();

        for(int i = 0; i < delays.length; i++) {
            DelayedSurefireExecution cleanExec = new DelayedSurefireExecution(
                    this.surefire, this.originalArgLine, this.mavenProject,
                    this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, delays[i]);
            try {
                this.executeSurefireExecution(null, cleanExec);
            } catch(MojoExecutionException e) {
                break;
            }
        }
    }


    private MojoExecutionException executeSurefireExecution(MojoExecutionException allExceptions,
                                                            DelayedSurefireExecution execution) throws MojoExecutionException {
        execution.run();
        return allExceptions;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    private boolean createWhiteList() {
        System.out.println("Inside createWhiteList");
        File whitelist = new File(this.mavenProject.getBasedir() + "/.flakesync/whitelist.txt");
        File outputDir = new File(this.mavenProject.getBuild().getOutputDirectory());

        try {
            whitelist.createNewFile();
            FileWriter outputLocationsFile = new FileWriter(whitelist);
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            try (Stream<Path> paths = Files.walk(Paths.get(outputDir.toURI()))) {
                List<String> classNames = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.toString().contains("Tests"))
                        .map(path -> path.toString()
                                .replaceFirst(".*target/classes/", "")
                                .replace("/", ".")
                                .replace(".class", ""))
                        .collect(Collectors.toList());

                for (String location : classNames) {
                    bw.write(location);
                    bw.newLine();
                }
                bw.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}