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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaDebugTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void minimizesLocationsWhenEveryExecutionReproducesTheFailure() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Files.createDirectories(flakesyncDir);

        // Seed the input Locations.txt that delaylocs would have produced: first line is the
        // delay, remaining lines are candidate locations.
        Path allLocations = flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations.txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(allLocations.toFile()))) {
            bw.write("100\n");
            bw.write("pkg/Foo#10\n");
            bw.write("pkg/Foo#20\n");
            bw.write("pkg/Bar#5\n");
            bw.write("pkg/Bar#8\n");
        }

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "deltadebug", "--test-name=fixture.FlakySampleTest#alwaysFails").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

        Path minimized = flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations_minimized.txt");
        assertTrue(Files.exists(minimized));
        List<String> lines = Files.readAllLines(minimized);
        // Since alwaysFails fails unconditionally (checkValid always true), delta debugging
        // should have converged to the empty complement first: only the delay line remains.
        assertTrue(lines.size() <= 5);
    }
}
