package flakesync.heuristic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores search candidates with a weighted sum of the heuristics (CP1-CP3 for critical points,
 * BP1-BP4 for barrier points). A candidate's score is the sum of the weights of the heuristics it matches.
 * Candidates are then tried highest score first. Equal scores keep FlakeSync's original order (stable sort).
 *
 * <p>The default weights are the hit rates measured on 20 flaky tests (FlakeSync_Heuristic_Summary.csv):
 * CP3 0.85, CP2 0.75, CP1 0.35, BP3 0.70, BP1 0.65, BP4 0.65, BP2 0.35.
 *
 * <p>System properties:
 * <ul>
 * <li>{@code -Dflakesync.heuristics=false} turns reordering off (original order, default is on)</li>
 * <li>{@code -Dflakesync.weight.CP3=0.9} overrides one weight (any of CP1, CP2, CP3, BP1, BP2, BP3, BP4)</li>
 * <li>{@code -Dflakesync.srcRoot=/path} folder searched for .java files (default is the module folder)</li>
 * </ul>
 */
public class CandidateScorer {
    public static final String PROP_ENABLED = "flakesync.heuristics";
    public static final String PROP_WEIGHT = "flakesync.weight.";
    public static final String PROP_SRC_ROOT = "flakesync.srcRoot";

    private static final Map<String, Double> DEFAULT_WEIGHTS = new LinkedHashMap<String, Double>();

    static {
        DEFAULT_WEIGHTS.put("CP3", 0.85);
        DEFAULT_WEIGHTS.put("CP2", 0.75);
        DEFAULT_WEIGHTS.put("CP1", 0.35);
        DEFAULT_WEIGHTS.put("BP3", 0.70);
        DEFAULT_WEIGHTS.put("BP1", 0.65);
        DEFAULT_WEIGHTS.put("BP4", 0.65);
        DEFAULT_WEIGHTS.put("BP2", 0.35);
    }

    private final SourceIndex index;    // null means scoring is off
    private final List<Heuristic> critical = new ArrayList<Heuristic>();
    private final List<Heuristic> barrier = new ArrayList<Heuristic>();
    private final Map<String, Double> weights = new LinkedHashMap<String, Double>();

    public CandidateScorer(SourceIndex index) {
        this.index = index;
        critical.add(new Cp3ForkDownstream());
        critical.add(new Cp2FrameworkCallback());
        critical.add(new Cp1WriteToField());
        barrier.add(new Bp3NearWait());
        barrier.add(new Bp1NearAssertion());
        barrier.add(new Bp4ShallowTestCode());
        barrier.add(new Bp2ReadsCriticalWrite());
        for (Map.Entry<String, Double> entry : DEFAULT_WEIGHTS.entrySet()) {
            weights.put(entry.getKey(), readWeight(entry.getKey(), entry.getValue()));
        }
    }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROP_ENABLED, "true"));
    }

    /**
     * Makes a scorer for a project. If scoring is turned off, or the sources cannot be read, the scorer keeps
     * the original order, so a search never fails because of the heuristics.
     */
    public static CandidateScorer forProject(File moduleDir) {
        if (!enabled()) {
            return new CandidateScorer(null);
        }
        try {
            String root = System.getProperty(PROP_SRC_ROOT);
            return new CandidateScorer(new SourceIndex(root == null ? moduleDir : new File(root)));
        } catch (IOException | RuntimeException ex) {
            System.err.println("[FlakeSync heuristics] cannot read sources, keeping original order: " + ex);
            return new CandidateScorer(null);
        }
    }

    public boolean isOn() {
        return index != null;
    }

    public double weight(String heuristicName) {
        return weights.get(heuristicName);
    }

    public String describe() {
        return isOn() ? "Heuristic candidate ordering ON, weights " + weights
                : "Heuristic candidate ordering OFF (original order)";
    }

    // Score of a critical point candidate (CP1-CP3). criticalLoc is not needed for these.
    public double critScore(Candidate cand) {
        return sum(critical, cand, "");
    }

    // Score of a barrier candidate (BP1-BP4). criticalLoc is the critical point "class#line".
    public double barrierScore(Candidate cand, String criticalLoc) {
        return sum(barrier, cand, criticalLoc);
    }

    // Score used by the offline replay: pick the CP or BP set by the kind of list.
    public double score(Candidate cand, String criticalLoc, boolean criticalList) {
        return criticalList ? critScore(cand) : barrierScore(cand, criticalLoc);
    }

    private double sum(List<Heuristic> heuristics, Candidate cand, String criticalLoc) {
        double total = 0;
        for (Heuristic heuristic : heuristics) {
            if (heuristic.matches(cand, criticalLoc, index)) {
                total += weights.get(heuristic.name());
            }
        }
        // Rounded so that equal sums (for example BP1 alone and BP4 alone) are exact ties.
        return Math.round(total * 1e6) / 1e6;
    }

    /**
     * Orders the lines of one class that FlakeSync is about to try as barrier points.
     *
     * @param className the class of the lines, in the same form FlakeSync uses (org/foo/Bar or org.foo.Bar)
     * @param lines the lines in FlakeSync's original order
     * @param criticalLoc the critical point "class#line" these barrier points belong to
     */
    public Plan planBarrierLines(String className, List<Integer> lines, String criticalLoc) {
        if (!isOn() || lines.size() < 2) {
            return Plan.original(lines);
        }
        final double[] score = new double[lines.size()];
        List<Integer> position = new ArrayList<Integer>();
        for (int i = 0; i < lines.size(); i++) {
            score[i] = barrierScore(new Candidate("barrier", criticalLoc, i + 1, className + "#" + lines.get(i),
                    "", 0), criticalLoc);
            position.add(i);
        }
        // Highest score first. Collections.sort is stable, so equal scores keep the original order.
        Collections.sort(position, new Comparator<Integer>() {
            @Override
            public int compare(Integer first, Integer second) {
                return Double.compare(score[second], score[first]);
            }
        });
        int[] order = new int[lines.size()];
        int[] original = new int[lines.size()];
        boolean changed = false;
        for (int k = 0; k < order.length; k++) {
            order[k] = lines.get(position.get(k));
            original[k] = position.get(k) + 1;
            changed |= position.get(k) != k;
        }
        return new Plan(order, original, changed);
    }

    /**
     * Orders the locations FlakeSync delays together in the delta debugging step, so the likeliest critical point
     * locations come first. Delta debugging cuts the list into contiguous chunks, so having the likely locations
     * next to each other lets one chunk (or its complement) hold them, which needs fewer Surefire runs.
     * Uses the critical point heuristics (CP1-CP3). Equal scores keep the original order (stable sort).
     * Never fails: if scoring is off, or anything goes wrong, the original order is returned.
     *
     * @param locations locations such as "org/foo/Bar#42" in FlakeSync's original order
     */
    public LocationOrder orderCriticalLocations(List<String> locations) {
        int size = locations.size();
        if (!isOn() || size < 2) {
            return new LocationOrder(new ArrayList<String>(locations), new double[size], false);
        }
        try {
            final double[] score = new double[size];
            List<Integer> position = new ArrayList<Integer>();
            for (int i = 0; i < size; i++) {
                score[i] = critScore(new Candidate("crit-location", "", i + 1, locations.get(i), "", 0));
                position.add(i);
            }
            // Highest score first. Collections.sort is stable, so equal scores keep the original order.
            Collections.sort(position, new Comparator<Integer>() {
                @Override
                public int compare(Integer first, Integer second) {
                    return Double.compare(score[first], score[second]);
                }
            });
            List<String> ordered = new ArrayList<String>();
            double[] orderedScore = new double[size];
            boolean changed = false;
            for (int k = 0; k < size; k++) {
                ordered.add(locations.get(position.get(k)));
                orderedScore[k] = score[position.get(k)];
                changed |= position.get(k) != k;
            }
            return new LocationOrder(ordered, orderedScore, changed);
        } catch (RuntimeException ex) {
            System.err.println("[FlakeSync heuristics] cannot score locations, keeping original order: " + ex);
            return new LocationOrder(new ArrayList<String>(locations), new double[size], false);
        }
    }

    private static double readWeight(String name, double fallback) {
        String text = System.getProperty(PROP_WEIGHT + name);
        if (text == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            System.err.println("[FlakeSync heuristics] bad value for " + PROP_WEIGHT + name + ": " + text);
            return fallback;
        }
    }

    /**
     * A list of locations in the order they should be given to delta debugging. {@code scores.get(k)} is the
     * critical point score of {@code locations.get(k)}; {@code reordered} is false when the order is unchanged.
     */
    public static final class LocationOrder {
        public final List<String> locations;
        public final double[] scores;
        public final boolean reordered;

        private LocationOrder(List<String> locations, double[] scores, boolean reordered) {
            this.locations = locations;
            this.scores = scores;
            this.reordered = reordered;
        }
    }

    /**
     * The order in which to try a list of lines. {@code line(k)} is the k-th line to try, and
     * {@code originalPosition(k)} is its 1-based position in FlakeSync's original order (used for the log).
     */
    public static final class Plan {
        private final int[] lines;
        private final int[] originalPositions;
        private final boolean reordered;

        private Plan(int[] lines, int[] originalPositions, boolean reordered) {
            this.lines = lines;
            this.originalPositions = originalPositions;
            this.reordered = reordered;
        }

        static Plan original(List<Integer> list) {
            int[] lines = new int[list.size()];
            int[] positions = new int[list.size()];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = list.get(i);
                positions[i] = i + 1;
            }
            return new Plan(lines, positions, false);
        }

        public int size() {
            return lines.length;
        }

        public int line(int index) {
            return lines[index];
        }

        public int originalPosition(int index) {
            return originalPositions[index];
        }

        // False when the order is unchanged, so callers can keep their original bookkeeping.
        public boolean reordered() {
            return reordered;
        }
    }
}