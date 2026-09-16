package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.tasks.TaskAction;

public class ConcurrentFindTask extends AbstractFlakeSyncTask {

    @TaskAction
    public void run() {
        TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                .agentMode("CONCURRENT_METHODS")
                .build();

        try {
            newExecutor().run(request);
        } catch (RuntimeException ex) {
            getLogger().error("Error executing test: the test did not run cleanly", ex);
        }
    }
}
