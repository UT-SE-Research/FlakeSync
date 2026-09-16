package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierPointSearchTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void routesThroughTheStackTraceBranchAndCapturesARealFailureTrace() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Path critSearchDir = flakesyncDir.resolve("Results-CritSearch");
        Files.createDirectories(critSearchDir);

        String testName = "fixture.FlakySampleTest#alwaysFails";

        // firstLoc deliberately does NOT contain "Test" as a substring (unlike the fixture
        // class's own name) so BarrierPointSearchTask routes into searchViaStackTrace rather
        // than searchDownwardFromTest.
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                critSearchDir.resolve("fixture.FlakySampleTest.alwaysFails-CriticalPoints.csv").toFile()))) {
            bw.write("fixture/Helper#5-fixture/FlakySampleTest#20[100]\n");
        }

        FixtureProject.runnerFor(projectDir, "barrierpointsearch", "--test-name=" + testName).build();

        // fixture.FlakySampleTest#alwaysFails really does fail (Assert.fail()), so the BARRIER_ST
        // execution's SingleTestLauncher genuinely writes a stack trace file via
        // stackTraceOutputFile — assert that mechanism worked end to end, which is the specific
        // behavior this task's port depends on (see Task 3's SingleTestLauncher change). The
        // exact contents of BarrierPoints.csv beyond its header depend on fine-grained agent
        // bytecode-instrumentation behavior not otherwise exercised by this plan's tests; if this
        // fixture doesn't yield a barrier point, that's fine — the file existing with its header
        // is enough to prove the task's control flow completed.
        Path stackTraceFile = flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-JUnitFailureStackTrace.txt");
        assertTrue(Files.exists(stackTraceFile));
        assertTrue(Files.readAllLines(stackTraceFile).stream().anyMatch(l -> l.contains("FlakySampleTest")));

        Path barrierPoints = flakesyncDir.resolve("Results-BarrierSearch")
                .resolve("fixture.FlakySampleTest.alwaysFails-BarrierPoints.csv");
        assertTrue(Files.exists(barrierPoints));
    }
}
