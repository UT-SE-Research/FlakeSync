package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;

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

    @Test
    void returnsFalseWhenTheForkedJUnit5TestPasses() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesJUnit5Test#thisAlwaysPasses")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertFalse(executor.run(request));
    }

    @Test
    void returnsTrueWhenTheForkedJUnit5TestFails() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysFailsJUnit5Test#thisAlwaysFails")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertTrue(executor.run(request));
    }

    @Test
    void propagatesTheTargetProjectsOwnSystemPropertiesIntoTheForkedJvm() {
        // Mirrors what the Maven plugin does by preserving Surefire's existing argLine: the target
        // project's own test-task system properties (e.g. ones its tests depend on for setup/
        // extension registration) must reach the forked JVM, not just flakesync's own properties.
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."),
                Collections.emptyList(), Collections.singletonMap("flakesync.trial.customProperty", "expected-value"));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.PropertyCheckingTest#checksCustomProperty")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertFalse(executor.run(request));
    }

    @Test
    void theForkedTestFailsWhenTheTargetProjectsSystemPropertyIsMissing() {
        // Same test method, but without propagating the target property — proves the previous
        // test's pass genuinely depends on propagation working, not just always passing.
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."),
                Collections.emptyList(), Collections.emptyMap());

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.PropertyCheckingTest#checksCustomProperty")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertTrue(executor.run(request));
    }

    @Test
    void propagatesTheTargetProjectsOwnJvmArgsIntoTheForkedJvm() {
        // A -D system property set via jvmArgs (as ServiceTalk's own test task does, e.g. for JVM
        // heap sizing and assertion flags) rather than Gradle's separate systemProperties map must
        // also reach the forked JVM as a plain JVM argument.
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."),
                Collections.singletonList("-Dflakesync.trial.customProperty=expected-value"), Collections.emptyMap());

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.PropertyCheckingTest#checksCustomProperty")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertFalse(executor.run(request));
    }
}
