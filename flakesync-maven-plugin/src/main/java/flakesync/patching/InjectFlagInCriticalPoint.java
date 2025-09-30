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

public class InjectFlagInCriticalPoint {

    public static final String NUM_EXECUTIONS_FIELD_NAME = "numExecutions";
    public static final String RESET_METHOD_NAME = "resetExecutions()";
    public static final String GET_EXECUTION_STATUS_METHOD_NAME = "getExecutedStatus()";

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

        // --- Text-based injection for minimal changes ---
        try {
            String source = new String(Files.readAllBytes(Paths.get(filePath)));
            boolean needsField = !source.contains("private static volatile int " + NUM_EXECUTIONS_FIELD_NAME);
            boolean needsReset = !source.contains("public static void " + RESET_METHOD_NAME);
            boolean needsGetStatus = !source.contains("public static int " + GET_EXECUTION_STATUS_METHOD_NAME);

            // 1. Inject field and helpers FIRST (always add 5 lines for helpers)
            if (needsField || needsReset || needsGetStatus) {
                // TODO: Currently assume standard case of one class in the Java file
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
                // Lines up to the first opening brace, e.g., imports, class definition
                String[] beforeLines = source.substring(0, afterBrace).split("\n", -1);
                // Lines after the first opening brace, e.g., all the definitions, including final closing brace
                String[] afterLines = source.substring(afterBrace).split("\n");

                List<String> newLines = new ArrayList<>(Arrays.asList(beforeLines));
                String indent = "    ";
                boolean injected = false;
                for (int i = 0; i < afterLines.length; i++) {
                    String line = afterLines[i];
                    String trimmed = line.trim();
                    // Found place to insert new definitions, as early as possible
                    if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                            && !trimmed.startsWith("*") && !trimmed.equals("}")
                            && !injected) {
                        // First determine how much indentation to add, by grabbing up to index of first non-whitespace
                        int whitespace = 0;
                        while (whitespace < line.length() && (line.charAt(whitespace) == ' '
                                || line.charAt(whitespace) == '\t')) {
                            whitespace++;
                        }
                        indent = line.substring(0, whitespace);

                        // Always add 5 lines for helpers (1 field + 1 blank + 2 methods + 1 blank)
                        StringBuilder inject = new StringBuilder();

                        // Append new field definition to measure number of times executed
                        inject.append(indent).append("private static volatile int ");
                        inject.append(NUM_EXECUTIONS_FIELD_NAME);
                        inject.append(";");
                        newLines.add(inject.toString());
                        newLines.add("");

                        // Append new method definition to reset the field
                        inject.setLength(0);
                        inject.append(indent).append("public static void ");
                        inject.append(RESET_METHOD_NAME);
                        inject.append(" { ");
                        inject.append(NUM_EXECUTIONS_FIELD_NAME);
                        inject.append(" = 0; }");
                        newLines.add(inject.toString());

                        // Append new method definition to get the field value
                        inject.setLength(0);
                        inject.append(indent).append("public static int ");
                        inject.append(GET_EXECUTION_STATUS_METHOD_NAME);
                        inject.append(" { return ");
                        inject.append(NUM_EXECUTIONS_FIELD_NAME);
                        inject.append("; }");
                        newLines.add(inject.toString());

                        injected = true;
                    }
                    newLines.add(afterLines[i]);
                }
                source = String.join("\n", newLines);
            }

            // 2. Insert numExecutionsFlakeSync++; at the user-specified lineNumber + 5 (since we added 5 new lines)
            String[] lines = source.split("\n", -1);
            int targetLineIndex = taLine - 1 + 5;   // Subtract 1 for 0-index; 5 is hard-coded number of new lines added
            System.out.println("[DEBUG] User requested lineNumber (1-based): " + taLine);
            System.out.println("[DEBUG] Inserting at shifted line (1-based): " + (targetLineIndex + 1));
            if (targetLineIndex >= 0 && targetLineIndex <= lines.length) {
                // Find a safe insertion point for increment (not in the middle of a multi-line statement)
                int insertLineIndex = targetLineIndex;
                //System.out.println("SCANNING ABOVE LINE: " + (insertLine - 1) + " " + lines[insertLine - 1].trim());
                while (insertLineIndex > 0
                         && !lines[insertLineIndex].trim().isEmpty()
                        && !lines[insertLineIndex].trim().endsWith(";")
                        && !lines[insertLineIndex].trim().endsWith("{")
                        && !lines[insertLineIndex].trim().endsWith("}")
                        && !lines[insertLineIndex].trim().endsWith(")")
                        && !lines[insertLineIndex].trim().endsWith("(")) {
                    insertLineIndex++;
                }
                // Determine indentation
                String refLine = lines[insertLineIndex + 1];
                int whitespace = 0;
                while (whitespace < refLine.length()
                        && (refLine.charAt(whitespace) == ' '
                        || refLine.charAt(whitespace) == '\t')) {
                    whitespace++;
                }
                String indent = refLine.substring(0, whitespace);

                List<String> newLines = new ArrayList<>(Arrays.asList(lines));
                // Compose increment line in parts to avoid line length issues
                String incrementLine = indent + NUM_EXECUTIONS_FIELD_NAME + "++;";
                newLines.add(insertLineIndex + 1, incrementLine);
                lines = newLines.toArray(new String[0]);
                System.out.println("[DEBUG] Inserted numExecutions++; at safe line (1-based): " + (insertLineIndex + 1));
            }
            Files.write(Paths.get(filePath), String.join("\n", lines).getBytes());
            System.out.println("numExecutions++; inserted at safe line, preserving all original code.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
