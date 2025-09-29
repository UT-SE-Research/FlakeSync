package flakesync.patching;

import flakesync.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SavePatch {
    public static String getFilePath(String slug, String className, boolean useOrig) throws IOException {
        Path filePath = InjectFlagInCriticalPoint.findJavaFilePath(slug, className);
        if (useOrig) {
            Path origPath = Paths.get(filePath.toString() + ".orig");
            if (Files.exists(origPath)) {
                return origPath.toString();
            }
        }
        return filePath.toString();
    }

    public static void makePatch(String originalFilePath, String modifiedFilePath, String patchDir) {
        System.out.println("Generating patch for: " + originalFilePath + " and " + modifiedFilePath);
        System.out.println("Original path: " + patchDir);
        Path patchDirectory = Paths.get(patchDir);
        try {
            if (!Files.exists(patchDirectory)) {
                Files.createDirectory(patchDirectory);
            }
            // Generate unified diff using 'diff -u'
            String patchFileName = Paths.get(modifiedFilePath).getFileName().toString() + ".patch";
            Path patchFilePath = patchDirectory.resolve(patchFileName);
            ProcessBuilder pb = new ProcessBuilder("diff", "-u", originalFilePath, modifiedFilePath);
            pb.redirectOutput(patchFilePath.toFile());
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("No differences found between files.");
            } else {
                System.out.println("Patch file generated at: " + patchFilePath);
            }
        } catch (IOException | InterruptedException ine) {
            System.err.println("Error generating patch: " + ine.getMessage());
            ine.printStackTrace();
        }
    }
}
