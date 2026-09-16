package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class BarrierPointSearchTask extends AbstractFlakeSyncTask {

    @TaskAction
    public void run() {
        File barrierResultsFile = resolve(Constants.getBarrierPointsResultsFilepath(".", getTestName()));
        File critPointsFile = resolve(Constants.getCritPointsResultsFilepath(".", getTestName()));

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(barrierResultsFile));
             BufferedReader br = new BufferedReader(new FileReader(critPointsFile))) {
            bw.write("#Test-Name,Boundary-Point,Barrier-Point,Threshold");
            bw.newLine();
            bw.flush();

            String line;
            while ((line = br.readLine()) != null) {
                String firstLoc = line.split("-")[0];
                String endLoc = line.split("-")[1].split("\\[")[0];
                int delay = Integer.parseInt(line.split("\\[")[1]
                        .substring(0, line.split("\\[")[1].length() - 1));

                if (firstLoc.contains("Test")) {
                    searchDownwardFromTest(bw, line, endLoc, delay, firstLoc);
                } else {
                    searchViaStackTrace(bw, line, endLoc, delay);
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Barrier point search failed", ioe);
        }
    }

    private void searchDownwardFromTest(BufferedWriter bw, String boundaryLine, String endLoc, int delay,
                                         String firstLoc) throws IOException {
        TestExecutionRequest downwardRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("DOWNWARD_MVN")
                .property("searchMethodEndLine", "search")
                .property("CodeToIntroduceVariable", firstLoc.replace("/", "."))
                .delay(delay)
                .build();
        newExecutor().run(downwardRequest);

        File endLineFile = resolve(Constants.getSearchMethodEndLineFilepath(".", getTestName()));
        String yieldPoint;
        try (BufferedReader reader = new BufferedReader(new FileReader(endLineFile))) {
            yieldPoint = reader.readLine().split("=")[1];
        }

        int fromLine = Integer.parseInt(endLoc.split("#")[1]);
        int toLine = Integer.parseInt(yieldPoint.split("#")[1]);
        for (int ln = fromLine; ln < toLine; ln++) {
            String yieldingPoint = yieldPoint.split("#")[0] + "#" + ln;

            if (tryYieldPoint(bw, boundaryLine, endLoc, delay, yieldingPoint)) {
                return;
            }
        }
    }

    private void searchViaStackTrace(BufferedWriter bw, String boundaryLine, String endLoc, int delay)
            throws IOException {
        File stackTraceFile = resolve(java.nio.file.Paths.get(".flakesync",
                getTestName().replace("#", ".") + "-JUnitFailureStackTrace.txt"));

        TestExecutionRequest stackTraceRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("BARRIER_ST")
                .property("CodeToIntroduceVariable", endLoc.replace("/", "."))
                .property("stackTraceCollect", "true")
                .property("stackTraceOutputFile", stackTraceFile.getAbsolutePath())
                .delay(delay)
                .build();
        newExecutor().run(stackTraceRequest);

        if (!stackTraceFile.exists()) {
            getLogger().warn("It appears that the stacktrace does not exist");
            return;
        }

        Map<String, String> classes = new HashMap<>();
        parseStackTrace(classes, stackTraceFile);

        List<String> reversedClasses = new ArrayList<>(classes.keySet());
        Collections.reverse(reversedClasses);

        Set<String> visited = new HashSet<>();
        String normalizedEndLoc = endLoc.replace("/", ".");
        for (String rawClassName : reversedClasses) {
            String className = rawClassName.contains(",")
                    ? rawClassName.substring(0, rawClassName.lastIndexOf(',')) : rawClassName;
            String yieldPoint = className + "#" + classes.get(className);
            if (!visited.add(yieldPoint)) {
                continue;
            }

            TestExecutionRequest barrierRequest = TestExecutionRequest.forTest(getTestName())
                    .agentMode("ADD_YIELD_PT2")
                    .property("CodeToIntroduceVariable", normalizedEndLoc)
                    .property("YieldingPoint", yieldPoint)
                    .property("threshold", "1")
                    .delay(delay)
                    .build();
            newExecutor().run(barrierRequest);

            File startLineFile = resolve(Constants.getSearchMethodAndLineFilepath(".", getTestName()));
            int beginning;
            try (BufferedReader reader = new BufferedReader(new FileReader(startLineFile))) {
                beginning = Integer.parseInt(reader.readLine().split("#")[1]);
            }

            int fromLine = Integer.parseInt(yieldPoint.split("#")[1]);
            for (int ln = fromLine; ln >= beginning; ln--) {
                String yieldingPoint = className + "#" + ln;
                if (tryYieldPoint(bw, boundaryLine, normalizedEndLoc, delay, yieldingPoint)) {
                    return;
                }
            }
        }
    }

    // Returns true once a working barrier point has been found and recorded.
    private boolean tryYieldPoint(BufferedWriter bw, String boundaryLine, String endLoc, int delay,
                                   String yieldingPoint) throws IOException {
        TestExecutionRequest yieldRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("ADD_YIELD_PT1")
                .property("CodeToIntroduceVariable", endLoc)
                .property("YieldingPoint", yieldingPoint)
                .property("threshold", "1")
                .delay(delay)
                .build();
        boolean failed = newExecutor().run(yieldRequest);

        if (!failed && checkValidPass()) {
            addBarrierPointToResults(bw, boundaryLine, yieldingPoint, 1);
            return true;
        }

        TestExecutionRequest execMonRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("EXEC_MONITOR")
                .property("CodeToIntroduceVariable", endLoc)
                .delay(delay)
                .build();
        newExecutor().run(execMonRequest);

        File execsFile = resolve(Constants.getThresholdFilepath(".", getTestName()));
        int numExecutions;
        try (BufferedReader reader = new BufferedReader(new FileReader(execsFile))) {
            numExecutions = Integer.parseInt(reader.readLine().split("=")[1]);
        }

        TestExecutionRequest yieldWithThreshold = TestExecutionRequest.forTest(getTestName())
                .agentMode("ADD_YIELD_PT1")
                .property("CodeToIntroduceVariable", endLoc)
                .property("YieldingPoint", yieldingPoint)
                .property("threshold", String.valueOf(numExecutions))
                .delay(delay)
                .build();
        failed = newExecutor().run(yieldWithThreshold);

        if (!failed && checkValidPass()) {
            addBarrierPointToResults(bw, boundaryLine, yieldingPoint, numExecutions);
            return true;
        }
        return false;
    }

    private void parseStackTrace(Map<String, String> classes, File stackTraceFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(stackTraceFile))) {
            String trace;
            while ((trace = reader.readLine()) != null) {
                if (trace.contains(".java:")) {
                    String className = trace.split("at ")[1].split("\\(")[0];
                    className = className.substring(0, className.lastIndexOf("."));
                    String lineNum = trace.split("\\(")[1].split(":")[1].split("\\)")[0];
                    if (!inBlackList(className)) {
                        if (classes.containsKey(className)) {
                            classes.put(className + ",", lineNum);
                        } else {
                            classes.put(className, lineNum);
                        }
                    }
                }
            }
        }
    }

    private boolean inBlackList(String className) throws IOException {
        try (InputStream blacklist = getClass().getResourceAsStream("/blacklist.txt")) {
            Scanner scanner = new Scanner(blacklist);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (className.contains(line)) {
                    return true;
                } else if (line.contains("*") && className.contains(line.substring(0, line.indexOf("*")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addBarrierPointToResults(BufferedWriter bw, String boundaryPoint, String barrierPoint,
                                           int threshold) throws IOException {
        bw.write(getTestName() + "," + boundaryPoint + "," + barrierPoint + "," + threshold);
        bw.newLine();
        bw.flush();
    }

    private boolean checkValidPass() throws IOException {
        File file = resolve(Constants.getYieldResultFilepath(".", getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            boolean allCriteriaMet = true;
            String flagLine;
            while ((flagLine = reader.readLine()) != null) {
                allCriteriaMet &= Boolean.parseBoolean(flagLine.split("=")[1]);
            }
            return allCriteriaMet;
        }
    }
}
