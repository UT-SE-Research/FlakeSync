package edu.utexas.ece.flakesync.launcher;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public final class SingleTestLauncher {

    public static final int EXIT_PASSED = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_CONFIG_ERROR = 2;

    private static final String JUNIT4_TEST_ANNOTATION = "org.junit.Test";
    private static final String JUNIT5_TEST_ANNOTATION = "org.junit.jupiter.api.Test";

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

        Method method = findMethod(testClass, methodName);
        if (method == null) {
            System.err.println("Could not find test method: " + className + "#" + methodName);
            return EXIT_CONFIG_ERROR;
        }

        if (hasAnnotation(method, JUNIT5_TEST_ANNOTATION)) {
            return runJUnit5(testClass, methodName);
        }
        if (hasAnnotation(method, JUNIT4_TEST_ANNOTATION)) {
            return runJUnit4(testClass, methodName);
        }
        System.err.println("Method is not annotated with a recognized @Test annotation: "
                + className + "#" + methodName);
        return EXIT_CONFIG_ERROR;
    }

    // JUnit5 idiomatically allows package-private test methods, including ones inherited from an
    // abstract base class — Class#getMethod alone only finds public methods, so walk the
    // superclass chain looking for a declared method of any visibility (getDeclaredMethod isn't
    // inherited by itself; it only searches the exact class it's called on).
    private static Method findMethod(Class<?> testClass, String methodName) {
        for (Class<?> current = testClass; current != null; current = current.getSuperclass()) {
            Method declared = declaredMethodOrNull(current, methodName);
            if (declared != null) {
                return declared;
            }
        }
        return null;
    }

    private static Method declaredMethodOrNull(Class<?> declaringClass, String methodName) {
        try {
            return declaringClass.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException nsme) {
            return null;
        }
    }

    // Checks the annotation by fully-qualified name via reflection, rather than referencing the
    // annotation class directly, so a target classpath with only one of JUnit4/JUnit5 present
    // never risks a NoClassDefFoundError just from checking for the other.
    private static boolean hasAnnotation(Method method, String annotationClassName) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> annotationClass = (Class<? extends Annotation>)
                    Class.forName(annotationClassName, false, method.getDeclaringClass().getClassLoader());
            return method.isAnnotationPresent(annotationClass);
        } catch (ClassNotFoundException cnfe) {
            return false;
        }
    }

    private static int runJUnit4(Class<?> testClass, String methodName) {
        Result result;
        try {
            result = new JUnitCore().run(Request.method(testClass, methodName));
        } catch (NoClassDefFoundError ncdfe) {
            System.err.println("JUnit4 is not available on the test classpath: " + ncdfe.getMessage());
            return EXIT_CONFIG_ERROR;
        }

        if (!result.wasSuccessful()) {
            writeStackTraceIfRequested(result.getFailures().isEmpty() ? null
                    : result.getFailures().get(0).getTrace());
            return EXIT_FAILED;
        }
        return EXIT_PASSED;
    }

    private static int runJUnit5(Class<?> testClass, String methodName) {
        TestExecutionSummary summary;
        try {
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectMethod(testClass, methodName))
                    .build();
            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.registerTestExecutionListeners((TestExecutionListener) listener);
            launcher.execute(request);
            summary = listener.getSummary();
        } catch (NoClassDefFoundError ncdfe) {
            System.err.println("JUnit Platform is not available on the test classpath: " + ncdfe.getMessage());
            return EXIT_CONFIG_ERROR;
        }

        if (summary.getTestsFoundCount() == 0) {
            System.err.println("Could not find JUnit5 test method: " + testClass.getName() + "#" + methodName);
            return EXIT_CONFIG_ERROR;
        }

        if (summary.getTotalFailureCount() > 0) {
            String trace = null;
            if (!summary.getFailures().isEmpty() && summary.getFailures().get(0).getException() != null) {
                StringWriter sw = new StringWriter();
                summary.getFailures().get(0).getException().printStackTrace(new PrintWriter(sw));
                trace = sw.toString();
            }
            writeStackTraceIfRequested(trace);
            return EXIT_FAILED;
        }
        return EXIT_PASSED;
    }

    private static void writeStackTraceIfRequested(String trace) {
        String outputPath = System.getProperty("stackTraceOutputFile");
        if (outputPath == null || trace == null) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new java.io.FileWriter(outputPath))) {
            writer.print(trace);
        } catch (java.io.IOException ioe) {
            System.err.println("Failed to write stack trace output file: " + ioe.getMessage());
        }
    }
}
