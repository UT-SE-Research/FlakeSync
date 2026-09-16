package edu.utexas.ece.flakesync.gradle.fixtures;

import org.junit.Test;

import static org.junit.Assert.fail;

public class AlwaysFailsTest {

    @Test
    public void thisAlwaysFails() {
        fail("intentionally failing fixture");
    }
}
