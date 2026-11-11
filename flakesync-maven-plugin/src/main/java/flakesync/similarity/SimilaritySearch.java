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

    public enum Verdict { SELF, MATCH, REJECT, UNREADABLE }

    /** Frames from the test framework and JDK: identical for every failure, so they carry no signal. */
    private static final String[] SCAFFOLDING = {"org.junit", "sun.reflect", "jdk.internal", "java.base",
        "java.lang.reflect", "java.lang.Thread", "java.util.", "org.apache.maven.surefire"};

    private final String stage;
    private final String baseDir;
    private final String testName;
    private final double threshold;
    private final Failure target;

    // in: ("DD", "<module dir>", "org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool")
    public SimilaritySearch(String stage, String baseDir, String testName) {
        this.stage = stage;
        this.baseDir = baseDir;
        this.testName = testName;
        this.threshold = Double.parseDouble(System.getProperty("flakesync.similarityThreshold", "0.9"));
        this.target = loadTarget();
        if (this.target == null) {
            // delaylocs writes the baseline; without it the stage stops here.
            throw new IllegalStateException("No baseline from delaylocs: " + targetFile());
        }
        System.out.println(stage + " BASELINE  " + shown(this.target.signature));
    }

    /** True unless this run failed in a clearly different way from the target. */
    public boolean accepts(String executionId) {
        // out: MATCH / SELF / UNREADABLE -> true;  REJECT -> false
        return judge(executionId) != Verdict.REJECT;
    }

    public Verdict judge(String executionId) {
        File reportDir = new File(String.valueOf(Constants.getExecutionDir(baseDir, executionId)));
        Failure failure = read(reportDir);
        if (failure == null) {
            // out: UNREADABLE  (the run passed: no <failure>/<error> in the report)
            return Verdict.UNREADABLE;
        }
        if (failure.selfInflicted()) {
            System.out.println(stage + " SELF    " + shown(failure.signature));
            return Verdict.SELF;
        }
        double score = jaccard(failure.signature, target.signature);
        Verdict verdict = score >= threshold ? Verdict.MATCH : Verdict.REJECT;
        System.out.printf("%s %-8s %.2f  %s%n", stage, verdict, score, shown(failure.signature));
        // out: MATCH 1.00 (same line) | REJECT 0.64 (@GrpcServerTest:92, other assertion) | REJECT 0.04 (RemotingException)
        return verdict;
    }

    /** The one failure Surefire recorded in a run's TEST-*.xml, or null if the run passed. */
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
    public static double jaccard(String one, String two) {
        Set<String> left = words(one);
        Set<String> right = words(two);
        Set<String> both = new HashSet<>(left);
        both.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) both.size() / union.size();
    }

    // in: "expected: &lt;0.0&gt; but was: &lt;1.0&gt;"    out: "expected: <n.n> but was: <n.n>"
    public static String normalize(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#10;", " ").replace("&#13;", " ").replace("&amp;", "&")
                .replaceAll("@[0-9a-fA-F]{4,}", "@H").replaceAll("\\d+", "N")
                .replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static boolean scaffolding(String frame) {
        for (String prefix : SCAFFOLDING) {
            if (frame.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Log lines carry the signature's head: type, message, test frame. The frames behind it are cut. */
    private static String shown(String signature) {
        return signature.length() <= 120 ? signature : signature.substring(0, 117) + "...";
    }

    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>(Arrays.asList(text.toLowerCase().split("[^a-z0-9$.:@]+")));
        out.remove("");
        return out;
    }

    private static String attribute(String tag, String name) {
        Matcher hit = Pattern.compile(name + "=\"([^\"]*)\"").matcher(tag);
        // out: "org.opentest4j.AssertionFailedError";  "" when the attribute is absent
        return hit.find() ? hit.group(1) : "";
    }

    private File targetFile() {
        return new File(String.valueOf(Constants.getTargetFailureFilepath(baseDir, testName)));
    }

    /** delaylocs calls this once, on its failing run: writes the baseline unless the failure was self-inflicted. */
    public static void saveBaseline(String baseDir, String testName, String executionId) {
        Failure failure = read(new File(String.valueOf(Constants.getExecutionDir(baseDir, executionId))));
        if (failure == null) {
            System.out.println("DELAY no baseline: no failure recorded in the report");
            return;
        }
        if (failure.selfInflicted()) {
            System.out.println("DELAY no baseline: self-inflicted  " + shown(failure.signature));
            return;
        }
        File file = new File(String.valueOf(Constants.getTargetFailureFilepath(baseDir, testName)));
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(failure.type);
            out.newLine();
            out.write(failure.frame);
            out.newLine();
            out.write(failure.message);
            out.newLine();
            out.write(failure.frames);
            out.newLine();

            System.out.println("DELAY BASELINE  " + shown(failure.signature));
        } catch (IOException ioe) {
            System.out.println("DELAY could not save baseline: " + ioe.getMessage());
        }
    }

    private Failure loadTarget() {
        File file = targetFile();
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String type = in.readLine();
            String frame = in.readLine();
            String message = in.readLine();
            String frames = in.readLine();
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
        public final String frames;
        public final String signature;

        Failure(String type, String message, String trace, String frame, String frames) {
            this.type = type;
            this.message = message;
            this.trace = trace;
            this.frame = frame;
            this.frames = frames;
            this.signature = type + ": " + message + " @" + frame + (frames.isEmpty() ? "" : " | " + frames);
        }

        public boolean selfInflicted() {
            return type.contains("TestTimedOutException")
                    || (type.contains("InterruptedException") && trace.contains("Utility.delay"))
                    || (type.contains("FileNotFoundException") && message.contains(".flakesync"));
        }
    }
}
