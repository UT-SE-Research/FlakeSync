package flakesync;


import flakesync.common.ConfigurationDefaults;

import flakesync.common.Level;
import flakesync.common.Logger;
import flakesync.common.Output;
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
    ArrayList<Output> results;
    HashSet<String> roots;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running CriticalPointMojo"));
        MojoExecutionException allExceptions = null;

        results = new ArrayList<Output>();

        /*runs mvn-run-and-find-stack-trace.sh: mvn test -pl $module -Dtest=$4  -Ddelay=$3  -Dlocations=$7*/

        List<String> locations = new ArrayList<String>();
        int delay = generateLocsList(locations, this.mavenProject.getBasedir()+"/.flakesync/Locations.txt");


        CritSearchSurefireExecution cleanExec  = new CritSearchSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                this.mavenSession, this.pluginManager,
                Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                this.localRepository, this.testName, delay, "/.flakesync/Locations_tmp.txt");

        if(!executeSurefireExecution(allExceptions, cleanExec)){ //Running this will create the stacktrace to be parsed later
            System.out.println("This minimized location is not a good one. Go back and run minimizer.");
            return;
        }


        /*parse-stack-trace.sh*/
        stackTraceLines = new HashSet<String>();
        parseStackTrace();
        System.out.println(stackTraceLines.toString());
        /*Iterating through parsed stack traces*/

        HashSet<String> visited = new HashSet<String>();
        roots = new HashSet<String>();

        for(String line : stackTraceLines) {
            String[] locs = line.split(",");
            int threadId = Integer.parseInt(locs[locs.length-1]);
            for(int i = 0; i < locs.length-1; i++) {
                String itemLocation = locs[i];
                //locations = new ArrayList<String>();
                if(!visited.contains(itemLocation)) {
                    visited.add(itemLocation);

                    String filePath = "/.flakesync/Locations/Line/loc-"+threadId+"-"+i+".txt";
                    try{
                        FileWriter fw = new FileWriter(this.baseDir+filePath);
                        BufferedWriter bw = new BufferedWriter(fw);

                        bw.write(itemLocation+":"+this.testName);
                        bw.newLine();
                        bw.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    cleanExec = new CritSearchSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                            this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, filePath);

                    int result = executeSurefireExecution(allExceptions, cleanExec, itemLocation, threadId);

                    if( result == 0) {
                        System.out.println("Linkage Error!");
                        break;
                    }else if(result == 3) { //We need to test longer delays
                        int maxDelay = 25600;
                        delay *= 2;
                        while(delay <= maxDelay) {
                            System.out.println("Let's try this again with a longer delay, it looks like the test passed");

                            cleanExec = new CritSearchSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                                    this.mavenSession, this.pluginManager,
                                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, delay, "./.flakesync/Locations_tmp.txt");


                            result = executeSurefireExecution(allExceptions, cleanExec, itemLocation, threadId);
                            if(result < 3) { //We got a failure, we can break
                                break;
                            }
                            delay *= 2;
                        }

                        //We reached the max delay without a failure, something is wrong here
                        results.add(new Output(testName, itemLocation, false, delay));
                    }else {
                        results.add(new Output(testName, itemLocation, true, delay));
                    }
                }
            }
        }

        if(!roots.isEmpty()) {
            createResultsFile();
        }
    }

    private void createResultsFile() {
        try {
            FileWriter outputLocationsFile = new FileWriter(this.mavenProject.getBasedir() +
                    "/.flakesync/Results-Boundary/Result.csv");

            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            bw.write("#Test-Name,Thread-ID,Location[delay]");
            bw.newLine();

            //Is the following info needed?
            // echo -n "${slug},${sha},${module},${testName}" >> "$currentDir/$result_file_name"

            for (String location : roots) {
                bw.write(testName + "," + location);
                bw.newLine();

                this.results.add(new Output(testName, location, false, 0));
            }
            bw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private int generateLocsList(List<String> locsList, String path){
        int delay = 0;
        try {
            //Input file
            File f = new File(path);
            BufferedReader reader = new BufferedReader(new FileReader(f));

            //Output file
            FileWriter outputLocationsFile = new FileWriter(this.mavenProject.getBasedir() +
                    "/.flakesync/Locations_tmp.txt");
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            String line = reader.readLine();
            System.out.println(line);
            while (line != null) {
                String data = line.split("&")[0];
                delay = Integer.parseInt(line.split("&")[1]);
                locsList.add(data);

                bw.write(data);
                bw.newLine();

                // read next line
                line = reader.readLine();
            }
            bw.flush();
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
                    //System.out.println(className);
                    int lineNumber = Integer.parseInt(data[1].split(":")[1].substring(0, data[1].split(":")[1].length() - 1));
                    //System.out.println(lineNumber);
                    // ${className}#${lineNumber},
                    parsedInfo += (className + "#" + lineNumber + ",");
                }
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String searchStackTrace(String searchKey1, String searchKey2){
        try {
            File f = new File(this.mavenProject.getBasedir()+"/.flakesync/Stacktrace-.txt");
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String parsedInfo = "";
            String line = reader.readLine();
            while (line != null) {
                if(line.contains(searchKey1) && line.contains(searchKey2)){
                    return line;
                }
                // read next line
                line = reader.readLine();
            }
            reader.close();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private int executeSurefireExecution(MojoExecutionException allExceptions,
                                         CritSearchSurefireExecution execution,
                                         String itemLoc,
                                         int threadID) {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            String className = itemLoc.split("#")[0];
            String lineNumber = itemLoc.split("#")[1];
            String rootLine = searchStackTrace(className, ":" + lineNumber);
            /*rootLine.replaceAll("\\(", "=");
            rootLine.replaceAll("\\)", "!");
            rootLine.replaceAll("\\$", "<");*/
            rootLine += "[" + execution.delay + "]";
            System.out.println(threadID + "," + className + ".*:" + lineNumber);
            System.out.println("Formatted rootLine: " + rootLine);
            roots.add(rootLine);
            return 1;
        }
        return 3;
    }

    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                                 CritSearchSurefireExecution execution) {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            return true;
        }
        return false;
    }

}