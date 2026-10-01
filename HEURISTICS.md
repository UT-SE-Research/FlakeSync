# Heuristic reordering (offline replay)

New code is in `flakesync-maven-plugin/src/main/java/flakesync/heuristic/`.
The heuristics are used in two ways: offline, on the log FlakeSync writes (sections below), and inside the barrier
point search, where a weighted score reorders the candidates before they are tried (last section).

## How to use

1. Run the search as before, with the barrier search in exhaustive mode so every candidate gets a result:
   `... barrierpointsearch -Dflakesync.exhaustive=true`
   This writes `.flakesync/<test>-CandidateOrder.csv` (from `SearchLogger`).
2. Build: `mvn clean install -DskipTests`
3. Replay (all heuristics, each one alone):
   `testscripts/run-heuristics.sh <test>-CandidateOrder.csv <projectDir> > result.csv`
   `<projectDir>` is the checked-out project of the flaky test (main and test sources are both searched).

Options: `--heuristic CP1|CP2|CP3|BP1|BP2|BP3|BP4|ALL`, `--truth-cp Class#line`, `--truth-bp Class#line`
(use the last two to give the real point yourself, for example `GrpcServerTest#72`).

## What is a "real point"

- crit-root: the last frame that still reproduced the failure
- crit-line: the line logged as `CRITICAL`
- barrier-test / barrier-frame: the candidate(s) logged as `FOUND`

## How candidates are scored

Each heuristic is used alone. A candidate that matches gets score 1, others 0. Candidates are sorted by score,
highest first. Equal scores keep the original order (stable sort).

## Output columns

`orig_rank` is the position of the real point in FlakeSync's order. `new_rank` is its position after sorting.
`best_rank`, `avg_rank`, `worst_rank` cover ties: the real point could be anywhere among candidates with the same score.
`selectivity_pct` is the percentage of candidates that match (a heuristic that matches everything cannot help).
`orig_runs`, `new_runs` are the Surefire runs spent before reaching the real point.

## Which heuristic applies to which list

| Heuristic | Lists | Check (simple, one source file, text based) |
|---|---|---|
| CP3 | crit-root, crit-line | inside a block opened by submit/execute/schedule/new Thread/..., or run()/call() of a Runnable/Callable/Thread class |
| CP2 | crit-root, crit-line | enclosing method is a known callback (run, call, beforeExecute, ...), or inside a synchronized block or method |
| CP1 | crit-root, crit-line | line assigns to a name or calls a mutator (set, put, add, inc, ...) |
| BP3 | barrier-* | sleep/await/join/poll/... within 5 lines |
| BP1 | barrier-* | test file, and an assertion within 5 lines |
| BP4 | barrier-* | line is in a test class |
| BP2 | barrier-* | line uses a name that the critical point line writes |

## Known limits

- CP1 checks only the write half. The barrier is not known while the critical point is searched.
- BP2 compares names only, so an alias (`gm` and `grpcMetrics`) is missed.
- CP3 does not follow calls between files. If the source is not in the project, CP3 falls back to the class name.
- BP4 is yes/no. It does not count call-stack depth.
- Blank and comment lines are candidates in FlakeSync's barrier search and count in the ranks.
- Reordering only changes the order. Every candidate is still tried, so no repair is lost.

## Scoring inside FlakeSync (barrier point search)

`barrierpointsearch` now tries the likeliest lines first. `CandidateScorer` gives each candidate line a score, the
sum of the weights of the barrier heuristics it matches, and the lines are tried highest score first. Lines with the
same score keep FlakeSync's original order.

| Heuristic | Weight | Heuristic | Weight |
|---|---|---|---|
| BP3 near wait | 0.70 | BP4 test code | 0.65 |
| BP1 near assertion | 0.65 | BP2 reads critical write | 0.35 |

`CandidateScorer` also has `critScore` (CP3 0.85, CP2 0.75, CP1 0.35). It is used by the offline replay and by the
`deltadebug` step (see below), not by `critsearch`. The weights are the hit rates from `FlakeSync_Heuristic_Summary.csv` (20 flaky tests).

Where the order is applied: the lines of the test method after the critical point, and, for each stack frame, the
lines from the frame line up to the start of the method. Frames are still visited in the original order.

Options (system properties):

- `-Dflakesync.heuristics=false` keeps the original order (default is on).
- `-Dflakesync.weight.BP3=0.9` changes one weight.
- `-Dflakesync.srcRoot=<dir>` sets where `.java` files are searched (default is the module folder). A class whose
  source is not found scores 0, so its lines stay in the original order.

Notes:

- The barrier point that is reported can differ from the original run. FlakeSync used to stop at the first working
  line in its own order. Now it stops at the first working line in score order. Both are checked the same way.
- `CandidateOrder.csv` still records `order` as the position in FlakeSync's original order, but rows are written in
  the order the lines were tried. `HeuristicReorder` sorts each list by `order` before replaying, so old and new
  logs replay the same way.
- `critsearch` is not reordered. Its root search walks the call stack outward and stops at the first frame where
  the failure is no longer reproduced, so the order is part of the algorithm. Its line scan runs every line of the
  root method to find all failing regions, so a different order would not save any runs.

## Delta debugging order (deltadebug step)

`deltadebug` puts the locations from `Locations.txt` in `critScore` order (highest first) before the first delta
debugging run. Delta debugging cuts the list into contiguous chunks and runs the test once per chunk or complement, so
when the locations that matter sit next to each other, fewer chunks need to be tried. Equal scores keep the original
order. `-Dflakesync.heuristics=false`, `-Dflakesync.weight.CPn` and `-Dflakesync.srcRoot` work as for the barrier search.
A location whose source is not found scores 0 (CP3 falls back to the class name).

The minimized location set can differ from the original run, because delta debugging returns one minimal set, not
the only one. Check that `critsearch` still finds the same critical points.

## Surefire runs of delaylocs and deltadebug

Both steps write to `CandidateOrder.csv` (columns as above), so their Surefire runs can be counted with and without
the ordering:

| phase | group | order | candidate | result | runs |
|---|---|---|---|---|---|
| `delaylocs` | `delay-all` | 1, 2, ... | `delay=<ms>` | `PASS`, `FAIL` (the loop stops) or `ERROR` | 1 |
| `delaylocs-total` | `all` | 0 | `delays_tried=<n>` | `TOTAL` | Surefire runs of the step |
| `deltadebug` | `level<k>-n<size>-g<granularity>` | run number | `complement[<size>]@delay=<ms>` or `chunk[...]` | `REPRODUCED` or `NOT_REPRODUCED` | 1 |
| `deltadebug-order` | `initial` | new position | location | `score=<CP sum>` | 0 (only when the order changed) |
| `deltadebug-total` | `all` | 0 | locations before->after, iterations, ordering ON/OFF, final delay | `TOTAL` | Surefire runs of the step |

To compare, run `deltadebug` once with `-Dflakesync.heuristics=false` and once without, and compare the `runs` value of
the `deltadebug-total` rows. `HeuristicReorder` skips these phases.

## Replaying the combined score

`HeuristicReorder` also replays the same weighted sum as heuristic `SUM` (CP weights for critical point lists, BP
weights for barrier lists). `--score single|sum|both` chooses what is printed (default `both`), and
`--heuristic SUM` prints only the combined score.

