package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentFindTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void writesResultMethodsFileForThePassingTest() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "concurrentfind", "--test-name=fixture.FlakySampleTest#alwaysPasses").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        Path resultMethods = projectDir.toPath()
                .resolve(".flakesync").resolve("fixture.FlakySampleTest.alwaysPasses-ResultMethods.txt");
        assertTrue(Files.exists(resultMethods));
    }
}
