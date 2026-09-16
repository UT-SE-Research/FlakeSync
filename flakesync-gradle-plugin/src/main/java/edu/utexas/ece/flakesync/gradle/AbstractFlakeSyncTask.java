package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.nio.file.Path;

public abstract class AbstractFlakeSyncTask extends DefaultTask {

    private String testName;

    protected AbstractFlakeSyncTask() {
        getOutputs().upToDateWhen(t -> false);
        dependsOn(getProject().getTasks().named("testClasses"));
    }

    @Input
    public String getTestName() {
        if (testName == null || testName.isEmpty()) {
            throw new GradleException("--test-name is required, e.g. --test-name=pkg.MyTest#myMethod");
        }
        return testName;
    }

    @Option(option = "test-name", description = "Fully qualified ClassName#methodName of the flaky test.")
    public void setTestName(String testName) {
        this.testName = testName;
    }

    @Internal
    protected File getAgentJar() {
        return getProject().getConfigurations()
                .getByName(FlakeSyncPlugin.AGENT_CONFIGURATION_NAME)
                .getSingleFile();
    }

    @Internal
    protected FileCollection getTestRuntimeClasspath() {
        return getProject().getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets().getByName("test").getRuntimeClasspath();
    }

    protected File resolve(Path constantsRelativePath) {
        File file = getProject().getProjectDir().toPath().resolve(constantsRelativePath).normalize().toFile();
        // flakesync.Constants' own path-builder methods sometimes mkdirs() a directory computed
        // relative to the JVM's raw working directory instead of the baseDir argument they were
        // given (a pre-existing quirk, left unchanged) — that side effect is irrelevant here since
        // we independently guarantee the real parent directory (resolved against the Gradle
        // project directory, not JVM CWD) exists before any task reads or writes through this method.
        file.getParentFile().mkdirs();
        return file;
    }

    protected SingleTestExecutor newExecutor() {
        return new SingleTestExecutor(getAgentJar(), getTestRuntimeClasspath(), getProject().getProjectDir());
    }
}
