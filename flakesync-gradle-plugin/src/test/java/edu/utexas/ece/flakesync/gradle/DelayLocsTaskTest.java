package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayLocsTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void stopsAtTheFirstDelayThatReproducesTheFailure() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "delaylocs", "--test-name=fixture.FlakySampleTest#alwaysFails").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

        Path locationsFile = projectDir.toPath()
                .resolve(".flakesync").resolve("fixture.FlakySampleTest.alwaysFails-Locations.txt");
        assertTrue(Files.exists(locationsFile));

        List<String> lines = Files.readAllLines(locationsFile);
        // First line is the delay that was used; since alwaysFails fails unconditionally,
        // it must have stopped at the very first delay in the ladder (100ms).
        assertEquals("100", lines.get(0));
    }
}
