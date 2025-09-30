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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class InjectYieldStatement {

    public static void injectYieldStatement(String slug, String targetClass, String testName, String className,
                                            int targetLine, int threshold) throws IOException {
        System.out.println("TESTNAME: " + testName);

        // Convert class name to path
        String filePath = InjectFlagInCriticalPoint.findJavaFilePath(slug, className).toString();
        System.out.println("FILEPATH: " + filePath);
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        if (targetLine < 1 || targetLine > lines.size()) {
            throw new RuntimeException("Invalid target line number. File has " + lines.size() + " lines.");
        }

        // Preserve indentation of the original line
        String target = lines.get(targetLine - 1);
        String indent = target.replaceAll("^(\\s*).*", "$1");

        // Inject yield statement
        List<String> injectedLines = new ArrayList<>();
        injectedLines.add(indent + "while (" + targetClass + "."
            + InjectFlagInCriticalPoint.GET_EXECUTION_STATUS_METHOD_NAME + " < " + threshold + ") {");
        injectedLines.add(indent + "    Thread.yield();");
        injectedLines.add(indent + "}");

        // Find a safe insertion point for yield block
        int insertLine = targetLine - 1;
        // If the target line is in the middle of a statement, move up to the start of the statement
        while (insertLine > 0 && !lines.get(insertLine).trim().isEmpty()
               && !lines.get(insertLine - 1).trim().endsWith(";")
               && !lines.get(insertLine - 1).trim().endsWith("{")
               && !lines.get(insertLine - 1).trim().endsWith("}")
               && !lines.get(insertLine - 1).trim().endsWith(")")
               && !lines.get(insertLine - 1).trim().endsWith("(")) {
            insertLine--;
        }
        // Insert yield block after the insert point (some line before the target line)
        // The insertLine is a line number (1-index), so we insert one line after it index-wise
        lines.addAll(insertLine, injectedLines);

        // Inject reset() at the beginning of the test method
        int methodLineIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("void " + testName.split("#")[1] + "(")) {
                methodLineIndex = i;
                break;
            }
        }

        if (methodLineIndex == -1) {
            throw new RuntimeException("Could not find test method: " + testName);
        }

        // Find the line with the opening brace of the method
        int braceLineIndex = methodLineIndex;
        while (braceLineIndex < lines.size() && !lines.get(braceLineIndex).contains("{")) {
            braceLineIndex++;
        }

        if (braceLineIndex >= lines.size()) {
            throw new RuntimeException("Opening brace not found");
        }

        // Find indentation of the first code line after the opening brace
        String nextLineIndent = "";
        for (int i = braceLineIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.trim().isEmpty()) {
                nextLineIndent = line.replaceAll("^(\\s*).*", "$1");
                break;
            }
        }
        // If no code line found, fallback to indentation after brace, with four spaces
        if (nextLineIndent.isEmpty()) {
            nextLineIndent = lines.get(braceLineIndex).replaceAll("^(\\s*).*", "$1") + "    ";
        }
        lines.add(braceLineIndex + 1, nextLineIndent + targetClass + "."
            + InjectFlagInCriticalPoint.RESET_METHOD_NAME + ";");

        // Save file
        Files.write(path, lines);
        System.out.println("Injected print statement before line " + targetLine);
        System.out.println("Modified file written to: " + filePath);

    }
}
