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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CritSearchTask extends AbstractFlakeSyncTask {

    static final int MAX_DELAY = 25600;

    private final Set<String> stackTraceLines = new HashSet<>();
    private final Set<String> roots = new HashSet<>();
    private final Map<Integer, List<String>> clusters = new HashMap<>();
    private int delay = 100;

    @TaskAction
    public void run() {
        List<String> locations = new ArrayList<>();
        generateLocsList(locations);

        TestExecutionRequest minimizedRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("DELAY_INJECTION_BY_LOC")
                .property("locations", Constants.getMinLocationsFilepath(getTestName()).toString())
                .delay(delay)
                .build();
        if (!newExecutor().run(minimizedRequest)) {
            getLogger().warn("This location is not a good one. Go back and run deltadebug again.");
            return;
        }

        parseStackTrace();

        Set<String> visitedTraces = new HashSet<>();
        for (String line : stackTraceLines) {
            String withoutThreadId = line.substring(0, line.lastIndexOf(","));
            if (visitedTraces.contains(withoutThreadId)) {
                continue;
            }
            visitedTraces.add(withoutThreadId);

            String[] locs = line.split(",");
            int threadId = Integer.parseInt(locs[locs.length - 1]);
            String rootLine = "";
            for (int i = 0; i < locs.length - 1; i++) {
                String[] parts = locs[i].split("#");
                String newLoc = parts[0] + "#" + parts[1];

                writeIndLoc(threadId, i, newLoc);

                int workingDelay = delay;
                boolean result = runWithDelayAtLoc(newLoc, workingDelay, threadId, i);
                if (!result) {
                    workingDelay *= 2;
                    while (workingDelay <= MAX_DELAY) {
                        result = runWithDelayAtLoc(newLoc, workingDelay, threadId, i);
                        if (result) {
                            break;
                        }
                        workingDelay *= 2;
                    }
                    if (!result) {
                        break;
                    }
                }

                rootLine = searchStackTrace(parts[0], ":" + parts[1]);
                rootLine += "[" + workingDelay + "]";
            }
            if (!rootLine.isEmpty()) {
                roots.add(rootLine);
            }
        }

        writeRootMethodsToFile();

        File resultsFile = resolve(Constants.getCritPointsResultsFilepath(".", getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(resultsFile))) {
            Set<String> visitedMethods = new HashSet<>();
            for (String root : roots) {
                String[] elements = root.split("/");
                String methodName = elements[elements.length - 2].split("\\(")[0];
                String className = root.substring(0, root.lastIndexOf("/"));
                className = className.substring(0, className.lastIndexOf("/"));
                String fullMethodName = className + "#" + methodName;
                int workingDelay = Integer.parseInt(root.split("\\[")[1].split("\\]")[0]);

                if (visitedMethods.add(fullMethodName)) {
                    writeRootMethodFile(fullMethodName);

                    TestExecutionRequest rmaRequest = TestExecutionRequest.forTest(getTestName())
                            .agentMode("ROOT_METHOD_ANALYSIS")
                            .property("rootMethod", Constants.getRootMethodFilepath(getTestName()).toString())
                            .property("methodOnly", methodName)
                            .delay(workingDelay)
                            .build();
                    newExecutor().run(rmaRequest);

                    sequentialDebug(workingDelay);
                    this.delay = workingDelay;
                    writeClustersToFile(bw);
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to write critical points results", ioe);
        }
    }

    private boolean runWithDelayAtLoc(String loc, int workingDelay, int threadId, int idx) {
        TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                .agentMode("DELAY_INJECTION_BY_LOC")
                .property("locations", Constants.getIndLocFilepath(".", getTestName(), threadId, idx).toString())
                .delay(workingDelay)
                .build();
        return newExecutor().run(request);
    }

    private void writeIndLoc(int threadId, int idx, String location) {
        File file = resolve(Constants.getIndLocFilepath(".", getTestName(), threadId, idx));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(location);
            bw.newLine();
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private void writeRootMethodFile(String fullMethodName) {
        File file = resolve(Constants.getRootMethodFilepath(getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(fullMethodName + ":" + getTestName());
            bw.newLine();
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private void writeRootMethodsToFile() {
        File file = resolve(Constants.getRootMethodResultsFilepath(".", getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("#Test-Name,Location[delay]");
            bw.newLine();
            for (String location : roots) {
                bw.write(getTestName() + "," + location);
                bw.newLine();
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private void writeClustersToFile(BufferedWriter bw) throws IOException {
        for (int i = 1; i <= clusters.size(); i++) {
            List<String> cluster = clusters.get(i);
            if (cluster == null || cluster.isEmpty()) {
                return;
            }
            if (cluster.size() > 1) {
                bw.write(cluster.get(0).trim() + "-" + cluster.get(cluster.size() - 1).trim() + "[" + delay + "]");
            } else {
                bw.write(cluster.get(0) + "-" + cluster.get(0) + "[" + delay + "]");
            }
            bw.newLine();
        }
    }

    private void generateLocsList(List<String> locsList) {
        File input = resolve(Constants.getMinLocationsFilepath(getTestName()));
        File output = resolve(Constants.getWorkingLocationsFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(input));
             BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
            this.delay = Integer.parseInt(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                locsList.add(line);
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to read/write locations", ioe);
        }
    }

    private void parseStackTrace() {
        File file = resolve(Constants.getStackTraceFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder parsedInfo = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if ("END".equals(data[1])) {
                    parsedInfo.append(data[0]);
                    stackTraceLines.add(parsedInfo.toString());
                    parsedInfo.setLength(0);
                } else if (data[1].split(":").length > 1) {
                    String tmp = data[1].substring(0, data[1].indexOf('('));
                    String className = tmp.substring(0, tmp.lastIndexOf('/'));
                    int lineNumber = Integer.parseInt(data[1].split(":")[1]
                            .substring(0, data[1].split(":")[1].length() - 1));
                    parsedInfo.append(className).append("#").append(lineNumber).append(",");
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to read " + file, ioe);
        }
    }

    private String searchStackTrace(String searchKey1, String searchKey2) {
        File file = resolve(Constants.getStackTraceFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(searchKey1) && line.contains(searchKey2)) {
                    String rest = line.split(",")[1];
                    return rest.substring(0, rest.indexOf(':'));
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to read " + file, ioe);
        }
        return null;
    }

    private void sequentialDebug(int workingDelay) {
        File methodStartEnd = new File(Constants.getMethodStartEndLineFile(
                getProject().getProjectDir().getAbsolutePath(), getTestName()));
        List<String> currentLines = new ArrayList<>();
        int cluster = 1;
        try (BufferedReader reader = new BufferedReader(new FileReader(methodStartEnd))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tmp = line.split("#");
                String className = tmp[0];
                int lowerLineNumber;
                int upperLineNumber;
                if (tmp.length < 3) {
                    lowerLineNumber = Integer.parseInt(tmp[1]);
                    upperLineNumber = Integer.parseInt(tmp[1]);
                } else {
                    lowerLineNumber = Integer.parseInt(tmp[1].substring(0, tmp[1].indexOf('-')));
                    upperLineNumber = Integer.parseInt(tmp[2]);
                }

                for (int i = lowerLineNumber; i < upperLineNumber; i++) {
                    File rootFile = resolve(Constants.getIndRootFilepath(".", getTestName(), i));
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(rootFile))) {
                        writer.write(className + "#" + i);
                        writer.newLine();
                    }

                    TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                            .agentMode("DELAY_INJECTION_BY_LOC")
                            .property("locations", Constants.getIndRootFilepath(".", getTestName(), i).toString())
                            .delay(workingDelay)
                            .build();

                    if (newExecutor().run(request)) {
                        currentLines.add(className + "#" + i);
                    } else if (!currentLines.isEmpty()) {
                        clusters.put(cluster++, new ArrayList<>(currentLines));
                        currentLines = new ArrayList<>();
                    }
                }
            }
            if (!currentLines.isEmpty()) {
                clusters.put(cluster, currentLines);
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed sequential debug over " + methodStartEnd, ioe);
        }
    }
}
