package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFlakeSyncTaskTest {

    public abstract static class NoOpTask extends AbstractFlakeSyncTask {
        @org.gradle.api.tasks.TaskAction
        public void run() {
        }
    }

    @Test
    void requiresTestNameToBeSet() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().register("noop", NoOpTask.class).get();

        assertThrows(GradleException.class, task::getTestName);
    }

    @Test
    void exposesTheConfiguredTestName() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().register("noop", NoOpTask.class).get();

        task.setTestName("pkg.Foo#bar");

        assertEquals("pkg.Foo#bar", task.getTestName());
    }

    @Test
    void resolvesRelativeConstantsPathsAgainstTheProjectDirectory() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().register("noop", NoOpTask.class).get();

        java.io.File resolved = task.resolve(java.nio.file.Paths.get(".", ".flakesync", "pkg.Foo.bar-Locations.txt"));

        assertTrue(resolved.getAbsolutePath().startsWith(project.getProjectDir().getAbsolutePath()));
        assertEquals(
                project.getProjectDir().toPath().resolve(".flakesync").resolve("pkg.Foo.bar-Locations.txt").normalize(),
                resolved.toPath().normalize());
    }

    @Test
    void resolveCreatesTheParentDirectoryRegardlessOfWhatConstantsMkdirsItself() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().register("noop", NoOpTask.class).get();

        java.io.File resolved = task.resolve(
                java.nio.file.Paths.get(".", ".flakesync", "Results-CritSearch", "pkg.Foo.bar-RootMethods.csv"));

        assertTrue(resolved.getParentFile().isDirectory());
    }

    @Test
    void readsTheTargetProjectsOwnTestTaskJvmArgsAndSystemProperties() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        project.getTasks().named("test", org.gradle.api.tasks.testing.Test.class, testTask -> {
            testTask.jvmArgs("-server", "-ea:io.example...");
            testTask.systemProperty("io.example.requireNativeLibs", "true");
        });
        NoOpTask task = project.getTasks().register("noop", NoOpTask.class).get();

        assertEquals(java.util.Arrays.asList("-server", "-ea:io.example..."), task.getTargetJvmArgs());
        assertEquals("true", task.getTargetSystemProperties().get("io.example.requireNativeLibs"));
    }

    @Test
    void includesTheTargetProjectsOwnHeapSizingInTheJvmArgs() {
        // Test#jvmArgs("-Xmx...", "-Xms...") is special-cased by Gradle: it parses those flags out
        // into minHeapSize/maxHeapSize rather than leaving them in getJvmArgs() — so heap sizing
        // has to be read from those properties separately, or it silently disappears when
        // forwarded to the forked JVM.
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        project.getTasks().named("test", org.gradle.api.tasks.testing.Test.class, testTask -> {
            testTask.setMinHeapSize("2g");
            testTask.setMaxHeapSize("4g");
        });
        NoOpTask task = project.getTasks().register("noop", NoOpTask.class).get();

        assertEquals(java.util.Arrays.asList("-Xms2g", "-Xmx4g"), task.getTargetJvmArgs());
    }
}
