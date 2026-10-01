package flakesync.heuristic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the source file for a "class#line" location by searching a project directory.
 */
public class SourceIndex {
    private final Map<String, List<File>> byName = new HashMap<String, List<File>>();
    private final Map<File, SourceFile> cache = new HashMap<File, SourceFile>();

    public SourceIndex(File root) throws IOException {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root.toPath())) {
            paths = stream.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        for (Path path : paths) {
            if (path.toString().replace('\\', '/').contains("/target/")) {
                continue;
            }
            String name = path.getFileName().toString();
            if (!byName.containsKey(name)) {
                byName.put(name, new ArrayList<File>());
            }
            byName.get(name).add(path.toFile());
        }
    }

    // Returns null when the source is not in the project (for example a library class).
    public SourceFile find(String location) {
        if (location.indexOf('#') < 0) {
            return null;
        }
        String rel = Candidate.classOf(location).replace('.', '/') + ".java";
        List<File> files = byName.get(rel.substring(rel.lastIndexOf('/') + 1));
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.getPath().replace('\\', '/').endsWith(rel)) {
                return load(file);
            }
        }
        return null;
    }

    private SourceFile load(File file) {
        if (!cache.containsKey(file)) {
            try {
                cache.put(file, new SourceFile(file));
            } catch (IOException ioe) {
                cache.put(file, null);
            }
        }
        return cache.get(file);
    }
}
