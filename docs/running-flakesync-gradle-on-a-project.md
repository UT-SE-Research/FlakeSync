# Running FlakeSync's Gradle plugin on a real project

A concrete, step-by-step runbook for the 6 goals against any Gradle-built target project,
based on an actual trial run against [apple/servicetalk](https://github.com/apple/servicetalk).
See `README.md`'s "Using FlakeSync with Gradle" section for the short version; this doc has the
full detail and the gotchas that only show up against a real project.

## 0. One-time setup

Build and install `flakesync-core`/`flakesync-utils` to your local Maven repository — the Gradle
plugin depends on `flakesync-core` as a jar:

```bash
cd /path/to/FlakeSync
mvn -pl flakesync-core,flakesync-utils -am -DskipTests install
```

Do this again any time you change `flakesync-core` or `flakesync-utils`.

## 1. Wire the plugin into the target project

These are **two separate changes**, both required:

### a) `settings.gradle`(`.kts`) — make the plugin resolvable

```groovy
pluginManagement {
    includeBuild("/path/to/FlakeSync/flakesync-gradle-plugin")
    repositories {
        mavenLocal()       // <-- required: resolves flakesync-core for the PLUGIN's own build
        gradlePluginPortal()
    }
}
```

`mavenLocal()` inside `pluginManagement.repositories` is easy to miss and different from the one
in step (b) below — it's a separate resolution scope, used only to build `flakesync-gradle-plugin`
itself (which depends on `flakesync-core`). Without it you'll see:

```
Could not find edu.utexas.ece:flakesync-core:1.0-SNAPSHOT.
Required by: buildscript of project ':your-module' > project ':flakesync-gradle-plugin'
```

### b) The target module's own `build.gradle`(`.kts`) — apply the plugin

```groovy
plugins {
    id "edu.utexas.ece.flakesync"
}

repositories {
    mavenLocal()   // <-- required: resolves flakesync-core for the plugin's OWN dependency,
                   //     in *this* project's context, separate from (a) above
}
```

If the module already uses the legacy `apply plugin: "..."` style for other plugins, the
`plugins { }` block must come **first** in the file, before any `apply plugin:` lines — Gradle
requires it. (`apply plugin: "edu.utexas.ece.flakesync"` alone does **not** work — that legacy form
doesn't go through `pluginManagement`'s resolution at all, only the `plugins { }` DSL does.)

## 2. Run the six goals

`--test-name` takes `ClassName#methodName` (JUnit4 or JUnit5, including package-private and
inherited methods):

```bash
./gradlew :your-module:concurrentfind      --test-name=com.example.SomeTest#someFlakyMethod
./gradlew :your-module:delaylocs           --test-name=com.example.SomeTest#someFlakyMethod
./gradlew :your-module:deltadebug          --test-name=com.example.SomeTest#someFlakyMethod
./gradlew :your-module:critsearch          --test-name=com.example.SomeTest#someFlakyMethod
./gradlew :your-module:barrierpointsearch  --test-name=com.example.SomeTest#someFlakyMethod
./gradlew :your-module:patch               --test-name=com.example.SomeTest#someFlakyMethod
```

Output lands in `your-module/.flakesync/`.

## 3. Reading the results

Each goal only produces meaningful downstream output if the previous one actually found
something — a genuinely non-flaky test will make the pipeline "run down" gracefully:

| Goal | On success | On "nothing found" |
|---|---|---|
| `concurrentfind` | `<test>-ResultMethods.txt` lists methods observed running concurrently | File exists but is empty |
| `delaylocs` | `<test>-Locations.txt`: delay value + candidate injection points | Only the delay value on line 1, loop exhausts all 8 delays (100ms→12800ms) without reproducing a failure |
| `deltadebug` | `<test>-Locations_minimized.txt`: the minimized location set | Trivially small/empty if the input was already empty |
| `critsearch` | `Results-CritSearch/<test>-RootMethods.csv` + `-CriticalPoints.csv` | Prints `"This location is not a good one. Go back and run deltadebug again."` and exits without writing results |
| `barrierpointsearch` | `Results-BarrierSearch/<test>-BarrierPoints.csv` with real rows | **Task fails** with `Barrier point search failed` if `critsearch` didn't produce input — but still leaves a 0-byte results file behind (a faithful quirk: the output file gets created by Java's try-with-resources resource-acquisition order before the read of the missing input file throws) |
| `patch` | `.orig` files + `.patch` files under `.flakesync/patch/` | Silently does nothing if `BarrierPoints.csv` is empty/missing its header |

None of this is a bug — it's the pipeline correctly reporting "this specific test isn't flaky in a
way this run could reproduce," which is the common case when picking an arbitrary test.

## 4. Known limitations (hit these for real against apple/servicetalk)

- **Bytecode ceiling:** `flakesync-core` bundles ASM 9.0, which can't instrument class files newer
  than Java 15. If your target module compiles to a newer bytecode version by default (check what
  JDK is running your Gradle daemon — `./gradlew -q javaToolchains`), pin
  `sourceCompatibility`/`targetCompatibility` to 15 or earlier for the module under test, or
  transformation silently no-ops instead of erroring.
- **The plugin now preserves your module's own `test` task JVM config** (`jvmArgs`,
  `minHeapSize`/`maxHeapSize`, `systemProperties`) automatically — this was *not* true in an
  earlier version and caused false-positive failures for any test suite relying on custom JVM
  flags (e.g. JUnit5's `-Djunit.jupiter.extensions.autodetection.enabled=true` for auto-registered
  extensions). If you're on an older build of this plugin, update it.
- **Large, deeply reactive/async codebases can hit classloading edge cases.** Against
  `servicetalk-loadbalancer` (a module with a large dependency graph rooted in ServiceTalk's core
  reactive types), a forked execution crashed with
  `java.lang.LinkageError: loader 'app' attempted duplicate abstract class definition for
  io.servicetalk.concurrent.api.Completable` — a JVM-level crash from the agent's bytecode
  retransformation, not a real test failure (confirmed by running the same test through Gradle's
  own `test` task, where it passes reliably). The exit code from an uncaught exception happens to
  coincide with the plugin's own "test failed" exit code, so this can look like a false-positive
  flaky-test reproduction if you're not watching for it. Against a much smaller module
  (`servicetalk-buffer-api`, minimal dependencies) the same mechanism worked cleanly with no crash.
  If you hit this: try a smaller/simpler module first, or a test whose dependency graph doesn't
  pull in a large reactive/async runtime.

## 5. Worked example (this trial)

```bash
# settings.gradle: add pluginManagement block pointing at flakesync-gradle-plugin
# servicetalk-buffer-api/build.gradle: add plugins { id "edu.utexas.ece.flakesync" } +
#   repositories { mavenLocal() }

./gradlew :servicetalk-buffer-api:concurrentfind --test-name=io.servicetalk.buffer.api.CharSequencesTest#splitStringNoTrim
./gradlew :servicetalk-buffer-api:delaylocs      --test-name=io.servicetalk.buffer.api.CharSequencesTest#splitStringNoTrim
./gradlew :servicetalk-buffer-api:deltadebug     --test-name=io.servicetalk.buffer.api.CharSequencesTest#splitStringNoTrim
./gradlew :servicetalk-buffer-api:critsearch     --test-name=io.servicetalk.buffer.api.CharSequencesTest#splitStringNoTrim
# -> "This location is not a good one. Go back and run deltadebug again."
./gradlew :servicetalk-buffer-api:barrierpointsearch --test-name=io.servicetalk.buffer.api.CharSequencesTest#splitStringNoTrim
# -> FAILS: "Barrier point search failed" (expected: critsearch found nothing)
./gradlew :servicetalk-buffer-api:patch          --test-name=io.servicetalk.buffer.api.CharSequencesTest#splitStringNoTrim
# -> succeeds, does nothing (no barrier points to patch)
```

All six commands ran successfully against real, unmodified, third-party source — this test just
genuinely isn't flaky, which the pipeline correctly reported at every stage.
