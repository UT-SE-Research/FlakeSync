package edu.utexas.ece.flakesync.launcher.fixtures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class AlwaysFailsJUnit5Test {

    @Test
    public void thisAlwaysFails() {
        fail("intentionally failing JUnit5 fixture");
    }
}
