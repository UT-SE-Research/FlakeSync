# FlakeSync Gradle Plugin — Design

Status: approved for planning
Date: 2026-09-15

## Goal

Make FlakeSync usable against Gradle-built target projects, with feature
parity across all 6 existing goals (`concurrentfind`, `delaylocs`,
`deltadebug`, `critsearch`, `barrierpointsearch`, `patch`), while leaving
the existing Maven plugin untouched and fully supported. The two plugins
coexist permanently — a user picks whichever matches their target
project's build tool.

## Background

FlakeSync today is a Maven reactor with three modules:

- `flakesync-core` — a `-javaagent` (bytecode instrumentation via ASM/
  Javassist) driven entirely by `System.getProperty(...)` and flat text
  files under `.flakesync/`. It has **no** Maven-specific dependencies.
- `flakesync-utils` — shared constants/path helpers (`Constants`), also
  build-tool-agnostic.
- `flakesync-maven-plugin` — six Mojos that orchestrate repeated,
  instrumented test executions by using `mojo-executor` to invoke the
  `maven-surefire-plugin:test` goal in-process, over and over (the
  delay-injection, delta-debugging, and critical/barrier-point-search
  phases can mean dozens to hundreds of executions per run). Every
  decision these Mojos make is based on whether that Surefire invocation
  threw (test failed) or not — no Mojo parses Surefire's XML reports for
  logic.

This last point is the load-bearing fact for the whole design: the only
signal FlakeSync's orchestration logic actually needs from a test
execution is pass/fail, not a rich report.

## Non-goals

- Replacing or deprecating the Maven plugin.
- JUnit5-native target project support in v1 (see Limitations).
- Publishing to the Gradle Plugin Portal (out of scope for this design;
  can be revisited once the plugin works via `mavenLocal()`/composite
  build).
- Changing `flakesync-core`'s instrumentation logic or the on-disk file
  protocol (`Constants`, `.flakesync/` layout) in any way that would
  break the Maven plugin.

## Repo layout

```
FlakeSync/
  pom.xml                      (existing Maven reactor — unchanged)
  flakesync-core/              (existing — one small addition)
  flakesync-utils/             (existing — gains DeltaDebugger, see below)
  flakesync-maven-plugin/      (existing — unchanged, adjusted per below)
  flakesync-gradle-plugin/     (new — its own Gradle build + wrapper)
    build.gradle.kts
    settings.gradle.kts
    src/main/java/edu/utexas/ece/flakesync/gradle/
      FlakeSyncPlugin.java
      FlakeSyncExtension.java
      AbstractFlakeSyncTask.java
      ConcurrentFindTask.java
      DelayLocsTask.java
      DeltaDebugTask.java
      CritSearchTask.java
      BarrierPointSearchTask.java
      PatchTask.java
      internal/SingleTestExecutor.java
    src/test/java/...
```

### Why a sibling Gradle build instead of a fourth Maven reactor module

A Gradle plugin artifact is just a jar with a
`META-INF/gradle-plugins/<id>.properties` file plus the Gradle API on the
compile classpath. Producing it idiomatically requires Gradle's own
`java-gradle-plugin` support (`gradleApi()`), which has no clean Maven
equivalent — pulling individual Gradle API jars from Gradle's own
repository into a Maven POM is fragile and undocumented for external
consumers. Two independent, tool-appropriate builds joined at the
published-artifact boundary (`flakesync-gradle-plugin` depends on
`flakesync-core`/`flakesync-utils` as ordinary jars, consumed via
`mavenLocal()` in development) is simpler and mirrors how a real
downstream consumer would use this: they apply the Gradle plugin from a
plugin repository, unrelated to how `flakesync-core` itself was built.

## Core mechanism: direct JVM forking

Each Gradle task forks a JVM directly (via `ExecOperations.javaexec`)
instead of going through Gradle's `Test` task type or shelling out to a
nested `gradlew test` invocation. Rationale:

- **Performance.** A nested-Gradle-build-per-execution approach (spawn
  `gradlew test --tests ...` and parse its JUnit XML) pays full Gradle
  build/configuration overhead on every single execution. Given phases
  like delta-debugging can mean hundreds of executions per run, this
  would make the tool impractically slow compared to the Maven version.
- **Sufficiency.** Since the only signal needed is pass/fail, a bare
  forked JVM's exit code is enough — no report-parsing layer is needed.
- **Core stays untouched.** `flakesync-core`'s agent is already
  launcher-agnostic; a direct fork is the natural fit.

### The launcher

`flakesync-core` gains one new class:
`edu.utexas.ece.flakesync.launcher.SingleTestLauncher`, with a
`main(String[] args)` that:

1. Parses a single argument `ClassName#methodName`.
2. Runs it via `org.junit.runner.JUnitCore` /
   `Request.method(Class, String)`.
3. Exits `0` on success, `1` on test failure, and a distinct non-zero
   code (e.g. `2`) with a clear stderr message if the class/method can't
   be found or JUnit4 isn't resolvable on the classpath at all (see
   Error handling).

`SingleTestLauncher` is **compiled** against the JUnit4 API
(`compileOnly`, since the API surface of `JUnitCore`/`Request`/`Result`
has been stable since 4.4) but resolves it at **runtime** against
whatever JUnit4 version is already on the target project's test
classpath — the same pattern Surefire's own JUnit4 provider uses. No
bundling, no shading, no version pinning of JUnit itself.

The existing `flakesync-core` jar's manifest gains a `Main-Class` entry
alongside its existing `Premain-Class` entry. Same artifact serves as
both the `-javaagent` and the launcher's entry point — no new module.

### Limitations (v1)

- Only JUnit4-style test methods are supported by the launcher. JUnit5
  (Jupiter) native projects are not supported in v1. A follow-up could
  add a second launcher variant using `junit-platform-launcher`
  (`compileOnly`, same runtime-resolution pattern) without changing this
  design's shape.
- No standard JUnit XML report is produced per execution (matches
  today's Maven behavior, where reports aren't consulted for logic
  either — `reportsDirectory` in `SurefireExecution` is bookkeeping, not
  a dependency of any Mojo's decisions).

## Component responsibilities

- **`FlakeSyncPlugin`** — applied via
  `id("edu.utexas.ece.flakesync")`. Registers the `flakesync` extension
  and the 6 tasks. Declares a `flakesyncAgent` `Configuration` with a
  dependency on `edu.utexas.ece:flakesync-core:<version>`, resolved to a
  file at task execution time. This replaces the Maven plugin's
  hardcoded local-repository path string
  (`ConfigurationDefaults.CORE_JAR`) with Gradle's normal dependency
  resolution — strictly more robust, not just a port.

- **`FlakeSyncExtension`** — project-level config block:
  ```kotlin
  flakesync {
      flakesyncDir.set(...)   // default ".flakesync", matches ConfigurationDefaults
      loggingLevel.set(...)   // default "CONFIG"
  }
  ```

- **`AbstractFlakeSyncTask`** — Gradle analog of
  `FlakeSyncAbstractMojo`. Common bits:
  - `@Input testName`, bound to `@Option(option = "test-name")` so it's
    usable from the CLI: `./gradlew delaylocs --test-name=pkg.Foo#bar`
    (idiomatic Gradle, replacing Maven's `-Dflakesync.testName=`).
  - Resolves the agent jar from the `flakesyncAgent` configuration.
  - Resolves `sourceSets.test.runtimeClasspath` for the forked JVM's
    classpath.
  - Depends on the `testClasses` task so compiled test code exists
    before any execution is attempted.

- **`internal/SingleTestExecutor`** — the direct analog of
  `SurefireExecution`, minus all `Xpp3Dom`/`mojo-executor` machinery.
  Given `(testName, delay, agentMode, extraSystemProperties, timeoutSeconds)`,
  it invokes `ExecOperations.javaexec` with:
  - `-javaagent:<resolved core jar>`
  - `-cp <test runtime classpath>:<core jar>`
  - main class `edu.utexas.ece.flakesync.launcher.SingleTestLauncher`
  - all system properties `SurefireExecution` sets today
    (`agentmode`, `delay`, `.test`, `locations`, `whitelist`,
    `concurrentmethods`, etc. — reusing `Constants` from
    `flakesync-utils` unchanged for every file path)

  and returns pass/fail from the exit code, mirroring how a thrown
  `MojoExecutionException` from Surefire is treated as "failed" today.
  A watchdog thread enforces a per-execution timeout (replacing
  Surefire's `forkedProcessTimeoutInSeconds`) by destroying the process,
  since `javaexec` has no built-in timeout.

- **The 6 task classes** — thin ports of their Mojo counterparts:

  | Gradle task            | Maven Mojo            |
  |-------------------------|------------------------|
  | `ConcurrentFindTask`    | `FindTestsRunMojo`     |
  | `DelayLocsTask`         | `RunWithDelaysMojo`    |
  | `DeltaDebugTask`        | `DeltaDebugMojo`       |
  | `CritSearchTask`        | `CritSearchMojo`       |
  | `BarrierPointSearchTask`| `BarrierPointMojo`     |
  | `PatchTask`             | `PatchingMojo`         |

  Each reuses `flakesync-utils`'s `Constants` unchanged, so the on-disk
  `.flakesync/` protocol (`ResultMethods.txt`, `Locations.txt`,
  `whitelist.txt`, etc.) is identical between the two plugins. A project
  could in principle run phase 1 via Maven and phase 2 via Gradle against
  the same `.flakesync/` directory, though that's not an explicit goal —
  just a natural consequence of sharing the protocol.

- **`DeltaDebugger` moves to `flakesync-utils`.** It's already
  build-tool-agnostic (pure bisection algorithm over a `List<T>` with an
  abstract `checkValid`); today it lives under
  `flakesync-maven-plugin/.../common/DeltaDebugger.java` purely for
  historical reasons. Moving it lets both plugins share the exact same
  algorithm instead of the Gradle side reimplementing delta-debugging
  bisection. `flakesync-maven-plugin`'s `DeltaDebugMojo` updates its
  import accordingly; behavior is unchanged.

## Data flow (worked example: `delaylocs`)

1. Task reads `testName` from `--test-name`, resolves agent jar + test
   runtime classpath.
2. Builds a whitelist of test-output class names (port of
   `RunWithDelaysMojo.createWhiteList`: walk `sourceSets.test.output`
   plus classpath jar entries, same filtering as today).
3. Loops over the fixed delay ladder (`100, 200, 400, ..., 12800`),
   invoking `SingleTestExecutor` once per delay with
   `agentmode=ALL_LOCATIONS`.
4. The agent (unchanged) writes `Locations.txt` on JVM shutdown, exactly
   as it does under Maven today.
5. The task stops at the first execution that reproduces the failure,
   exactly like `RunWithDelaysMojo` does today.

The other 5 tasks follow the same shape: build a `SurefireFactory`-style
request via `SingleTestExecutor`, loop per that goal's algorithm (ported
1:1 from its Mojo), consult only pass/fail, read/write the same
`Constants`-defined files.

## Error handling

- **Execution timeout** → watchdog destroys the forked process; treated
  as a failed/inconclusive execution, matching today's "Surefire TIMED
  OUT" log-and-escalate path.
- **Agent jar unresolvable** → fail fast with a clear Gradle
  `GradleException` before forking anything (an improvement over the
  Maven plugin's silent fallback to a guessed local-repo path).
- **JUnit4 unresolvable / test method not found on the classpath** →
  `SingleTestLauncher` exits with a distinct code (`2`, reserved from
  the pass/fail `0`/`1`) and a clear stderr message; the task surfaces
  this as a `GradleException` rather than silently treating it as "test
  failed" (a correctness gap the Maven side doesn't have to worry about
  since Surefire itself validates test resolution before FlakeSync ever
  sees a result).

## Testing strategy

- **Unit tests** (`flakesync-gradle-plugin`, using JUnit5 — this is
  about testing the plugin's own code, unrelated to what test framework
  the plugin runs against target projects):
  - `SingleTestExecutor`'s argument/classpath/system-property building,
    tested without actually forking a process.
  - Task wiring and extension defaults, tested via Gradle TestKit
    (`ProjectBuilder` for unit-level, `GradleRunner` for task-execution-
    level).
- **Integration test:** a small dedicated sample Gradle project (JUnit4,
  with a known/seeded flaky async test) exercised end-to-end through all
  6 tasks, asserting the same output files the Maven side produces today
  against an equivalent Maven fixture — this is the real parity check.
  Reuses ideas from the existing `testscripts/` fixture where
  applicable, but as a proper Gradle project rather than shell scripts.

## Open items deferred to implementation planning

- Exact Gradle minimum-version target and `java-gradle-plugin` plugin
  version.
- Whether `flakesync-gradle-plugin` is consumed during development via
  `mavenLocal()` publishing of `flakesync-core`/`flakesync-utils`, or via
  a Gradle composite build (`includeBuild`) pointed at the Maven
  modules' output directories. Either works; the plan should pick one
  and document the dev-loop command.
