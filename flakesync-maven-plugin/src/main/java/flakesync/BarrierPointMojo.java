package flakesync;

import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "barrierpointsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class BarrierPointMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running BarrierPointSearchMojo"));

        //Setup
        try {
            FileWriter barrierResults = new FileWriter(this.mavenProject.getBasedir()
                + "/.flakesync/Results-Barrier/Results.csv");
            BufferedWriter bw = new BufferedWriter(barrierResults);

            bw.write("Test-Name,Boundary-Point,Barrier-Point,Threshold,Time");
            bw.newLine();

            FileReader boundaryResults = new FileReader(".flakesync/Results-Boundary/" + testName + "Result.csv");
            BufferedReader br = new BufferedReader(boundaryResults);

            String line = br.readLine();
            while (line != null) {
                //Debugging
                System.out.println(line);

                String firstLoc = line.split("-")[0];
                String endLoc = line.split("-")[1].split("\\[")[0];
                int delay = Integer.parseInt(line.split("\\[")[1].substring(0, line.split("\\[")[1].length() - 1));

                if (firstLoc.contains("Test")) {
                    System.out.println("It appears that the boundary exists in the test code");
                    // bash $currentDir/downward_mvn_run.sh $module $testName ${upper_boundary} $currentDir $line $delay 1
                    // mvn test -pl $module -Dtest="$testName" -DsearchMethodEndLine="search" -DCodeToIntroduceVariable="$starting_boundary"

                    CleanSurefireExecution downwardMvnExecution = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                        this.mavenProject, this.mavenSession, this.pluginManager,
                        Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                        this.localRepository, this.testName, delay, firstLoc, 1);

                    executeSurefireExecution(null, downwardMvnExecution);

                    //Result file is SearchedMethodEndLine.txt --> parse for starting boundary and end line
                    File endLineFile = new File(this.mavenProject.getBasedir() + "/.flakesync/SearchedMethodEndLine.txt");
                    BufferedReader reader = new BufferedReader(new FileReader(endLineFile));

                    //Between these lines get <class>#<line> and pass to timeout 3m mvn test -pl $module -Dtest="$testName" -Ddelay=$delay -DCodeToIntroduceVariable=$starting_boundary -DYieldingPoint="$each_line_of_yield_item" -Dthreshold="$threshold"
                    String yieldingPoint = ""; //<class>#<line>
                    downwardMvnExecution = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                        this.mavenProject, this.mavenSession, this.pluginManager,
                        Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                        this.localRepository, this.testName, delay, firstLoc, yieldingPoint, 1);

                    executeSurefireExecution(null, downwardMvnExecution);
                    //Result file is FlagDelayANDUpdateANDYielding.txt --> make sure delay, update, and yield happen --> delay_update_yield flag = 1
                    File barrierResultFile = new File(this.mavenProject.getBasedir() + "/.flakesync/FlagDelayANDUpdateANDYielding.txt");
                    reader = new BufferedReader(new FileReader(barrierResultFile));

                    // If test passed add to results file
                } else {
                    // bash "$currentDir/collect_stacktrace.sh" $module $testName $delay $currentDir $upper_boundary &> "$logs_dir/stacktrace_log_${testName}"
                    // #Adding delay within boundary to observe failure and collect stacktrace.

                    // mvn test -pl $module -Dtest="$testName" -DstackTraceCollect="flag" -Ddelay=$delay -DCodeToIntroduceVariable=$upper_boundary
                    CleanSurefireExecution stackTraceExec = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                            this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, firstLoc, 2);
                    //need to programmatically parse stack trace
                    File directory = new File( this.mavenProject.getBasedir() + "/target/surefire-reports/");
                    File stackTraceFile = null;
                    for (File f: directory.listFiles()) {
                        if (f.getName().equals("TEST-*.xml")) {
                            stackTraceFile = f;
                            break;
                        }
                    }
                    if (stackTraceFile == null) {
                        System.out.println("It appears that the stacktrace does not exist");
                        return;
                    }
                    BufferedReader reader = new BufferedReader(new FileReader(stackTraceFile));
                    // Parse the stack trace
                    HashMap<String, String> classes = new HashMap<String, String>();
                    String trace = reader.readLine();
                    while (trace != null) {
                        if (trace.contains(".java:")) {
                            if (!inBlackList("")) {
                                classes.put("", "");
                            }
                        }
                    }

                    // bash $currentDir/run_mvn_test_with_yield_and_cut.sh
                    // Iterate over yielding points --> mvn test -pl $module -Dtest="$testName" -DsearchForMethodName="search" -DCodeToIntroduceVariable="$upper_boundary" -DYieldingPoint="$yield_item"
                    HashSet<String> visited = new HashSet<String>();
                    for (String classN: classes.keySet()) {
                        String yieldPoint = classN + "#" + classes.get(classN);
                        if (!visited.contains(classN + "#" + classes.get(classN))) {
                            CleanSurefireExecution barrierP = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                this.mavenProject, this.mavenSession, this.pluginManager,
                                Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                this.localRepository, this.testName, delay, endLoc, yieldPoint, true);
                            executeSurefireExecution(null, barrierP);
                        }
                    }
                    // Result method is SearchedMethodANDLine.txt
                    // Result file is SearchedMethodEndLine.txt --> parse for starting boundary and end line
                    File startLineFile = new File(this.mavenProject.getBasedir() + "/.flakesync/SearchedMethodANDLine.txt");
                    reader = new BufferedReader(new FileReader(startLineFile));

                    // Between these lines get <class>#<line> and pass to timeout 3m mvn test -pl $module -Dtest="$testName" -Ddelay=$delay -DCodeToIntroduceVariable=$starting_boundary -DYieldingPoint="$each_line_of_yield_item" -Dthreshold="$threshold"
                    int beginning = 0; //parse from file
                    for (int ln = Integer.parseInt(endLoc); ln >= beginning; ln--) {
                        String yieldingPoint = ""; //<class>#<ln>
                        CleanSurefireExecution barrierP = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                            this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(),
                                ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, endLoc, yieldingPoint, 1);

                        boolean pass = executeSurefireExecution(null, barrierP);

                        File barrierResultFile = new File(this.mavenProject.getBasedir()
                            + "/.flakesync/FlagDelayANDUpdateANDYielding.txt");
                        reader = new BufferedReader(new FileReader(barrierResultFile));

                        // Result file is FlagDelayANDUpdateANDYielding.txt --> make sure delay, update, and yield happen --> delay_update_yield flag = 1
                        if (pass) {
                            // If test passed, barrier point worked, and add to results file
                        } else {
                            //If method fails --> mvn test $JMVNOPTIONS  -pl $module -Dtest="$testName" -DexecutionMonitor="flag" -Ddelay=$delay -DCodeToIntroduceVariable=$upper_boundary -DYieldingPoint=""
                            CleanSurefireExecution execMon = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                this.mavenProject, this.mavenSession, this.pluginManager,
                                Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                this.localRepository, this.testName, delay, Integer.parseInt(endLoc));
                            executeSurefireExecution(null, execMon);

                            File execsFile = new File(this.mavenProject.getBasedir() + "/.flakesync/ExecutionMonitor.txt");
                            reader = new BufferedReader(new FileReader(execsFile));
                            int numExecutions = 0; //parse from file --> new threshold

                            barrierP = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                this.mavenProject, this.mavenSession, this.pluginManager,
                                Paths.get(this.baseDir.getAbsolutePath(),
                                    ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                this.localRepository, this.testName, delay, endLoc, yieldingPoint, numExecutions);

                            pass = executeSurefireExecution(null, barrierP);

                            if (pass) {
                                // If test passed, barrier point worked, and add to results file
                            } else {
                                // This barrier point does not work
                            }
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

    }

    private boolean inBlackList(String className) throws IOException {
        File blacklist = new File("/../barrierSearch-core/src/main/resources/blacklist.txt");
        BufferedReader reader = new BufferedReader(new FileReader(blacklist));

        String line = reader.readLine();

        while (line != null) {
            if (line.contains(className)) {
                return true;
            }
        }
        return false;
    }


    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
            CleanSurefireExecution execution) throws MojoExecutionException {
        try {
            execution.run();
        } catch (MojoExecutionException mee) {
            return true;
        }
        return false;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}
