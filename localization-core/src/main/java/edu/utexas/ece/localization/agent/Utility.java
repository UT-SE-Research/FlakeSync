package edu.utexas.ece.localization.agent;

import flakesync.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Utility {

    public static List<String> stackTraceList = new ArrayList<String>();
    private static int delay;

    static {
        try {
            delay = Integer.parseInt(System.getProperty("delay", "100"));
        } catch (NumberFormatException nfe) {
            delay = 100;
        }
    }

    public static void stack(Thread th) {
        System.out.println("CALLING STACK");
        try {
            java.lang.reflect.Field targetField = Thread.class.getDeclaredField("target");
            targetField.setAccessible(true);
            System.out.println("TARGET FIELD " + targetField.get(th).getClass());
            System.out.println("FOUND IT");
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                stackTraceList.add(ste.toString());
            }
        } catch (Exception ex) {
            System.out.println("EXCEPTION");
        }
    }

    public static void injectDelay() {
        System.out.println("1. Delaying");
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            System.out.println("Exception " );
            ie.printStackTrace();
        }
    }

    public static void delay(String testName, String className) {
        System.out.println("2. Delaying " + delay);
        try {
            Thread.sleep(delay);
            collectStackTrace(testName, className);
        } catch (InterruptedException ie) {
            System.out.println("Exception " );
            ie.printStackTrace();
        }
    }

    public static void delayInDifferentPlacesInMethod(String location) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            System.out.println("Exception " );
            ie.printStackTrace();
        }
    }

    public static void onlyDelay(String location) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            System.out.println("Exception " );
            ie.printStackTrace();
        }
    }

    public static void collectStackTrace(String testName, String className) {
        System.out.println("HOW DO I GET THIS TO BE CALLED");
        className = className.replaceAll("[/]",".");
        System.out.println("collectStackTrace*** ClassName=" + className);
        String[] classNameItems = className.split("#", 2);
        String fileName = String.valueOf(Constants.getStackTraceFilepath(testName));
        try {
            FileWriter outputFile = new FileWriter(fileName, true);
            BufferedWriter bf = new BufferedWriter(outputFile);
            // Collect the thread id ***
            long threadId = 0000;
            for (StackTraceElement ste: Thread.currentThread().getStackTrace()) {
                String ste2String = ste.toString();
                System.out.println(ste2String);
                boolean foundInBlackList = false;

                threadId = Thread.currentThread().getId();
                String elem = threadId + "," + ste2String;
                String[] blackListedElement = {"edu.utexas.ece.localization.agent.Utility", "java.", "org.apache.lucene",
                    "org.junit", "org.apache.maven.surefire"};
                for (int i = 0; i < blackListedElement.length; i++) {
                    if (elem.contains(blackListedElement[i])) {
                        foundInBlackList = true;
                        break;
                    }
                }
                if (!(foundInBlackList)) {
                    String elemWithSlash =  elem.replace(".", "/");
                    System.out.println("&&&&&&&&&" + elemWithSlash);
                    bf.write(elemWithSlash);
                    bf.newLine();
                }
            }
            bf.write(threadId + ",END");
            bf.newLine();
            bf.flush();
            bf.close();
        } catch (IOException ioe) {
            System.out.println("I/O happened");
        }
    }
}