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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Mojo(name = "critsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CritSearchMojo extends FlakeSyncAbstractMojo {

    private HashSet<String> stackTraceLines;
    private HashSet<Output> results;
    private HashSet<String> roots;
    private int delay;
    private boolean beginningFail = false;
    private HashMap<Integer, ArrayList<String>> clusters = new HashMap<Integer, ArrayList<String>>();


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running CriticalPointMojo"));
        MojoExecutionException allExceptions = null;

        results = new HashSet<Output>();

        //runs mvn-run-and-find-stack-trace.sh: mvn test -pl $module -Dtest=$4  -Ddelay=$3  -Dlocations=$7

        List<String> locations = new ArrayList<String>();
        this.delay = generateLocsList(locations, this.mavenProject.getBasedir() + "/.flakesync/Locations_minimized.txt");

        //roots = new HashSet<String>();


        try {
            CleanSurefireExecution cleanExec = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                    this.mavenProject, this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, this.delay, "./.flakesync/Locations_tmp.txt", 0);
            if (!executeSurefireExecution(null, cleanExec)) { // Running this will create the stacktrace to be parsed later
                System.out.println("This minimized location is not a good one. Go back and run minimizer.");
                return;
            }

            System.out.println("NOW WE ARE PARSING THE STACK TRACE");
            //parse-stack-trace.sh
            stackTraceLines = new HashSet<String>();
            parseStackTrace();
            System.out.println(stackTraceLines.toString());
            //Iterating through parsed stack traces

            HashSet<String> visited = new HashSet<String>();

            for (String line : stackTraceLines) {
                String[] locs = line.split(",");
                int threadId = Integer.parseInt(locs[locs.length - 1]);
                for (int i = 0; i < locs.length - 1; i++) {
                    String itemLocation = locs[i];
                    if (!visited.contains(itemLocation)) {
                        int workingDelay = delay;
                        visited.add(itemLocation);

                        System.out.println(itemLocation);
                        String className = itemLocation.split("#")[0];
                        if (className.contains("$")) {
                            itemLocation = itemLocation.split("$")[0];
                        }

                        File directory = new File(this.baseDir + "/.flakesync/Locations/Line/");
                        System.out.println(directory.mkdirs() + " = whether dir creation worked");
                        String filePath = "/.flakesync/Locations/Line/loc-" + threadId + "-" + i + ".txt";
                        try {
                            File file = new File(this.baseDir + filePath);
                            file.createNewFile();
                            FileWriter fw = new FileWriter(file);
                            BufferedWriter bw = new BufferedWriter(fw);

                            bw.write(itemLocation + ":" + this.testName);
                            bw.newLine();
                            bw.flush();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }

                        cleanExec = new CleanSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                            this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, delay, "." + filePath,
                                0);

                        int result = executeSurefireExecution(allExceptions, cleanExec, itemLocation, threadId);

                        if (result == 0) {
                            System.out.println("Linkage Error!");
                            break;
                        } else if (result == 3) { // We need to test longer delays
                            int maxDelay = 25600;
                            workingDelay *= 2;
                            while (workingDelay <= maxDelay) {
                                System.out.println("Let's try this again with a longer delay,"
                                        + " it looks like the test passed: "
                                        + workingDelay);

                                cleanExec = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                        this.mavenProject, this.mavenSession, this.pluginManager,
                                    Paths.get(this.baseDir.getAbsolutePath(),
                                        ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, delay, "." + filePath,
                                        0);

                                result = executeSurefireExecution(allExceptions, cleanExec, itemLocation, threadId);
                                if (result < 3) { //We got a failure, we can break
                                    break;
                                }
                                workingDelay *= 2;
                            }

                            // We reached the max delay without a failure, something is wrong here
                            //results.add(new Output(testName, new String(itemLocation), false, delay));
                        } /*else {
                            //results.add(new Output(testName, new String(itemLocation), true, delay));
                        }*/
                    }
                }
            }
        } catch (Throwable exception) {
            System.out.println(exception);
            throw new RuntimeException();
        }

        /*if (!roots.isEmpty()) {
            createResultsFile1();
        } else {
            System.out.println("No roots found");
        }*/

        if (!results.isEmpty()) {
            createResultsFile1();
        } else {
            System.out.println("No roots found");
        }


        // Now we analyze the root methods
        HashSet<String> rootsFromFile;
        rootsFromFile = getRoots();
        if (!rootsFromFile.isEmpty()) {
            roots = rootsFromFile;
        }

        FileWriter resultsFile = null;
        try {
            resultsFile = new FileWriter(mavenProject.getBasedir() + "/.flakesync/Results-Boundary/Boundary-"
                    + testName + "-Result.csv");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        BufferedWriter bw = new BufferedWriter(resultsFile);

        for (String root : roots) {
            System.out.println("Root String: " + root);
            String tmp = root.split(",")[1].split("\\(")[0];
            String[] tmpArr = tmp.split("/");
            String methodName = tmpArr[tmpArr.length - 1];
            System.out.println("METHOD NAME: " + methodName);
            String className = tmp.substring(0, tmp.lastIndexOf("/"));
            //Create the root file
            //Java API to get delimiters to generate paths, concatenate with current OS delimiter
            try {
                System.out.println("Root file contents:     " + className + "#" + methodName + ":" + testName);
                FileWriter rootFile = new FileWriter(mavenProject.getBasedir() + "/.flakesync/Locations/Root.txt");
                BufferedWriter bw1 = new BufferedWriter(rootFile);
                bw1.write(className + "#" + methodName + ":" + testName);
                bw1.newLine();
                bw1.flush();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            String beginningLineName;
            try {
                CleanSurefireExecution rootMethodAnalysisExecution =
                        new CleanSurefireExecution(this.surefire, this.originalArgLine, this.mavenProject,
                                this.mavenSession, this.pluginManager,
                                Paths.get(this.baseDir.getAbsolutePath(),
                                        ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                        this.localRepository, this.testName, delay, methodName);

                executeSurefireExecution(null, rootMethodAnalysisExecution);

                beginningLineName = delayInjection();
                System.out.println("HERE ARE THE RESULTS OF DELAY INJECTION: " + beginningLineName);

                String upperBoundary;
                if (beginningLineName != null) { // Injecting the delay at this line did in fact work
                    upperBoundary = sequentialDebug(beginningLineName);
                } else {
                    upperBoundary = sequentialDebug(null);
                }
                System.out.println("HERE ARE THE RESULTS OF SEQUENTIAL DEBUG: " + clusters.size());

            } catch (Throwable exception) {
                System.out.println(exception);
                throw new RuntimeException();
            }

            try {
                if (!clusters.keySet().isEmpty()) {
                    System.out.println("Cluster keyset " + clusters.keySet() );
                    for (int i = 1; i <= clusters.size(); i++) {
                        System.out.println("Cluster: " + clusters.get(i));
                        if (clusters.get(i).size() > 1) {
                            System.out.println("Cluster with more than 1 element");
                            bw.write(clusters.get(i).get(0).trim() + "-"
                                + clusters.get(i).get(clusters.get(i).size() - 1).trim() + "[" + delay + "]");
                            bw.newLine();
                        } else {
                            System.out.println("Cluster with < 1 element");
                            if (clusters.get(i).isEmpty()) {
                                System.out.println("Cluster without element: NO CRITICAL POINT FOUND");
                                return;
                            }
                            bw.write(clusters.get(i).get(0) + "-" + clusters.get(i).get(0)
                                + "[" + delay + "]");
                            bw.newLine();
                        }
                    }
                } else {
                    if (!beginningFail) {
                        bw.write(beginningLineName + "-" + beginningLineName + "[" + delay + "]");
                        bw.newLine();
                    }
                }
                bw.flush();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

    }

    private HashSet<String> getRoots() {
        HashSet<String> roots = new HashSet<String>();

        File file = new File(mavenProject.getBasedir()
            + "/.flakesync/Results-Boundary/" + this.testName + "Result.csv");

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            String line = reader.readLine();

            while (line != null) {
                if (!line.startsWith("#")) {
                    roots.add(line.split(",")[1] + "," + line.split(",")[2]);
                }
                line = reader.readLine();
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return roots;
    }

    private void createResultsFile1() {
        try {
            File resultsDir = new File(mavenProject.getBasedir() + "/.flakesync/Results-Boundary/");
            resultsDir.mkdirs();
            FileWriter outputLocationsFile = new FileWriter(this.mavenProject.getBasedir()
                + "/.flakesync/Results-Boundary/" + testName + "Result.csv");

            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            bw.write("#Test-Name,Thread-ID,Location[delay]");
            bw.newLine();

            //Is the following info needed?
            // echo -n "${slug},${sha},${module},${testName}" >> "$currentDir/$result_file_name"

            for (Output output : results) {
                String location = output.getLocation();
                bw.write(testName + "," + location);
                bw.newLine();

                //this.results.add(new Output(testName, location, false, this.delay));
            }
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private int generateLocsList(List<String> locsList, String path) {
        int delay = 0;
        try {
            // Input file
            File file = new File(path);

            BufferedReader reader = new BufferedReader(new FileReader(file));

            // Output file
            FileWriter outputLocationsFile = new FileWriter(this.mavenProject.getBasedir()
                + "/.flakesync/Locations_tmp.txt");
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            String line = reader.readLine();
            while (line != null) {
                String data = line.split("&")[0];
                delay = Integer.parseInt(line.split("&")[1]);
                locsList.add(data);

                bw.write(data);
                bw.newLine();

                // Read next line
                line = reader.readLine();
            }
            bw.flush();
            reader.close();
            bw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return delay;
    }

    private void parseStackTrace() {
        try {
            File file = new File(this.mavenProject.getBasedir() + "/.flakesync/Stacktrace.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String parsedInfo = "";
            String line = reader.readLine();
            while (line != null) {
                String[] data = line.split(",");
                if (("END").equals(data[1])) {
                    parsedInfo += data[0];
                    this.stackTraceLines.add(parsedInfo);
                    parsedInfo = "";
                } else {
                    System.out.println(line);
                    String tmp = data[1].substring(0, data[1].indexOf('('));
                    String className = tmp.substring(0, tmp.lastIndexOf('/'));
                    int lineNumber = Integer.parseInt(data[1].split(":")[1]
                        .substring(0, data[1].split(":")[1].length() - 1));
                    parsedInfo += (className + "#" + lineNumber + ",");
                }
                // Read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private String searchStackTrace(String searchKey1, String searchKey2) {
        try {
            File file = new File(this.mavenProject.getBasedir() + "/.flakesync/Stacktrace.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String parsedInfo = "";
            String line = reader.readLine();
            while (line != null) {
                if (line.contains(searchKey1) && line.contains(searchKey2)) {
                    return line;
                }
                // Read next line
                line = reader.readLine();
            }
            reader.close();
            return null;
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }

    private String delayInjection() throws Throwable {
        String beginningLineName = null;
        try {
            File lines = new File(this.mavenProject.getBasedir() + "/.flakesync/MethodStartAndEndLine.txt");
            BufferedReader reader = new BufferedReader(new FileReader(lines));
            String line = reader.readLine();
            while (line != null) {
                // Parse method name from line
                System.out.println("hahahah" + line);
                String[] tmp = line.split("#");
                String className = tmp[0];
                int completeLine = tmp[1].indexOf('-');
                String lowerLineNumber;
                if (completeLine < 0) {
                    line = reader.readLine();
                    continue;
                }
                String methodName = tmp[3];
                lowerLineNumber = tmp[1].substring(0, completeLine);

                System.out.println(
                        "Trying to run delay injection "
                        + methodName + " "
                        + lowerLineNumber
                );

                File rootFile = new File(this.mavenProject.getBasedir() + "/.flakesync/Locations/Root-"
                    + lowerLineNumber + ".txt");
                BufferedWriter writer = new BufferedWriter(new FileWriter(rootFile));
                writer.write(className + "#" + lowerLineNumber);
                writer.newLine();
                writer.flush();

                CleanSurefireExecution execution = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                    this.mavenProject, this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, this.delay,
                        "./.flakesync/Locations/Root-" + lowerLineNumber + ".txt", methodName, false);

                beginningFail = executeSurefireExecution(null, execution);

                if (beginningFail) {
                    beginningLineName = "";
                    File result = new File(this.mavenProject.getBasedir() + "/.flakesync/Locations/Root-"
                        + lowerLineNumber + ".txt");
                    BufferedReader resultsReader = new BufferedReader(new FileReader(result));
                    String next = resultsReader.readLine();
                    while (next != null) {
                        beginningLineName += (next + "\n");
                        next = resultsReader.readLine();
                    }
                }
                // Read next line
                line = reader.readLine();
            }
            reader.close();
            return beginningLineName;
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return beginningLineName;
    }

    private String sequentialDebug(String start) throws Throwable {
        System.out.println("SEQUENTIAL DEBUGGING:    " + start);
        String upperBoundary = "";
        ArrayList<String> currentLines = new ArrayList<String>();
        if (start != null) {
            currentLines.add(start);
        }
        try {
            File lines = new File(this.mavenProject.getBasedir() + "/.flakesync/MethodStartAndEndLine.txt");
            BufferedReader reader = new BufferedReader(new FileReader(lines));
            String line = reader.readLine();
            int cluster = 1;
            while (line != null) {
                System.out.println("huhuhuh" + line);
                String[] tmp = line.split("#");
                String className = tmp[0];
                int completeLine = tmp[1].indexOf('-');
                String lowerLineNumber;
                if (completeLine < 0) {
                    line = reader.readLine();
                    continue;
                }
                lowerLineNumber = tmp[1].substring(0, tmp[1].indexOf('-'));
                String upperLineNumber = tmp[2];

                for (int i = Integer.parseInt(lowerLineNumber); i < Integer.parseInt(upperLineNumber); i++) {
                    System.out.println( "Trying to run sequential debug " + className + " " + i);

                    File rootFile = new File(this.mavenProject.getBasedir() + "/.flakesync/Locations/Root-" + i + ".txt");
                    rootFile.createNewFile();
                    BufferedWriter writer = new BufferedWriter(new FileWriter(rootFile));
                    writer.write(className + "#" + i);
                    writer.newLine();
                    writer.flush();

                    CleanSurefireExecution execution = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                        this.mavenProject, this.mavenSession, this.pluginManager,
                        Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                        this.localRepository, this.testName, this.delay, "./.flakesync/Locations/Root-" + i + ".txt", 3);

                    boolean failed = executeSurefireExecution(null, execution);

                    if (failed) {
                        /*String lineName = "";
                        File result = new File(this.mavenProject.getBasedir() + "/.flakesync/Locations/Root-" + i + ".txt");
                        BufferedReader resultsReader = new BufferedReader(new FileReader(result));
                        String next = resultsReader.readLine();
                        while (next != null) {
                            lineName += (next + "\n");
                            next = resultsReader.readLine();
                        }*/
                        currentLines.add(className + "#" + i);
                    } else {
                        if (!currentLines.isEmpty()) {
                            clusters.put(cluster, currentLines);
                            cluster++;
                        }
                        currentLines = new ArrayList<String>();

                    }
                }
                // Read next line
                line = reader.readLine();
            }
            reader.close();
            if (!currentLines.isEmpty()) {
                System.out.println("Adding the final cluster: " + currentLines);
                clusters.put(cluster, currentLines);
            }
            System.out.println("FINAL final cluster from deq deb: " + clusters);
            return upperBoundary;
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return upperBoundary;
    }

    private int executeSurefireExecution(MojoExecutionException allExceptions,
            CleanSurefireExecution execution, String itemLoc, int threadID)
            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            String className = itemLoc.split("#")[0];
            String lineNumber = itemLoc.split("#")[1];
            String rootLine = searchStackTrace(className, ":" + lineNumber);
            rootLine += "[" + this.delay + "]";
            System.out.println(threadID + "," + className + ".*:" + lineNumber);
            System.out.println("Formatted rootLine: " + rootLine);
            //roots.add(rootLine);
            Output root = new Output(testName, new String(rootLine), true, delay);
            results.add(root);
            return 1;
        }
        return 3;
    }

    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                             CleanSurefireExecution execution)
                                            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            Utils.linkException(ex, allExceptions);
            return true;
        }
        return false;
    }

}
