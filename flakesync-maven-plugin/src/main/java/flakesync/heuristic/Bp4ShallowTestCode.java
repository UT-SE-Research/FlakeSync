package flakesync.heuristic;

/**
 * BP4: the line is in a test class (the test method or a helper of the test), not in production or library code.
 * This is a yes/no version. It does not count call-stack depth.
 */
public class Bp4ShallowTestCode implements Heuristic {
    @Override
    public String name() {
        return "BP4";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        return src != null && src.isTestFile();
    }
}
