package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPatchFilesForTheCriticalAndBarrierPoints() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Files.createDirectories(flakesyncDir);

        String testName = "fixture.FlakySampleTest#alwaysFails";

        Path barrierSearchDir = flakesyncDir.resolve("Results-BarrierSearch");
        Files.createDirectories(barrierSearchDir);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                barrierSearchDir.resolve("fixture.FlakySampleTest.alwaysFails-BarrierPoints.csv").toFile()))) {
            bw.write("#Test-Name,Boundary-Point,Barrier-Point,Threshold\n");
            // Critical point (Helper) and barrier point (FlakySampleTest) are deliberately in
            // different classes: PatchTask copies each file to a sibling ".orig" before patching,
            // and colliding on the same file throws FileAlreadyExistsException on the second copy
            // — a real, pre-existing limitation of the ported PatchingMojo logic, not something to
            // work around here. The barrier point must land in a file containing a method named
            // after the test ("void alwaysFails(" in FlakySampleTest.java) since
            // InjectYieldStatement searches the barrier point's own file for that method to insert
            // its reset() call — another faithfully-ported assumption, not incidental to this test.
            bw.write(testName + ",fixture/Helper#6-fixture/Helper#6[100],"
                    + "fixture/FlakySampleTest#12,1\n");
        }

        BuildResult result = FixtureProject.runnerFor(projectDir, "patch", "--test-name=" + testName).build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        Path patchDir = flakesyncDir.resolve("patch");
        assertTrue(Files.isDirectory(patchDir));
        try (java.util.stream.Stream<Path> patches = Files.list(patchDir)) {
            assertTrue(patches.anyMatch(p -> p.toString().endsWith(".patch")));
        }
    }
}
