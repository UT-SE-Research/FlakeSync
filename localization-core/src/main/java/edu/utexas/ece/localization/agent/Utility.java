package edu.utexas.ece.localization.agent;

import java.util.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Utility{
    private static int delay;
    public static List<String> stackTraceList = new ArrayList<String>();  

    static {
        try {
            delay = Integer.parseInt(System.getProperty("delay", "100"));
        } catch (NumberFormatException e) {
            delay = 100;
        }
    }
  
    public static void stack(Thread t)/* throws Exception*/ {
        System.out.println("CALLING STACK");
        try {
            java.lang.reflect.Field targetField = Thread.class.getDeclaredField("target");
            targetField.setAccessible(true);
            System.out.println("TARGET FIELD " + targetField.get(t).getClass());
            //if (targetField.get(t).getClass().getName().contains("WebSocketClient") || targetField.get(t).getClass().getSuperclass().getName().contains("WebSocketClient")) {
                System.out.println("FOUND IT");
                for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                    //System.out.println("ste="+ste);
                    stackTraceList.add(ste.toString());
                }
            //}
        } catch (Exception ex) {
            System.out.println("EXCEPTION");
        }
    }  

 
    public static void injectDelay() {
        try {
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            System.out.println("Exception " );
            e.printStackTrace();
        }
    }

    public static void delay(String testName, String className) {
        /*int x = 42; 
        for (long i = 0L; i < 10000000000L; i++) {
            x = x*3 / 234 + 32 * x;
        } */

        collectStackTrace(testName, className);
        try {
            //locationSavedInFile(location);
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            System.out.println("Exception " );
            e.printStackTrace();
        }

/*        try {
            collectStackTrace(testName, className);
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            System.out.println("Exception " );
            e.printStackTrace();
        }*/
    }
    public static void delayInDifferentPlacesInMethod(String location) {
        //System.out.println("Delay injected for testName ");
        try {
            //locationSavedInFile(location);
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            System.out.println("Exception " );
            e.printStackTrace();
        }
    }

    public static void onlyDelay(String location) {
        //System.out.println("Delay injected for location, delay= "+delay);
        try {
            //locationSavedInFile(location);
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            System.out.println("Exception " );
            e.printStackTrace();
        }
    }


    public static void collectStackTrace(String testName, String className) {
        className = className.replaceAll("[/]",".");
        //System.out.println("collectStackTrace*** ClassName="+className);
        String[] classNameItems = className.split("#", 2);
        //System.out.println("Stack Trace Collect, className="+className);
        String fileName="./.flakesync/StackTrace-"+testName+".txt";
        try {   
            FileWriter outputFile = new FileWriter(fileName, true);
            BufferedWriter bf = new BufferedWriter(outputFile);
            //collect the thread id ***
            long threadId=0000;
            for (StackTraceElement ste: Thread.currentThread().getStackTrace()){
                String ste2String=ste.toString();
                boolean foundInBlackList=false;

                threadId = Thread.currentThread().getId();
                String elem=threadId+","+ste2String;
                String[] blackListedElement = {"edu.utexas.ece.localization.agent.Utility", "java.", "org.apache.lucene", "org.junit", "org.apache.maven.surefire" };
                for (int i=0; i<blackListedElement.length; i++){
                    if (elem.contains(blackListedElement[i])){
                        foundInBlackList=true;
                        break;
                    }
                }
                if (! (foundInBlackList)){
                    String elemWithSlash =  elem.replace(".", "/"); //ste2String.replaceAll("[.]","/");
                    bf.write(elemWithSlash);
                    bf.newLine();
                }
            }
            bf.write( threadId+ ",END");
            bf.newLine();
            bf.flush();
            bf.close();
         }catch(IOException e){
            System.out.println("I/O happened");
         }
    }
}
