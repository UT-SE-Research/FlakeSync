package flakesync.heuristic;

import java.util.regex.Pattern;

/**
 * BP3: a wait, sleep or polling call is within 5 lines of the line.
 */
public class Bp3NearWait implements Heuristic {
    private static final int RADIUS = 5;
    private static final Pattern WAIT = Pattern.compile("sleep\\s*\\(|\\.await\\w*\\s*\\(|awaitAsync|waitFor\\w*\\s*\\("
            + "|\\.wait\\s*\\(|\\.join\\s*\\(|\\.poll\\s*\\(|\\.take\\s*\\(|\\.get\\s*\\(\\s*\\d|\\.tryAcquire\\s*\\("
            + "|\\.acquire\\s*\\(|Awaitility");

    @Override
    public String name() {
        return "BP3";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        return src != null && src.hasNear(Candidate.lineOf(cand.location), WAIT, RADIUS);
    }
}
