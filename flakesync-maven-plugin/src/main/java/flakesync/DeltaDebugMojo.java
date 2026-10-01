/*
The MIT License (MIT)
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman
Copyright (c) 2025 August Shi


Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package flakesync;


import flakesync.common.ConfigurationDefaults;
import flakesync.common.DeltaDebugger;
import flakesync.common.Level;
import flakesync.common.Logger;
import flakesync.heuristic.CandidateScorer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "deltadebug", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class DeltaDebugMojo extends FlakeSyncAbstractMojo {

    protected static final int MAX_DELAY = 25600;

    protected int delay;

    // [SearchLogger] one row per Surefire run of this step, plus order and total rows
    private void logCandidate(String phase, String group, int order, String candidate, String result, int runs) {
        SearchLogger.log(this.baseDir.getAbsolutePath(), testName, phase, group, order, candidate, result, runs);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        Logger.getGlobal().log(Level.INFO, ("Running DeltaDebugMojo"));
        MojoExecutionException allExceptions = null;

        //Generate list of all locations
        List<String> locations = new ArrayList<String>();
        this.delay = generateLocsList(locations);

        // [Heuristics] puts the locations that the critical point heuristics (CP1-CP3) rate highest first, so they end
        // up in the same chunks (-Dflakesync.heuristics=false keeps the original order)
        int totalLocations = locations.size();
        CandidateScorer scorer = CandidateScorer.forProject(this.mavenProject.getBasedir());
        Logger.getGlobal().log(Level.INFO, scorer.describe());
        CandidateScorer.LocationOrder ordering = scorer.orderCriticalLocations(locations);
        locations = ordering.locations;
        if (ordering.reordered) {
            // [SearchLogger] "order" is the new position, the score is in "result"
            for (int k = 0; k < locations.size(); k++) {
                logCandidate("deltadebug-order", "initial", k + 1, locations.get(k), "score=" + ordering.scores[k], 0);
            }
        }

        LocationsDeltaDebug deltaDebug = new LocationsDeltaDebug(
                this.surefire, this.originalArgLine, this.mavenProject,
                this.mavenSession, this.pluginManager,
                this.baseDir,
                this.localRepository, this.testName, this.delay);



        //Run delta debugging
        locations = deltaDebug.deltaDebug(locations, 2);
        this.delay = deltaDebug.delay;  // Get the delay after delta debugging finished
        writeLocationsToFile(locations);

        // [SearchLogger] total number of Surefire runs of this step (the "runs" column)
        logCandidate("deltadebug-total", "all", 0, "locations=" + totalLocations + "->" + locations.size()
                + ";iterations=" + deltaDebug.getIterations() + ";ordering=" + (scorer.isOn() ? "ON" : "OFF")
                + ";final_delay=" + this.delay, "TOTAL", deltaDebug.calls);
    }

    private void writeLocationsToFile(List<String> locs) {
        File file = new File(String.valueOf(Paths.get(String.valueOf(this.baseDir),
                String.valueOf(Constants.getMinLocationsFilepath(testName)))));
        try {
            FileWriter outputLocationsFile = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(outputLocationsFile);
            bw.write(this.delay + "\n");

            for (String location : locs) {
                bw.write(location);
                bw.newLine();
            }
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private int generateLocsList(List<String> locsList) {
        int delay = 0;
        try {
            File file = new File(String.valueOf(Paths.get(String.valueOf(this.baseDir),
                    String.valueOf(Constants.getAllLocationsFilepath(testName)))));
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            delay = Integer.parseInt(line);
            line = reader.readLine();
            while (line != null) {
                locsList.add(line);
                // Read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return delay;
    }


    public void setTestName(String testName) {
        this.testName = testName;
    }

    private class LocationsDeltaDebug extends DeltaDebugger<String> {

        protected Plugin surefire;
        protected MavenProject mavenProject;
        protected MavenSession mavenSession;
        protected BuildPluginManager pluginManager;
        protected File baseDir;
        protected String testName;
        protected String localRepository;
        protected String originalArgLine;
        protected int delay;

        // [SearchLogger] Surefire runs so far, and what the next runs are for
        private int calls = 0;
        private String group = "start";
        private String kind = "";

        private LocationsDeltaDebug(Plugin surefire, String argLine, MavenProject mavenProject,
                                    MavenSession mavenSession, BuildPluginManager pluginManager,
                                    File baseDir, String localRepository, String testName, int delay) {
            this.surefire = surefire;
            this.originalArgLine = argLine;
            this.mavenProject = mavenProject;
            this.mavenSession = mavenSession;
            this.pluginManager = pluginManager;
            this.baseDir = baseDir;
            this.localRepository = localRepository;
            this.testName = testName;
            this.delay = delay;
        }

        // Redo core delta debug logic since we need to rerun with higher delays in middle
        @Override
        public List<String> deltaDebug(final List<String> elements, int granularity) {
            this.iterations++;
            this.group = "level" + this.iterations + "-n" + elements.size() + "-g" + granularity;   // [SearchLogger]

            // If n granularity is greater than number of tests, then finished, simply return passed in tests
            if (elements.size() < granularity) {
                return elements;
            }

           // Cut the elements into n equal chunks and try each chunk
            int chunkSize = (int)Math.round((double)(elements.size()) / granularity);
            List<List<String>> chunks = new ArrayList<>();
            for (int i = 0; i < elements.size(); i += chunkSize) {
                List<String> chunk = new ArrayList<>();
                List<String> otherChunk = new ArrayList<>();
                // Create chunk starting at this iteration
                int endpoint = Math.min(elements.size(), i + chunkSize);
                chunk.addAll(elements.subList(i, endpoint));

                // Complement chunk are elements before and after this current chunk
                otherChunk.addAll(elements.subList(0, i));
                otherChunk.addAll(elements.subList(endpoint, elements.size()));

                while (this.delay <= MAX_DELAY) {
                    // Try to other, complement chunk first, with theory that valid elements are closer to end
                    this.kind = "complement";
                    if (checkValid(otherChunk)) {
                        return deltaDebug(otherChunk, 2);   // If works, then delta debug some more the complement chunk
                    }
                    // Check if running this chunk works
                    this.kind = "chunk";
                    if (checkValid(chunk)) {
                        return deltaDebug(chunk, 2);        // If works, then delta debug some more this chunk
                    }
                    this.delay *= 2;
                    System.out.println("DELTA UPDATE DELAY: " + this.delay);
                }

            }
            // If size is equal to number of chunks, we are finished, cannot go down more
            if (elements.size() == granularity) {
                return elements;
            }
            // If not chunk/complement work, increase granularity and try again
            if (elements.size() < granularity * 2) {
                return deltaDebug(elements, elements.size());
            } else {
                return deltaDebug(elements, granularity * 2);
            }
        }

        @Override
        public boolean checkValid(List<String> elements) {
            //First we need to write the elements to a temporary file for the agent to write to
            createTempFile(elements);

            SurefireExecution cleanExec = SurefireExecution.SurefireFactory.createDeltaDebugExec(
                    this.surefire, this.originalArgLine, this.mavenProject, this.mavenSession,
                    this.pluginManager,
                    Paths.get(this.baseDir.getAbsolutePath(), ConfigurationDefaults.DEFAULT_FLAKESYNC_DIR).toString(),
                    this.localRepository, this.testName, this.delay);

            boolean reproduced;
            try {
                reproduced = this.executeSurefireExecution(null, cleanExec);
            } catch (Throwable exception) {
                System.out.println(exception);
                throw new RuntimeException();
            }
            // [SearchLogger] one Surefire run: was the failure reproduced with only these locations delayed?
            this.calls++;
            logCandidate("deltadebug", this.group, this.calls,
                    this.kind + "[" + elements.size() + "]@delay=" + this.delay,
                    reproduced ? "REPRODUCED" : "NOT_REPRODUCED", 1);
            return reproduced;
        }

        private void createTempFile(List<String> elements) {
            File locsFile = new File(String.valueOf(Paths.get(String.valueOf(this.baseDir),
                    String.valueOf(Constants.getWorkingLocationsFilepath(testName)))));
            try {
                FileWriter outputLocationsFile = new FileWriter(locsFile);
                BufferedWriter bw = new BufferedWriter(outputLocationsFile);

                for (String location : elements) {
                    bw.write(location);
                    bw.newLine();
                }
                bw.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        private boolean executeSurefireExecution(MojoExecutionException allExceptions,
                                                 SurefireExecution execution)
                throws Throwable {
            try {
                execution.run();
            } catch (Exception ex) {
                return true;
            }
            return false;
        }
    }
}
