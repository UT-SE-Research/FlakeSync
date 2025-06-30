package edu.utexas.ece.barrierSearch.agent;

public class Utility {

    public static int executionCount = 0;
    public static int testVarCount = 0;

    private static int delay;
    private static int threshold;

    static {
        try {
            delay = Integer.parseInt(System.getProperty("delay", "100"));
            threshold = Integer.parseInt(System.getProperty("threshold", "1"));
        } catch (NumberFormatException nfe) {
            delay = 100;
            threshold = 1;
        }
    }

    public static void yield() {
        long start = System.currentTimeMillis();
        //Add an external timeout that can be set by the user
        //int timeout;
        while ((testVarCount < threshold) &&
                (System.currentTimeMillis() < start + (delay * 1000))) {
            Thread.yield();
        }
        testVarCount = 0;
    }

    public static void update() {
        System.out.println("Updating from Utility..");
        testVarCount += 1;
    }

    public static void counter() {
        executionCount += 1;
        System.out.println("Incrementing from Utility.." + executionCount);
    }

    public static void init() {
        testVarCount = 0;
    }

    public static void delay() {
        try {
            //System.out.println("DELAY***From Utility, Delay=" + delay);
            Thread.sleep(delay);
            //Long start = System.currentTimeMillis();
            //Long current = start;
            //while(current < start + (delay * 1000)) {
                //current++;
            //}
        } catch (InterruptedException ie) {
            System.out.println("Exception ");
            ie.printStackTrace();
        }
    }
}
