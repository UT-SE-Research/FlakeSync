package edu.utexas.ece.flakesync.launcher.fixtures;

abstract class BaseWithPackagePrivateTest {

    @org.junit.jupiter.api.Test
    void thisAlwaysPasses() {
        org.junit.jupiter.api.Assertions.assertTrue(true);
    }
}
