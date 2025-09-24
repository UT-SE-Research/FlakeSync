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
            System.out.println("delaying: " + delay);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            System.out.println("Exception");
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

}
