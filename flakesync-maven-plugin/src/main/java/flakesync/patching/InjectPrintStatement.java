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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


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
            System.err.println("Invalid format. Must be ClassName#LineNumber.");
            System.exit(1);
        }

        String className = parts[0];  // org.java_websocket.issues.Issue677Test
        int targetLine = Integer.parseInt(parts[1]);

        // Convert class name to path
        String filePath = "TooTallNate/Java-WebSocket/src/test/java/" + className.replace('.', '/') + ".java";

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
        String injected = indent + "System.out.println(\"[Injected before line " + targetLine + "]\");";
        lines.add(targetLine - 1, injected);

        // Save file
        Files.write(path, lines);
        System.out.println("Injected print statement before line " + targetLine);
        System.out.println("Modified file written to: " + filePath);
    }
}
