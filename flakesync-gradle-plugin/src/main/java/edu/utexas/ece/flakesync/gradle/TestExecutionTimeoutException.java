package edu.utexas.ece.flakesync.gradle;

public class TestExecutionTimeoutException extends RuntimeException {

    public TestExecutionTimeoutException(String message) {
        super(message);
    }
}
