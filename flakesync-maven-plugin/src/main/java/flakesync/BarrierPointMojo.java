package flakesync;


import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.Level;
import flakesync.common.Logger;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "barrierpointsearch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class BarrierPointMojo extends FlakeSyncAbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running BarrierPointSearchMojo"));

        //Setup
        try {
            File barrierResFileDir = new File(this.mavenProject.getBasedir() + "/.flakesync/Results-Barrier/");
            barrierResFileDir.mkdirs();
            File barrierResFile = new File(barrierResFileDir, "Results.csv");
            FileWriter barrierResults = new FileWriter(barrierResFile);
            BufferedWriter bw = new BufferedWriter(barrierResults);

            bw.write("Test-Name,Boundary-Point,Barrier-Point,Threshold");
            bw.newLine();

            FileReader boundaryResults = new FileReader(this.mavenProject.getBasedir() + "/.flakesync/Results-Boundary/Boundary-" + testName + "-Result.csv");
            BufferedReader br = new BufferedReader(boundaryResults);

            String line = br.readLine();
            //while (line != null) {
                //Debugging
                System.out.println(line);

                String firstLoc = line.split("-")[0];
                String endLoc = line.split("-")[1].split("\\[")[0];
                int delay = Integer.parseInt(line.split("\\[")[1].substring(0, line.split("\\[")[1].length()-1));

                if(firstLoc.contains("Test")) {
                    System.out.println("It appears that the boundary exists in the test code");
                    //  bash $currentDir/downward_mvn_run.sh $module $testName ${upper_boundary} $currentDir $line $delay 1
                    //  mvn test -pl $module -Dtest="$testName" -DsearchMethodEndLine="search" -DCodeToIntroduceVariable="$starting_boundary"

                    CleanSurefireExecution downwardMvnExecution = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                            this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, firstLoc.replace("/", "."), false);

                    executeSurefireExecution(null, downwardMvnExecution);

                    //Result file is SearchedMethodEndLine.txt --> parse for starting boundary and end line
                    File endLineFile = new File(this.mavenProject.getBasedir()+"/.flakesync/SearchedMethodEndLine.txt");
                    BufferedReader reader = new BufferedReader(new FileReader(endLineFile));

                    //Between these lines get <class>#<line> and pass to timeout 3m mvn test -pl $module -Dtest="$testName" -Ddelay=$delay -DCodeToIntroduceVariable=$starting_boundary -DYieldingPoint="$each_line_of_yield_item" -Dthreshold="$threshold"
                    String yieldingPoint = ""; //<class>#<line>
                    downwardMvnExecution = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                            this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, firstLoc, yieldingPoint, 1);

                    executeSurefireExecution(null, downwardMvnExecution);
                    //Result file is FlagDelayANDUpdateANDYielding.txt --> make sure delay, update, and yield happen --> delay_update_yield flag = 1
                    File barrierResultFile = new File(this.mavenProject.getBasedir()+"/.flakesync/FlagDelayANDUpdateANDYielding.txt");
                    reader = new BufferedReader(new FileReader(barrierResultFile));

                    // If test passed add to results file
                } else {
                    // bash "$currentDir/collect_stacktrace.sh" $module $testName $delay $currentDir $upper_boundary &> "$logs_dir/stacktrace_log_${testName}"
                    // #Adding delay within boundary to observe failure and collect stacktrace.

                    System.out.println("GETTING THE STACKTRACE===========================================================");
                    //mvn test -pl $module -Dtest="$testName" -DstackTraceCollect="flag" -Ddelay=$delay -DCodeToIntroduceVariable=$upper_boundary
                    CleanSurefireExecution stackTraceExec = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                            this.mavenProject, this.mavenSession, this.pluginManager,
                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                            this.localRepository, this.testName, delay, firstLoc.replace("/", "."), 2);

                    executeSurefireExecution(null, stackTraceExec);

                    //need to programmatically parse stack trace
                    System.out.println(this.mavenProject.getBasedir() + "/.flakesync/" + stackTraceExec.getExecID());
                    File directory = new File( this.mavenProject.getBasedir() + "/.flakesync/" + stackTraceExec.getExecID());
                    File stackTraceFile = null;
                    for(File f: directory.listFiles()) {
                        if(f.getName().contains("TEST-")) {
                            stackTraceFile = f;
                            break;
                        }
                    }
                    if(stackTraceFile == null) {
                        System.out.println("It appears that the stacktrace does not exist");
                        return;
                    }
                    BufferedReader reader = new BufferedReader(new FileReader(stackTraceFile));
                    //parse the stack trace
                    HashMap<String, String> classes = new HashMap<String, String>();
                    String trace = reader.readLine();
                    while(trace != null) {
                        if(trace.contains(".java:")) {
                            System.out.println(trace);
                            String className = trace.split("at ")[1].split("\\(")[0];
                            className = className.substring(0, className.lastIndexOf("."));
                            String lineNum = trace.split("\\(")[1].split(":")[1].split("\\)")[0];
                            System.out.println(className + " " + lineNum);
                            if(!inBlackList(className)){
                                System.out.println(trace);
                                classes.put(className, lineNum);
                            }
                        }
                        trace = reader.readLine();
                    }

                    System.out.println("FINISHED PARSING THE STACKTRACE===========================================================");
                    System.out.println(classes);

                    // bash $currentDir/run_mvn_test_with_yield_and_cut.sh
                    // Iterate over yielding points --> mvn test -pl $module -Dtest="$testName" -DsearchForMethodName="search" -DCodeToIntroduceVariable="$upper_boundary" -DYieldingPoint="$yield_item"
                    HashSet<String> visited = new HashSet<String>();
                    for(String classN: classes.keySet()) {
                        String yieldPoint = classN+"#"+classes.get(classN);
                        endLoc = endLoc.replace("/", ".");
                        System.out.println(endLoc + "\n" + yieldPoint);
                        if(!visited.contains(classN+"#"+classes.get(classN))) {
                            visited.add(classN+"#"+classes.get(classN));
                            CleanSurefireExecution barrierPoint = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                    this.mavenProject, this.mavenSession, this.pluginManager,
                                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                    this.localRepository, this.testName, delay, endLoc, yieldPoint, true);
                            executeSurefireExecution(null, barrierPoint);

                            //Result file is SearchedMethodEndLine.txt --> parse for starting boundary and end line
                            File startLineFile = new File(this.mavenProject.getBasedir()+"/.flakesync/SearchedMethodANDLine.txt");
                            reader = new BufferedReader(new FileReader(startLineFile));
                            String beginLine = reader.readLine();
                            //Between these lines get <class>#<line> and pass to timeout 3m mvn test -pl $module -Dtest="$testName" -Ddelay=$delay -DCodeToIntroduceVariable=$starting_boundary -DYieldingPoint="$each_line_of_yield_item" -Dthreshold="$threshold"
                            int beginning = Integer.parseInt(beginLine.split("#")[1]);//parse from file
                            System.out.println("Going through lines: " + beginning + " to " + Integer.parseInt(endLoc.split("#")[1]));
                            for(int ln = Integer.parseInt(endLoc.split("#")[1]); ln >= beginning; ln--) {
                                String yieldingPoint = classN+"#"+ln; //<class>#<ln>
                                barrierPoint = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                        this.mavenProject, this.mavenSession, this.pluginManager,
                                        Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                        this.localRepository, this.testName, delay, endLoc, yieldingPoint, 1);

                                boolean fail = executeSurefireExecution(null, barrierPoint);

                                //Result file is FlagDelayANDUpdateANDYielding.txt --> make sure delay, update, and yield happen --> delay_update_yield flag = 1
                                if (!fail && checkValidPass()) {
                                    addBarrierPointToResults(bw, line, yieldingPoint, 1);
                                } else {
                                    //If method fails --> mvn test $JMVNOPTIONS  -pl $module -Dtest="$testName" -DexecutionMonitor="flag" -Ddelay=$delay -DCodeToIntroduceVariable=$upper_boundary -DYieldingPoint=""
                                    System.out.println("EPIC FAIL: " + yieldingPoint + " " + endLoc);
                                    CleanSurefireExecution execMon = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                            this.mavenProject, this.mavenSession, this.pluginManager,
                                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                            this.localRepository, this.testName, delay, endLoc.replace("/", "."), true);
                                    executeSurefireExecution(null, execMon);

                                    File execsFile = new File(this.mavenProject.getBasedir() + "/.flakesync/ExecutionMonitor.txt");
                                    reader = new BufferedReader(new FileReader(execsFile));
                                    int numExecutions = Integer.parseInt(reader.readLine().split("=")[1]); //parse from file --> new threshold

                                    barrierPoint = new CleanSurefireExecution(this.surefire, this.originalArgLine,
                                            this.mavenProject, this.mavenSession, this.pluginManager,
                                            Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                                            this.localRepository, this.testName, delay, endLoc.replace("/", "."),
                                            yieldingPoint, numExecutions);

                                    fail = executeSurefireExecution(null, barrierPoint);

                                    if (!fail && checkValidPass()) {
                                        // If test passed, barrier point worked, and add to results file
                                        addBarrierPointToResults(bw, line, yieldingPoint, numExecutions);
                                    } else {
                                        //This barrier point does not work
                                        System.out.println("EPIC FAIL: " + yieldingPoint + " " + numExecutions);
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
                //line = br.readLine();
            //}
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private boolean inBlackList(String className) throws IOException {
        InputStream blacklist = this.getClass().getResourceAsStream("/blacklist.txt");
        Scanner s = new Scanner(blacklist);
        while(s.hasNextLine()) {
            String line = s.nextLine();
            if(className.contains(line)) {
                return true;
            }else if(line.contains("*") && className.contains(line.substring(0, line.indexOf("*")))) {
                return true;
            }
        }
        return false;
    }

    private void addBarrierPointToResults(BufferedWriter bw, String bop, String bap, int threshold) throws IOException {
        bw.write(this.testName + "," + bop + "," + bap + "," + threshold);
        bw.newLine();
        bw.flush();
    }

    private boolean checkValidPass() throws IOException {
        File barrierResultFile = new File(this.mavenProject.getBasedir()+"/.flakesync/FlagDelayANDUpdateANDYielding.txt");
        BufferedReader reader = new BufferedReader(new FileReader(barrierResultFile));

        String flagLine = reader.readLine();
        boolean hitscriteria = true;
        while(flagLine != null) {
            System.out.println(Boolean.parseBoolean(flagLine.split("=")[1]));
            hitscriteria &= Boolean.parseBoolean(flagLine.split("=")[1]);
            flagLine = reader.readLine();
        }
        if(hitscriteria) {
            System.out.println("We've actually got one!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            return true;
        }
        return false;
    }


    private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                                            CleanSurefireExecution execution) throws MojoExecutionException {
        try {
            execution.run();
        } catch (MojoExecutionException e){
            return true;
        }
        return false;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
}