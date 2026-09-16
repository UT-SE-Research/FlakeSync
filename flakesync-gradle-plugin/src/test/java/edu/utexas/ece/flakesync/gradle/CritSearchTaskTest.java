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

class CritSearchTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void producesRootMethodsAndCriticalPointsFiles() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Files.createDirectories(flakesyncDir);

        String testName = "fixture.FlakySampleTest#alwaysFails";

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations_minimized.txt").toFile()))) {
            bw.write("100\n");
            bw.write("fixture/FlakySampleTest#20\n");
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-StackTrace.txt").toFile()))) {
            bw.write("0,fixture/FlakySampleTest/alwaysFails(FlakySampleTest/java:20)\n");
            bw.write("0,END\n");
        }

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "critsearch", "--test-name=" + testName).build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.exists(flakesyncDir.resolve("Results-CritSearch")
                .resolve("fixture.FlakySampleTest.alwaysFails-RootMethods.csv")));
        assertTrue(Files.exists(flakesyncDir.resolve("Results-CritSearch")
                .resolve("fixture.FlakySampleTest.alwaysFails-CriticalPoints.csv")));
    }
}
