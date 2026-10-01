package flakesync.heuristic;

import java.util.regex.Pattern;

/**
 * BP1: the line is in a test file and an assertion is within 5 lines of it.
 */
public class Bp1NearAssertion implements Heuristic {
    private static final int RADIUS = 5;
    private static final Pattern ASSERTION = Pattern.compile("\\b(assert\\w*|verify|fail|expect)\\s*\\("
            + "|\\.(isEqualTo|isTrue|isFalse|isNull|isNotNull|hasSize)\\s*\\(");

    @Override
    public String name() {
        return "BP1";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        return src != null && src.isTestFile() && src.hasNear(Candidate.lineOf(cand.location), ASSERTION, RADIUS);
    }
}
