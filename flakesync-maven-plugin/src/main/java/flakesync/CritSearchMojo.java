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
import java.nio.Buffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mojo(name = "critsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CritSearchMojo extends FlakeSyncAbstractMojo {

    private Set<String> stackTraceLines = new HashSet<String>();
    private Set<String> roots = new HashSet<String>();
    private int delay = 100;
    private boolean beginningFail = false;
    private Map<Integer, ArrayList<String>> clusters = new HashMap<Integer, ArrayList<String>>();


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running CritSearchMojo"));
        MojoExecutionException allExceptions = null;

        List<String> locations = new ArrayList<String>();
        String locationsPath = String.valueOf(Constants.getMinLocationsFilepath(testName));
        generateLocsList(locations, locationsPath);


        locationsPath = String.valueOf(Constants.getMinLocationsFilepath(testName));
        try {
            SurefireExecution cleanExec = SurefireExecution.SurefireFactory.getDelayLocExec(this.surefire,
                    this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, this.delay, locationsPath);
            if (!executeSurefireExecution(null, cleanExec)) {
                System.out.println("This minimized location is not a good one. Go back and run minimizer.");
                return;
            }

            // Parse the stacktrace file to get all the locations to iterate through
            stackTraceLines = new HashSet<String>();
            parseStackTrace();

            // Iterate over stacktrace locations
            Set<String> visited = new HashSet<String>();    // Keep track of whether we have seen some exact trace
            for (String line : stackTraceLines) {
                // Check whether we have gone through this exact trace (without thread ID)
                if (visited.contains(line.substring(0, line.lastIndexOf(",")))) {
                    continue;
                }
                visited.add(line.substring(0, line.lastIndexOf(",")));

                String[] locs = line.split(",");                        // All elements are comma-delimited
                int threadId = Integer.parseInt(locs[locs.length - 1]); // Thread ID is the last element
                String rootLine = "";                                   // rootLine represents the final failing loc
                for (int i = 0; i < locs.length - 1; i++) {
                    String itemLocation = locs[i];

                    int workingDelay = delay;
                    String className = itemLocation.split("#")[0];
                    /*if (className.contains("$")) {
                        className = className.split("\\$")[0];
                    }*/
                    String lineNumber = itemLocation.split("#")[1];
                    String newLoc = className + "#" + lineNumber;

                    File file = new File(String.valueOf(Constants.getIndLocFilepath(
                                    this.mavenProject.getBasedir().toString(), this.testName, threadId, i)));
                    try {
                        file.createNewFile();
                        FileWriter fw = new FileWriter(file);
                        BufferedWriter bw = new BufferedWriter(fw);

                        bw.write(newLoc);
                        bw.newLine();
                        bw.flush();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }

                    boolean result = runWithDelayAtLoc(newLoc, workingDelay, threadId, i);

                    if (!result) { // Test passes, we need to try longer delays
                        int maxDelay = 25600;
                        workingDelay *= 2;
                        while (workingDelay <= maxDelay) {
                            result = runWithDelayAtLoc(newLoc, workingDelay, threadId, i);

                            if (result) { // We got a failure, we can stop
                                break;
                            }
                            workingDelay *= 2;
                        }
                        // If tried to max delay and still does not fail, then we need to stop
                        // The previous location is a root
                        if (!result) {
                            break;
                        }
                    }

                    rootLine = searchStackTrace(className, ":" + lineNumber);
                    rootLine += "[" + workingDelay + "]";
                }
                // If root was identified, then can add to the list of roots
                if (!rootLine.isEmpty()) {
                    roots.add(rootLine);
                }
            }

            // Create the file with all identified roots
            writeRootMethodsToFile();

            // Create the FileWriter for writing the critical points to the results file
            FileWriter resultsFile = null;
            BufferedWriter bw;
            try {
                resultsFile = new FileWriter(new File(String.valueOf(Constants.getCritPointsResultsFilepath(
                        String.valueOf(this.mavenProject.getBasedir()), testName))));
                bw = new BufferedWriter(resultsFile);
            } catch (IOException exception) {
                exception.printStackTrace();
                throw new RuntimeException(exception);
            }

            // Iterate over each root method to identify specific critical points
            visited = new HashSet<String>();    // Do not check duplicate methods
            for (String root : roots) {
                String[] rootNameElements = root.split("/");
                // Method name is the part right next to ( near end of root name
                String methodName = rootNameElements[rootNameElements.length - 2];
                methodName = methodName.split("\\(")[0];
                String className = root.substring(0, root.lastIndexOf("/"));
                className = className.substring(0, className.lastIndexOf("/"));
                String fullMethodName = className + "#" + methodName;
                int workingDelay = Integer.parseInt(root.split("\\[")[1].split("\\]")[0]);  // Delay is number in between []
                if (!visited.contains(fullMethodName)) {
                    visited.add(fullMethodName);
                    try {
                        FileWriter rootFile = new FileWriter(mavenProject.getBasedir()
                                + String.valueOf(Constants.getRootMethodFilepath(testName)).substring(1));
                        BufferedWriter bw1 = new BufferedWriter(rootFile);
                        bw1.write(fullMethodName + ":" + testName);
                        bw1.newLine();
                        bw1.flush();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                        throw new RuntimeException(ioe);
                    }

                    // Run to get the MethodStartAndEndLine file that shows the beginning and end line of the root
                    String beginningLineName;
                    SurefireExecution rootMethodAnalysisExecution =
                            SurefireExecution.SurefireFactory.getRMAExec(this.surefire, this.originalArgLine,
                                    this.mavenProject, this.mavenSession, this.pluginManager,
                                    Paths.get(this.baseDir.getAbsolutePath(),
                                            ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, workingDelay, methodName);
                    executeSurefireExecution(null, rootMethodAnalysisExecution);

                    // Try adding delays from beginning of method until it can fail
                    sequentialDebug(workingDelay);
                    writeClustersToFile(bw);
                }
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
            throw new RuntimeException();
        }
    }

    private boolean runWithDelayAtLoc(String loc, int workingDelay, int threadId, int idx) throws Throwable {
        SurefireExecution cleanExec = SurefireExecution.SurefireFactory.getDelayLocExec(this.surefire, this.originalArgLine,
                this.mavenProject, this.mavenSession, this.pluginManager,
                Paths.get(this.baseDir.getAbsolutePath(),
                        ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(), this.localRepository,
                this.testName, workingDelay,
                String.valueOf(Constants.getIndLocFilepath(".", this.testName, threadId, idx)));

        return executeSurefireExecution(cleanExec, loc, threadId);
    }

    private void writeRootMethodsToFile() {
        try {
            File outputRootMethodsFile = new File(
                    String.valueOf(Constants.getRootMethodResultsFilepath(String.valueOf(this.mavenProject.getBasedir()),
                            this.testName)));
            FileWriter outputFileWriter = new FileWriter(outputRootMethodsFile);

            BufferedWriter bw = new BufferedWriter(outputFileWriter);

            bw.write("#Test-Name,Location[delay]");
            bw.newLine();

            for (String location : roots) {
                bw.write(testName + "," + location);
                bw.newLine();
            }
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void writeClustersToFile(BufferedWriter bw) {
        try {
            if (!clusters.keySet().isEmpty()) {
                for (int i = 1; i <= clusters.size(); i++) {
                    if (clusters.get(i).size() > 1) {
                        bw.write(clusters.get(i).get(0).trim() + "-"
                                + clusters.get(i).get(clusters.get(i).size() - 1).trim() + "[" + delay + "]");
                        bw.newLine();
                    } else {
                        if (clusters.get(i).isEmpty()) {
                            return;
                        }
                        bw.write(clusters.get(i).get(0) + "-" + clusters.get(i).get(0)
                                + "[" + delay + "]");
                        bw.newLine();
                    }
                }
            }
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void generateLocsList(List<String> locsList, String path) {
        try {
            File file = new File(this.mavenProject.getBasedir() + path.substring(1));
            BufferedReader reader = new BufferedReader(new FileReader(file));

            // Output file
            FileWriter outputLocationsFile = new FileWriter(this.mavenProject.getBasedir()
                + String.valueOf(Constants.getWorkingLocationsFilepath(testName)).substring(1));
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);

            String line = reader.readLine();
            this.delay = Integer.parseInt(line);
            line = reader.readLine();
            while (line != null) {
                locsList.add(line);
                bw.write(line);
                bw.newLine();

                line = reader.readLine();
            }
            bw.flush();
            reader.close();
            bw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void parseStackTrace() {
        try {
            File file = new File(this.mavenProject.getBasedir()
                    + String.valueOf(Constants.getStackTraceFilepath(testName)).substring(1));
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder parsedInfo = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                String[] data = line.split(",");
                if ("END".equals(data[1])) {
                    parsedInfo.append(data[0]); // End of the trace is the thread ID
                    this.stackTraceLines.add(parsedInfo.toString());
                    parsedInfo.setLength(0);
                } else {
                    String tmp = data[1].substring(0, data[1].indexOf('('));    // Parse out the class+method part
                    String className = tmp.substring(0, tmp.lastIndexOf('/'));  // Parse out the class name
                    int lineNumber = Integer.parseInt(data[1].split(":")[1]     // Parse out the line number
                        .substring(0, data[1].split(":")[1].length() - 1));
                    // Format of element is className#lineNumber
                    parsedInfo.append(className);
                    parsedInfo.append("#");
                    parsedInfo.append(lineNumber);
                    parsedInfo.append(",");
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
            String line = reader.readLine();
            while (line != null) {
                if (line.contains(searchKey1) && line.contains(searchKey2)) {
                    line = line.split(",")[1];
                    return line.substring(0, line.indexOf(':'));
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

    // Search through method one location at a time, injecting the specified delay amount
    private void sequentialDebug(int workingDelay) throws Throwable {
        String upperBoundary = "";
        ArrayList<String> currentLines = new ArrayList<String>();
        try {
            String path = Constants.getMethodStartEndLineFile(String.valueOf(this.mavenProject.getBasedir()), testName);
            File lines = new File(path);
            BufferedReader reader = new BufferedReader(new FileReader(lines));
            String line = reader.readLine();
            int cluster = 1;    // Want to keep track of different "clusters" or regions of failing locations
            while (line != null) {
                String[] tmp = line.split("#");
                String className = tmp[0];
                int lowerLineNumber = Integer.parseInt(tmp[1].substring(0, tmp[1].indexOf('-')));
                int upperLineNumber = Integer.parseInt(tmp[2]);

                // Try to delay throughout the method, from first line to last line
                for (int i = lowerLineNumber; i < upperLineNumber; i++) {
                    // Configure where to write the delay
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
                            this.localRepository, this.testName, workingDelay,
                            String.valueOf(Constants.getIndRootFilepath(".", testName, i)));

                    // Check if delaying at current location makes the test fail (means it is in region)
                    boolean failed = executeSurefireExecution(null, execution);
                    if (failed) {
                        currentLines.add(className + "#" + i);
                    } else {
                        // Delaying at this line stops the failures, we are at the end of the region
                        if (!currentLines.isEmpty()) {
                            clusters.put(cluster, currentLines);
                            cluster++;
                        }
                        currentLines = new ArrayList<String>();
                    }
                }
                line = reader.readLine();
            }
            reader.close();

            // Record each region or "cluster" of failing delay locations
            if (!currentLines.isEmpty()) {
                clusters.put(cluster, currentLines);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // Helper method to run test with delay at specified location
    // Return true if it fails, false if otherwise
    private boolean executeSurefireExecution(SurefireExecution execution, String itemLoc, int threadID)
            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            return true;
        }
        return false;
    }

    // Helper method to run test
    // Return true if it fails, false if otherwise
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
