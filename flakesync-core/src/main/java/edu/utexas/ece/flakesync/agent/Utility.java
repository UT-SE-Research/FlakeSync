/*
The MIT License (MIT)
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman
Copyright (c) 2025 August Shi



Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package edu.utexas.ece.flakesync.agent;

import flakesync.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class Utility {

    // Map of threads to stack of currently executing methods
    public static Map<Long, Stack<String>> threadToMethods = new ConcurrentHashMap<>();
    public static List<Long>  threadCountListFromUtility = new ArrayList(); // To collect thread count

    // Set of methods found to have been run concurrently with other methods
    public static Set<String> methodsRunConcurrently = new HashSet<>();
    public static HashMap<String, Stack<String>> concurrentMethodPairs = new HashMap<>();

    // List of stack trace elements
    public static List<String> stackTraceList = new ArrayList<String>();

    public static int executionCount = 0;   // Number of times critical point is executed in passing run
    public static int testVarCount = 0;     // Keep track of how often critcal point is executed, for threshold count

    private static int delay;       // Amount to delay
    private static int threshold;   // Execution threshold to reach when applying barrier point

    static {
        try {
            delay = Integer.parseInt(System.getProperty("delay", "100"));
            threshold = Integer.parseInt(System.getProperty("threshold", "1"));
        } catch (NumberFormatException nfe) {
            delay = 100;
        }
    }

    // Delay a specified amount of time
    public static void delay() {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    // Delay a specified amount of time, but also log stack trace
    public static void delay(String testName, String className) {
        try {
            collectStackTrace(testName, className);
            Thread.sleep(delay);

        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    // Helper method to handle when method has been started, so should be tracked
    public static void recordMethodEntry(String methodName) {
        synchronized (methodsRunConcurrently) {
            Long threadId = Thread.currentThread().getId();
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

            while (!threadToMethods.get(threadId).peek().equals(methodName)) {
                threadToMethods.get(threadId).pop();
            }
            threadToMethods.get(threadId).pop();
        }
    }

    // Helper method to collect the stack trace after a delay
    private static void collectStackTrace(String testName, String className) {
        className = className.replaceAll("[/]", ".");
        String[] classNameItems = className.split("#", 2);
        String fileName = String.valueOf(Constants.getStackTraceFilepath(testName));
        try {
            FileWriter outputFile = new FileWriter(fileName, true);
            BufferedWriter bf = new BufferedWriter(outputFile);
            // Collect the thread id ***
            long threadId = Thread.currentThread().getId();
            for (StackTraceElement ste: Thread.currentThread().getStackTrace()) {
                String ste2String = ste.toString();
                boolean foundInBlackList = false;

                String elem = threadId + "," + ste2String;
                String[] blackListedElement = {"edu.utexas.ece.flakesync.agent.Utility", "java.", "org.apache.lucene",
                    "org.junit", "org.apache.maven.surefire"};
                for (int i = 0; i < blackListedElement.length; i++) {
                    if (elem.contains(blackListedElement[i])) {
                        foundInBlackList = true;
                        break;
                    }
                }
                if (!(foundInBlackList)) {
                    String elemWithSlash =  elem.replace(".", "/");
                    bf.write(elemWithSlash);
                    bf.newLine();
                }
            }
            bf.write(threadId + ",END");
            bf.newLine();
            bf.flush();
            bf.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static void stack(Thread th) {
        try {
            java.lang.reflect.Field targetField = Thread.class.getDeclaredField("target");
            targetField.setAccessible(true);
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                stackTraceList.add(ste.toString());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Apply a yield for barrier point, waiting up until execution of critical point reaches a threshold
    public static void yield() {
        long start = System.currentTimeMillis();    // Set a time limit in case it goes too long
        long end = start;
        while ((testVarCount < threshold)
                && ((end = System.currentTimeMillis()) < start + (delay * 200))) {
            Thread.yield();
        }

        // Count as failure if it timed out
        if (end > start + (delay * 200)) {
            throw new RuntimeException("Yield timed out");
        }
        testVarCount = 0;
    }

    // Used to count how often the critical point was reached, for the barrier point
    public static void update() {
        testVarCount += 1;
    }

    // Used to count how often critical point was executed in normal run, to set threshold
    public static void counter() {
        executionCount += 1;
    }

    // Resets the count
    public static void init() {
        testVarCount = 0;
    }
}
