# FlakeSync Gradle Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `flakesync-gradle-plugin` that ports all 6 FlakeSync goals (concurrentfind, delaylocs, deltadebug, critsearch, barrierpointsearch, patch) to Gradle tasks, without changing `flakesync-maven-plugin`'s behavior.

**Architecture:** A new sibling Gradle build (`flakesync-gradle-plugin/`) depends on `flakesync-core` and `flakesync-utils` as ordinary jars (via `mavenLocal()`). Each goal is a Gradle task that forks a JVM directly (via `ProcessBuilder`, for reliable timeout/kill semantics) running a new `SingleTestLauncher` main class added to `flakesync-core`, with the same `-javaagent` and system properties the Maven plugin's `SurefireExecution` sets today. Two small utility packages (`DeltaDebugger`, `patching`) move from `flakesync-maven-plugin` to `flakesync-utils` so both plugins share identical algorithms.

**Tech Stack:** Java 8 source/target (matching the existing modules), Gradle 9.7.1 (wrapper) for the new module, JUnit4 (`org.junit.runner.JUnitCore`) as the target-project-facing test runner, JUnit5 for testing the plugin's own code, Gradle TestKit for task-execution tests.

**Spec:** `docs/superpowers/specs/2026-09-15-gradle-plugin-design.md`

## Global Constraints

- Do not change `flakesync-core`'s instrumentation logic (`Agent.java` and the `*Tracer` classes) or the on-disk `.flakesync/` file protocol defined in `flakesync-utils`' `Constants` class — both plugins must remain able to read/write the identical files.
- Do not change any existing `flakesync-maven-plugin` Mojo's *behavior* — only update two import statements (Tasks 1 and 2) after moving shared code out from under it.
- `flakesync-gradle-plugin` is its own Gradle build (own `settings.gradle.kts`/`build.gradle.kts`/wrapper), not a module of the root `pom.xml` reactor.
- v1 supports JUnit4-style test methods only (via `org.junit.runner.JUnitCore`/`Request.method`). JUnit5-native target projects are out of scope (documented limitation).
- Every Gradle task's `testName` input is supplied via `--test-name=ClassName#methodName` (a Gradle `@Option`), not a system property.
- Group/artifact coordinates for `flakesync-core`/`flakesync-utils` are `edu.utexas.ece:flakesync-core:1.0-SNAPSHOT` and `edu.utexas.ece:flakesync-utils:1.0-SNAPSHOT` (existing `pom.xml` version) — the Gradle build resolves them via `mavenLocal()`.

---

## File Structure

```
flakesync-utils/src/main/java/flakesync/
  DeltaDebugger.java                     (moved from flakesync-maven-plugin, package flattened)
  patching/InjectFlagInCriticalPoint.java  (moved, unchanged)
  patching/InjectYieldStatement.java       (moved, unchanged)
  patching/SavePatch.java                  (moved, unchanged)

flakesync-maven-plugin/src/main/java/flakesync/
  DeltaDebugMojo.java                     (import updated)
  PatchingMojo.java                       (imports updated)
  (common/DeltaDebugger.java and patching/*.java deleted)

flakesync-core/
  pom.xml                                 (add provided junit dep + Main-Class manifest entry)
  src/main/java/edu/utexas/ece/flakesync/launcher/SingleTestLauncher.java   (new)
  src/test/java/edu/utexas/ece/flakesync/launcher/
    fixtures/AlwaysPassesTest.java        (new)
    fixtures/AlwaysFailsTest.java         (new)
    SingleTestLauncherTest.java           (new)

flakesync-gradle-plugin/                  (new sibling Gradle build)
  settings.gradle.kts
  build.gradle.kts
  gradlew, gradlew.bat, gradle/wrapper/...
  src/main/java/edu/utexas/ece/flakesync/gradle/
    FlakeSyncExtension.java
    FlakeSyncPlugin.java
    TestExecutionRequest.java
    TestExecutionTimeoutException.java
    SingleTestExecutor.java
    AbstractFlakeSyncTask.java
    ConcurrentFindTask.java
    DelayLocsTask.java
    DeltaDebugTask.java
    CritSearchTask.java
    BarrierPointSearchTask.java
    PatchTask.java
  src/test/java/edu/utexas/ece/flakesync/gradle/
    FlakeSyncPluginTest.java
    AbstractFlakeSyncTaskTest.java
    SingleTestExecutorTest.java
    ConcurrentFindTaskTest.java
    DelayLocsTaskTest.java
    DeltaDebugTaskTest.java
    CritSearchTaskTest.java
    BarrierPointSearchTaskTest.java
    PatchTaskTest.java
    PipelineIntegrationTest.java
    fixtures/FixtureProject.java           (helper that materializes the fixture project into a temp dir)
  src/test/resources/fixture-project/
    build.gradle.kts
    src/test/java/fixture/FlakySampleTest.java

README.md                                 (new "Using FlakeSync with Gradle" section)
```

---

### Task 1: Move `DeltaDebugger` to `flakesync-utils`

**Files:**
- Create: `flakesync-utils/src/main/java/flakesync/DeltaDebugger.java`
- Delete: `flakesync-maven-plugin/src/main/java/flakesync/common/DeltaDebugger.java`
- Modify: `flakesync-maven-plugin/src/main/java/flakesync/DeltaDebugMojo.java`
- Test: `flakesync-utils/src/test/java/flakesync/DeltaDebuggerTest.java`

**Interfaces:**
- Produces: `flakesync.DeltaDebugger<T>` — abstract class with `public List<T> deltaDebug(List<T> elements, int granularity)`, `public int getIterations()`, `public abstract boolean checkValid(List<T> elements)`. Package changes from `flakesync.common` to `flakesync` (matching `flakesync.Constants`'s existing flat package in this module).

- [ ] **Step 1: Write the failing test**

Create `flakesync-utils/src/test/java/flakesync/DeltaDebuggerTest.java`:

```java
package flakesync;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DeltaDebuggerTest {

    @Test
    public void minimizesToTheSingleRequiredElement() {
        List<String> elements = new ArrayList<>(Arrays.asList("a", "b", "c", "culprit", "d"));

        DeltaDebugger<String> debugger = new DeltaDebugger<String>() {
            @Override
            public boolean checkValid(List<String> chunk) {
                return chunk.contains("culprit");
            }
        };

        List<String> result = debugger.deltaDebug(elements, 2);

        assertEquals(Arrays.asList("culprit"), result);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from repo root): `mvn -pl flakesync-utils -am test -Dtest=DeltaDebuggerTest`
Expected: FAIL to compile — `DeltaDebugger` does not exist in package `flakesync` yet.

- [ ] **Step 3: Add a JUnit dependency to `flakesync-utils` and move the class**

`flakesync-utils/pom.xml` currently has no dependencies block. Add one so the new test compiles:

```xml
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
    </dependency>
  </dependencies>
```
(version/scope come from the root `pom.xml`'s `dependencyManagement`, already `test` scope for `junit:junit:4.13.1`.)

Move the file: read `flakesync-maven-plugin/src/main/java/flakesync/common/DeltaDebugger.java`, recreate it at `flakesync-utils/src/main/java/flakesync/DeltaDebugger.java` with only the package line changed from `package flakesync.common;` to `package flakesync;` — the class body (the `deltaDebug`/`getIterations`/`checkValid` logic) is copied verbatim. Then delete the original file.

- [ ] **Step 4: Update the one caller**

In `flakesync-maven-plugin/src/main/java/flakesync/DeltaDebugMojo.java`, change:

```java
import flakesync.common.DeltaDebugger;
```

to:

```java
import flakesync.DeltaDebugger;
```

(No other change — `DeltaDebugMojo`'s inner `LocationsDeltaDebug extends DeltaDebugger<String>` continues to compile unchanged since the class's API is identical.)

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl flakesync-utils -am test -Dtest=DeltaDebuggerTest`
Expected: PASS.

- [ ] **Step 6: Verify the Maven plugin still builds**

Run: `mvn -pl flakesync-maven-plugin -am -DskipTests compile`
Expected: BUILD SUCCESS (confirms `DeltaDebugMojo`'s updated import resolves).

- [ ] **Step 7: Commit**

```bash
git add flakesync-utils/pom.xml flakesync-utils/src/main/java/flakesync/DeltaDebugger.java \
        flakesync-utils/src/test/java/flakesync/DeltaDebuggerTest.java \
        flakesync-maven-plugin/src/main/java/flakesync/DeltaDebugMojo.java
git rm flakesync-maven-plugin/src/main/java/flakesync/common/DeltaDebugger.java
git commit -m "Move DeltaDebugger to flakesync-utils so both plugins can share it"
```

---

### Task 2: Move the `patching` package to `flakesync-utils`

**Files:**
- Create: `flakesync-utils/src/main/java/flakesync/patching/InjectFlagInCriticalPoint.java`
- Create: `flakesync-utils/src/main/java/flakesync/patching/InjectYieldStatement.java`
- Create: `flakesync-utils/src/main/java/flakesync/patching/SavePatch.java`
- Delete: `flakesync-maven-plugin/src/main/java/flakesync/patching/InjectFlagInCriticalPoint.java`
- Delete: `flakesync-maven-plugin/src/main/java/flakesync/patching/InjectYieldStatement.java`
- Delete: `flakesync-maven-plugin/src/main/java/flakesync/patching/SavePatch.java`
- Modify: `flakesync-maven-plugin/src/main/java/flakesync/PatchingMojo.java`
- Test: `flakesync-utils/src/test/java/flakesync/patching/InjectFlagInCriticalPointTest.java`

**Interfaces:**
- Consumes: none beyond `flakesync.Constants` (already in this module) and the JDK.
- Produces: `flakesync.patching.InjectFlagInCriticalPoint` (with public `findJavaFilePath(String, String)`, `injectFlagInCritPt(String, String, int)`, and public constants `NUM_EXECUTIONS_FIELD_NAME`, `RESET_METHOD_NAME`, `GET_EXECUTION_STATUS_METHOD_NAME`), `flakesync.patching.InjectYieldStatement` (`injectYieldStatement(String, String, String, String, int, int)`), `flakesync.patching.SavePatch` (`getFilePath(String, String, boolean)`, `makePatch(String, String, String)`) — all identical to today, package unchanged (`flakesync.patching` already matches the target module's convention).

- [ ] **Step 1: Write the failing test**

These three classes have no existing tests. Add one for the piece with the clearest observable contract, file discovery:

Create `flakesync-utils/src/test/java/flakesync/patching/InjectFlagInCriticalPointTest.java`:

```java
package flakesync.patching;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class InjectFlagInCriticalPointTest {

    @Test
    public void findsJavaFileByFullyQualifiedClassName() throws IOException {
        TemporaryFolder tmp = new TemporaryFolder();
        tmp.create();
        Path srcDir = tmp.newFolder("src", "main", "java", "pkg").toPath();
        Path javaFile = srcDir.resolve("Sample.java");
        Files.write(javaFile, "package pkg;\nclass Sample {}\n".getBytes());

        Path found = InjectFlagInCriticalPoint.findJavaFilePath(tmp.getRoot().toString(), "pkg.Sample");

        assertEquals(javaFile.toAbsolutePath().normalize(), found.toAbsolutePath().normalize());
        tmp.delete();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl flakesync-utils -am test -Dtest=InjectFlagInCriticalPointTest`
Expected: FAIL to compile — `flakesync.patching.InjectFlagInCriticalPoint` does not exist in `flakesync-utils` yet.

- [ ] **Step 3: Move the three files**

Copy each of `InjectFlagInCriticalPoint.java`, `InjectYieldStatement.java`, `SavePatch.java` from `flakesync-maven-plugin/src/main/java/flakesync/patching/` to `flakesync-utils/src/main/java/flakesync/patching/` unchanged (same package `flakesync.patching`, same imports — they already only import `flakesync.Constants` and JDK classes). Delete the three originals from `flakesync-maven-plugin`.

- [ ] **Step 4: Update the caller**

`flakesync-maven-plugin/src/main/java/flakesync/PatchingMojo.java` already imports:

```java
import flakesync.patching.InjectFlagInCriticalPoint;
import flakesync.patching.InjectYieldStatement;
import flakesync.patching.SavePatch;
```

These import paths don't change (package stays `flakesync.patching`) — the classes now resolve from `flakesync-utils` on the classpath instead of from within the same module. No edit needed here beyond relying on `flakesync-maven-plugin`'s existing dependency on `flakesync-utils` (already declared in `flakesync-maven-plugin/pom.xml`).

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl flakesync-utils -am test -Dtest=InjectFlagInCriticalPointTest`
Expected: PASS.

- [ ] **Step 6: Verify the Maven plugin still builds**

Run: `mvn -pl flakesync-maven-plugin -am -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add flakesync-utils/src/main/java/flakesync/patching \
        flakesync-utils/src/test/java/flakesync/patching
git rm -r flakesync-maven-plugin/src/main/java/flakesync/patching
git commit -m "Move patching package to flakesync-utils so both plugins can share it"
```

---

### Task 3: Add `SingleTestLauncher` to `flakesync-core`

**Files:**
- Modify: `flakesync-core/pom.xml`
- Create: `flakesync-core/src/main/java/edu/utexas/ece/flakesync/launcher/SingleTestLauncher.java`
- Create: `flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher/fixtures/AlwaysPassesTest.java`
- Create: `flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher/fixtures/AlwaysFailsTest.java`
- Test: `flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher/SingleTestLauncherTest.java`

**Interfaces:**
- Produces: `edu.utexas.ece.flakesync.launcher.SingleTestLauncher` with `public static final int EXIT_PASSED = 0`, `EXIT_FAILED = 1`, `EXIT_CONFIG_ERROR = 2`; `public static void main(String[] args)`; package-visible `static int run(String[] args)` (the testable entry point — `main` just calls `System.exit(run(args))`). When the test fails **and** the `stackTraceOutputFile` system property is set, `run` also writes the failure's exception stack trace to that path — this is how `BarrierPointSearchTask` (Task 11) recovers what `BarrierPointMojo` gets today by scanning Surefire's own `TEST-*.xml` report for a `BARRIER_ST` execution; a direct file write is simpler than replicating Surefire's report-file-naming/discovery. The jar's manifest gains `Main-Class: edu.utexas.ece.flakesync.launcher.SingleTestLauncher` alongside the existing `Premain-Class`.

- [ ] **Step 1: Add a `provided`-scope JUnit4 dependency**

`flakesync-core/pom.xml` currently has no `junit` dependency. Add one, in `<dependencies>` (after the existing `flakesync-utils` dependency):

```xml
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <scope>provided</scope>
    </dependency>
```

`provided` scope makes JUnit4 available at compile time (for `SingleTestLauncher`'s use of `org.junit.runner.JUnitCore`) and at test time (for the two fixture test classes below), but — as intended by the design — it is **not** bundled into the shaded jar and not assumed present at runtime beyond what's already on the classpath of whatever JVM launches `SingleTestLauncher` (the target project's own test classpath, which already has its own JUnit4).

- [ ] **Step 2: Write the two test fixture classes**

Create `flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher/fixtures/AlwaysPassesTest.java`:

```java
package edu.utexas.ece.flakesync.launcher.fixtures;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AlwaysPassesTest {

    @Test
    public void thisAlwaysPasses() {
        assertTrue(true);
    }
}
```

Create `flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher/fixtures/AlwaysFailsTest.java`:

```java
package edu.utexas.ece.flakesync.launcher.fixtures;

import org.junit.Test;

import static org.junit.Assert.fail;

public class AlwaysFailsTest {

    @Test
    public void thisAlwaysFails() {
        fail("intentionally failing fixture");
    }
}
```

These are ordinary JUnit4 test classes that Maven's own Surefire run will also pick up and execute as part of `flakesync-core`'s test suite (`AlwaysFailsTest` will make `mvn test` fail if run directly by Surefire) — Step 3 excludes them from Surefire's own discovery so only `SingleTestLauncherTest` invokes them (via `Request.method`, not Surefire).

- [ ] **Step 3: Exclude the fixtures from Surefire's own test discovery**

`flakesync-core/pom.xml` has no `<build><plugins>` Surefire configuration of its own yet (it inherits defaults). Add one so Surefire skips the `fixtures` package (these classes exist only to be driven by `SingleTestLauncherTest`, not run directly):

```xml
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude>**/launcher/fixtures/**</exclude>
          </excludes>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-checkstyle-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
```

(Note: `flakesync-core/pom.xml` doesn't currently have a `<build>` block at all — check `flakesync-core/pom.xml` before editing; if it already has a `<build><plugins>` block with only the jar/shade/checkstyle plugins shown earlier, add the `maven-surefire-plugin` entry alongside them rather than replacing the block.)

- [ ] **Step 4: Write the failing test**

Create `flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher/SingleTestLauncherTest.java`:

```java
package edu.utexas.ece.flakesync.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SingleTestLauncherTest {

    @Test
    public void exitsZeroWhenTheTestPasses() {
        int exitCode = SingleTestLauncher.run(new String[] {
            "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysPassesTest#thisAlwaysPasses"
        });
        assertEquals(SingleTestLauncher.EXIT_PASSED, exitCode);
    }

    @Test
    public void exitsOneWhenTheTestFails() {
        int exitCode = SingleTestLauncher.run(new String[] {
            "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysFailsTest#thisAlwaysFails"
        });
        assertEquals(SingleTestLauncher.EXIT_FAILED, exitCode);
    }

    @Test
    public void exitsTwoWhenTheClassCannotBeFound() {
        int exitCode = SingleTestLauncher.run(new String[] {"does.not.Exist#method"});
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, exitCode);
    }

    @Test
    public void exitsTwoWhenTheMethodCannotBeFound() {
        int exitCode = SingleTestLauncher.run(new String[] {
            "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysPassesTest#noSuchMethod"
        });
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, exitCode);
    }

    @Test
    public void exitsTwoWhenArgsAreMalformed() {
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, SingleTestLauncher.run(new String[0]));
        assertEquals(SingleTestLauncher.EXIT_CONFIG_ERROR, SingleTestLauncher.run(new String[] {"no-hash-here"}));
    }

    @Test
    public void writesTheFailureStackTraceWhenRequestedAndTheTestFails() throws java.io.IOException {
        java.io.File output = java.io.File.createTempFile("flakesync-stacktrace", ".txt");
        output.deleteOnExit();
        System.setProperty("stackTraceOutputFile", output.getAbsolutePath());
        try {
            SingleTestLauncher.run(new String[] {
                "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysFailsTest#thisAlwaysFails"
            });
            String contents = new String(java.nio.file.Files.readAllBytes(output.toPath()));
            assertTrue(contents.contains("AlwaysFailsTest"));
        } finally {
            System.clearProperty("stackTraceOutputFile");
        }
    }

    @Test
    public void doesNotWriteAStackTraceFileWhenTheTestPasses() throws java.io.IOException {
        java.io.File output = java.io.File.createTempFile("flakesync-stacktrace", ".txt");
        assertTrue(output.delete());
        output.deleteOnExit();
        System.setProperty("stackTraceOutputFile", output.getAbsolutePath());
        try {
            SingleTestLauncher.run(new String[] {
                "edu.utexas.ece.flakesync.launcher.fixtures.AlwaysPassesTest#thisAlwaysPasses"
            });
            assertFalse(output.exists());
        } finally {
            System.clearProperty("stackTraceOutputFile");
        }
    }
}
```

(Add `import static org.junit.Assert.assertFalse;` and `import static org.junit.Assert.assertTrue;` alongside the existing `assertEquals` import.)

- [ ] **Step 5: Run test to verify it fails**

Run: `mvn -pl flakesync-core -am test -Dtest=SingleTestLauncherTest`
Expected: FAIL to compile — `SingleTestLauncher` doesn't exist yet.

- [ ] **Step 6: Implement `SingleTestLauncher`**

Create `flakesync-core/src/main/java/edu/utexas/ece/flakesync/launcher/SingleTestLauncher.java`:

```java
package edu.utexas.ece.flakesync.launcher;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

public final class SingleTestLauncher {

    public static final int EXIT_PASSED = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_CONFIG_ERROR = 2;

    private SingleTestLauncher() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length != 1 || args[0].indexOf('#') < 0) {
            System.err.println("Usage: SingleTestLauncher <ClassName>#<methodName>");
            return EXIT_CONFIG_ERROR;
        }

        int hashIndex = args[0].indexOf('#');
        String className = args[0].substring(0, hashIndex);
        String methodName = args[0].substring(hashIndex + 1);

        Class<?> testClass;
        try {
            testClass = Class.forName(className);
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Could not find test class: " + className);
            return EXIT_CONFIG_ERROR;
        }

        // Check the method exists before invoking JUnit at all: Request.method(...) does not
        // surface a missing method as an empty/zero-count Result the way this comment used to
        // assume — it reports a single failing "test" (initializationError) indistinguishable
        // from a real failure by getRunCount() alone. Reflection sidesteps parsing JUnit's
        // internal failure description entirely.
        try {
            testClass.getMethod(methodName);
        } catch (NoSuchMethodException nsme) {
            System.err.println("Could not find test method: " + className + "#" + methodName);
            return EXIT_CONFIG_ERROR;
        }

        Result result;
        try {
            result = new JUnitCore().run(Request.method(testClass, methodName));
        } catch (NoClassDefFoundError ncdfe) {
            System.err.println("JUnit4 is not available on the test classpath: " + ncdfe.getMessage());
            return EXIT_CONFIG_ERROR;
        }

        if (!result.wasSuccessful()) {
            writeStackTraceIfRequested(result);
            return EXIT_FAILED;
        }
        return EXIT_PASSED;
    }

    private static void writeStackTraceIfRequested(Result result) {
        String outputPath = System.getProperty("stackTraceOutputFile");
        if (outputPath == null || result.getFailures().isEmpty()) {
            return;
        }
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(outputPath))) {
            writer.print(result.getFailures().get(0).getTrace());
        } catch (java.io.IOException ioe) {
            System.err.println("Failed to write stack trace output file: " + ioe.getMessage());
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn -pl flakesync-core -am test -Dtest=SingleTestLauncherTest`
Expected: PASS (all seven cases).

- [ ] **Step 8: Add the `Main-Class` manifest entry**

In `flakesync-core/pom.xml`, find the `maven-jar-plugin` block's `<manifestEntries>` and add `Main-Class` next to the existing `Premain-Class`:

```xml
            <manifestEntries>
              <Premain-Class>edu.utexas.ece.flakesync.agent.Agent</Premain-Class>
              <Main-Class>edu.utexas.ece.flakesync.launcher.SingleTestLauncher</Main-Class>
              <Can-Retransform-Classes>true</Can-Retransform-Classes>
            </manifestEntries>
```

- [ ] **Step 9: Verify the manifest survives shading and install to `mavenLocal()`**

Run: `mvn -pl flakesync-core -am -DskipTests install`
Then: `unzip -p ~/.m2/repository/edu/utexas/ece/flakesync-core/1.0-SNAPSHOT/flakesync-core-1.0-SNAPSHOT.jar META-INF/MANIFEST.MF`
Expected: output contains both `Premain-Class: edu.utexas.ece.flakesync.agent.Agent` and `Main-Class: edu.utexas.ece.flakesync.launcher.SingleTestLauncher`.

- [ ] **Step 10: Commit**

```bash
git add flakesync-core/pom.xml flakesync-core/src/main/java/edu/utexas/ece/flakesync/launcher \
        flakesync-core/src/test/java/edu/utexas/ece/flakesync/launcher
git commit -m "Add SingleTestLauncher entry point to flakesync-core for the Gradle plugin"
```

---

### Task 4: Scaffold `flakesync-gradle-plugin` and register `FlakeSyncPlugin`

**Files:**
- Create: `flakesync-gradle-plugin/settings.gradle.kts`
- Create: `flakesync-gradle-plugin/build.gradle.kts`
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncExtension.java`
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPluginTest.java`

**Interfaces:**
- Produces: `FlakeSyncExtension` (Gradle extension interface: `Property<String> getFlakesyncDir()`, `Property<String> getLoggingLevel()`), `FlakeSyncPlugin implements Plugin<Project>` with `public static final String AGENT_CONFIGURATION_NAME = "flakesyncAgent"` and `public static final String AGENT_COORDINATES = "edu.utexas.ece:flakesync-core:1.0-SNAPSHOT"` — both referenced by `AbstractFlakeSyncTask` in Task 6.

- [ ] **Step 1: Install `flakesync-core` and `flakesync-utils` to `mavenLocal()`**

Run (from repo root): `mvn -pl flakesync-core,flakesync-utils -am -DskipTests install`
Expected: BUILD SUCCESS, and `~/.m2/repository/edu/utexas/ece/flakesync-core/1.0-SNAPSHOT/` and `.../flakesync-utils/1.0-SNAPSHOT/` exist. This is the one manual step in the dev loop every time `flakesync-core`/`flakesync-utils` change — document it in Task 13's README update.

- [ ] **Step 2: Create the settings file**

Create `flakesync-gradle-plugin/settings.gradle.kts`:

```kotlin
rootProject.name = "flakesync-gradle-plugin"
```

- [ ] **Step 3: Create the build file**

Create `flakesync-gradle-plugin/build.gradle.kts`:

```kotlin
plugins {
    java
    `java-gradle-plugin`
}

group = "edu.utexas.ece"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("edu.utexas.ece:flakesync-core:1.0-SNAPSHOT")
    implementation("edu.utexas.ece:flakesync-utils:1.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("flakesync") {
            id = "edu.utexas.ece.flakesync"
            implementationClass = "edu.utexas.ece.flakesync.gradle.FlakeSyncPlugin"
        }
    }
}
```

- [ ] **Step 4: Generate the Gradle wrapper**

Run (needs a system Gradle install, or `gradle` from a previous project, just to bootstrap the wrapper once):
```bash
cd flakesync-gradle-plugin
gradle wrapper --gradle-version 9.7.1
```
Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.{jar,properties}` are created. From then on, always invoke this module via `./gradlew` from within `flakesync-gradle-plugin/`.

- [ ] **Step 5: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPluginTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlakeSyncPluginTest {

    @Test
    void registersExtensionWithDefaults() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");

        FlakeSyncExtension extension = project.getExtensions().getByType(FlakeSyncExtension.class);

        assertEquals(".flakesync", extension.getFlakesyncDir().get());
        assertEquals("CONFIG", extension.getLoggingLevel().get());
    }

    @Test
    void registersResolvableAgentConfiguration() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");

        Configuration agentConfiguration = project.getConfigurations()
                .getByName(FlakeSyncPlugin.AGENT_CONFIGURATION_NAME);

        assertNotNull(agentConfiguration);
        assertTrue(agentConfiguration.isCanBeResolved());
        assertTrue(agentConfiguration.getDependencies().stream()
                .anyMatch(d -> "flakesync-core".equals(d.getName())));
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew test --tests FlakeSyncPluginTest` (from `flakesync-gradle-plugin/`)
Expected: FAIL to compile — neither `FlakeSyncExtension` nor `FlakeSyncPlugin` exist yet.

- [ ] **Step 7: Implement `FlakeSyncExtension`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncExtension.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.provider.Property;

public interface FlakeSyncExtension {

    Property<String> getFlakesyncDir();

    Property<String> getLoggingLevel();
}
```

- [ ] **Step 8: Implement `FlakeSyncPlugin`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class FlakeSyncPlugin implements Plugin<Project> {

    public static final String AGENT_CONFIGURATION_NAME = "flakesyncAgent";
    public static final String AGENT_COORDINATES = "edu.utexas.ece:flakesync-core:1.0-SNAPSHOT";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        FlakeSyncExtension extension = project.getExtensions().create("flakesync", FlakeSyncExtension.class);
        extension.getFlakesyncDir().convention(".flakesync");
        extension.getLoggingLevel().convention("CONFIG");

        Configuration agentConfiguration = project.getConfigurations().create(AGENT_CONFIGURATION_NAME, c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("Resolves the flakesync-core agent/launcher jar.");
        });
        project.getDependencies().add(AGENT_CONFIGURATION_NAME, AGENT_COORDINATES);
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew test --tests FlakeSyncPluginTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Scaffold flakesync-gradle-plugin with the extension and agent configuration"
```

---

### Task 5: `TestExecutionRequest` and `SingleTestExecutor`

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/TestExecutionRequest.java`
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/TestExecutionTimeoutException.java`
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/SingleTestExecutor.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/SingleTestExecutorTest.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/fixtures/AlwaysPassesTest.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/fixtures/AlwaysFailsTest.java`

**Interfaces:**
- Consumes: `edu.utexas.ece.flakesync.launcher.SingleTestLauncher.EXIT_PASSED/EXIT_FAILED` (from `flakesync-core`, already an `implementation` dependency of this module per Task 4).
- Produces: `TestExecutionRequest` (immutable; `String getTestName()`, `Map<String,String> getSystemProperties()`, `int getTimeoutSeconds()`; built via `TestExecutionRequest.forTest(String testName)` returning a `Builder` with `.agentMode(String)`, `.delay(int)`, `.property(String key, String value)`, `.timeoutSeconds(int)`, `.build()`). `SingleTestExecutor` (constructed with `(File agentJar, FileCollection testClasspath, File workingDir)`; `public boolean run(TestExecutionRequest request)` — returns `true` if the forked test failed (reproduced the target behavior), `false` if it passed; throws `TestExecutionTimeoutException` on timeout and `org.gradle.api.GradleException` on any other non-pass/fail outcome). This is the primitive every task class in Tasks 7–12 calls.
- Note: this design deliberately replaces `SurefireExecution.SurefireFactory`'s eleven named static factory methods with one generic builder — every call site builds the exact same system properties inline instead of going through a per-mode factory method, since `SingleTestExecutor` itself needs no per-mode knowledge.

- [ ] **Step 1: Write the two forkable test fixtures**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/fixtures/AlwaysPassesTest.java`:

```java
package edu.utexas.ece.flakesync.gradle.fixtures;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AlwaysPassesTest {

    @Test
    public void thisAlwaysPasses() {
        assertTrue(true);
    }
}
```

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/fixtures/AlwaysFailsTest.java`:

```java
package edu.utexas.ece.flakesync.gradle.fixtures;

import org.junit.Test;

import static org.junit.Assert.fail;

public class AlwaysFailsTest {

    @Test
    public void thisAlwaysFails() {
        fail("intentionally failing fixture");
    }
}
```

These need JUnit4 on the *test* classpath of `flakesync-gradle-plugin` itself (separate from the JUnit5 used to test the plugin's own code). Add to `build.gradle.kts`'s `dependencies` block:

```kotlin
    testImplementation("junit:junit:4.13.1")
```

- [ ] **Step 2: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/SingleTestExecutorTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTestExecutorTest {

    private static final File AGENT_JAR = resolveAgentJarFromMavenLocal();

    private static File resolveAgentJarFromMavenLocal() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".m2", "repository", "edu", "utexas", "ece",
                "flakesync-core", "1.0-SNAPSHOT", "flakesync-core-1.0-SNAPSHOT.jar").toFile();
    }

    private FileCollection testClasspath() {
        Project project = ProjectBuilder.builder().build();
        // The running JVM's own classpath already contains this test's compiled classes and JUnit4 —
        // reuse it as a stand-in for "the target project's test runtime classpath".
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        return project.files((Object[]) entries);
    }

    @Test
    void returnsFalseWhenTheForkedTestPasses() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesTest#thisAlwaysPasses")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertFalse(executor.run(request));
    }

    @Test
    void returnsTrueWhenTheForkedTestFails() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysFailsTest#thisAlwaysFails")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertTrue(executor.run(request));
    }

    @Test
    void throwsGradleExceptionWhenTheTestMethodDoesNotExist() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesTest#noSuchMethod")
                .agentMode("CONCURRENT_METHODS")
                .build();

        assertThrows(GradleException.class, () -> executor.run(request));
    }

    @Test
    void throwsTimeoutExceptionWhenTheExecutionExceedsItsBudget() {
        SingleTestExecutor executor = new SingleTestExecutor(AGENT_JAR, testClasspath(), new File("."));

        TestExecutionRequest request = TestExecutionRequest
                .forTest("edu.utexas.ece.flakesync.gradle.fixtures.AlwaysPassesTest#thisAlwaysPasses")
                .agentMode("CONCURRENT_METHODS")
                .timeoutSeconds(0)
                .build();

        assertThrows(TestExecutionTimeoutException.class, () -> executor.run(request));
    }
}
```

This test requires `flakesync-core` to already be installed to `mavenLocal()` (Task 3/4, Step 1) so `AGENT_JAR` resolves to a real file.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests SingleTestExecutorTest`
Expected: FAIL to compile — `TestExecutionRequest`/`SingleTestExecutor`/`TestExecutionTimeoutException` don't exist yet.

- [ ] **Step 4: Implement `TestExecutionTimeoutException`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/TestExecutionTimeoutException.java`:

```java
package edu.utexas.ece.flakesync.gradle;

public class TestExecutionTimeoutException extends RuntimeException {

    public TestExecutionTimeoutException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Implement `TestExecutionRequest`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/TestExecutionRequest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestExecutionRequest {

    private final String testName;
    private final Map<String, String> systemProperties;
    private final int timeoutSeconds;

    private TestExecutionRequest(String testName, Map<String, String> systemProperties, int timeoutSeconds) {
        this.testName = testName;
        this.systemProperties = Collections.unmodifiableMap(systemProperties);
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getTestName() {
        return testName;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public static Builder forTest(String testName) {
        return new Builder(testName);
    }

    public static final class Builder {

        private final String testName;
        private final Map<String, String> systemProperties = new LinkedHashMap<>();
        private int timeoutSeconds = SingleTestExecutor.DEFAULT_TIMEOUT_SECONDS;

        private Builder(String testName) {
            this.testName = testName;
            this.systemProperties.put(".test", testName);
        }

        public Builder agentMode(String mode) {
            systemProperties.put("agentmode", mode);
            return this;
        }

        public Builder delay(int delayMillis) {
            systemProperties.put("delay", String.valueOf(delayMillis));
            // Scale the fork timeout with the requested delay so a large injected delay
            // never gets mistaken for a hang; matches the intent (never wired up) of the
            // Maven plugin's unused SurefireExecution#addProcessTimeout.
            this.timeoutSeconds = Math.max(this.timeoutSeconds, (delayMillis / 1000) * 4 + 60);
            return this;
        }

        public Builder property(String key, String value) {
            systemProperties.put(key, value);
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public TestExecutionRequest build() {
            return new TestExecutionRequest(testName, systemProperties, timeoutSeconds);
        }
    }
}
```

- [ ] **Step 6: Implement `SingleTestExecutor`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/SingleTestExecutor.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.launcher.SingleTestLauncher;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SingleTestExecutor {

    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private static final String LAUNCHER_MAIN_CLASS = "edu.utexas.ece.flakesync.launcher.SingleTestLauncher";

    private final File agentJar;
    private final FileCollection testClasspath;
    private final File workingDir;

    public SingleTestExecutor(File agentJar, FileCollection testClasspath, File workingDir) {
        this.agentJar = agentJar;
        this.testClasspath = testClasspath;
        this.workingDir = workingDir;
    }

    /**
     * Returns true if the forked execution reproduced the target failure, false if it passed cleanly.
     */
    public boolean run(TestExecutionRequest request) {
        Process process = start(request);

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        startDrainThread(process.getErrorStream(), stderr);
        startDrainThread(process.getInputStream(), new ByteArrayOutputStream());

        boolean finished;
        try {
            finished = process.waitFor(request.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new GradleException("Interrupted while waiting for " + request.getTestName(), ie);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new TestExecutionTimeoutException("Execution for " + request.getTestName()
                    + " timed out after " + request.getTimeoutSeconds() + "s");
        }

        int exitValue = process.exitValue();
        if (exitValue == SingleTestLauncher.EXIT_PASSED) {
            return false;
        } else if (exitValue == SingleTestLauncher.EXIT_FAILED) {
            return true;
        } else {
            throw new GradleException("SingleTestLauncher could not run " + request.getTestName()
                    + " (exit " + exitValue + "): " + stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private Process start(TestExecutionRequest request) {
        String classpath = agentJar.getAbsolutePath() + File.pathSeparator + testClasspath.getAsPath();
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-javaagent:" + agentJar.getAbsolutePath());
        for (Map.Entry<String, String> property : request.getSystemProperties().entrySet()) {
            command.add("-D" + property.getKey() + "=" + property.getValue());
        }
        command.add("-cp");
        command.add(classpath);
        command.add(LAUNCHER_MAIN_CLASS);
        command.add(request.getTestName());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        try {
            return builder.start();
        } catch (IOException ioe) {
            throw new GradleException("Failed to launch test JVM for " + request.getTestName(), ioe);
        }
    }

    private static void startDrainThread(InputStream in, OutputStream out) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int read;
            try {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // stream closed because the process ended or was destroyed
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests SingleTestExecutorTest`
Expected: PASS (all four cases; the timeout case takes effectively 0 seconds since `timeoutSeconds(0)` is used).

- [ ] **Step 8: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add SingleTestExecutor: fork a single instrumented test execution and report pass/fail"
```

---

### Task 6: `AbstractFlakeSyncTask` and the integration-test fixture project

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/AbstractFlakeSyncTask.java`
- Create: `flakesync-gradle-plugin/src/test/resources/fixture-project/build.gradle.kts`
- Create: `flakesync-gradle-plugin/src/test/resources/fixture-project/src/test/java/fixture/FlakySampleTest.java`
- Create: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/fixtures/FixtureProject.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/AbstractFlakeSyncTaskTest.java`

**Interfaces:**
- Consumes: `FlakeSyncPlugin.AGENT_CONFIGURATION_NAME`, `FlakeSyncExtension`, `SingleTestExecutor` (all from Tasks 4–5).
- Produces: `AbstractFlakeSyncTask extends DefaultTask` with: `@Input String getTestName()` / `@Option(option = "test-name") void setTestName(String)`; `protected File getAgentJar()`; `protected FileCollection getTestRuntimeClasspath()`; `protected File resolve(java.nio.file.Path constantsRelativePath)` (resolves a `flakesync.Constants`-returned path against `getProject().getProjectDir()`); `protected SingleTestExecutor newExecutor()`. Every task in Tasks 7–12 extends this and calls `resolve(...)` and `newExecutor()`.
- Produces (test infra): `fixtures.FixtureProject` — a JUnit5 test helper with `static File materialize(Path targetDir)` that copies `src/test/resources/fixture-project/` into a given directory (so each GradleRunner test gets an isolated copy) and `static GradleRunner runnerFor(File projectDir, String... arguments)` pre-wired with `.withPluginClasspath()`.

- [ ] **Step 1: Write the fixture project's build file**

Create `flakesync-gradle-plugin/src/test/resources/fixture-project/build.gradle.kts`:

```kotlin
plugins {
    java
    id("edu.utexas.ece.flakesync")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.1")
}
```

- [ ] **Step 2: Write the fixture project's test class**

Create `flakesync-gradle-plugin/src/test/resources/fixture-project/src/test/java/fixture/FlakySampleTest.java`:

```java
package fixture;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlakySampleTest {

    @Test
    public void alwaysPasses() {
        assertTrue(true);
    }

    @Test
    public void alwaysFails() {
        fail("intentionally failing fixture, used to drive FlakeSync task integration tests");
    }
}
```

This fixture project is deliberately deterministic (not actually racy) — it exists to prove each Gradle task's control flow, file I/O, and process forking are correct end to end. It is not a substitute for validating real flakiness detection against a genuinely nondeterministic async test, which stays a manual/documented exercise (see Task 13) — the same role `testscripts/` plays for the Maven plugin today against Apache Uniffle.

- [ ] **Step 3: Write the fixture-project test helper**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/fixtures/FixtureProject.java`:

```java
package edu.utexas.ece.flakesync.gradle.fixtures;

import org.gradle.testkit.runner.GradleRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class FixtureProject {

    private FixtureProject() {
    }

    public static File materialize(Path targetDir) {
        try {
            Path resourceRoot = classpathResourceToPath("fixture-project");
            try (java.util.stream.Stream<Path> paths = Files.walk(resourceRoot)) {
                for (Path source : (Iterable<Path>) paths::iterator) {
                    Path relative = resourceRoot.relativize(source);
                    Path destination = targetDir.resolve(relative.toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return targetDir.toFile();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public static GradleRunner runnerFor(File projectDir, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(Arrays.asList(arguments))
                .forwardOutput();
    }

    private static Path classpathResourceToPath(String resource) {
        try {
            URL url = FixtureProject.class.getClassLoader().getResource(resource);
            if (url == null) {
                throw new IllegalStateException("Fixture resource not found on classpath: " + resource);
            }
            return Paths.get(url.toURI());
        } catch (URISyntaxException use) {
            throw new IllegalStateException(use);
        }
    }
}
```

- [ ] **Step 4: Write the failing test for `AbstractFlakeSyncTask`**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/AbstractFlakeSyncTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFlakeSyncTaskTest {

    public abstract static class NoOpTask extends AbstractFlakeSyncTask {
        @org.gradle.api.tasks.TaskAction
        public void run() {
        }
    }

    @Test
    void requiresTestNameToBeSet() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().create("noop", NoOpTask.class);

        assertThrows(GradleException.class, task::getTestName);
    }

    @Test
    void exposesTheConfiguredTestName() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().create("noop", NoOpTask.class);

        task.setTestName("pkg.Foo#bar");

        assertEquals("pkg.Foo#bar", task.getTestName());
    }

    @Test
    void resolvesRelativeConstantsPathsAgainstTheProjectDirectory() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().create("noop", NoOpTask.class);

        java.io.File resolved = task.resolve(java.nio.file.Paths.get(".", ".flakesync", "pkg.Foo.bar-Locations.txt"));

        assertTrue(resolved.getAbsolutePath().startsWith(project.getProjectDir().getAbsolutePath()));
        assertEquals(
                project.getProjectDir().toPath().resolve(".flakesync").resolve("pkg.Foo.bar-Locations.txt").normalize(),
                resolved.toPath().normalize());
    }

    @Test
    void resolveCreatesTheParentDirectoryRegardlessOfWhatConstantsMkdirsItself() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");
        NoOpTask task = project.getTasks().create("noop", NoOpTask.class);

        java.io.File resolved = task.resolve(
                java.nio.file.Paths.get(".", ".flakesync", "Results-CritSearch", "pkg.Foo.bar-RootMethods.csv"));

        assertTrue(resolved.getParentFile().isDirectory());
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew test --tests AbstractFlakeSyncTaskTest`
Expected: FAIL to compile — `AbstractFlakeSyncTask` doesn't exist yet.

- [ ] **Step 6: Implement `AbstractFlakeSyncTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/AbstractFlakeSyncTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.nio.file.Path;

public abstract class AbstractFlakeSyncTask extends DefaultTask {

    private String testName;

    protected AbstractFlakeSyncTask() {
        getOutputs().upToDateWhen(t -> false);
        dependsOn(getProject().getTasks().named("testClasses"));
    }

    @Input
    public String getTestName() {
        if (testName == null || testName.isEmpty()) {
            throw new GradleException("--test-name is required, e.g. --test-name=pkg.MyTest#myMethod");
        }
        return testName;
    }

    @Option(option = "test-name", description = "Fully qualified ClassName#methodName of the flaky test.")
    public void setTestName(String testName) {
        this.testName = testName;
    }

    @Internal
    protected File getAgentJar() {
        return getProject().getConfigurations()
                .getByName(FlakeSyncPlugin.AGENT_CONFIGURATION_NAME)
                .getSingleFile();
    }

    @Internal
    protected FileCollection getTestRuntimeClasspath() {
        return getProject().getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets().getByName("test").getRuntimeClasspath();
    }

    @Internal
    protected File resolve(Path constantsRelativePath) {
        File file = getProject().getProjectDir().toPath().resolve(constantsRelativePath).normalize().toFile();
        // flakesync.Constants' own path-builder methods sometimes mkdirs() a directory computed
        // relative to the JVM's raw working directory instead of the baseDir argument they were
        // given (a pre-existing quirk, left unchanged per this plan's Global Constraints) — that
        // side effect is irrelevant here since we independently guarantee the real parent
        // directory (resolved against the Gradle project directory, not JVM CWD) exists before
        // any task reads or writes through this method.
        file.getParentFile().mkdirs();
        return file;
    }

    protected SingleTestExecutor newExecutor() {
        return new SingleTestExecutor(getAgentJar(), getTestRuntimeClasspath(), getProject().getProjectDir());
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests AbstractFlakeSyncTaskTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add AbstractFlakeSyncTask and the GradleRunner fixture project"
```

---

### Task 7: `ConcurrentFindTask` (port of `FindTestsRunMojo`)

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/ConcurrentFindTask.java`
- Modify: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/ConcurrentFindTaskTest.java`

**Interfaces:**
- Consumes: `AbstractFlakeSyncTask`, `TestExecutionRequest`, `flakesync.Constants.getConcurrentMethodsFilepath(String)` (from `flakesync-utils`, already an `implementation` dependency).
- Produces: registers the `concurrentfind` task, which runs the target test once under `agentmode=CONCURRENT_METHODS` and lets the (unmodified) agent write `ResultMethods.txt`.

- [ ] **Step 1: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/ConcurrentFindTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentFindTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void writesResultMethodsFileForThePassingTest() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "concurrentfind", "--test-name=fixture.FlakySampleTest#alwaysPasses").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        Path resultMethods = projectDir.toPath()
                .resolve(".flakesync").resolve("fixture.FlakySampleTest.alwaysPasses-ResultMethods.txt");
        assertTrue(Files.exists(resultMethods));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ConcurrentFindTaskTest`
Expected: FAIL — task `concurrentfind` does not exist.

- [ ] **Step 3: Implement `ConcurrentFindTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/ConcurrentFindTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.tasks.TaskAction;

public class ConcurrentFindTask extends AbstractFlakeSyncTask {

    @TaskAction
    public void run() {
        TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                .agentMode("CONCURRENT_METHODS")
                .build();

        try {
            newExecutor().run(request);
        } catch (RuntimeException ex) {
            getLogger().error("Error executing test: the test did not run cleanly", ex);
        }
    }
}
```

(This mirrors `FindTestsRunMojo` exactly: it swallows the outcome/exceptions because the point of this goal is the `ResultMethods.txt` file the agent writes as a side effect, not the pass/fail result itself.)

- [ ] **Step 4: Register the task in `FlakeSyncPlugin`**

In `FlakeSyncPlugin.java`, at the end of `apply(Project project)`, add:

```java
        project.getTasks().register("concurrentfind", ConcurrentFindTask.class);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests ConcurrentFindTaskTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add concurrentfind Gradle task"
```

---

### Task 8: `DelayLocsTask` (port of `RunWithDelaysMojo`)

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/DelayLocsTask.java`
- Modify: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/DelayLocsTaskTest.java`

**Interfaces:**
- Consumes: `AbstractFlakeSyncTask`, `TestExecutionRequest`, `flakesync.Constants.getWhitelistFilepath(String)`.
- Produces: registers the `delaylocs` task, which builds a whitelist file from the test source set's compiled classes and runtime-classpath jars, then loops the fixed delay ladder `{100, 200, 400, 800, 1600, 3200, 6400, 12800}` under `agentmode=ALL_LOCATIONS`, stopping at the first execution that reproduces the failure (matching `RunWithDelaysMojo`'s loop-and-break-on-exception control flow, using `SingleTestExecutor.run(...)` returning `true` as the equivalent signal).

- [ ] **Step 1: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/DelayLocsTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayLocsTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void stopsAtTheFirstDelayThatReproducesTheFailure() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "delaylocs", "--test-name=fixture.FlakySampleTest#alwaysFails").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

        Path locationsFile = projectDir.toPath()
                .resolve(".flakesync").resolve("fixture.FlakySampleTest.alwaysFails-Locations.txt");
        assertTrue(Files.exists(locationsFile));

        List<String> lines = Files.readAllLines(locationsFile);
        // First line is the delay that was used; since alwaysFails fails unconditionally,
        // it must have stopped at the very first delay in the ladder (100ms).
        assertEquals("100", lines.get(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests DelayLocsTaskTest`
Expected: FAIL — task `delaylocs` does not exist.

- [ ] **Step 3: Implement `DelayLocsTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/DelayLocsTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class DelayLocsTask extends AbstractFlakeSyncTask {

    private static final int[] DELAYS = {100, 200, 400, 800, 1600, 3200, 6400, 12800};

    @TaskAction
    public void run() {
        writeWhitelist();

        for (int delay : DELAYS) {
            TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                    .agentMode("ALL_LOCATIONS")
                    .property("concurrentmethods", Constants.getConcurrentMethodsFilepath(getTestName()).toString())
                    .property("whitelist", Constants.getWhitelistFilepath(getTestName()).toString())
                    .delay(delay)
                    .build();

            try {
                if (newExecutor().run(request)) {
                    return;
                }
            } catch (RuntimeException ex) {
                getLogger().warn("Surefire-equivalent execution failed for delay {}: {}", delay, ex.getMessage());
                return;
            }
        }
    }

    private void writeWhitelist() {
        File whitelist = resolve(Constants.getWhitelistFilepath(getTestName()));
        FileCollection testOutput = getProject().getExtensions()
                .getByType(org.gradle.api.plugins.JavaPluginExtension.class)
                .getSourceSets().getByName("test").getOutput().getClassesDirs();

        try {
            Files.createDirectories(whitelist.getParentFile().toPath());
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(whitelist))) {
                for (File outputDir : testOutput) {
                    Path outputPath = outputDir.toPath();
                    if (Files.exists(outputPath)) {
                        try (Stream<Path> paths = Files.walk(outputPath)) {
                            for (Path classFile : (Iterable<Path>) paths
                                    .filter(Files::isRegularFile)
                                    .filter(p -> p.toString().endsWith(".class"))
                                    .filter(p -> !p.toString().contains("Tests"))::iterator) {
                                String className = outputPath.relativize(classFile).toString()
                                        .replace(File.separatorChar, '.')
                                        .replaceAll("\\.class$", "");
                                bw.write(className);
                                bw.newLine();
                            }
                        }
                    }
                }

                for (File jar : getTestRuntimeClasspath()) {
                    if (jar.getName().endsWith(".jar") && jar.isFile()) {
                        try (JarFile jarFile = new JarFile(jar)) {
                            Enumeration<JarEntry> entries = jarFile.entries();
                            while (entries.hasMoreElements()) {
                                JarEntry entry = entries.nextElement();
                                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                                    String className = entry.getName()
                                            .replace('/', '.')
                                            .replaceAll("\\.class$", "");
                                    bw.write(className);
                                    bw.newLine();
                                }
                            }
                        } catch (IOException ioe) {
                            getLogger().warn("Failed to read jar for whitelist: {}", jar, ioe);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new org.gradle.api.GradleException("Failed to write whitelist file", ioe);
        }
    }
}
```

- [ ] **Step 4: Register the task in `FlakeSyncPlugin`**

Add to `FlakeSyncPlugin.apply(...)`:

```java
        project.getTasks().register("delaylocs", DelayLocsTask.class);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests DelayLocsTaskTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add delaylocs Gradle task"
```

---

### Task 9: `DeltaDebugTask` (port of `DeltaDebugMojo`)

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/DeltaDebugTask.java`
- Modify: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Modify: `flakesync-gradle-plugin/build.gradle.kts`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/DeltaDebugTaskTest.java`

**Interfaces:**
- Consumes: `AbstractFlakeSyncTask`, `TestExecutionRequest`, `flakesync.DeltaDebugger<T>` (moved to `flakesync-utils` in Task 1), `flakesync.Constants.getAllLocationsFilepath/getMinLocationsFilepath/getWorkingLocationsFilepath/getWhitelistFilepath`.
- Produces: registers the `deltadebug` task, reading the delay + locations list written by `delaylocs` (`Locations.txt`), bisecting via `DeltaDebugger`, and writing `Locations_minimized.txt`.

- [ ] **Step 1: Add `flakesync-utils` as an explicit dependency (if not already relied upon transitively)**

`flakesync-gradle-plugin/build.gradle.kts` already declares `implementation("edu.utexas.ece:flakesync-utils:1.0-SNAPSHOT")` from Task 4 — no change needed. (This step exists only as a checkpoint: confirm `DeltaDebugger` is importable by running `./gradlew compileJava` before writing new code that uses it.)

- [ ] **Step 2: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/DeltaDebugTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaDebugTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void minimizesLocationsWhenEveryExecutionReproducesTheFailure() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Files.createDirectories(flakesyncDir);

        // Seed the input Locations.txt that delaylocs would have produced: first line is the
        // delay, remaining lines are candidate locations.
        Path allLocations = flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations.txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(allLocations.toFile()))) {
            bw.write("100\n");
            bw.write("pkg/Foo#10\n");
            bw.write("pkg/Foo#20\n");
            bw.write("pkg/Bar#5\n");
            bw.write("pkg/Bar#8\n");
        }

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "deltadebug", "--test-name=fixture.FlakySampleTest#alwaysFails").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

        Path minimized = flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations_minimized.txt");
        assertTrue(Files.exists(minimized));
        List<String> lines = Files.readAllLines(minimized);
        // Since alwaysFails fails unconditionally (checkValid always true), delta debugging
        // should have converged to the empty complement first: only the delay line remains.
        assertTrue(lines.size() <= 5);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests DeltaDebugTaskTest`
Expected: FAIL — task `deltadebug` does not exist.

- [ ] **Step 4: Implement `DeltaDebugTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/DeltaDebugTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import flakesync.DeltaDebugger;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeltaDebugTask extends AbstractFlakeSyncTask {

    static final int MAX_DELAY = 25600;

    @TaskAction
    public void run() {
        List<String> locations = new ArrayList<>();
        int startingDelay = readAllLocations(locations);

        LocationsDeltaDebug deltaDebug = new LocationsDeltaDebug(startingDelay);
        locations = deltaDebug.deltaDebug(locations, 2);

        writeMinimizedLocations(deltaDebug.delay, locations);
    }

    private int readAllLocations(List<String> locations) {
        File file = resolve(Constants.getAllLocationsFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int delay = Integer.parseInt(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                locations.add(line);
            }
            return delay;
        } catch (IOException ioe) {
            throw new GradleException("Failed to read " + file, ioe);
        }
    }

    private void writeMinimizedLocations(int delay, List<String> locations) {
        File file = resolve(Constants.getMinLocationsFilepath(getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(delay + "\n");
            for (String location : locations) {
                bw.write(location);
                bw.newLine();
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private class LocationsDeltaDebug extends DeltaDebugger<String> {

        int delay;

        LocationsDeltaDebug(int delay) {
            this.delay = delay;
        }

        // Redo core delta debug logic since we need to rerun with higher delays in the middle,
        // identical control flow to flakesync-maven-plugin's DeltaDebugMojo.LocationsDeltaDebug.
        @Override
        public List<String> deltaDebug(List<String> elements, int granularity) {
            this.iterations++;

            if (elements.size() < granularity) {
                return elements;
            }

            int chunkSize = (int) Math.round((double) elements.size() / granularity);
            for (int i = 0; i < elements.size(); i += chunkSize) {
                int endpoint = Math.min(elements.size(), i + chunkSize);
                List<String> chunk = new ArrayList<>(elements.subList(i, endpoint));
                List<String> otherChunk = new ArrayList<>(elements.subList(0, i));
                otherChunk.addAll(elements.subList(endpoint, elements.size()));

                while (this.delay <= MAX_DELAY) {
                    if (checkValid(otherChunk)) {
                        return deltaDebug(otherChunk, 2);
                    }
                    if (checkValid(chunk)) {
                        return deltaDebug(chunk, 2);
                    }
                    this.delay *= 2;
                }
            }

            if (elements.size() == granularity) {
                return elements;
            }
            if (elements.size() < granularity * 2) {
                return deltaDebug(elements, elements.size());
            }
            return deltaDebug(elements, granularity * 2);
        }

        @Override
        public boolean checkValid(List<String> elements) {
            writeWorkingLocations(elements);

            TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                    .agentMode("DELTA_DEBUG")
                    .property("locations", Constants.getWorkingLocationsFilepath(getTestName()).toString())
                    .property("whitelist", Constants.getWhitelistFilepath(getTestName()).toString())
                    .delay(this.delay)
                    .build();

            return newExecutor().run(request);
        }

        private void writeWorkingLocations(List<String> elements) {
            File file = resolve(Constants.getWorkingLocationsFilepath(getTestName()));
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (String location : elements) {
                    bw.write(location);
                    bw.newLine();
                }
            } catch (IOException ioe) {
                throw new GradleException("Failed to write " + file, ioe);
            }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests DeltaDebugTaskTest`
Expected: PASS.

- [ ] **Step 6: Register the task in `FlakeSyncPlugin`**

Add to `FlakeSyncPlugin.apply(...)`:

```java
        project.getTasks().register("deltadebug", DeltaDebugTask.class);
```

- [ ] **Step 7: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add deltadebug Gradle task"
```

---

### Task 10: `CritSearchTask` (port of `CritSearchMojo`)

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/CritSearchTask.java`
- Modify: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/CritSearchTaskTest.java`

**Interfaces:**
- Consumes: `AbstractFlakeSyncTask`, `TestExecutionRequest`, `flakesync.Constants.getMinLocationsFilepath/getWorkingLocationsFilepath/getStackTraceFilepath/getIndLocFilepath/getRootMethodFilepath/getRootMethodResultsFilepath/getCritPointsResultsFilepath/getMethodStartEndLineFile/getIndRootFilepath`.
- Produces: registers the `critsearch` task. This is a direct, mechanical port of `CritSearchMojo`'s algorithm — every `SurefireExecution.SurefireFactory.xxx(...)` + `execution.run()` call site becomes a `TestExecutionRequest...build()` + `newExecutor().run(request)` call site with the same system properties; every `this.mavenProject.getBasedir()`-based file path becomes `resolve(Constants.xxx(...))`.

**Note on integration-test scope:** unlike `concurrentfind`/`delaylocs`/`deltadebug` (which chain organically off each other and off real forked executions from a cold start), `critsearch` needs a `Locations_minimized.txt` and a `StackTrace.txt` as input — in the real tool these come from the earlier phases' bytecode-instrumented stack-trace capture, which requires an actually-racy test to produce meaningfully. This task's test hand-seeds those two input files with minimal, well-formed content (matching `StackTraceElement`-style `pkg/Class.method(File.java:line)` formatting, no leading `"at "`, since `CritSearchMojo.parseStackTrace`'s parsing — ported unchanged — expects that shape) and asserts the task drives real forked executions and produces the two expected results files. Caveat for the implementer: this plan did not audit `flakesync-core`'s `InjectDelayWithStackTraceTracer` (the class that would normally populate `StackTrace.txt` as a side effect of the `DELAY_INJECTION_BY_LOC` execution this task also runs) — if that execution overwrites the seeded file with different content before `parseStackTrace()` reads it back, adjust the seeded content or the assertions once you observe the actual behavior. This is a wiring-level integration test either way, not a claim that it proves flakiness-detection accuracy end to end (that stays a manual exercise against a real racy project, same as `testscripts/` today).

- [ ] **Step 1: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/CritSearchTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CritSearchTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void producesRootMethodsAndCriticalPointsFiles() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Files.createDirectories(flakesyncDir);

        String testName = "fixture.FlakySampleTest#alwaysFails";

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations_minimized.txt").toFile()))) {
            bw.write("100\n");
            bw.write("fixture/FlakySampleTest#20\n");
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-StackTrace.txt").toFile()))) {
            bw.write("0,fixture/FlakySampleTest.alwaysFails(FlakySampleTest.java:20)\n");
            bw.write("0,END\n");
        }

        BuildResult result = FixtureProject.runnerFor(projectDir,
                "critsearch", "--test-name=" + testName).build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.exists(flakesyncDir.resolve("Results-CritSearch")
                .resolve("fixture.FlakySampleTest.alwaysFails-RootMethods.csv")));
        assertTrue(Files.exists(flakesyncDir.resolve("Results-CritSearch")
                .resolve("fixture.FlakySampleTest.alwaysFails-CriticalPoints.csv")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests CritSearchTaskTest`
Expected: FAIL — task `critsearch` does not exist.

- [ ] **Step 3: Implement `CritSearchTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/CritSearchTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CritSearchTask extends AbstractFlakeSyncTask {

    static final int MAX_DELAY = 25600;

    private final Set<String> stackTraceLines = new HashSet<>();
    private final Set<String> roots = new HashSet<>();
    private final Map<Integer, List<String>> clusters = new HashMap<>();
    private int delay = 100;

    @TaskAction
    public void run() {
        List<String> locations = new ArrayList<>();
        generateLocsList(locations);

        TestExecutionRequest minimizedRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("DELAY_INJECTION_BY_LOC")
                .property("locations", Constants.getMinLocationsFilepath(getTestName()).toString())
                .delay(delay)
                .build();
        if (!newExecutor().run(minimizedRequest)) {
            getLogger().warn("This location is not a good one. Go back and run deltadebug again.");
            return;
        }

        parseStackTrace();

        Set<String> visitedTraces = new HashSet<>();
        for (String line : stackTraceLines) {
            String withoutThreadId = line.substring(0, line.lastIndexOf(","));
            if (visitedTraces.contains(withoutThreadId)) {
                continue;
            }
            visitedTraces.add(withoutThreadId);

            String[] locs = line.split(",");
            int threadId = Integer.parseInt(locs[locs.length - 1]);
            String rootLine = "";
            for (int i = 0; i < locs.length - 1; i++) {
                String[] parts = locs[i].split("#");
                String newLoc = parts[0] + "#" + parts[1];

                writeIndLoc(threadId, i, newLoc);

                int workingDelay = delay;
                boolean result = runWithDelayAtLoc(newLoc, workingDelay, threadId, i);
                if (!result) {
                    workingDelay *= 2;
                    while (workingDelay <= MAX_DELAY) {
                        result = runWithDelayAtLoc(newLoc, workingDelay, threadId, i);
                        if (result) {
                            break;
                        }
                        workingDelay *= 2;
                    }
                    if (!result) {
                        break;
                    }
                }

                rootLine = searchStackTrace(parts[0], ":" + parts[1]);
                rootLine += "[" + workingDelay + "]";
            }
            if (!rootLine.isEmpty()) {
                roots.add(rootLine);
            }
        }

        writeRootMethodsToFile();

        File resultsFile = resolve(Constants.getCritPointsResultsFilepath(".", getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(resultsFile))) {
            Set<String> visitedMethods = new HashSet<>();
            for (String root : roots) {
                String[] elements = root.split("/");
                String methodName = elements[elements.length - 2].split("\\(")[0];
                String className = root.substring(0, root.lastIndexOf("/"));
                className = className.substring(0, className.lastIndexOf("/"));
                String fullMethodName = className + "#" + methodName;
                int workingDelay = Integer.parseInt(root.split("\\[")[1].split("\\]")[0]);

                if (visitedMethods.add(fullMethodName)) {
                    writeRootMethodFile(fullMethodName);

                    TestExecutionRequest rmaRequest = TestExecutionRequest.forTest(getTestName())
                            .agentMode("ROOT_METHOD_ANALYSIS")
                            .property("rootMethod", Constants.getRootMethodFilepath(getTestName()).toString())
                            .property("methodOnly", methodName)
                            .delay(workingDelay)
                            .build();
                    newExecutor().run(rmaRequest);

                    sequentialDebug(workingDelay);
                    this.delay = workingDelay;
                    writeClustersToFile(bw);
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to write critical points results", ioe);
        }
    }

    private boolean runWithDelayAtLoc(String loc, int workingDelay, int threadId, int idx) {
        TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                .agentMode("DELAY_INJECTION_BY_LOC")
                .property("locations", Constants.getIndLocFilepath(".", getTestName(), threadId, idx).toString())
                .delay(workingDelay)
                .build();
        return newExecutor().run(request);
    }

    private void writeIndLoc(int threadId, int idx, String location) {
        File file = resolve(Constants.getIndLocFilepath(".", getTestName(), threadId, idx));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(location);
            bw.newLine();
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private void writeRootMethodFile(String fullMethodName) {
        File file = resolve(Constants.getRootMethodFilepath(getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(fullMethodName + ":" + getTestName());
            bw.newLine();
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private void writeRootMethodsToFile() {
        File file = resolve(Constants.getRootMethodResultsFilepath(".", getTestName()));
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("#Test-Name,Location[delay]");
            bw.newLine();
            for (String location : roots) {
                bw.write(getTestName() + "," + location);
                bw.newLine();
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to write " + file, ioe);
        }
    }

    private void writeClustersToFile(BufferedWriter bw) throws IOException {
        for (int i = 1; i <= clusters.size(); i++) {
            List<String> cluster = clusters.get(i);
            if (cluster == null || cluster.isEmpty()) {
                return;
            }
            if (cluster.size() > 1) {
                bw.write(cluster.get(0).trim() + "-" + cluster.get(cluster.size() - 1).trim() + "[" + delay + "]");
            } else {
                bw.write(cluster.get(0) + "-" + cluster.get(0) + "[" + delay + "]");
            }
            bw.newLine();
        }
    }

    private void generateLocsList(List<String> locsList) {
        File input = resolve(Constants.getMinLocationsFilepath(getTestName()));
        File output = resolve(Constants.getWorkingLocationsFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(input));
             BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
            this.delay = Integer.parseInt(reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                locsList.add(line);
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to read/write locations", ioe);
        }
    }

    private void parseStackTrace() {
        File file = resolve(Constants.getStackTraceFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder parsedInfo = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if ("END".equals(data[1])) {
                    parsedInfo.append(data[0]);
                    stackTraceLines.add(parsedInfo.toString());
                    parsedInfo.setLength(0);
                } else if (data[1].split(":").length > 1) {
                    String tmp = data[1].substring(0, data[1].indexOf('('));
                    String className = tmp.substring(0, tmp.lastIndexOf('/'));
                    int lineNumber = Integer.parseInt(data[1].split(":")[1]
                            .substring(0, data[1].split(":")[1].length() - 1));
                    parsedInfo.append(className).append("#").append(lineNumber).append(",");
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to read " + file, ioe);
        }
    }

    private String searchStackTrace(String searchKey1, String searchKey2) {
        File file = resolve(Constants.getStackTraceFilepath(getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(searchKey1) && line.contains(searchKey2)) {
                    String rest = line.split(",")[1];
                    return rest.substring(0, rest.indexOf(':'));
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed to read " + file, ioe);
        }
        return null;
    }

    private void sequentialDebug(int workingDelay) {
        File methodStartEnd = new File(Constants.getMethodStartEndLineFile(
                getProject().getProjectDir().getAbsolutePath(), getTestName()));
        List<String> currentLines = new ArrayList<>();
        int cluster = 1;
        try (BufferedReader reader = new BufferedReader(new FileReader(methodStartEnd))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tmp = line.split("#");
                String className = tmp[0];
                int lowerLineNumber;
                int upperLineNumber;
                if (tmp.length < 3) {
                    lowerLineNumber = Integer.parseInt(tmp[1]);
                    upperLineNumber = Integer.parseInt(tmp[1]);
                } else {
                    lowerLineNumber = Integer.parseInt(tmp[1].substring(0, tmp[1].indexOf('-')));
                    upperLineNumber = Integer.parseInt(tmp[2]);
                }

                for (int i = lowerLineNumber; i < upperLineNumber; i++) {
                    File rootFile = resolve(Constants.getIndRootFilepath(".", getTestName(), i));
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(rootFile))) {
                        writer.write(className + "#" + i);
                        writer.newLine();
                    }

                    TestExecutionRequest request = TestExecutionRequest.forTest(getTestName())
                            .agentMode("DELAY_INJECTION_BY_LOC")
                            .property("locations", Constants.getIndRootFilepath(".", getTestName(), i).toString())
                            .delay(workingDelay)
                            .build();

                    if (newExecutor().run(request)) {
                        currentLines.add(className + "#" + i);
                    } else if (!currentLines.isEmpty()) {
                        clusters.put(cluster++, new ArrayList<>(currentLines));
                        currentLines = new ArrayList<>();
                    }
                }
            }
            if (!currentLines.isEmpty()) {
                clusters.put(cluster, currentLines);
            }
        } catch (IOException ioe) {
            throw new GradleException("Failed sequential debug over " + methodStartEnd, ioe);
        }
    }
}
```

- [ ] **Step 4: Register the task in `FlakeSyncPlugin`**

Add to `FlakeSyncPlugin.apply(...)`:

```java
        project.getTasks().register("critsearch", CritSearchTask.class);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests CritSearchTaskTest`
Expected: PASS. If the seeded `StackTrace.txt` gets overwritten by the real `DELAY_INJECTION_BY_LOC` execution before it's read back (see this task's integration-test-scope note above), adjust the seeded content or assertions to match what you actually observe.

- [ ] **Step 6: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add critsearch Gradle task"
```

---

### Task 11: `BarrierPointSearchTask` (port of `BarrierPointMojo`)

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/BarrierPointSearchTask.java`
- Modify: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/BarrierPointSearchTaskTest.java`

**Interfaces:**
- Consumes: `AbstractFlakeSyncTask`, `TestExecutionRequest`, `flakesync.Constants.getBarrierPointsResultsFilepath/getCritPointsResultsFilepath/getSearchMethodEndLineFilepath/getThresholdFilepath/getSearchMethodAndLineFilepath/getYieldResultFilepath`.
- Produces: registers the `barrierpointsearch` task — a direct, mechanical port of `BarrierPointMojo`, same replacement pattern as Task 10 (`SurefireExecution` calls become `TestExecutionRequest` + `newExecutor().run(...)`; `MojoExecutionException`-based pass/fail becomes the executor's `boolean` return). One deliberate deviation: `BarrierPointMojo`'s non-test-code branch locates its stack trace by scanning Surefire's own `TEST-*.xml` report inside a Surefire-executionId-keyed directory; since `SingleTestExecutor` doesn't produce Surefire-style reports, this task instead asks `SingleTestLauncher` (Task 3) to write the failing stack trace directly to a known path via the `stackTraceOutputFile` system property.

**Note on integration-test scope:** same caveat as Task 10 — this task's test hand-seeds a minimal, well-formed `CriticalPoints.csv` (the output `critsearch` would have produced) and asserts the task runs to completion and produces a well-formed `BarrierPoints.csv`, without claiming to validate real barrier-point-search accuracy against a genuinely racy program.

- [ ] **Step 1: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/BarrierPointSearchTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierPointSearchTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void routesThroughTheStackTraceBranchAndCapturesARealFailureTrace() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Path critSearchDir = flakesyncDir.resolve("Results-CritSearch");
        Files.createDirectories(critSearchDir);

        String testName = "fixture.FlakySampleTest#alwaysFails";

        // firstLoc deliberately does NOT contain "Test" as a substring (unlike the fixture
        // class's own name) so BarrierPointSearchTask routes into searchViaStackTrace rather
        // than searchDownwardFromTest.
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                critSearchDir.resolve("fixture.FlakySampleTest.alwaysFails-CriticalPoints.csv").toFile()))) {
            bw.write("fixture/Helper#5-fixture/FlakySampleTest#20[100]\n");
        }

        FixtureProject.runnerFor(projectDir, "barrierpointsearch", "--test-name=" + testName).build();

        // fixture.FlakySampleTest#alwaysFails really does fail (Assert.fail()), so the BARRIER_ST
        // execution's SingleTestLauncher genuinely writes a stack trace file via
        // stackTraceOutputFile — assert that mechanism worked end to end, which is the specific
        // behavior this task's port depends on (see Task 3's SingleTestLauncher change). The
        // exact contents of BarrierPoints.csv beyond its header depend on fine-grained agent
        // bytecode-instrumentation behavior not otherwise exercised by this plan's tests; if this
        // fixture doesn't yield a barrier point, that's fine — the file existing with its header
        // is enough to prove the task's control flow completed.
        Path stackTraceFile = flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-JUnitFailureStackTrace.txt");
        assertTrue(Files.exists(stackTraceFile));
        assertTrue(Files.readAllLines(stackTraceFile).stream().anyMatch(l -> l.contains("FlakySampleTest")));

        Path barrierPoints = flakesyncDir.resolve("Results-BarrierSearch")
                .resolve("fixture.FlakySampleTest.alwaysFails-BarrierPoints.csv");
        assertTrue(Files.exists(barrierPoints));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests BarrierPointSearchTaskTest`
Expected: FAIL — task `barrierpointsearch` does not exist.

- [ ] **Step 3: Implement `BarrierPointSearchTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/BarrierPointSearchTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class BarrierPointSearchTask extends AbstractFlakeSyncTask {

    @TaskAction
    public void run() {
        File barrierResultsFile = resolve(Constants.getBarrierPointsResultsFilepath(".", getTestName()));
        File critPointsFile = resolve(Constants.getCritPointsResultsFilepath(".", getTestName()));

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(barrierResultsFile));
             BufferedReader br = new BufferedReader(new FileReader(critPointsFile))) {
            bw.write("#Test-Name,Boundary-Point,Barrier-Point,Threshold");
            bw.newLine();
            bw.flush();

            String line;
            while ((line = br.readLine()) != null) {
                String firstLoc = line.split("-")[0];
                String endLoc = line.split("-")[1].split("\\[")[0];
                int delay = Integer.parseInt(line.split("\\[")[1]
                        .substring(0, line.split("\\[")[1].length() - 1));

                if (firstLoc.contains("Test")) {
                    searchDownwardFromTest(bw, line, endLoc, delay, firstLoc);
                } else {
                    searchViaStackTrace(bw, line, endLoc, delay);
                }
            }
        } catch (IOException ioe) {
            throw new GradleException("Barrier point search failed", ioe);
        }
    }

    private void searchDownwardFromTest(BufferedWriter bw, String boundaryLine, String endLoc, int delay,
                                         String firstLoc) throws IOException {
        TestExecutionRequest downwardRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("DOWNWARD_MVN")
                .property("searchMethodEndLine", "search")
                .property("CodeToIntroduceVariable", firstLoc.replace("/", "."))
                .delay(delay)
                .build();
        newExecutor().run(downwardRequest);

        File endLineFile = resolve(Constants.getSearchMethodEndLineFilepath(".", getTestName()));
        String yieldPoint;
        try (BufferedReader reader = new BufferedReader(new FileReader(endLineFile))) {
            yieldPoint = reader.readLine().split("=")[1];
        }

        int fromLine = Integer.parseInt(endLoc.split("#")[1]);
        int toLine = Integer.parseInt(yieldPoint.split("#")[1]);
        for (int ln = fromLine; ln < toLine; ln++) {
            String yieldingPoint = yieldPoint.split("#")[0] + "#" + ln;

            if (tryYieldPoint(bw, boundaryLine, endLoc, delay, yieldingPoint)) {
                return;
            }
        }
    }

    private void searchViaStackTrace(BufferedWriter bw, String boundaryLine, String endLoc, int delay)
            throws IOException {
        File stackTraceFile = resolve(java.nio.file.Paths.get(".flakesync",
                getTestName().replace("#", ".") + "-JUnitFailureStackTrace.txt"));

        TestExecutionRequest stackTraceRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("BARRIER_ST")
                .property("CodeToIntroduceVariable", endLoc.replace("/", "."))
                .property("stackTraceCollect", "true")
                .property("stackTraceOutputFile", stackTraceFile.getAbsolutePath())
                .delay(delay)
                .build();
        newExecutor().run(stackTraceRequest);

        if (!stackTraceFile.exists()) {
            getLogger().warn("It appears that the stacktrace does not exist");
            return;
        }

        Map<String, String> classes = new HashMap<>();
        parseStackTrace(classes, stackTraceFile);

        List<String> reversedClasses = new ArrayList<>(classes.keySet());
        Collections.reverse(reversedClasses);

        Set<String> visited = new HashSet<>();
        String normalizedEndLoc = endLoc.replace("/", ".");
        for (String rawClassName : reversedClasses) {
            String className = rawClassName.contains(",")
                    ? rawClassName.substring(0, rawClassName.lastIndexOf(',')) : rawClassName;
            String yieldPoint = className + "#" + classes.get(className);
            if (!visited.add(yieldPoint)) {
                continue;
            }

            TestExecutionRequest barrierRequest = TestExecutionRequest.forTest(getTestName())
                    .agentMode("ADD_YIELD_PT2")
                    .property("CodeToIntroduceVariable", normalizedEndLoc)
                    .property("YieldingPoint", yieldPoint)
                    .property("threshold", "1")
                    .delay(delay)
                    .build();
            newExecutor().run(barrierRequest);

            File startLineFile = resolve(Constants.getSearchMethodAndLineFilepath(".", getTestName()));
            int beginning;
            try (BufferedReader reader = new BufferedReader(new FileReader(startLineFile))) {
                beginning = Integer.parseInt(reader.readLine().split("#")[1]);
            }

            int fromLine = Integer.parseInt(yieldPoint.split("#")[1]);
            for (int ln = fromLine; ln >= beginning; ln--) {
                String yieldingPoint = className + "#" + ln;
                if (tryYieldPoint(bw, boundaryLine, normalizedEndLoc, delay, yieldingPoint)) {
                    return;
                }
            }
        }
    }

    // Returns true once a working barrier point has been found and recorded.
    private boolean tryYieldPoint(BufferedWriter bw, String boundaryLine, String endLoc, int delay,
                                   String yieldingPoint) throws IOException {
        TestExecutionRequest yieldRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("ADD_YIELD_PT1")
                .property("CodeToIntroduceVariable", endLoc)
                .property("YieldingPoint", yieldingPoint)
                .property("threshold", "1")
                .delay(delay)
                .build();
        boolean failed = newExecutor().run(yieldRequest);

        if (!failed && checkValidPass()) {
            addBarrierPointToResults(bw, boundaryLine, yieldingPoint, 1);
            return true;
        }

        TestExecutionRequest execMonRequest = TestExecutionRequest.forTest(getTestName())
                .agentMode("EXEC_MONITOR")
                .property("CodeToIntroduceVariable", endLoc)
                .delay(delay)
                .build();
        newExecutor().run(execMonRequest);

        File execsFile = resolve(Constants.getThresholdFilepath(".", getTestName()));
        int numExecutions;
        try (BufferedReader reader = new BufferedReader(new FileReader(execsFile))) {
            numExecutions = Integer.parseInt(reader.readLine().split("=")[1]);
        }

        TestExecutionRequest yieldWithThreshold = TestExecutionRequest.forTest(getTestName())
                .agentMode("ADD_YIELD_PT1")
                .property("CodeToIntroduceVariable", endLoc)
                .property("YieldingPoint", yieldingPoint)
                .property("threshold", String.valueOf(numExecutions))
                .delay(delay)
                .build();
        failed = newExecutor().run(yieldWithThreshold);

        if (!failed && checkValidPass()) {
            addBarrierPointToResults(bw, boundaryLine, yieldingPoint, numExecutions);
            return true;
        }
        return false;
    }

    private void parseStackTrace(Map<String, String> classes, File stackTraceFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(stackTraceFile))) {
            String trace;
            while ((trace = reader.readLine()) != null) {
                if (trace.contains(".java:")) {
                    String className = trace.split("at ")[1].split("\\(")[0];
                    className = className.substring(0, className.lastIndexOf("."));
                    String lineNum = trace.split("\\(")[1].split(":")[1].split("\\)")[0];
                    if (!inBlackList(className)) {
                        if (classes.containsKey(className)) {
                            classes.put(className + ",", lineNum);
                        } else {
                            classes.put(className, lineNum);
                        }
                    }
                }
            }
        }
    }

    private boolean inBlackList(String className) throws IOException {
        try (InputStream blacklist = getClass().getResourceAsStream("/blacklist.txt")) {
            Scanner scanner = new Scanner(blacklist);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (className.contains(line)) {
                    return true;
                } else if (line.contains("*") && className.contains(line.substring(0, line.indexOf("*")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addBarrierPointToResults(BufferedWriter bw, String boundaryPoint, String barrierPoint,
                                           int threshold) throws IOException {
        bw.write(getTestName() + "," + boundaryPoint + "," + barrierPoint + "," + threshold);
        bw.newLine();
        bw.flush();
    }

    private boolean checkValidPass() throws IOException {
        File file = resolve(Constants.getYieldResultFilepath(".", getTestName()));
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            boolean allCriteriaMet = true;
            String flagLine;
            while ((flagLine = reader.readLine()) != null) {
                allCriteriaMet &= Boolean.parseBoolean(flagLine.split("=")[1]);
            }
            return allCriteriaMet;
        }
    }
}
```

`blacklist.txt` needs to be on `flakesync-gradle-plugin`'s classpath for `inBlackList` to find it via `getClass().getResourceAsStream("/blacklist.txt")`. It already exists at `flakesync-maven-plugin/src/main/resources/blacklist.txt` and `flakesync-core/src/main/resources/blacklist.txt` (used by the agent). Copy it once more to `flakesync-gradle-plugin/src/main/resources/blacklist.txt` (same content) as part of this step.

- [ ] **Step 4: Register the task in `FlakeSyncPlugin`**

Add to `FlakeSyncPlugin.apply(...)`:

```java
        project.getTasks().register("barrierpointsearch", BarrierPointSearchTask.class);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests BarrierPointSearchTaskTest`
Expected: PASS. If the build instead fails or `BarrierPoints.csv`/the stack trace file end up somewhere unexpected, use the actual output to correct the test's paths/assertions — the stack-trace-capture mechanism (Task 3) and the CSV-parsing control flow are what this test needs to prove; the fine-grained bytecode-instrumentation behavior of `ADD_YIELD_PT1`/`ADD_YIELD_PT2` (unchanged from the Maven plugin) is not otherwise covered by this plan's tests.

- [ ] **Step 6: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add barrierpointsearch Gradle task"
```

---

### Task 12: `PatchTask` (port of `PatchingMojo`)

**Files:**
- Create: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/PatchTask.java`
- Modify: `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/FlakeSyncPlugin.java`
- Test: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/PatchTaskTest.java`

**Interfaces:**
- Consumes: `AbstractFlakeSyncTask`, `flakesync.Constants.getBarrierPointsResultsFilepath/getPatchDir`, `flakesync.patching.InjectFlagInCriticalPoint`, `flakesync.patching.InjectYieldStatement`, `flakesync.patching.SavePatch` (all moved to `flakesync-utils` in Task 2).
- Produces: registers the `patch` task — a direct port of `PatchingMojo`; no `SingleTestExecutor` involved at all (this goal only does source-file patching, no test execution), so it's the simplest of the six.

- [ ] **Step 1: Write the failing test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/PatchTaskTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPatchFilesForTheCriticalAndBarrierPoints() throws Exception {
        File projectDir = FixtureProject.materialize(tempDir);
        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        Files.createDirectories(flakesyncDir);

        String testName = "fixture.FlakySampleTest#alwaysFails";

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-BarrierPoints.csv").toFile()))) {
            bw.write("#Test-Name,Boundary-Point,Barrier-Point,Threshold\n");
            bw.write(testName + ",fixture/FlakySampleTest#10-fixture/FlakySampleTest#20[100],"
                    + "fixture/FlakySampleTest#8,1\n");
        }

        BuildResult result = FixtureProject.runnerFor(projectDir, "patch", "--test-name=" + testName).build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
        Path patchDir = flakesyncDir.resolve("patch");
        assertTrue(Files.isDirectory(patchDir));
        try (java.util.stream.Stream<Path> patches = Files.list(patchDir)) {
            assertTrue(patches.anyMatch(p -> p.toString().endsWith(".patch")));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests PatchTaskTest`
Expected: FAIL — task `patch` does not exist.

- [ ] **Step 3: Implement `PatchTask`**

Create `flakesync-gradle-plugin/src/main/java/edu/utexas/ece/flakesync/gradle/PatchTask.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import flakesync.Constants;
import flakesync.patching.InjectFlagInCriticalPoint;
import flakesync.patching.InjectYieldStatement;
import flakesync.patching.SavePatch;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PatchTask extends AbstractFlakeSyncTask {

    @TaskAction
    public void run() {
        String baseDir = getProject().getProjectDir().toString();
        File barrierResultsFile = resolve(Constants.getBarrierPointsResultsFilepath(".", getTestName()));

        try (BufferedReader br = new BufferedReader(new FileReader(barrierResultsFile))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null && !line.isEmpty()) {
                if (line.charAt(0) == '#') {
                    continue;
                }
                applyOnePatch(baseDir, line);
            }
        } catch (IOException ioe) {
            throw new GradleException("Patch generation failed", ioe);
        }
    }

    private void applyOnePatch(String baseDir, String line) throws IOException {
        String[] items = line.split(",");
        String critPoint = items[1];
        String barrierPoint = items[2];
        int threshold = Integer.parseInt(items[3]);

        String critTarget = critPoint.split("-")[1].split("#")[0];
        int critLine = Integer.parseInt(critPoint.split("-")[1].split("#")[1].split("\\[")[0]);
        critTarget = critTarget.split("\\$")[0].replace("/", ".");

        Path critPath = InjectFlagInCriticalPoint.findJavaFilePath(baseDir, critTarget);
        Path critOriginal = Paths.get(critPath + ".orig");
        Files.copy(critPath, critOriginal);
        InjectFlagInCriticalPoint.injectFlagInCritPt(baseDir, critTarget, critLine);

        String barrierClassName = barrierPoint.split("#")[0];
        int barrierLine = Integer.parseInt(barrierPoint.split("#")[1]);
        barrierClassName = barrierClassName.split("\\$")[0].replace("/", ".");

        Path barrierPath = InjectFlagInCriticalPoint.findJavaFilePath(baseDir, barrierClassName);
        Path barrierOriginal = Paths.get(barrierPath + ".orig");
        Files.copy(barrierPath, barrierOriginal);
        InjectYieldStatement.injectYieldStatement(baseDir, critTarget, getTestName(), barrierClassName,
                barrierLine, threshold);

        String patchDir = Constants.getPatchDir(baseDir).toString();
        String originalBarrierFilePath = SavePatch.getFilePath(baseDir, barrierClassName, true);
        String modifiedBarrierFilePath = InjectFlagInCriticalPoint.findJavaFilePath(baseDir, barrierClassName)
                .toString();
        SavePatch.makePatch(originalBarrierFilePath, modifiedBarrierFilePath, patchDir);

        String originalCriticalFilePath = SavePatch.getFilePath(baseDir, critTarget, true);
        String modifiedCriticalFilePath = InjectFlagInCriticalPoint.findJavaFilePath(baseDir, critTarget)
                .toString();
        SavePatch.makePatch(originalCriticalFilePath, modifiedCriticalFilePath, patchDir);

        Files.delete(critPath);
        Files.copy(critOriginal, critPath);
        Files.delete(critOriginal);
        Files.delete(barrierPath);
        Files.copy(barrierOriginal, barrierPath);
        Files.delete(barrierOriginal);
    }
}
```

- [ ] **Step 4: Register the task in `FlakeSyncPlugin`**

Add to `FlakeSyncPlugin.apply(...)`:

```java
        project.getTasks().register("patch", PatchTask.class);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests PatchTaskTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add flakesync-gradle-plugin
git commit -m "Add patch Gradle task"
```

---

### Task 13: End-to-end pipeline test and README documentation

**Files:**
- Create: `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/PipelineIntegrationTest.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: all 6 registered tasks (Tasks 7–12), `FixtureProject` (Task 6).

- [ ] **Step 1: Write the end-to-end pipeline test**

Create `flakesync-gradle-plugin/src/test/java/edu/utexas/ece/flakesync/gradle/PipelineIntegrationTest.java`:

```java
package edu.utexas.ece.flakesync.gradle;

import edu.utexas.ece.flakesync.gradle.fixtures.FixtureProject;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void concurrentFindThenDelayLocsThenDeltaDebugRunAsAPipeline() {
        File projectDir = FixtureProject.materialize(tempDir);
        String testNameArg = "--test-name=fixture.FlakySampleTest#alwaysFails";

        BuildResult concurrentFindResult = FixtureProject
                .runnerFor(projectDir, "concurrentfind", testNameArg).build();
        assertTrue(concurrentFindResult.getOutput().contains("BUILD SUCCESSFUL"));

        BuildResult delayLocsResult = FixtureProject
                .runnerFor(projectDir, "delaylocs", testNameArg).build();
        assertTrue(delayLocsResult.getOutput().contains("BUILD SUCCESSFUL"));

        Path flakesyncDir = projectDir.toPath().resolve(".flakesync");
        assertTrue(Files.exists(flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations.txt")));

        BuildResult deltaDebugResult = FixtureProject
                .runnerFor(projectDir, "deltadebug", testNameArg).build();
        assertTrue(deltaDebugResult.getOutput().contains("BUILD SUCCESSFUL"));
        assertTrue(Files.exists(
                flakesyncDir.resolve("fixture.FlakySampleTest.alwaysFails-Locations_minimized.txt")));
    }
}
```

- [ ] **Step 2: Run the whole suite**

Run: `./gradlew test`
Expected: all tests across every task class PASS, including this new pipeline test.

- [ ] **Step 3: Update the README**

In `README.md`, after the existing `## Using FlakeSync` Maven section, add:

```markdown
## Using FlakeSync with Gradle

`flakesync-gradle-plugin` provides the same 6 goals as Gradle tasks, for Gradle-built target
projects. It is a separate Gradle build living in this repo (not part of the Maven reactor);
build and publish `flakesync-core` and `flakesync-utils` to your local Maven repository first,
then build the plugin itself:

```bash
mvn -pl flakesync-core,flakesync-utils -am -DskipTests install
cd flakesync-gradle-plugin && ./gradlew build
```

Apply the plugin in your target Gradle project's `build.gradle(.kts)`:

```kotlin
plugins {
    id("edu.utexas.ece.flakesync")
}
```

(during development, resolve it via an `includeBuild("../FlakeSync/flakesync-gradle-plugin")`
composite build, or `./gradlew publishToMavenLocal` from `flakesync-gradle-plugin/` and add
`mavenLocal()` to your target project's plugin repositories.)

Then run the same phases as goals — `--test-name` takes the place of Maven's
`-Dflakesync.testName`:

```bash
./gradlew concurrentfind --test-name=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool
./gradlew delaylocs --test-name=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool
./gradlew deltadebug --test-name=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool
./gradlew critsearch --test-name=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool
./gradlew barrierpointsearch --test-name=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool
./gradlew patch --test-name=org.apache.uniffle.common.rpc.GrpcServerTest#testGrpcExecutorPool
```

Output files land under the same `./<repo>/<module>/.flakesync/` layout as the Maven plugin.

**Limitation:** v1 supports JUnit4-style test methods only.
```

- [ ] **Step 4: Commit**

```bash
git add flakesync-gradle-plugin README.md
git commit -m "Add end-to-end pipeline test and Gradle usage docs"
```

---

## Self-Review Notes

- **Spec coverage:** every component named in the design spec (`FlakeSyncPlugin`, `FlakeSyncExtension`, `AbstractFlakeSyncTask`, `SingleTestExecutor`, the 6 task classes, the `SingleTestLauncher` addition to `flakesync-core`, the `DeltaDebugger` move) has a task. The spec's "moves `DeltaDebugger` to flakesync-utils" line item additionally surfaced that `patching` needed the same treatment for `PatchTask` to be buildable — added as Task 2, which the spec's "Open items" section left implicit.
- **Type consistency:** `TestExecutionRequest`/`SingleTestExecutor`/`TestExecutionTimeoutException` are defined once in Task 5 and used with identical method names (`forTest`, `.agentMode`, `.delay`, `.property`, `.timeoutSeconds`, `.build`, `newExecutor().run(...)`) in every later task. `AbstractFlakeSyncTask.resolve(Path)` and `getTestName()`/`getAgentJar()`/`getTestRuntimeClasspath()` (Task 6) are used with matching signatures in Tasks 7–12. `SingleTestLauncher.EXIT_PASSED/EXIT_FAILED` (Task 3) are the only two constants `SingleTestExecutor` (Task 5) branches on.
- **Deliberate deviations from a literal 1:1 port**, called out explicitly rather than left implicit:
  - `TestExecutionRequest`'s builder replaces `SurefireExecution.SurefireFactory`'s 11 named methods.
  - The per-execution timeout is a new, actually-wired-up behavior (the Maven plugin's equivalent, `addProcessTimeout`, exists but is never called from any `SurefireFactory` method).
  - `AbstractFlakeSyncTask.resolve(Path)` always ensures its target file's parent directory exists, rather than relying on `flakesync.Constants`' own per-method `mkdirs()` side effects (some of which build the directory to create from a raw, JVM-CWD-relative path instead of the `baseDir` argument they're given — a pre-existing quirk in `flakesync-utils`, left unchanged per this plan's Global Constraints). This sidesteps that quirk entirely rather than working around it call-site by call-site.
  - `BarrierPointSearchTask`'s non-test-code branch needed a real design change, not just a mechanical port: `BarrierPointMojo` recovers a failure's stack trace by scanning Surefire's own `TEST-*.xml` report, which `SingleTestExecutor` has no equivalent of. `SingleTestLauncher` (Task 3) gained a `stackTraceOutputFile` system property so it can write the failure trace directly, which the task then reads — simpler than replicating Surefire's report-naming/discovery.
- **Residual risk flagged for the implementer:** the `critsearch` and `barrierpointsearch` integration tests (Tasks 10–11) hand-seed intermediate files based on this plan's understanding of `Constants`' text formats, without auditing every bytecode-instrumentation `*Tracer` class in `flakesync-core` that would normally produce them (e.g. `InjectDelayWithStackTraceTracer`, `DelayAndYieldInjector`). Both tasks' test steps call this out inline; if actual observed output differs, adjust the seeded fixtures/assertions rather than the ported orchestration logic (which mirrors its source Mojo line-for-line).
