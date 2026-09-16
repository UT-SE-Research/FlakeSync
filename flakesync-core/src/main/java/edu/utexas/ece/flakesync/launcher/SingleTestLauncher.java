package edu.utexas.ece.flakesync.launcher;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

public final class SingleTestLauncher {

    public static final int EXIT_PASSED = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_CONFIG_ERROR = 2;

    private SingleTestLauncher() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length != 1 || args[0].indexOf('#') < 0) {
            System.err.println("Usage: SingleTestLauncher <ClassName>#<methodName>");
            return EXIT_CONFIG_ERROR;
        }

        int hashIndex = args[0].indexOf('#');
        String className = args[0].substring(0, hashIndex);
        String methodName = args[0].substring(hashIndex + 1);

        Class<?> testClass;
        try {
            testClass = Class.forName(className);
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Could not find test class: " + className);
            return EXIT_CONFIG_ERROR;
        }

        try {
            testClass.getMethod(methodName);
        } catch (NoSuchMethodException nsme) {
            System.err.println("Could not find test method: " + className + "#" + methodName);
            return EXIT_CONFIG_ERROR;
        }

        Result result;
        try {
            result = new JUnitCore().run(Request.method(testClass, methodName));
        } catch (NoClassDefFoundError ncdfe) {
            System.err.println("JUnit4 is not available on the test classpath: " + ncdfe.getMessage());
            return EXIT_CONFIG_ERROR;
        }

        if (!result.wasSuccessful()) {
            writeStackTraceIfRequested(result);
            return EXIT_FAILED;
        }
        return EXIT_PASSED;
    }

    private static void writeStackTraceIfRequested(Result result) {
        String outputPath = System.getProperty("stackTraceOutputFile");
        if (outputPath == null || result.getFailures().isEmpty()) {
            return;
        }
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(outputPath))) {
            writer.print(result.getFailures().get(0).getTrace());
        } catch (java.io.IOException ioe) {
            System.err.println("Failed to write stack trace output file: " + ioe.getMessage());
        }
    }
}
