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

    public static void main(String[] args) throws IOException {
        if (args.length != 4 || !args[0].contains("#")) {
            System.err.println("Usage: java InjectPrintStatement <ClassName#LineNumber>");
            System.err.println("Example: java InjectPrintStatement org.java_websocket.issues.Issue677Test#121");
            System.exit(1);
        }

        // Parse input: org.java_websocket.issues.Issue677Test#121
        String[] parts = args[0].split("#");
        if (parts.length != 2) {
            System.err.println("Invalid format. Must be ClassName#LineNumber.");
            System.exit(1);
        }

        String className = parts[0];  // org.java_websocket.issues.Issue677Test
        int targetLine = Integer.parseInt(parts[1]);
        String targetClass = args[1];
        String testName = args[2];
        String slug = args[3];
        System.out.println("TESTNAME: " + testName);

        // Convert class name to path
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
        injectedLines.add(indent + "while (!" + targetClass + ".getExecutedStatus()) {");
        injectedLines.add(indent + "    Thread.yield();");
        injectedLines.add(indent + "}");

        // Find a safe insertion point for yield block
        int insertLine = targetLine - 1;
        // If the target line is in the middle of a statement, move up to the start of the statement
        while (insertLine > 0 && !lines.get(insertLine).trim().isEmpty()
                && !lines.get(insertLine).trim().endsWith(";")
                && !lines.get(insertLine).trim().endsWith("{")
                && !lines.get(insertLine).trim().endsWith("}")) {
            insertLine--;
        }
        // Insert yield block before the statement
        lines.addAll(insertLine + 1, injectedLines);

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

        // Find indentation of the first code line after the opening brace
        String nextLineIndent = "";
        for (int i = braceLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.trim().isEmpty()) {
                nextLineIndent = line.replaceAll("^(\\s*).*", "$1");
                break;
            }
        }
        // If no code line found, fallback to indentation after brace
        if (nextLineIndent.isEmpty()) {
            nextLineIndent = lines.get(braceLine).replaceAll("^(\\s*).*", "$1") + "    ";
        }
        lines.add(braceLine + 1, nextLineIndent + targetClass + ".reset();");

        // Save file
        Files.write(path, lines);
        System.out.println("Injected print statement before line " + targetLine);
        System.out.println("Modified file written to: " + filePath);
    }
}
