package edu.utexas.ece.barrierSearch.agent;

public class Utility {
    private static int delay;
    public static int executionCount = 0;
    private static int threshold;
    public static int testVarCount=0;
    //public static int executionCountResult;
    static {
        try {
            delay = Integer.parseInt(System.getProperty("delay", "100"));
            threshold = Integer.parseInt(System.getProperty("threshold", "1"));
        } catch (NumberFormatException e) {
            delay = 100;
            threshold = 1;
        }
    }

    public static void yield() {
        //System.out.println("Utility yielding.., threshold="+threshold + ",testVarCount= "+testVarCount);
        while (testVarCount < threshold) {
            //System.out.println(" Within while loop testVarCount="+testVarCount+ ",threshold="+threshold);
            Thread.yield();
        }
        testVarCount=0;
        //System.out.println("Yielding from Utility..");
    }

    public static void update(){
        System.out.println("Updating from Utility..");
        testVarCount += 1;
    }

    public static void counter(){
        executionCount+=1;
        //executionCountResult=executionCount;
        System.out.println("Incrementing from Utility.."+executionCount);
    }

    public static void init(){
        testVarCount = 0;
    }

    public static void delay() {
        try {
            System.out.println("DELAY***From Utility, Delay="+delay);
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            System.out.println("Exception " );
            e.printStackTrace();
        }
    }
}
