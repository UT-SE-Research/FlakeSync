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
    }
}
