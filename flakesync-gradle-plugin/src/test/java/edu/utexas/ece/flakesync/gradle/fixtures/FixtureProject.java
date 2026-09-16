package edu.utexas.ece.flakesync.gradle.fixtures;

import org.gradle.testkit.runner.GradleRunner;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.stream.Stream;

public final class FixtureProject {

    private FixtureProject() {
    }

    public static File materialize(Path targetDir) {
        try {
            Path resourceRoot = classpathResourceToPath("fixture-project");
            try (Stream<Path> paths = Files.walk(resourceRoot)) {
                for (Path source : (Iterable<Path>) paths::iterator) {
                    Path relative = resourceRoot.relativize(source);
                    Path destination = targetDir.resolve(relative.toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return targetDir.toFile();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public static GradleRunner runnerFor(File projectDir, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(Arrays.asList(arguments))
                .forwardOutput();
    }

    private static Path classpathResourceToPath(String resource) {
        try {
            URL url = FixtureProject.class.getClassLoader().getResource(resource);
            if (url == null) {
                throw new IllegalStateException("Fixture resource not found on classpath: " + resource);
            }
            return Paths.get(url.toURI());
        } catch (URISyntaxException use) {
            throw new IllegalStateException(use);
        }
    }
}
