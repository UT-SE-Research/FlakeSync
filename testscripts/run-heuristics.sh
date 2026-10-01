#!/bin/bash
# Replays a CandidateOrder.csv under each heuristic (see HEURISTICS.md).
# Usage: testscripts/run-heuristics.sh <file-CandidateOrder.csv> <projectDir> [extra options]
# Build first: mvn clean install -DskipTests
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CSV="$1"
SRC="$2"
shift 2
java -cp "$ROOT/flakesync-maven-plugin/target/classes" flakesync.heuristic.HeuristicReorder --csv "$CSV" --src "$SRC" "$@"
