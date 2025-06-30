package flakesync.common;

public class Output {
    String test;
    String location;
    boolean fails;
    int delay;

    public Output(String testName, String location, boolean fails, int delay) {
        this.test = testName;
        this.location = location;
        this.fails = fails;
        this.delay = delay;

        System.out.println("New output just dropped: " + test + ": " + location);
    }

    @Override
    public int hashCode() {
        String location = this.location.substring(this.location.indexOf(','),
                this.location.lastIndexOf('('));
        System.out.println("Inside hashCode: " + location);
        System.out.println("Inside hashCode: " + location.hashCode());
        return location.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Output) {
            return obj.hashCode() == this.hashCode();
        }
        return false;
    }

    public String getLocation() {
        return location;
    }
}
