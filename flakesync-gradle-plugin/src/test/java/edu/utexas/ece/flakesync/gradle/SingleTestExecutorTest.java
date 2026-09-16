package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTestExecutorTest {

    private static final File AGENT_JAR = resolveAgentJarFromMavenLocal();

    private static File resolveAgentJarFromMavenLocal() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".m2", "repository", "edu", "utexas", "ece",
                "flakesync-core", "1.0-SNAPSHOT", "flakesync-core-1.0-SNAPSHOT.jar").toFile();
    }

    private FileCollection testClasspath() {
        Project project = ProjectBuilder.builder().build();
        // The running JVM's own classpath already contains this test's compiled classes and JUnit4 —
        // reuse it as a stand-in for "the target project's test runtime classpath".
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        return project.files((Object[]) entries);
    }

    @Test
    void returnsFalseWhenTheForkedTestPasses() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesTest#thisAlwaysPasses")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertFalse(executor.run(request));
    }

    @Test
    void returnsTrueWhenTheForkedTestFails() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysFailsTest#thisAlwaysFails")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertTrue(executor.run(request));
    }

    @Test
    void throwsGradleExceptionWhenTheTestMethodDoesNotExist() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesTest#noSuchMethod")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertThrows(GradleException.class, () -> executor.run(request));
    }

    @Test
    void throwsTimeoutExceptionWhenTheExecutionExceedsItsBudget() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesTest#thisAlwaysPasses")
                .agentMode("CONCURRENT_METHODS")
                .timeoutSeconds(0)
                .build();

        assertThrows(TestExecutionTimeoutException.class, () -> executor.run(request));
    }
}
