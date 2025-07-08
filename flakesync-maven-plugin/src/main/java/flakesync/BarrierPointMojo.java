package flakesync;

import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;


@Mojo(name = "barrierpointsearch", defaultPhase = LifecyclePhase.TEST,
        requiresDependencyResolution = ResolutionScope.TEST)
public class BarrierPointMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running BarrierPointSearchMojo"));

        //Setup
        try {
            File barrierResFileDir = new File(this.mavenProject.getBasedir()
                    + "/.flakesync/Results-Barrier/");
            barrierResFileDir.mkdirs();
            File barrierResFile = new File(barrierResFileDir, "Results.csv");
            FileWriter barrierResults = new FileWriter(barrierResFile);
            BufferedWriter bw = new BufferedWriter(barrierResults);

            bw.write("Test-Name,Boundary-Point,Barrier-Point,Threshold");
            bw.newLine();

            FileReader boundaryResults = new FileReader(this.mavenProject.getBasedir()
                    + "/.flakesync/Results-Boundary/Boundary-" + testName + "-Result.csv");
            BufferedReader br = new BufferedReader(boundaryResults);

            String line = br.readLine();
            while (line != null) {
                System.out.println(line);

                String firstLoc = line.split("-")[0];
                String endLoc = line.split("-")[1].split("\\[")[0];
                int delay = Integer.parseInt(line.split("\\[")[1].substring(0,
                        line.split("\\[")[1].length() - 1));

                boolean elseb = false;
                if (firstLoc.contains("Test") && elseb) {
                    System.out.println("It appears that the boundary exists in the test code");

                    SurefireExecution downwardMvnExecution = SurefireExecution.SurefireFactory.createDwnwrdMvnExec(
                            this.surefire, this.originalArgLine, this.mavenProject, this.mavenSession,
                            this.pluginManager, Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay,
                            firstLoc.replace("/", "."));

                    executeSurefireExecution(null, downwardMvnExecution);

                    File endLineFile = new File(this.mavenProject.getBasedir()
                            + "/.flakesync/SearchedMethodEndLine.txt");
                    BufferedReader reader = new BufferedReader(new FileReader(endLineFile));

                    //Fix this!
                    String yieldingPoint = "";
                    downwardMvnExecution = SurefireExecution.SurefireFactory.createBasicBarrierPtExec(this.surefire,
                            this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, firstLoc, yieldingPoint);

                    boolean fail = executeSurefireExecution(null, downwardMvnExecution);

                    if (!fail && checkValidPass()) {
                        addBarrierPointToResults(bw, firstLoc, yieldingPoint, delay);
                    }
                    // If test passed add to results file*/
                } else {
                    System.out.println("GETTING THE STACKTRACE======================================================");
                    SurefireExecution stackTraceExec = SurefireExecution.SurefireFactory.createBarrSearchStacktraceExec(
                            this.surefire, this.originalArgLine, this.mavenProject, this.mavenSession,
                            this.pluginManager, Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, firstLoc.replace("/", "."));

                    executeSurefireExecution(null, stackTraceExec);

                    //need to programmatically parse stack trace
                    System.out.println(this.mavenProject.getBasedir() + "/.flakesync/" + stackTraceExec.getExecID());
                    File directory = new File(this.mavenProject.getBasedir() + "/.flakesync/"
                            + stackTraceExec.getExecID());
                    File stackTraceFile = null;
                    for (File f : directory.listFiles()) {
                        if (f.getName().contains("TEST-")) {
                            stackTraceFile = f;
                            break;
                        }
                    }
                    if (stackTraceFile == null) {
                        System.out.println("It appears that the stacktrace does not exist");
                        return;
                    }
                    BufferedReader reader = new BufferedReader(new FileReader(stackTraceFile));
                    //parse the stack trace
                    HashMap<String, String> classes = new HashMap<String, String>();
                    String trace = reader.readLine();
                    while (trace != null) {
                        if (trace.contains(".java:")) {
                            //System.out.println(trace);
                            String className = trace.split("at ")[1].split("\\(")[0];
                            className = className.substring(0, className.lastIndexOf("."));
                            String lineNum = trace.split("\\(")[1].split(":")[1].split("\\)")[0];
                            //System.out.println(className + " " + lineNum);
                            if (!inBlackList(className)) {
                                System.out.println(trace);
                                classes.put(className, lineNum);
                            }
                        }
                        trace = reader.readLine();
                    }

                    System.out.println("FINISHED PARSING THE STACKTRACE============================");
                    System.out.println(classes);


                    HashSet<String> visited = new HashSet<String>();
                    for (String classN : classes.keySet()) {
                        String yieldPoint = classN + "#" + classes.get(classN);
                        endLoc = endLoc.replace("/", ".");
                        System.out.println(endLoc + "\n" + yieldPoint);
                        if (!visited.contains(classN + "#" + classes.get(classN))) {
                            visited.add(classN + "#" + classes.get(classN));
                            SurefireExecution barrierPoint = SurefireExecution.SurefireFactory.createPrepBarrierPtExec(
                                    this.surefire, this.originalArgLine, this.mavenProject, this.mavenSession,
                                    this.pluginManager, Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, delay, endLoc, yieldPoint);

                            executeSurefireExecution(null, barrierPoint);

                            //Result file is SearchedMethodEndLine.txt --> parse for starting boundary and end line
                            File startLineFile = new File(this.mavenProject.getBasedir()
                                    + "/.flakesync/SearchedMethodANDLine.txt");
                            reader = new BufferedReader(new FileReader(startLineFile));
                            String beginLine = reader.readLine();

                            int beginning = Integer.parseInt(beginLine.split("#")[1]);//parse from file
                            System.out.println("Going through lines: " + beginning + " to "
                                    + Integer.parseInt(endLoc.split("#")[1]));
                            for (int ln = Integer.parseInt(yieldPoint.split("#")[1]); ln >= beginning; ln--) {
                                String yieldingPoint = classN + "#" + ln;

                                barrierPoint = SurefireExecution.SurefireFactory.createBasicBarrierPtExec(this.surefire,
                                        this.originalArgLine, this.mavenProject, this.mavenSession, this.pluginManager,
                                        Paths.get(this.baseDir.getAbsolutePath(),
                                                ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                        this.localRepository, this.testName, delay, endLoc, yieldingPoint);

                                boolean fail = executeSurefireExecution(null, barrierPoint);

                                if (!fail && checkValidPass()) {
                                    addBarrierPointToResults(bw, line, yieldingPoint, 1);
                                    break;
                                } else {
                                    System.out.println("FAIL: " + yieldingPoint + " " + endLoc);
                                    SurefireExecution execMon = SurefireExecution.SurefireFactory.createExecMonitorExec(
                                            this.surefire, this.originalArgLine, this.mavenProject, this.mavenSession,
                                            this.pluginManager, Paths.get(this.baseDir.getAbsolutePath(),
                                            ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                            this.localRepository, this.testName, delay, endLoc);
                                    executeSurefireExecution(null, execMon);

                                    File execsFile = new File(this.mavenProject.getBasedir()
                                            + "/.flakesync/ExecutionMonitor.txt");
                                    reader = new BufferedReader(new FileReader(execsFile));
                                    int numExecutions = Integer.parseInt(reader.readLine().split("=")[1]);
                                    if (numExecutions > 1) {
                                        System.out.println("WE HAVE A NEW THRESHOLD: " + numExecutions);
                                        barrierPoint = SurefireExecution.SurefireFactory.createBarrierPtExec(this.surefire,
                                                this.originalArgLine, this.mavenProject, this.mavenSession,
                                                this.pluginManager, Paths.get(this.baseDir.getAbsolutePath(),
                                                        ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                                this.localRepository, this.testName, delay, endLoc, yieldingPoint,
                                                numExecutions);

                                        fail = executeSurefireExecution(null, barrierPoint);

                                        if (!fail && checkValidPass()) {
                                            // If test passed, barrier point worked, and add to results file
                                            addBarrierPointToResults(bw, line, yieldingPoint, numExecutions);
                                            break;
                                        } else {
                                            //This barrier point does not work
                                            checkValidPass();
                                            System.out.println("FAIL: " + yieldingPoint + " " + numExecutions);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                line = br.readLine();
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        } catch (Throwable exception) {
            System.out.println(exception);
            throw new RuntimeException();
        }

    }

    private boolean inBlackList(String className) throws IOException {
        InputStream blacklist = this.getClass().getResourceAsStream("/blacklist.txt");
        Scanner scanner = new Scanner(blacklist);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (className.contains(line)) {
                return true;
            } else if (line.contains("*") && className.contains(line.substring(0, line.indexOf("*")))) {
                return true;
            }
        }
        return false;
    }

    private void addBarrierPointToResults(BufferedWriter bw, String bop, String bap, int threshold)
            throws IOException {
        bw.write(this.testName + "," + bop + "," + bap + "," + threshold);
        bw.newLine();
        bw.flush();
    }

    private boolean checkValidPass() throws IOException {
        File barrierResultFile = new File(this.mavenProject.getBasedir()
                + "/.flakesync/FlagDelayANDUpdateANDYielding.txt");
        BufferedReader reader = new BufferedReader(new FileReader(barrierResultFile));

        String flagLine = reader.readLine();
        boolean hitscriteria = true;
        while (flagLine != null) {
            System.out.println(Boolean.parseBoolean(flagLine.split("=")[1]));
            hitscriteria &= Boolean.parseBoolean(flagLine.split("=")[1]);
            flagLine = reader.readLine();
        }
        if (hitscriteria) {
            return true;
        }
        return false;
    }


    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                             SurefireExecution execution)
            throws Throwable {
        try {
            execution.run();
        } catch (MojoExecutionException exception) {
            return true;
        }
        return false;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}