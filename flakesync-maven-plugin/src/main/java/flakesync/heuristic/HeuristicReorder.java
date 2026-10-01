package flakesync.heuristic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Replays a CandidateOrder.csv (made with -Dflakesync.exhaustive=true) under each heuristic.
 * Each heuristic is used alone: candidates that match get score 1, the others 0, then candidates are sorted
 * by score (highest first). Candidates with the same score keep their original order.
 * The output is one CSV line per (search list, heuristic) showing where the real point ends up.
 *
 * <p>Besides the single heuristics, the weighted-sum score used inside FlakeSync (see {@link CandidateScorer}) is
 * replayed as heuristic "SUM": CP1-CP3 weighted by their hit rates for critical point lists, BP1-BP4 for barrier lists.
 *
 * <p>Usage: HeuristicReorder --csv file-CandidateOrder.csv --src projectDir
 * [--heuristic CP1|CP2|CP3|BP1|BP2|BP3|BP4|SUM|ALL] [--score single|sum|both]
 * [--truth-cp Class#line] [--truth-bp Class#line]
 */
public class HeuristicReorder {
    private final Map<String, String> options;
    private final SourceIndex index;
    private final CandidateScorer scorer;
    private final List<Heuristic> heuristics = new ArrayList<Heuristic>();

    public HeuristicReorder(Map<String, String> options) throws IOException {
        this.options = options;
        this.index = new SourceIndex(new File(options.get("--src")));
        this.scorer = new CandidateScorer(index);
        heuristics.add(new Cp3ForkDownstream());
        heuristics.add(new Cp2FrameworkCallback());
        heuristics.add(new Cp1WriteToField());
        heuristics.add(new Bp3NearWait());
        heuristics.add(new Bp1NearAssertion());
        heuristics.add(new Bp4ShallowTestCode());
        heuristics.add(new Bp2ReadsCriticalWrite());
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = new HashMap<String, String>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        if (!options.containsKey("--csv") || !options.containsKey("--src")) {
            System.err.println("Usage: HeuristicReorder --csv <file-CandidateOrder.csv> --src <projectDir>"
                    + " [--heuristic CP1|CP2|CP3|BP1|BP2|BP3|BP4|SUM|ALL] [--score single|sum|both]"
                    + " [--truth-cp Class#line] [--truth-bp Class#line]");
            return;
        }
        new HeuristicReorder(options).run();
    }

    public void run() throws IOException {
        File csv = new File(options.get("--csv"));
        String test = csv.getName().replace("-CandidateOrder.csv", "");

        // Group the rows into search lists: one list per (phase, group).
        Map<String, List<Candidate>> lists = new LinkedHashMap<String, List<Candidate>>();
        Map<String, Set<String>> criticalPoints = new HashMap<String, Set<String>>();
        for (String text : Files.readAllLines(csv.toPath(), StandardCharsets.UTF_8)) {
            if (text.trim().isEmpty() || text.startsWith("phase,")) {
                continue;
            }
            Candidate row;
            try {
                row = new Candidate(text);
            } catch (RuntimeException ex) {
                System.err.println("Skipping bad line: " + text);
                continue;
            }
            if (row.phase.startsWith("delaylocs") || row.phase.startsWith("deltadebug")) {
                continue;   // Surefire-call log of the delaylocs and deltadebug steps, not a search list to replay
            }
            if ("crit-point".equals(row.phase)) {
                String key = "crit-line|" + row.group;
                if (!criticalPoints.containsKey(key)) {
                    criticalPoints.put(key, new HashSet<String>());
                }
                criticalPoints.get(key).add(row.location);
            } else {
                String key = row.phase + "|" + row.group;
                if (!lists.containsKey(key)) {
                    lists.put(key, new ArrayList<Candidate>());
                }
                lists.get(key).add(row);
            }
        }

        // FlakeSync logs the position in its original order in "order". When scoring was on during the search the rows
        // are written in the order they were tried, so put every list back in the original order first.
        for (List<Candidate> list : lists.values()) {
            Collections.sort(list, new Comparator<Candidate>() {
                @Override
                public int compare(Candidate first, Candidate second) {
                    return Integer.compare(first.order, second.order);
                }
            });
        }

        System.out.println("test,phase,group,list_size,heuristic,matched,selectivity_pct,orig_rank,new_rank,"
                + "best_rank,avg_rank,worst_rank,orig_runs,new_runs");
        for (Map.Entry<String, List<Candidate>> entry : lists.entrySet()) {
            List<Candidate> list = entry.getValue();
            String phase = list.get(0).phase;
            String group = list.get(0).group;
            boolean[] target = findTargets(phase, list, criticalPoints.get(entry.getKey()));
            if (!hasTrue(target)) {
                System.err.println("No real point found in " + entry.getKey() + ", skipped.");
                continue;
            }
            boolean isCritical = phase.startsWith("crit");
            String criticalLoc = isCritical ? "" : group;
            String wanted = options.containsKey("--heuristic") ? options.get("--heuristic") : "ALL";
            String mode = options.containsKey("--score") ? options.get("--score") : "both";
            if (!"sum".equalsIgnoreCase(mode)) {
                for (Heuristic heuristic : heuristics) {
                    boolean rightKind = heuristic.name().startsWith("CP") == isCritical;
                    if (rightKind && ("ALL".equalsIgnoreCase(wanted) || wanted.equalsIgnoreCase(heuristic.name()))) {
                        evaluate(test, list, target, heuristic, criticalLoc);
                    }
                }
            }
            if (!"single".equalsIgnoreCase(mode) && ("ALL".equalsIgnoreCase(wanted) || "SUM".equalsIgnoreCase(wanted))) {
                evaluateSum(test, list, target, isCritical, criticalLoc);
            }
        }
    }

    // Marks which candidates in the list are the real point.
    private boolean[] findTargets(String phase, List<Candidate> list, Set<String> criticalPoints) {
        boolean[] target = new boolean[list.size()];
        boolean isCritical = phase.startsWith("crit");
        String truth = options.get(isCritical ? "--truth-cp" : "--truth-bp");
        int lastRoot = -1;
        for (int i = 0; i < list.size(); i++) {
            Candidate cand = list.get(i);
            if (truth != null) {
                target[i] = isTruth(cand.location, truth);
            } else if ("crit-root".equals(phase)) {
                if ("FAIL_REPRODUCED".equals(cand.result)) {
                    lastRoot = i;
                }
            } else if ("crit-line".equals(phase)) {
                target[i] = criticalPoints != null && criticalPoints.contains(cand.location);
            } else {
                target[i] = "FOUND".equals(cand.result);
            }
        }
        if (lastRoot >= 0) {
            target[lastRoot] = true;
        }
        return target;
    }

    // truth looks like "GrpcServerTest#72" or "GrpcServerTest.java#72".
    private boolean isTruth(String location, String truth) {
        if (location.indexOf('#') < 0) {
            return false;
        }
        try {
            String name = Candidate.simpleNameOf(location) + "#" + Candidate.lineOf(location);
            return name.equals(truth.replace(".java", ""));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean hasTrue(boolean[] values) {
        for (boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private void evaluate(String test, List<Candidate> list, boolean[] target, Heuristic heuristic,
                          String criticalLoc) {
        double[] score = new double[list.size()];
        for (int i = 0; i < score.length; i++) {
            score[i] = heuristic.matches(list.get(i), criticalLoc, index) ? 1 : 0;
        }
        rank(test, list, target, heuristic.name(), score);
    }

    // The weighted sum of all heuristics of this kind (CP1-CP3 or BP1-BP4), as used inside FlakeSync.
    private void evaluateSum(String test, List<Candidate> list, boolean[] target, boolean isCritical,
                             String criticalLoc) {
        double[] score = new double[list.size()];
        for (int i = 0; i < score.length; i++) {
            score[i] = scorer.score(list.get(i), criticalLoc, isCritical);
        }
        rank(test, list, target, "SUM", score);
    }

    // Sorts by score (highest first, ties keep the original order) and prints where the real point ends up.
    private void rank(String test, List<Candidate> list, boolean[] target, String name, final double[] score) {
        int size = list.size();
        int matched = 0;
        for (int i = 0; i < size; i++) {
            if (score[i] > 0) {
                matched++;
            }
        }

        // Highest score first. Collections.sort is stable, so equal scores keep the original order.
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer first, Integer second) {
                return Double.compare(score[second], score[first]);
            }
        });

        int origRank = 0;
        int origRuns = 0;
        for (int i = 0; i < size && origRank == 0; i++) {
            origRuns += list.get(i).runs;
            if (target[i]) {
                origRank = i + 1;
            }
        }
        int newRank = 0;
        int newRuns = 0;
        for (int pos = 0; pos < size && newRank == 0; pos++) {
            newRuns += list.get(order.get(pos)).runs;
            if (target[order.get(pos)]) {
                newRank = pos + 1;
            }
        }

        // With ties the true position could be anywhere among the equal-score candidates.
        int found = order.get(newRank - 1);
        int better = 0;
        int tied = 0;
        for (int i = 0; i < size; i++) {
            if (score[i] > score[found]) {
                better++;
            } else if (score[i] == score[found]) {
                tied++;
            }
        }
        int best = better + 1;
        int worst = better + tied;

        Candidate first = list.get(0);
        System.out.println(String.format(Locale.ROOT, "%s,%s,%s,%d,%s,%d,%.1f,%d,%d,%d,%.1f,%d,%d,%d", test, first.phase,
                first.group, size, name, matched, 100.0 * matched / size, origRank, newRank, best,
                (best + worst) / 2.0, worst, origRuns, newRuns));
    }
}
