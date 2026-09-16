package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class DelayLocsTask extends AbstractFlakeSyncTask {

    private static final int[] DELAYS = {100, 200, 400, 800, 1600, 3200, 6400, 12800};

    @TaskAction
    public void run() {
        writeWhitelist();

        for (int delay : DELAYS) {
            TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                    .agentMode("ALL_LOCATIONS")
                    .property("concurrentmethods", Constants.getConcurrentMethodsFilepath(getTestName()).toString())
                    .property("whitelist", Constants.getWhitelistFilepath(getTestName()).toString())
                    .delay(delay)
                    .build();

            try {
                if (newExecutor().run(request)) {
                    return;
                }
            } catch (RuntimeException ex) {
                getLogger().warn("Surefire-equivalent execution failed for delay {}: {}", delay, ex.getMessage());
                return;
            }
        }
    }

    private void writeWhitelist() {
        File whitelist = resolve(Constants.getWhitelistFilepath(getTestName()));
        FileCollection testOutput = getProject().getExtensions()
                .getByType(org.gradle.api.plugins.JavaPluginExtension.class)
                .getSourceSets().getByName("test").getOutput().getClassesDirs();

        try {
            Files.createDirectories(whitelist.getParentFile().toPath());
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(whitelist))) {
                for (File outputDir : testOutput) {
                    Path outputPath = outputDir.toPath();
                    if (Files.exists(outputPath)) {
                        try (Stream<Path> paths = Files.walk(outputPath)) {
                            for (Path classFile : (Iterable<Path>) paths
                                    .filter(Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".class"))
                                    .filter(p -> !p.toString().contains("Tests"))::iterator) {
                                String className = outputPath.relativize(classFile).toString()
                                        .replace(File.separatorChar, '.')
                                        .replaceAll("\\.class$", "");
                                bw.write(className);
                                bw.newLine();
                            }
                        }
                    }
                }

                for (File jar : getTestRuntimeClasspath()) {
                    if (jar.getName().endsWith(".jar") && jar.isFile()) {
                        try (JarFile jarFile = new JarFile(jar)) {
                            Enumeration<JarEntry> entries = jarFile.entries();
                            while (entries.hasMoreElements()) {
                                JarEntry entry = entries.nextElement();
                                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                                    String className = entry.getName()
                                            .replace('/', '.')
                                            .replaceAll("\\.class$", "");
                                    bw.write(className);
                                    bw.newLine();
                                }
                            }
                        } catch (IOException ioe) {
                            getLogger().warn("Failed to read jar for whitelist: {}", jar, ioe);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new org.gradle.api.GradleException("Failed to write whitelist file", ioe);
        }
    }
}
