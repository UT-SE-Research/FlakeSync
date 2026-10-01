package flakesync.heuristic;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BP2: the line uses a name that the critical point line writes.
 * This compares names only. It does not follow aliases (gm and grpcMetrics for the same object).
 */
public class Bp2ReadsCriticalWrite implements Heuristic {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");

    @Override
    public String name() {
        return "BP2";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        SourceFile critical = index.find(criticalLoc);
        if (src == null || critical == null) {
            return false;
        }
        Set<String> written = Writes.namesIn(critical.line(Candidate.lineOf(criticalLoc)));
        Matcher matcher = IDENTIFIER.matcher(src.line(Candidate.lineOf(cand.location)));
        while (matcher.find()) {
            if (written.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }
}
