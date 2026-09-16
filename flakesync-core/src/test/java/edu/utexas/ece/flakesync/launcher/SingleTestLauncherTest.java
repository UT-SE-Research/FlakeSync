package edu.utexas.ece.flakesync.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SingleTestLauncherTest {

    @Test
    public void exitsZeroWhenTheTestPasses() {
        int exitCode = SingleTestLauncher.run(new String[] {
            "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysPassesTest#thisAlwaysPasses"
        });
        assertEquals(SingleTestLauncher.EXIT_PASSED, exitCode);
    }

    @Test
    public void exitsOneWhenTheTestFails() {
        int exitCode = SingleTestLauncher.run(new String[] {
            "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysFailsTest#thisAlwaysFails"
        });
        assertEquals(SingleTestLauncher.EXIT_FAILED, exitCode);
    }

    @Test
    public void exitsTwoWhenTheClassCannotBeFound() {
        int exitCode = SingleTestLauncher.run(new String[] {"does.not.Exist#method"});
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, exitCode);
    }

    @Test
    public void exitsTwoWhenTheMethodCannotBeFound() {
        int exitCode = SingleTestLauncher.run(new String[] {
            "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysPassesTest#noSuchMethod"
        });
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, exitCode);
    }

    @Test
    public void exitsTwoWhenArgsAreMalformed() {
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, SingleTestLauncher.run(new String[0]));
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, SingleTestLauncher.run(new String[] {"no-hash-here"}));
    }

    @Test
    public void writesTheFailureStackTraceWhenRequestedAndTheTestFails() throws java.io.IOException {
        java.io.File output = java.io.File.createTempFile("flakesync-stacktrace", ".txt");
        output.deleteOnExit();
        System.setProperty("stackTraceOutputFile", output.getAbsolutePath());
        try {
            SingleTestLauncher.run(new String[] {
                "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysFailsTest#thisAlwaysFails"
            });
            String contents = new String(java.nio.file.Files.readAllBytes(output.toPath()));
            assertTrue(contents.contains("AlwaysFailsTest"));
        } finally {
            System.clearProperty("stackTraceOutputFile");
        }
    }

    @Test
    public void doesNotWriteAStackTraceFileWhenTheTestPasses() throws java.io.IOException {
        java.io.File output = java.io.File.createTempFile("flakesync-stacktrace", ".txt");
        assertTrue(output.delete());
        output.deleteOnExit();
        System.setProperty("stackTraceOutputFile", output.getAbsolutePath());
        try {
            SingleTestLauncher.run(new String[] {
                "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysPassesTest#thisAlwaysPasses"
            });
            assertFalse(output.exists());
        } finally {
            System.clearProperty("stackTraceOutputFile");
        }
    }
}
