package flakesync.heuristic;

/**
 * A yes/no check on one search candidate. A match adds +1 to the candidate's score.
 */
public interface Heuristic {
    // CP1, CP2, CP3, BP1, BP2, BP3 or BP4.
    String name();

    // criticalLoc is the critical point ("class#line") when ranking barrier candidates, otherwise "".
    boolean matches(Candidate cand, String criticalLoc, SourceIndex index);
}
