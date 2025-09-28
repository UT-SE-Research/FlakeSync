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

import flakesync.common.Level;
import flakesync.common.Logger;
import flakesync.patching.InjectFlagInCriticalPoint;
import flakesync.patching.InjectYieldStatement;
import flakesync.patching.SavePatch;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static flakesync.patching.SavePatch.getFilePath;
import static flakesync.patching.SavePatch.makePatch;

@Mojo(name = "patch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class PatchingMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running PatchingMojo"));

        try {
            FileReader boundaryResults = new FileReader(String.valueOf(Constants.getBarrierPointsResultsFilepath(
                    String.valueOf(this.mavenProject.getBasedir()), testName)));

            BufferedReader br = new BufferedReader(boundaryResults);
            String line = br.readLine(); // Skip line with headers
            while (line != null && !line.isEmpty()) {
                if (line.charAt(0) != '#') {
                    String slug = String.valueOf(this.mavenProject.getBasedir());

                    String[] lineItems = line.split(",");
                    String critPoint = lineItems[1];
                    String barrPoint = lineItems[2];
                    int threshold = Integer.parseInt(lineItems[3]);

                    // Inject code for critical point

                    // Find the right class to modify
                    String target = critPoint.split("-")[1].split("#")[0];
                    int targetLine = Integer.parseInt(critPoint.split("-")[1].split("#")[1].split("\\[")[0]);
                    target = target.split("\\$")[0].replace("/", ".");

                    // Find the corresponding Java file, saving the original file to revert later
                    Path critPath = InjectFlagInCriticalPoint.findJavaFilePath(slug, target);
                    Path critOriginal = Paths.get(String.valueOf(
                            InjectFlagInCriticalPoint.findJavaFilePath(slug, target)) + ".orig");
                    Files.copy(critPath, critOriginal);

                    // Inject the code into the Java file
                    InjectFlagInCriticalPoint.injectFlagInCritPt(slug, target, targetLine);

                    // Inject code for barrier point

                    // Find the right class to modify
                    String className = barrPoint.split("#")[0];
                    int lineNum = Integer.parseInt(barrPoint.split("#")[1]);
                    className = className.split("\\$")[0].replace("/", ".");

                    // Find the corresponding Java file, saving the original file to revert later
                    Path barrierPath = InjectFlagInCriticalPoint.findJavaFilePath(slug, className);
                    Path barrierOriginal = Paths.get(String.valueOf(
                            InjectFlagInCriticalPoint.findJavaFilePath(slug, className) + ".orig"));
                    Files.copy(barrierPath, barrierOriginal);

                    // Inject the code into the Java file
                    InjectYieldStatement.injectYieldStatement(slug, target, testName, className, lineNum, threshold);

                    className =  className.split("#")[0];
                    target = target.split("\\$")[0];

                    // Save the patch file
                    String originalBarrierFilePath = getFilePath(slug, className, true);
                    String modifiedBarrierFilePath = String.valueOf(InjectFlagInCriticalPoint
                            .findJavaFilePath(slug, className));
                    makePatch(originalBarrierFilePath, modifiedBarrierFilePath,
                        Constants.getFlakeSyncDir(slug.toString()).toString());
                    String originalCriticFilePath = getFilePath(slug, target, true);
                    String modifiedCriticFilePath = String.valueOf(InjectFlagInCriticalPoint
                            .findJavaFilePath(slug, target));
                    makePatch(originalCriticFilePath, modifiedCriticFilePath,
                        Constants.getFlakeSyncDir(slug.toString()).toString());

                    // Reset all the files
                    Files.copy(critOriginal, critPath);
                    Files.delete(critOriginal);
                    Files.copy(barrierOriginal, barrierPath);
                    Files.delete(barrierOriginal);
                }
                line = br.readLine();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }



    }

}
