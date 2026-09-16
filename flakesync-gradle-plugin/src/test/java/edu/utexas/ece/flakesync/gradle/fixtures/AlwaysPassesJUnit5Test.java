package edu.utexas.ece.flakesync.gradle.fixtures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AlwaysPassesJUnit5Test {

    @Test
    public void thisAlwaysPasses() {
        assertTrue(true);
    }
}
