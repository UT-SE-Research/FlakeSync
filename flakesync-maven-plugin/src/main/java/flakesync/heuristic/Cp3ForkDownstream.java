package flakesync.heuristic;

import java.util.regex.Pattern;

/**
 * CP3: the line is downstream of a fork point.
 * Static approximation inside one file: the line sits in a block opened by a fork call
 * (submit, execute, schedule, new Thread, ...) or inside run()/call() of a Runnable/Callable/Thread class.
 * If the source is not available (library code), the class name is used instead.
 */
public class Cp3ForkDownstream implements Heuristic {
    private static final Pattern FORK = Pattern.compile("\\.(submit|execute|schedule\\w*|invokeAll|invokeAny|runAsync"
            + "|supplyAsync|start)\\s*\\(|new\\s+Thread\\s*\\(|new\\s+(Runnable|Callable)\\b");
    private static final Pattern RUNNABLE_CLASS = Pattern.compile("implements\\s+[^{]*\\b(Runnable|Callable)\\b"
            + "|extends\\s+Thread\\b");
    private static final Pattern FORK_CLASS_NAME = Pattern.compile("Lambda|Thread|Executor|Runnable|Callable|FutureTask");

    @Override
    public String name() {
        return "CP3";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        if (src == null) {
            return FORK_CLASS_NAME.matcher(cand.location).find();
        }
        int ln = Candidate.lineOf(cand.location);
        for (String header : src.enclosingHeaders(ln)) {
            if (FORK.matcher(header).find()) {
                return true;
            }
        }
        String method = src.enclosingMethod(ln);
        return ("run".equals(method) || "call".equals(method)) && src.hasAnyLine(RUNNABLE_CLASS);
    }
}
