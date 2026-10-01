package flakesync.heuristic;

/**
 * One row of a CandidateOrder.csv file written by SearchLogger.
 */
public class Candidate {
    public final String phase;
    public final String group;
    public final int order;
    public final String location;
    public final String result;
    public final int runs;

    // Builds a candidate directly from a location such as "org/foo/Bar#42" (used when scoring inside FlakeSync).
    public Candidate(String phase, String group, int order, String location, String result, int runs) {
        this.phase = phase;
        this.group = group;
        this.order = order;
        this.location = location;
        this.result = result;
        this.runs = runs;
    }

    public Candidate(String csvLine) {
        String[] parts = csvLine.split(",");
        this.phase = parts[0];
        this.group = parts[1];
        this.order = Integer.parseInt(parts[2]);
        this.location = parts[3];
        this.result = parts[4];
        this.runs = Integer.parseInt(parts[5]);
    }

    // "org/foo/Bar$1#42" gives "org.foo.Bar" (inner class suffix removed).
    public static String classOf(String location) {
        String name = location.substring(0, location.indexOf('#')).replace('/', '.');
        int dollar = name.indexOf('$');
        return dollar < 0 ? name : name.substring(0, dollar);
    }

    // "org/foo/Bar#42" gives 42.
    public static int lineOf(String location) {
        return Integer.parseInt(location.substring(location.indexOf('#') + 1).trim());
    }

    // "org/foo/Bar#42" gives "Bar".
    public static String simpleNameOf(String location) {
        String name = classOf(location);
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
