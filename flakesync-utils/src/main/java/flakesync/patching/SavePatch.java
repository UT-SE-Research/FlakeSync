/*
The MIT License (MIT)
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman
Copyright (c) 2025 August Shi



Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

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
