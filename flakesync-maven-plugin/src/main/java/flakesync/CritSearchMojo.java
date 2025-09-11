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
    private ArrayList<Output> results;
    private HashSet<String> roots;
    private int delay;
    private boolean beginningFail = false;
    private HashMap<Integer, ArrayList<String>> clusters = new HashMap<Integer, ArrayList<String>>();


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running CriticalPointMojo"));
        MojoExecutionException allExceptions = null;

        results = new ArrayList<Output>();

        List<String> locations = new ArrayList<String>();
        String locationsPath = String.valueOf(Constants.getMinLocationsFilepath(testName));
        this.delay = generateLocsList(locations, locationsPath);

        roots = new HashSet<String>();

        locationsPath = String.valueOf(Constants.getWorkingLocationsFilepath(testName));
        try {
            SurefireExecution cleanExec = SurefireExecution.SurefireFactory.getDelayLocExec(this.surefire,
                    this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, this.delay, locationsPath);
            if (!executeSurefireExecution(null, cleanExec)) {
                System.out.println("This minimized location is not a good one. Go back and run minimizer.");
                return;
            }

            //Parse the stacktrace
            stackTraceLines = new HashSet<String>();
            parseStackTrace();
            System.out.println(stackTraceLines.toString());

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

                        File file = new File(String.valueOf( Constants.getIndLocFilepath(
                                this.mavenProject.getBasedir().toString(), this.testName, threadId, i)));
                        try {
                            file.createNewFile();
                            System.out.println(file.getAbsolutePath());
                            FileWriter fw = new FileWriter(file);
                            BufferedWriter bw = new BufferedWriter(fw);

                            bw.write(itemLocation);
                            bw.newLine();
                            bw.flush();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }

                        cleanExec = SurefireExecution.SurefireFactory.getDelayLocExec(this.surefire, this.originalArgLine,
                                this.mavenProject, this.mavenSession, this.pluginManager,
                                Paths.get(this.baseDir.getAbsolutePath(),
                                        ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(), this.localRepository,
                                this.testName, workingDelay,
                                String.valueOf(Constants.getIndLocFilepath(".", this.testName, threadId, i)));

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

                                cleanExec = SurefireExecution.SurefireFactory.getDelayLocExec(this.surefire,
                                        this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                                        Paths.get(this.baseDir.getAbsolutePath(),
                                                ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                        this.localRepository, this.testName, workingDelay,
                                        String.valueOf(Constants.getIndLocFilepath(".", this.testName, threadId, i)));

                                result = executeSurefireExecution(allExceptions, cleanExec, itemLocation, threadId);
                                if (result < 3) { //We got a failure, we can break
                                    break;
                                }
                                workingDelay *= 2;
                            }

                            // We reached the max delay without a failure, something is wrong here
                            results.add(new Output(testName, itemLocation, false, workingDelay));
                        } else {
                            results.add(new Output(testName, itemLocation, true, workingDelay));
                        }
                    }
                }
            }
        } catch (Throwable exception) {
            System.out.println(exception);
            throw new RuntimeException();
        }

        if (!roots.isEmpty()) {
            createResultsFile1();
        } else {
            System.out.println("No roots found");
        }


        // Now we analyze the root methods
        /*HashSet<String> rootsFromFile;
        rootsFromFile = getRoots();
        if (!rootsFromFile.isEmpty()) {
            roots = rootsFromFile;
        }*/

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
            try {
                System.out.println("Root file contents:     " + className + "#" + methodName + ":" + testName);
                FileWriter rootFile = new FileWriter(mavenProject.getBasedir()
                        + String.valueOf(Constants.getRootMethodFilepath(testName)).substring(1));
                BufferedWriter bw1 = new BufferedWriter(rootFile);
                bw1.write(className + "#" + methodName + ":" + testName);
                bw1.newLine();
                bw1.flush();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            String beginningLineName;
            try {
                SurefireExecution rootMethodAnalysisExecution =
                        SurefireExecution.SurefireFactory.getRMAExec(this.surefire, this.originalArgLine,
                                this.mavenProject, this.mavenSession, this.pluginManager,
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

    /*private HashSet<String> getRoots() {
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
    }*/

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

            for (String location : roots) {
                bw.write(testName + "," + location);
                bw.newLine();

                this.results.add(new Output(testName, location, false, this.delay));
            }
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private int generateLocsList(List<String> locsList, String path) {
        int delay = 0;
        try {
            File file = new File(this.mavenProject.getBasedir() + path.substring(1));
            BufferedReader reader = new BufferedReader(new FileReader(file));

            // Output file
            FileWriter outputLocationsFile = new FileWriter(this.mavenProject.getBasedir()
                + String.valueOf(Constants.getWorkingLocationsFilepath(testName)).substring(1));
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            String line = reader.readLine();
            delay = Integer.parseInt(line);
            line = reader.readLine();
            while (line != null) {
                locsList.add(line);
                bw.write(line);
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
            File file = new File(this.mavenProject.getBasedir()
                    + String.valueOf(Constants.getStackTraceFilepath(testName)).substring(1));
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
            File file = new File(this.mavenProject.getBasedir()
                    + String.valueOf(Constants.getStackTraceFilepath(testName)).substring(1));
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
            String pathName = String.valueOf(Constants.getMethodStartEndLineFile(
                    String.valueOf(this.mavenProject.getBasedir()), testName));
            File lines = new File(pathName);
            BufferedReader reader = new BufferedReader(new FileReader(lines));
            String line = reader.readLine();
            while (line != null) {
                // Parse method name from line
                String[] tmp = line.split("#");
                String methodName = tmp[3];
                String className = tmp[0];
                String lowerLineNumber = tmp[1].substring(0, tmp[1].indexOf('-'));

                pathName = String.valueOf(Constants.getIndRootFilepath(String.valueOf(this.mavenProject.getBasedir()),
                        testName, Integer.parseInt(lowerLineNumber)));
                File rootFile = new File(pathName);
                BufferedWriter writer = new BufferedWriter(new FileWriter(rootFile));
                writer.write(className + "#" + lowerLineNumber);
                writer.newLine();
                writer.flush();

                SurefireExecution execution = SurefireExecution.SurefireFactory.getDelayMethodExec(this.surefire,
                        this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                        Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                        this.localRepository, this.testName, this.delay,
                        String.valueOf(Constants.getIndRootFilepath(".",  testName, Integer.parseInt(lowerLineNumber))),
                        methodName);

                beginningFail = executeSurefireExecution(null, execution);

                if (beginningFail) {
                    beginningLineName = "";
                    pathName = String.valueOf(Constants.getIndRootFilepath(String.valueOf(this.mavenProject.getBasedir()),
                            testName, Integer.parseInt(lowerLineNumber)));
                    File result = new File(pathName);
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
        String upperBoundary = "";
        ArrayList<String> currentLines = new ArrayList<String>();
        if (start != null) {
            currentLines.add(start);
        }
        try {
            String path = Constants.getMethodStartEndLineFile(String.valueOf(this.mavenProject.getBasedir()), testName);
            File lines = new File(path);
            BufferedReader reader = new BufferedReader(new FileReader(lines));
            String line = reader.readLine();
            int cluster = 1;
            while (line != null) {
                String[] tmp = line.split("#");
                String className = tmp[0];
                String lowerLineNumber = tmp[1].substring(0, tmp[1].indexOf('-'));
                String upperLineNumber = tmp[2];

                for (int i = Integer.parseInt(lowerLineNumber); i < Integer.parseInt(upperLineNumber); i++) {
                    String path2 = String.valueOf(Constants.getIndRootFilepath(
                            String.valueOf(this.mavenProject.getBasedir()), testName, i));
                    File rootFile = new File(path2);
                    rootFile.createNewFile();
                    BufferedWriter writer = new BufferedWriter(new FileWriter(rootFile));
                    writer.write(className + "#" + i);
                    writer.newLine();
                    writer.flush();

                    SurefireExecution execution = SurefireExecution.SurefireFactory.getDelayLocExec(this.surefire,
                            this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, this.delay,
                            String.valueOf(Constants.getIndRootFilepath(".", testName, i)));

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
            SurefireExecution execution, String itemLoc, int threadID)
            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            String className = itemLoc.split("#")[0];
            String lineNumber = itemLoc.split("#")[1];
            String rootLine = searchStackTrace(className, ":" + lineNumber);
            rootLine += "[" + execution.delay + "]";
            System.out.println(threadID + "," + className + ".*:" + lineNumber);
            System.out.println("Formatted rootLine: " + rootLine);
            roots.add(rootLine);
            return 1;
        }
        return 3;
    }

    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                             SurefireExecution execution)
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
