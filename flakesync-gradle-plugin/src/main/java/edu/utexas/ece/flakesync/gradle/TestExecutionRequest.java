package edu.utexas.ece.flakesync.gradle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestExecutionRequest {

    private final String testName;
    private final Map<String, String> systemProperties;
    private final int timeoutSeconds;

    private TestExecutionRequest(String testName, Map<String, String> systemProperties, int timeoutSeconds) {
        this.testName = testName;
        this.systemProperties = Collections.unmodifiableMap(systemProperties);
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getTestName() {
        return testName;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public static Builder forTest(String testName) {
        return new Builder(testName);
    }

    public static final class Builder {

        private final String testName;
        private final Map<String, String> systemProperties = new LinkedHashMap<>();
        private int timeoutSeconds = SingleTestExecutor.DEFAULT_TIMEOUT_SECONDS;

        private Builder(String testName) {
            this.testName = testName;
            this.systemProperties.put(".test", testName);
        }

        public Builder agentMode(String mode) {
            systemProperties.put("agentmode", mode);
            return this;
        }

        public Builder delay(int delayMillis) {
            systemProperties.put("delay", String.valueOf(delayMillis));
            // Scale the fork timeout with the requested delay so a large injected delay
            // never gets mistaken for a hang; matches the intent (never wired up) of the
            // Maven plugin's unused SurefireExecution#addProcessTimeout.
            this.timeoutSeconds = Math.max(this.timeoutSeconds, (delayMillis / 1000) * 4 + 60);
            return this;
        }

        public Builder property(String key, String value) {
            systemProperties.put(key, value);
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public TestExecutionRequest build() {
            return new TestExecutionRequest(testName, systemProperties, timeoutSeconds);
        }
    }
}
