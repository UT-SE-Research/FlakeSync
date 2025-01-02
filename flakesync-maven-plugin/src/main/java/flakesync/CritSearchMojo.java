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
import java.util.HashSet;
import java.util.List;

@Mojo(name = "critsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CritSearchMojo extends FlakeSyncAbstractMojo {

    HashSet<String> stackTraceLines;

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


        /*parse-stack-trace.sh*/
        stackTraceLines = new HashSet<String>();
        parseStackTrace();



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

    private void parseStackTrace(){
        try {
            File f = new File(this.mavenProject.getBasedir()+"/.flakesync/Stacktrace-.txt");
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String parsedInfo = "";
            String line = reader.readLine();
            System.out.println(line);
            while (line != null) {
                String[] data = line.split(",");
                if(("END").equals(data[1])){
                    parsedInfo += data[0];
                    this.stackTraceLines.add(parsedInfo);
                    parsedInfo = "";
                }else{
                    String tmp = data[1].substring(0, data[1].indexOf('('));
                    String className = tmp.substring(0, tmp.lastIndexOf('/'));
                    System.out.println(className);
                    int lineNumber = Integer.parseInt(data[1].split(":")[1].substring(0, data[1].split(":")[1].length() - 1));
                    System.out.println(lineNumber);
                    // ${className}#${lineNumber},
                    parsedInfo += (className + "#" + lineNumber + ",");
                }
                // read next line
                line = reader.readLine();
            }
            reader.close();

            f = new File(this.mavenProject.getBasedir()+"/.flakesync/stackTraced-parsed-.txt");
            f.delete();
            f.createNewFile();
            FileWriter outputLocationsFile = new FileWriter(f);
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            for (String location : this.stackTraceLines) {
                bw.write(location);
                bw.newLine();
            }
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
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