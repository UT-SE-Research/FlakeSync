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

@Mojo(name = "critsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CritSearchMojo extends FlakeSyncAbstractMojo {

    private HashSet<String> stackTraceLines = new HashSet<String>();
    private HashSet<String> roots = new HashSet<String>();
    private int delay = 100;
    private boolean beginningFail = false;
    private HashMap<Integer, ArrayList<String>> clusters = new HashMap<Integer, ArrayList<String>>();


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running CriticalPointMojo"));
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

            //Parse the stacktrace
            stackTraceLines = new HashSet<String>();
            parseStackTrace();

            //Iterate over stacktrace locations
            HashSet<String> visited = new HashSet<String>();
            for (String line : stackTraceLines) {
                String[] locs = line.split(",");
                int threadId = Integer.parseInt(locs[locs.length - 1]);
                for (int i = 0; i < locs.length - 1; i++) {
                    String itemLocation = locs[i];
                    if (!visited.contains(itemLocation.split("#")[0])) {
                        visited.add(itemLocation.split("#")[0]);
                        int workingDelay = delay;

                        String className = itemLocation.split("#")[0];
                        if (className.contains("$")) {
                            itemLocation = itemLocation.split("$")[0];
                        }

                        File file = new File(String.valueOf(Constants.getIndLocFilepath(
                                this.mavenProject.getBasedir().toString(), this.testName, threadId, i)));
                        try {
                            file.createNewFile();
                            FileWriter fw = new FileWriter(file);
                            BufferedWriter bw = new BufferedWriter(fw);

                            bw.write(itemLocation);
                            bw.newLine();
                            bw.flush();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }

                        int result = runWithDelayAtLoc(itemLocation, workingDelay, threadId, i);

                        if (result == 0) {
                            System.out.println("Linkage Error!");
                            break;
                        } else if (result == 3) { // We need to test longer delays
                            int maxDelay = 25600;
                            workingDelay *= 2;
                            while (workingDelay <= maxDelay) {
                                result = runWithDelayAtLoc(itemLocation, workingDelay, threadId, i);

                                if (result < 3) { //We got a failure, we can break
                                    break;
                                }
                                workingDelay *= 2;
                            }
                        }
                    }
                }
            }

            //Create a results file with all identified root methods
            writeRootMethodsToFile();

            //Create the FileWriter for writing the critical points to the results file
            FileWriter resultsFile = null;
            BufferedWriter bw;
            try {
                resultsFile = new FileWriter(new File(String.valueOf(Constants.getCritPointsResultsFilepath(
                        String.valueOf(this.mavenProject.getBasedir()), testName
                ))));
                bw = new BufferedWriter(resultsFile);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }

            //Iterate over each root method to identify specific critical points
            visited = new HashSet<String>();
            for (String root : roots) {
                String[] tmpArr = root.split("/");
                String methodName = tmpArr[tmpArr.length - 2];
                methodName = methodName.split("\\(")[0];
                String className = root.substring(0, root.lastIndexOf("/"));
                className = className.substring(0, className.lastIndexOf("/"));
                if (!visited.contains(methodName)) {
                    visited.add(methodName);
                    try {
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
                    SurefireExecution rootMethodAnalysisExecution =
                            SurefireExecution.SurefireFactory.getRMAExec(this.surefire, this.originalArgLine,
                                    this.mavenProject, this.mavenSession, this.pluginManager,
                                    Paths.get(this.baseDir.getAbsolutePath(),
                                            ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, delay, methodName);

                    executeSurefireExecution(null, rootMethodAnalysisExecution);

                    beginningLineName = delayInjection();

                    if (beginningLineName != null) { // Injecting the delay at this line did in fact work
                        sequentialDebug(beginningLineName);
                    } else {
                        sequentialDebug(null);
                    }
                    writeClustersToFile(bw);
                }
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
            throw new RuntimeException();
        }
    }

    private int runWithDelayAtLoc(String loc, int workingDelay, int threadId, int idx) throws Throwable {
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
                        currentLines.add(className + "#" + i);
                        break;
                    } else {
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
            if (!currentLines.isEmpty()) {
                clusters.put(cluster, currentLines);
            }
            return upperBoundary;
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return upperBoundary;
    }

    private int executeSurefireExecution(SurefireExecution execution, String itemLoc, int threadID)
            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException ex) {
            String className = itemLoc.split("#")[0];
            String lineNumber = itemLoc.split("#")[1];
            String rootLine = searchStackTrace(className, ":" + lineNumber);
            rootLine += "[" + execution.delay + "]";
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
