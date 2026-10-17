package flakesync.similarity;

import flakesync.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a failed test run failed the same way as the stage's first real failure.
 *
 * Any stage asks {@link #accepts(String)} after a run fails. The first real failure becomes the
 * target and is written to {@code .flakesync/<test>-TargetFailure.txt}, so later stages inherit it.
 * Failures the injected delay caused itself are never the target and always count, as before.
 * Threshold: {@code -Dflakesync.similarityThreshold} (default 0.8); 0 accepts everything.
 */
public final class SimilaritySearch {

    public enum Verdict { SELF, TARGET, MATCH, SAMETEST, REJECT, UNREADABLE }

    /** Frames from the test framework and JDK: identical for every failure, so they carry no signal. */
    private static final String[] SCAFFOLDING = {"org.junit", "sun.reflect", "jdk.internal", "java.base",
        "java.lang.reflect", "java.lang.Thread", "java.util.", "org.apache.maven.surefire"};

    private final String stage;
    private final String baseDir;
    private final String testName;
    private final double threshold;
    private Failure target;

    // in: ("DD", "<module dir>", "org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool")
    public SimilaritySearch(String stage, String baseDir, String testName) {
        this.stage = stage;
        this.baseDir = baseDir;
        this.testName = testName;
        this.threshold = Double.parseDouble(System.getProperty("flakesync.similarityThreshold", "0.8"));
        this.target = loadTarget();
        if (this.target != null) {
            System.out.println(stage + " TARGET(loaded)  " + shown(this.target.signature));
            // out (log): DD TARGET(loaded)  org.opentest4j.AssertionFailedError: expected: <n.n> but was: <n.n>
            //    @GrpcServerTest:90 | ...
        }
    }

    /** True unless this run failed in a clearly different way from the target. */
    // in: executionId "clean_3+EGtp3wuz0wHAV2BajvRMH8u3whjf2O3O4d8tCJBY="
    public boolean accepts(String executionId) {
        // out: MATCH / SAMETEST / TARGET / SELF / UNREADABLE -> true;  REJECT -> false
        return judge(executionId) != Verdict.REJECT;
    }

    // in: executionId "clean_3+EGtp3w..."  whose .flakesync/clean_.../TEST-*.xml holds this run's failure
    public Verdict judge(String executionId) {
        File reportDir = new File(String.valueOf(Constants.getExecutionDir(baseDir, executionId)));
        Failure failure = read(reportDir);
        if (failure == null) {
            // out: UNREADABLE  (the run passed: no <failure>/<error> in the report)
            return Verdict.UNREADABLE;
        }
        if (failure.selfInflicted()) {
            System.out.println(stage + " SELF    " + shown(failure.signature));
            // out: SELF  e.g. "org.junit...TestTimedOutException: test timed out after n seconds @CompatibilityTest:167"
            return Verdict.SELF;
        }
        if (target == null) {
            target = failure;
            saveTarget(failure);
            System.out.println(stage + " TARGET  " + shown(failure.signature));
            // out: TARGET  e.g. "org.opentest4j.AssertionFailedError: expected: <n.n> but was: <n.n> @GrpcServerTest:90
            //    | at ..."
            return Verdict.TARGET;
        }
        double score = jaccard(failure.signature, target.signature);
        // Same exception in the same test class is the same test failing, whichever
        // assertion caught it; the message only says which check came first.
        boolean sameTest = failure.type.equals(target.type)
                && !failure.frameClass.equals("none") && failure.frameClass.equals(target.frameClass);
        Verdict verdict = score >= threshold ? Verdict.MATCH : sameTest ? Verdict.SAMETEST : Verdict.REJECT;
        System.out.printf("%s %-8s %.2f  %s%n", stage, verdict, score, shown(failure.signature));
        // out: MATCH 1.00 (same line) | SAMETEST 0.64 (@GrpcServerTest:92, same test) | REJECT 0.04 (a RemotingException)
        return verdict;
    }

    // out: the Failure adopted at TARGET (or loaded from the handoff file); null before the first real failure
    public Failure target() {
        // out: Failure{type "org.opentest4j.AssertionFailedError", frame "GrpcServerTest:90", ...} or null
        return target;
    }

    /** The one failure Surefire recorded in a run's TEST-*.xml, or null if the run passed. */
    // in: reportDir ".flakesync/clean_3+EGtp3w.../"  containing TEST-org.apache.uniffle.common.rpc.GrpcServerTest.xml
    public static Failure read(File reportDir) {
        File[] files = reportDir.listFiles();
        if (files == null) {
            // out: null  (directory missing)
            return null;
        }
        Pattern element = Pattern.compile("<(?:failure|error)[^>]*>");
        Pattern frame = Pattern.compile("at (\\S+)\\.\\w+\\((\\w*Test\\w*)\\.java:(\\d+)\\)");
        for (File file : files) {
            if (!file.getName().startsWith("TEST-") || !file.getName().endsWith(".xml")) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String type = null;
                String message = "";
                String where = "none";
                StringBuilder trace = new StringBuilder();
                List<String> kept = new ArrayList<>();
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (type == null) {
                        Matcher hit = element.matcher(line);
                        if (!hit.find()) {
                            continue;
                        }
                        type = attribute(hit.group(), "type");
                        message = attribute(hit.group(), "message");
                    }
                    if (line.contains("</failure>") || line.contains("</error>")) {
                        break;
                    }
                    trace.append(line).append('\n');
                    String at = line.trim();
                    if (at.startsWith("at ") && !scaffolding(at)) {
                        kept.add(at);
                    }
                    Matcher hit = frame.matcher(line);
                    if (where.equals("none") && hit.find()) {
                        where = hit.group(2) + ":" + hit.group(3);
                    }
                }
                // out: Failure(type "org.opentest4j.AssertionFailedError", message "expected: <n.n> but was: <n.n>",
                //    frame "GrpcServerTest:90")
                return type == null ? null
                        : new Failure(type, normalize(message), trace.toString(), where, String.join(" | ", kept));
            } catch (IOException ioe) {
                // out: null  (report unreadable)
                return null;
            }
        }
        // out: null  (no TEST-*.xml here: the run passed)
        return null;
    }

    /** Jaccard index over the two signatures' word sets. */
    // in: ("...AssertionFailedError: expected: <n.n> but was: <n.n> @GrpcServerTest:90 | ...", the same but
    //    @GrpcServerTest:92)
    public static double jaccard(String one, String two) {
        Set<String> left = words(one);
        Set<String> right = words(two);
        Set<String> both = new HashSet<>(left);
        both.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        // out: 1.00 identical | 0.64 uniffle :90 vs :92 | 0.38 db-scheduler :87 vs :88 | 0.04 AssertionError vs
        //    RemotingException
        return union.isEmpty() ? 0.0 : (double) both.size() / union.size();
    }

    // in: "expected: &lt;0.0&gt; but was: &lt;1.0&gt;"    out: "expected: <n.n> but was: <n.n>"
    public static String normalize(String text) {
        // out: "expected: <n.n> but was: <n.n>"
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#10;", " ").replace("&#13;", " ").replace("&amp;", "&")
                .replaceAll("@[0-9a-fA-F]{4,}", "@H").replaceAll("\\d+", "N")
                .replaceAll("\\s+", " ").trim().toLowerCase();
    }

    // in: "at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:70)" -> true;  "at
    //    ...GrpcServerTest...(GrpcServerTest.java:90)" -> false
    private static boolean scaffolding(String frame) {
        for (String prefix : SCAFFOLDING) {
            if (frame.contains(prefix)) {
                // out: true  (e.g. the frame is under org.junit)
                return true;
            }
        }
        // out: false  (an application frame, e.g. ...GrpcServerTest.testGrpcExecutorPool(GrpcServerTest.java:90))
        return false;
    }

    /** Log lines carry the signature's head: type, message, test frame. The frames behind it are cut. */
    // in: a 300-char signature    out: its first 117 chars + "..."
    private static String shown(String signature) {
        // out: "org.opentest4j.AssertionFailedError: expected: <n.n> but was: <n.n> @GrpcServerTest:90 | at
        //    org.apache.un..."
        return signature.length() <= 120 ? signature : signature.substring(0, 117) + "...";
    }

    // in: "java.lang.AssertionError: expected: is <n> but: was <n> @CompatibilityTest:87"
    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>(Arrays.asList(text.toLowerCase().split("[^a-z0-9$.:@]+")));
        out.remove("");
        // out: {java.lang.assertionerror:, expected:, is, n, but:, was, @compatibilitytest:87}
        return out;
    }

    // in: ('<failure message="expected: &lt;0.0&gt; but was: &lt;1.0&gt;" type="org.opentest4j.AssertionFailedError">',
    //    "type")
    private static String attribute(String tag, String name) {
        Matcher hit = Pattern.compile(name + "=\"([^\"]*)\"").matcher(tag);
        // out: "org.opentest4j.AssertionFailedError";  "" when the attribute is absent
        return hit.find() ? hit.group(1) : "";
    }

    // out: <module>/.flakesync/org.apache.uniffle.common.rpc.GrpcServerTest.testGrpcExecutorPool-TargetFailure.txt
    private File targetFile() {
        // out: <module>/.flakesync/org.apache.uniffle.common.rpc.GrpcServerTest.testGrpcExecutorPool-TargetFailure.txt
        return new File(String.valueOf(Constants.getTargetFailureFilepath(baseDir, testName)));
    }

    // in: the adopted Failure.  Writes 4 lines: type / frame / message / frames  (see loadTarget for the sample)
    private void saveTarget(Failure failure) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(targetFile()))) {
            out.write(failure.type);
            out.newLine();
            out.write(failure.frame);
            out.newLine();
            out.write(failure.message);
            out.newLine();
            out.write(failure.frames);
            out.newLine();
        } catch (IOException ioe) {
            System.out.println(stage + " could not save target: " + ioe.getMessage());
        }
    }

    // in: the 4-line file: "org.opentest4j.AssertionFailedError" / "GrpcServerTest:90" / "expected: <n.n> but was:
    //    <n.n>" / "at ..."
    private Failure loadTarget() {
        File file = targetFile();
        if (!file.exists()) {
            // out: null  (no earlier stage wrote a baseline; delta debug will learn one)
            return null;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String type = in.readLine();
            String frame = in.readLine();
            String message = in.readLine();
            String frames = in.readLine();
            // out: the same Failure delta debug adopted, rebuilt from the file; trace is "" (not stored, not needed again)
            return type == null || frame == null ? null
                    : new Failure(type, message == null ? "" : message, "", frame, frames == null ? "" : frames);
        } catch (IOException ioe) {
            // out: null  (file unreadable)
            return null;
        }
    }

    /** One recorded failure: type, normalized message, trace, the test-class frame, and the application frames. */
    public static final class Failure {
        public final String type;
        public final String message;
        public final String trace;
        public final String frame;
        public final String frameClass;
        public final String frames;
        public final String signature;

        // in: ("org.opentest4j.AssertionFailedError", "expected: <n.n> but was: <n.n>", <trace>, "GrpcServerTest:90",
        //    "at ...:90)")
        Failure(String type, String message, String trace, String frame, String frames) {
            this.type = type;
            this.message = message;
            this.trace = trace;
            this.frame = frame;
            this.frameClass = frame.contains(":") ? frame.substring(0, frame.indexOf(':')) : frame;
            this.frames = frames;
            this.signature = type + ": " + message + " @" + frame + (frames.isEmpty() ? "" : " | " + frames);
            // signature: "org.opentest4j.AssertionFailedError: expected: <n.n> but was: <n.n> @GrpcServerTest:90 | at
            //    ...GrpcServerTest.java:90)"
        }

        /** The delay did this, not the subject: a timeout, our own sleep interrupted, our own file missing. */
        // out: true for "TestTimedOutException: test timed out after n seconds";  false for an AssertionError from the test
        public boolean selfInflicted() {
            // out: true  for "org.junit.runners.model.TestTimedOutException";  false for
            //    "org.opentest4j.AssertionFailedError"
            return type.contains("TestTimedOutException")
                    || (type.contains("InterruptedException") && trace.contains("Utility.delay"))
                    || (type.contains("FileNotFoundException") && message.contains(".flakesync"));
        }
    }
}
