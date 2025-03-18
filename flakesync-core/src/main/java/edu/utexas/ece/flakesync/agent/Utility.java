package edu.utexas.ece.flakesync.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class Utility {

    // Map of threads to stack of currently executing methods
    public static Map<Long, Stack<String>> threadToMethods = new ConcurrentHashMap<>();
    public static List<Long> threadCountListFromUtility = new ArrayList(); // To collect thread count

    // Set of methods found to have been run concurrently with other methods
    public static Set<String> methodsRunConcurrently = new HashSet<>();
    public static HashMap<String, Stack<String>> concurrentMethodPairs = new HashMap<>();

    private static Random rand = new Random(42);

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

    // Randomly delay (hard-coded 0ms to 100ms)
    public static void randomDelay() {
        try {
            if (rand.nextInt(100) < 5) {   // 5% chance of delaying
                Thread.sleep(rand.nextInt(100));
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    // Helper method to handle when method has been started, so should be tracked
    public static void recordMethodEntry(String methodName) {
        synchronized (methodsRunConcurrently) {
            Long threadId = Thread.currentThread().getId();
            //System.out.println("threadId="+threadId);
            if (!threadCountListFromUtility.contains(threadId)) {
                threadCountListFromUtility.add(threadId);
            }

            if (!threadToMethods.containsKey(threadId)) {
                threadToMethods.put(threadId, new Stack());
            }

            threadToMethods.get(threadId).push(methodName);

            // Consider all other methods currently running on other threads as able to run concurrently
            // Only consider those that are in the code-under-test, to filter out some more
            for (Map.Entry<Long, Stack<String>> entry : threadToMethods.entrySet()) {
                if (entry.getKey() == threadId) {
                    continue;
                }
                for (String otherMethod : entry.getValue()) {
                    // TODO: This is somewhat unoptimal as it is adding methodName to set multiple times, but somewhat works
                    methodsRunConcurrently.add(methodName);
                }
            }

        }
    }

    // Helper method to handle when method has finished, so should be removed from tracking
    public static void recordMethodExit(String methodName) {
        synchronized (methodsRunConcurrently) {
            Long threadId = Thread.currentThread().getId();
            if (!threadToMethods.containsKey(threadId)) {
                throw new RuntimeException("EXIT BEFORE ENTER?");
            }

            // TODO: Currently not handling exception exits, so hack for now is to pop until reaching same method name,
            //       with the assumption that all popped methods have actually "exited", so should be removed anyway.
            //       Note that can be imperfect due to same method called multiple times so may not remove correctly...
            while (!threadToMethods.get(threadId).peek().equals(methodName)) {
                threadToMethods.get(threadId).pop();
                //throw new RuntimeException("EXITING FROM NOT MOST RECENT METHOD?");
            }
            threadToMethods.get(threadId).pop();
        }
    }

}
