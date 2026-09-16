package edu.utexas.ece.flakesync.gradle.fixtures;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropertyCheckingTest {

    @Test
    public void checksCustomProperty() {
        assertEquals("expected-value", System.getProperty("flakesync.trial.customProperty"));
    }
}
