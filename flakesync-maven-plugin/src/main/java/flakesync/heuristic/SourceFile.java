package flakesync.heuristic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Java source file kept as a list of lines, with string literals and // comments removed.
 * All line numbers are 1-based, like the numbers in a stack trace.
 */
public class SourceFile {
    private static final Pattern STRING = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    private static final Pattern METHOD_DECL = Pattern.compile("^\\s*(?:[\\w<>\\[\\],.?@]+\\s+)+(\\w+)\\s*\\(.*");
    private static final Pattern NOT_METHOD = Pattern.compile("^\\s*(return|new|throw|else|case|assert)\\b.*");

    private final String path;
    private final List<String> lines = new ArrayList<String>();

    public SourceFile(File file) throws IOException {
        this.path = file.getPath().replace('\\', '/');
        for (String raw : Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1)) {
            String clean = STRING.matcher(raw).replaceAll("\"\"");
            int comment = clean.indexOf("//");
            if (comment >= 0) {
                clean = clean.substring(0, comment);
            }
            lines.add(clean);
        }
    }

    public String line(int number) {
        if (number < 1 || number > lines.size()) {
            return "";
        }
        return lines.get(number - 1);
    }

    public boolean isTestFile() {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return path.contains("/test/") || name.matches("(Test.*|.*Tests?|.*IT)\\.java");
    }

    public boolean hasAnyLine(Pattern pattern) {
        for (String text : lines) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // True if the pattern appears within "radius" lines of the given line (the line itself included).
    public boolean hasNear(int number, Pattern pattern, int radius) {
        for (int ln = number - radius; ln <= number + radius; ln++) {
            if (pattern.matcher(line(ln)).find()) {
                return true;
            }
        }
        return false;
    }

    // Header lines of all blocks that contain the given line, innermost first.
    // Example headers: "public void run() {", "executor.submit(() -> {", "synchronized (lock) {".
    public List<String> enclosingHeaders(int number) {
        List<String> headers = new ArrayList<String>();
        int depth = 0;
        for (int ln = number - 1; ln >= 1; ln--) {
            String text = line(ln);
            for (int pos = text.length() - 1; pos >= 0; pos--) {
                char ch = text.charAt(pos);
                if (ch == '}') {
                    depth++;
                } else if (ch == '{') {
                    if (depth == 0) {
                        headers.add(headerText(ln));
                    } else {
                        depth--;
                    }
                }
            }
        }
        return headers;
    }

    // Name of the method that contains the given line, or "" if none is found.
    public String enclosingMethod(int number) {
        String own = methodName(line(number));
        if (own != null) {
            return own;
        }
        for (String header : enclosingHeaders(number)) {
            String name = methodName(header);
            if (name != null) {
                return name;
            }
        }
        return "";
    }

    // If the "{" is alone on its line, the real header is the line before it.
    private String headerText(int number) {
        if ("{".equals(line(number).trim()) && number > 1) {
            return line(number - 1);
        }
        return line(number);
    }

    private static String methodName(String text) {
        if (NOT_METHOD.matcher(text).matches()) {
            return null;
        }
        Matcher matcher = METHOD_DECL.matcher(text);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
