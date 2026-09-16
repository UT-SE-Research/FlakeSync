package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import flakesync.DeltaDebugger;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeltaDebugTask extends AbstractFlakeSyncTask {

    static final int MAX_DELAY = 25600;

    @TaskAction
    public void run() {
        List<String> locations = new ArrayList<>();
        int startingDelay = readAllLocations(locations);

        LocationsDeltaDebug deltaDebug = new LocationsDeltaDebug(startingDelay);
        locations = deltaDebug.deltaDebug(locations, 2);

        writeMinimizedLocations(deltaDebug.delay, locations);
    }

    private int readAllLocations(List<String> locations) {
        File file = resolve(Constants.getAllLocationsFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int delay = Integer.parseInt(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                locations.add(line);
            }
            return delay;
        } catch (IOException ioe) {
            throw new GradleException("Failed to read " + file, ioe);
        }
    }

    private void writeMinimizedLocations(int delay, List<String> locations) {
        File file = resolve(Constants.getMinLocationsFilepath(getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(delay + "\n");
            for (String location : locations) {
                bw.write(location);
                bw.newLine();
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private class LocationsDeltaDebug extends DeltaDebugger<String> {

        int delay;

        LocationsDeltaDebug(int delay) {
            this.delay = delay;
        }

        // Redo core delta debug logic since we need to rerun with higher delays in the middle,
        // identical control flow to flakesync-maven-plugin's DeltaDebugMojo.LocationsDeltaDebug.
        @Override
        public List<String> deltaDebug(List<String> elements, int granularity) {
            this.iterations++;

            if (elements.size() < granularity) {
                return elements;
            }

            int chunkSize = (int) Math.round((double) elements.size() / granularity);
            for (int i = 0; i < elements.size(); i += chunkSize) {
                int endpoint = Math.min(elements.size(), i + chunkSize);
                List<String> chunk = new ArrayList<>(elements.subList(i, endpoint));
                List<String> otherChunk = new ArrayList<>(elements.subList(0, i));
                otherChunk.addAll(elements.subList(endpoint, elements.size()));

                while (this.delay <= MAX_DELAY) {
                    if (checkValid(otherChunk)) {
                        return deltaDebug(otherChunk, 2);
                    }
                    if (checkValid(chunk)) {
                        return deltaDebug(chunk, 2);
                    }
                    this.delay *= 2;
                }
            }

            if (elements.size() == granularity) {
                return elements;
            }
            if (elements.size() < granularity * 2) {
                return deltaDebug(elements, elements.size());
            }
            return deltaDebug(elements, granularity * 2);
        }

        @Override
        public boolean checkValid(List<String> elements) {
            writeWorkingLocations(elements);

            TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                    .agentMode("DELTA_DEBUG")
                    .property("locations", Constants.getWorkingLocationsFilepath(getTestName()).toString())
                    .property("whitelist", Constants.getWhitelistFilepath(getTestName()).toString())
                    .delay(this.delay)
                    .build();

            return newExecutor().run(request);
        }

        private void writeWorkingLocations(List<String> elements) {
            File file = resolve(Constants.getWorkingLocationsFilepath(getTestName()));
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (String location : elements) {
                    bw.write(location);
                    bw.newLine();
                }
            } catch (IOException ioe) {
                throw new GradleException("Failed to write " + file, ioe);
            }
        }
    }
}
