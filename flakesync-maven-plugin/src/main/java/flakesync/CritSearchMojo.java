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

import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "critsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CritSearchMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running CriticalPointMojo"));
        MojoExecutionException allExceptions = null;

        /*runs mvn-run-and-find-stack-trace.sh: mvn test -pl $module -Dtest=$4  -Ddelay=$3  -Dlocations=$7*/

        List<String> locations = new ArrayList<String>();
        int delay = generateLocsList(locations);

        CritSearchSurefireExecution cleanExec = new CritSearchSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                this.mavenSession, this.pluginManager,
                Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                this.localRepository, this.testName, delay);

        executeSurefireExecution(allExceptions, cleanExec);

    }


    private int generateLocsList(List<String> locsList){
        int delay = 0;
        try {
            File f = new File(this.mavenProject.getBasedir()+"/.flakesync/Locations.txt");
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line = reader.readLine();
            System.out.println(line);
            while (line != null) {
                String data = line.split("&")[0];
                delay = Integer.parseInt(line.split("&")[1]);
                locsList.add(data);
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return delay;
    }



    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                                 CritSearchSurefireExecution execution) {
        try {
            execution.run();
        } catch (Exception ex) {
            return true;
        }
        return false;
    }

}