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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/*import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.type.*;*/

public class InjectFlagInCriticalPoint {
    public static Path findJavaFilePath(String slug, String className) throws IOException {
        // Use only the outer class name for the filename
        String outerClassName = className.substring(className.lastIndexOf('.') + 1).split("\\$")[0];
        String classFileName = outerClassName + ".java";

        try (Stream<Path> stream = Files.walk(Paths.get(slug))) {
            return stream
                    .filter(path -> path.getFileName().toString().equals(classFileName))
                    .filter(path -> path.toString().replace(File.separatorChar, '.').contains(className.replace('$', '.')))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Could not find Java file for class: " + className));
        }
    }

    public static void injectFlagInCritPt(String slug, String targetClass, int taLine) throws IOException {

        String filePath = findJavaFilePath(slug, targetClass).toString();
        System.out.println("Critc Point FILEPATH: " + filePath);

        // --- Text-based injection for minimal changes ---
        try {
            String source = new String(Files.readAllBytes(Paths.get(filePath)));
            boolean needsField = !source.contains("private static volatile int numExecutions");
            boolean needsReset = !source.contains("public static void resetFlakesync()");
            boolean needsGetStatus = !source.contains("public static int getExecutedStatus()");

            // 1. Inject field and helpers FIRST (always add 5 lines for helpers)
            int linesAdded = 0;
            int insertLine = 0;
            int absoluteInsertLine = -1;
            if (needsField || needsReset || needsGetStatus) {
                String className = targetClass.substring(targetClass.lastIndexOf('.') + 1);
                int classIdx = source.indexOf("class " + className);
                if (classIdx == -1) {
                    throw new RuntimeException("Class declaration not found");
                }
                int braceIdx = source.indexOf('{', classIdx);
                if (braceIdx == -1) {
                    throw new RuntimeException("Class opening brace not found");
                }
                int afterBrace = braceIdx + 1;
                String[] before = source.substring(0, afterBrace).split("\n", -1);
                String[] afterLines = source.substring(afterBrace).split("\n");
                String indent = null;
                for (int i = 0; i < afterLines.length; i++) {
                    String lne = afterLines[i];
                    String trimmed = lne.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                            && !trimmed.startsWith("*") && !trimmed.equals("}")) {
                        int ws = 0;
                        while (ws < lne.length() && (lne.charAt(ws) == ' ' || lne.charAt(ws) == '\t')) {
                            ws++;
                        }
                        indent = lne.substring(0, ws);
                        insertLine = i;
                        break;
                    }
                }
                if (indent == null) {
                    indent = "    ";
                }
                StringBuilder inject = new StringBuilder();
                inject.append(indent).append("private static volatile int numExecutions;\n\n");
                inject.append(indent).append("public static void resetFlakesync() { numExecutions = 0; }\n");
                inject.append(indent).append("public static int getExecutedStatus() { return numExecutions; }\n");
                // Always add 5 lines for helpers (1 field + 1 blank + 2 methods + 1 blank)
                linesAdded = 5;
                absoluteInsertLine = before.length + insertLine;
                List<String> newLines = new ArrayList<>(Arrays.asList(before));
                for (int i = 0; i < afterLines.length; i++) {
                    if (i == insertLine) {
                        String injectStr = inject.toString().replaceAll("\\n$", "");
                        newLines.add(injectStr);
                    }
                    newLines.add(afterLines[i]);
                }
                source = String.join("\n", newLines);
            }

            // 2. Insert hasExecuted = true; at the user-specified lineNumber + 5 (do not remove any line)
            String[] lines = source.split("\n", -1);
            int targetLine = taLine - 1 + 5;
            System.out.println("[DEBUG] User requested lineNumber (1-based): " + taLine);
            System.out.println("[DEBUG] Inserting at shifted line (1-based): " + (targetLine + 1));
            if (targetLine >= 0 && targetLine <= lines.length) {
                // Insert (not replace) at the correct line
                String indent = "";
                if (targetLine > 0 && targetLine <= lines.length) {
                    String refLine = lines[targetLine - 1];
                    int ws = 0;
                    while (ws < refLine.length() && (refLine.charAt(ws) == ' ' || refLine.charAt(ws) == '\t')) {
                        ws++;
                    }
                    indent = refLine.substring(0, ws);
                }
                List<String> newLines = new ArrayList<>(Arrays.asList(lines));
                newLines.add(targetLine, indent + "numExecutions++;");
                lines = newLines.toArray(new String[0]);
                System.out.println("[DEBUG] Inserted numExecutions++; at line (1-based): " + (targetLine + 1));
            }
            Files.write(Paths.get(filePath), String.join("\n", lines).getBytes());
            System.out.println("numExecutions++; inserted at lineNumber + 5, preserving all original code.");
        } catch (Exception exc) {
            exc.printStackTrace();
            System.exit(1);
        }
    }

    private static int countChar(String str, char cha) {
        int count = 0;
        for (char ch : str.toCharArray()) {
            if (ch == cha) {
                count++;
            }
        }
        return count;
    }
}
