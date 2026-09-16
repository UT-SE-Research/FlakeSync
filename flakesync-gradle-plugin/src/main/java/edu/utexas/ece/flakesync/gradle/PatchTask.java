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
