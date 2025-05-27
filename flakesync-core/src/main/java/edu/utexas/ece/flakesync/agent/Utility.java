package edu.utexas.ece.flakesync.agent;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class Utility {

    // Set of methods found to have been run concurrently with other methods
    public static Set<String> methodsRunConcurrently = new HashSet<>();
    public static HashMap<String, Stack<String>> concurrentMethodPairs = new HashMap<>();


    private static int delay;

    static {
        try {
            delay = Integer.parseInt(System.getProperty("delay", "100"));
        } catch (NumberFormatException nfe) {
            delay = 100;
        }
    }

    public static void delay() {
        try {
            System.out.println("Inside delay method: calling Thread.sleep(" + delay + ")");
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            System.out.println("Exception");
            ie.printStackTrace();
        }
    }
}
