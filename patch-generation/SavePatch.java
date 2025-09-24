import java.io.*;
import java.nio.file.*;

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
        Path patchDirectory = Paths.get(patchDir, ".patch");
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
        } catch (IOException | InterruptedException e) {
            System.err.println("Error generating patch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if(args.length < 5) {
            System.err.println("Usage: java SavePatch <original_barrier_class> <modified_barrier_class> <original_critic_class> <modified_critic_class> <slug>");
            System.exit(1);
        }
        String original_barrier_class = args[0];
        String modified_barrier_class = args[1];
        String original_critic_class = args[2];
        String modified_critic_class = args[3];
        String slug = args[4];
        try {
            String original_barrier_filePath = getFilePath(slug, original_barrier_class, true);
            String modified_barrier_filePath = getFilePath(slug, modified_barrier_class, false);
            makePatch(original_barrier_filePath, modified_barrier_filePath, slug);
            String original_critic_filePath = getFilePath(slug, original_critic_class, true);
            String modified_critic_filePath = getFilePath(slug, modified_critic_class, false);
            makePatch(original_critic_filePath, modified_critic_filePath, slug);
        } catch (IOException e) {
            System.err.println("Error finding file paths: " + e.getMessage());
            e.printStackTrace();
        }   
    }
}
