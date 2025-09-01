import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.io.File;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.type.*;

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

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !args[0].contains("~") || !args[0].contains("#") || !args[0].contains("[")) {
            System.err.println("Usage: java InjectFlagInCriticalPoint <StartClass#Line~TargetClass#Line[Delay]>");
            System.err.println("Example: java InjectFlagInCriticalPoint org/java_websocket/WebSocketImpl#513~org/java_websocket/WebSocketImpl#515[100]");
            System.exit(1);
        }

        // Parse input
        String[] rangeParts = args[0].split("~");
        String targetInfo = rangeParts[1];
        String classPart = targetInfo.split("#")[0].split("\\$")[0];
        classPart = classPart.replace('/', '.');  // convert slash to dot
        String lineAndDelay = targetInfo.split("#")[1];
        String slug = args[1];
        System.out.println("Target Class: " + classPart);

        int lineNumber = Integer.parseInt(lineAndDelay.split("\\[")[0]);
        int delay = Integer.parseInt(lineAndDelay.split("\\[")[1].replace("]", ""));

        String filePath = findJavaFilePath(slug, classPart).toString();
        System.out.println("Critc Point FILEPATH: " + filePath);

        // --- Text-based injection for minimal changes ---
        try {
            String source = new String(Files.readAllBytes(Paths.get(filePath)));
            boolean needsField = !source.contains("private static volatile boolean hasExecuted");
            boolean needsReset = !source.contains("public static void reset()");
            boolean needsGetStatus = !source.contains("public static boolean getExecutedStatus()");

            // 1. Inject field and helpers FIRST (always add 5 lines for helpers)
            int linesAdded = 0;
            int insertLine = 0;
            int absoluteInsertLine = -1;
            if (needsField || needsReset || needsGetStatus) {
                String className = classPart.substring(classPart.lastIndexOf('.')+1);
                int classIdx = source.indexOf("class " + className);
                if (classIdx == -1) throw new RuntimeException("Class declaration not found");
                int braceIdx = source.indexOf('{', classIdx);
                if (braceIdx == -1) throw new RuntimeException("Class opening brace not found");
                int afterBrace = braceIdx + 1;
                String[] before = source.substring(0, afterBrace).split("\n", -1);
                String[] afterLines = source.substring(afterBrace).split("\n");
                String indent = null;
                for (int i = 0; i < afterLines.length; i++) {
                    String l = afterLines[i];
                    String trimmed = l.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*") && !trimmed.equals("}")) {
                        int ws = 0;
                        while (ws < l.length() && (l.charAt(ws) == ' ' || l.charAt(ws) == '\t')) ws++;
                        indent = l.substring(0, ws);
                        insertLine = i;
                        break;
                    }
                }
                if (indent == null) indent = "    ";
                StringBuilder inject = new StringBuilder();
                inject.append(indent).append("private static volatile boolean hasExecuted;\n\n");
                inject.append(indent).append("public static void reset() { hasExecuted = false; }\n");
                inject.append(indent).append("public static boolean getExecutedStatus() { return hasExecuted; }\n");
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
            int targetLine = lineNumber - 1 + 5;
            System.out.println("[DEBUG] User requested lineNumber (1-based): " + lineNumber);
            System.out.println("[DEBUG] Inserting at shifted line (1-based): " + (targetLine + 1));
            if (targetLine >= 0 && targetLine <= lines.length) {
                // Insert (not replace) at the correct line
                String indent = "";
                if (targetLine > 0 && targetLine <= lines.length) {
                    String refLine = lines[targetLine - 1];
                    int ws = 0;
                    while (ws < refLine.length() && (refLine.charAt(ws) == ' ' || refLine.charAt(ws) == '\t')) ws++;
                    indent = refLine.substring(0, ws);
                }
                List<String> newLines = new ArrayList<>(Arrays.asList(lines));
                newLines.add(targetLine, indent + "hasExecuted = true;");
                lines = newLines.toArray(new String[0]);
                System.out.println("[DEBUG] Inserted hasExecuted = true; at line (1-based): " + (targetLine + 1));
            }
            Files.write(Paths.get(filePath), String.join("\n", lines).getBytes());
            System.out.println("hasExecuted = true; inserted at lineNumber + 5, preserving all original code.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
}
