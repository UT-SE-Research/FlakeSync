import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class InjectPrintStatement {
    public static void main(String[] args) throws IOException {
        if (args.length != 1 || !args[0].contains("#")) {
            System.err.println("Usage: java InjectPrintStatement <ClassName#LineNumber>");
            System.err.println("Example: java InjectPrintStatement org.java_websocket.issues.Issue677Test#121");
            System.exit(1);
        }

        // Parse input: org.java_websocket.issues.Issue677Test#121
        String[] parts = args[0].split("#");
        if (parts.length != 2) {
            System.err.println("❌ Invalid format. Must be ClassName#LineNumber.");
            System.exit(1);
        }

        String className = parts[0];  // org.java_websocket.issues.Issue677Test
        int targetLine = Integer.parseInt(parts[1]);

        // Convert class name to path
        String filePath = "TooTallNate/Java-WebSocket/src/test/java/" + className.replace('.', '/') + ".java";

        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        if (targetLine < 1 || targetLine > lines.size()) {
            System.err.println("❌ Invalid target line number. File has " + lines.size() + " lines.");
            System.exit(1);
        }

        // Preserve indentation of the original line
        String target = lines.get(targetLine - 1);
        String indent = target.replaceAll("^(\\s*).*", "$1");

        // Inject print statement
        String injected = indent + "System.out.println(\"[Injected before line " + targetLine + "]\");";
        lines.add(targetLine - 1, injected);

        // Save file
        Files.write(path, lines);
        System.out.println("✅ Injected print statement before line " + targetLine);
        System.out.println("💾 Modified file written to: " + filePath);
    }
}

/*import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class InjectPrintStatement {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java InjectPrintStatement <JavaFile> <TargetLine>");
            System.exit(1);
        }

        String filePath = args[0];
        int targetLine = Integer.parseInt(args[1]);

        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        if (targetLine < 1 || targetLine > lines.size()) {
            System.err.println("❌ Invalid target line number. File has " + lines.size() + " lines.");
            System.exit(1);
        }

        // Get indentation from the target line
        String target = lines.get(targetLine - 1);
        String indent = target.replaceAll("^(\\s*).*", "$1");

        // Prepare injected line with matched indentation
        String injected = indent + "System.out.println(\"Injected before line " + targetLine + "pp\");";

        // Inject before the target line
        lines.add(targetLine - 1, injected);

        // Save modified file
        Files.write(path, lines);
        System.out.println("✅ Injected print statement before Vim-visible line " + targetLine);
    }
}*/
