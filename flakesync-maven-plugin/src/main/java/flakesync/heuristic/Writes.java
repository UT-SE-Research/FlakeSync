package flakesync.heuristic;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the names that a single line of code writes to (used by CP1 and BP2).
 */
public final class Writes {
    private static final Pattern LOCAL_DECL = Pattern.compile("^\\s*(?:final\\s+)?[\\w<>\\[\\],.?]+\\s+\\w+\\s*=.*");
    private static final Pattern ASSIGN = Pattern.compile("([A-Za-z_][\\w.]*)\\s*(?:\\[[^\\]]*\\])?\\s*[-+*/|&]?=(?!=)");
    private static final Pattern INCREMENT = Pattern.compile("(\\w+)\\s*(?:\\+\\+|--)");
    private static final Pattern MUTATOR = Pattern.compile("(\\w+)\\s*\\.\\s*(?:set|put|add|inc|increment|decrement|offer"
            + "|push|remove|update|clear|write|append|countDown|release|complete|register|record)\\w*\\s*\\(");

    private Writes() {
    }

    // "this.count = 1" gives {count}, "metrics.incGauge(X)" gives {metrics}, "int local = 1" gives {}.
    public static Set<String> namesIn(String line) {
        Set<String> names = new HashSet<String>();
        if (!LOCAL_DECL.matcher(line).matches()) {
            Matcher assign = ASSIGN.matcher(line);
            while (assign.find()) {
                String[] parts = assign.group(1).split("\\.");
                names.add(parts[0]);
                names.add(parts[parts.length - 1]);
            }
        }
        Matcher mutator = MUTATOR.matcher(line);
        while (mutator.find()) {
            names.add(mutator.group(1));
        }
        Matcher increment = INCREMENT.matcher(line);
        while (increment.find()) {
            names.add(increment.group(1));
        }
        names.remove("this");
        return names;
    }
}
