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
    public static Path findJavaFilePath(String slug, String className) throws IOException {
        String classFileName = className.substring(className.lastIndexOf('.') + 1) + ".java";

        try (Stream<Path> stream = Files.walk(Paths.get(slug))) {
            return stream
                    .filter(path -> path.getFileName().toString().equals(classFileName))
                    .filter(path -> path.toString().replace(File.separatorChar, '.').contains(className))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Could not find Java file for class: " + className));
        }
    }

    public static void injectYieldStatement(String slug, String testName, String className, int targetLine,
                                            int threshold) throws IOException {

        // Convert class name to path
        //slug+ "src/test/java/" + className.replace('.', '/') + ".java";
        String filePath = findJavaFilePath(slug, className).toString();
        System.out.println("FILEPATH: " + filePath);
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        if (targetLine < 1 || targetLine > lines.size()) {
            System.err.println("Invalid target line number. File has " + lines.size() + " lines.");
            System.exit(1);
        }

        // Preserve indentation of the original line
        String target = lines.get(targetLine - 1);
        String indent = target.replaceAll("^(\\s*).*", "$1");

        // Inject print statement
        //String injected = indent + "System.out.println(\"[Injected before line " + targetLine + "]\");";
        List<String> injectedLines = new ArrayList<>();
        injectedLines.add(indent + "while (" + className + ".getExecutedStatus() < " + threshold + ") {");
        injectedLines.add(indent + "    Thread.yield();");
        injectedLines.add(indent + "}");
        lines.addAll(targetLine - 1, injectedLines);

        // ===== Inject reset() at the beginning of the test method =====
        int methodLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("void " + testName + "(")) {
                methodLine = i;
                break;
            }
        }

        if (methodLine == -1) {
            System.err.println("Could not find method: " + testName);
            System.exit(1);
        }

        // Find the line with the opening brace of the method
        int braceLine = methodLine;
        while (braceLine < lines.size() && !lines.get(braceLine).contains("{")) {
            braceLine++;
        }

        if (braceLine >= lines.size()) {
            System.err.println("Opening brace not found for method: " + testName);
            System.exit(1);
        }

        // Inject reset() after the opening brace
        String methodIndent = lines.get(braceLine).replaceAll("^(\\s*).*", "$1");
        lines.add(braceLine + 1, methodIndent + "    " + className + ".reset();");


        // Save file
        Files.write(path, lines);
        System.out.println("Injected print statement before line " + targetLine);
        System.out.println("Modified file written to: " + filePath);
    }
}
