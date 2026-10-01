package flakesync.heuristic;

/**
 * CP1: the critical point writes a field or shared object.
 * The full CP1 also needs the barrier point to compare with, but the barrier is searched after the
 * critical point. So this checks only the write half: the line assigns to something or calls a mutator.
 */
public class Cp1WriteToField implements Heuristic {
    @Override
    public String name() {
        return "CP1";
    }

    @Override
    public boolean matches(Candidate cand, String criticalLoc, SourceIndex index) {
        SourceFile src = index.find(cand.location);
        if (src == null) {
            return false;
        }
        return !Writes.namesIn(src.line(Candidate.lineOf(cand.location))).isEmpty();
    }
}
