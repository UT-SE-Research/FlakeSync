package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentFindThenDelayLocsThenDeltaDebugRunAsAPipeline() {
        File projectDir = FixtureProject.materialize(tempDir);
        String testNameArg = "--test-name=fixture.FlakySampleTest#alwaysFails";

        BuildResult concurrentFindResult = FixtureProject
                .runnerFor(projectDir, "concurrentfind", testNameArg).build();
        assertTrue(concurrentFindResult.getOutput().contains("BUILD SUCCESSFUL"));

        BuildResult delayLocsResult = FixtureProject
                .runnerFor(projectDir, "delaylocs", testNameArg).build();
        assertTrue(delayLocsResult.getOutput().contains("BUILD SUCCESSFUL"));

        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        assertTrue(Files.exists(flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations.txt")));

        BuildResult deltaDebugResult = FixtureProject
                .runnerFor(projectDir, "deltadebug", testNameArg).build();
        assertTrue(deltaDebugResult.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.exists(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations_minimized.txt")));
    }
}
