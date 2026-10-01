package flakesync.heuristic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CP2: the line is inside a framework callback method or inside a synchronized block or method.
 */
public class Cp2FrameworkCallback implements Heuristic {
    private static final Set<String> CALLBACKS = new HashSet<String>(Arrays.asList(
            "run", "call", "beforeExecute", "afterExecute", "terminated", "onMessage", "onEvent", "onNext",
            "onComplete", "onError", "onSuccess", "onFailure", "channelRead", "channelRead0", "messageReceived",
            "handle", "handleMessage", "handleRequest", "accept", "completed", "failed", "uncaughtException"));

    @Override
    public String name() {
        return "CP2";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        if (src == null) {
            return false;
        }
        int ln = Candidate.lineOf(cand.location);
        if (CALLBACKS.contains(src.enclosingMethod(ln))) {
            return true;
        }
        List<String> headers = src.enclosingHeaders(ln);
        for (String header : headers) {
            if (header.trim().startsWith("synchronized") || header.contains(" synchronized ")) {
                return true;
            }
        }
        return false;
    }
}
