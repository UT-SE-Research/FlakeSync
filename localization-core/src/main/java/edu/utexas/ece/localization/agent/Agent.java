package edu.utexas.ece.localization.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import java.io.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Agent {

    private static List<String> blackList;
    private static List<String> locationList = new ArrayList<>();
    private static List<String> rootMethodList = new ArrayList<>();
    private static List<String> classLineList = new ArrayList<>();

    static {
        blackList = new ArrayList<>();
        try {
            // get the file url, not working in JAR file.
            ClassLoader classloader = Agent.class.getClassLoader();
            InputStream is = classloader.getResourceAsStream("blacklist.txt");
            if (is == null) {
                System.out.println("blacklist.txt not found");
            }
            else {
                // failed if files have whitespaces or special characters
                InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader);
                String line = reader.readLine();
                while (line != null) {
                    blackList.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean blackListContains(String s) {
        for (String prefix : blackList) {
            if (s.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // White list consists of specific class names (not package prefixing as black list relies on)
    public static boolean locationListContains(String s) {
        if (locationList.isEmpty()) {
            locationList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                System.out.println("Location arg is given: " + System.getProperty("locations"));
                //else //if (System.getProperty("findRootOfRunMethod") != null)  // Needed to find root method of run(), because the stacktrace for this run() is hidden
                //    reader = new BufferedReader(new FileReader(new File(System.getProperty("findRootOfRunMethod"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split("#", 2);
                    String givenClassName = arr[0].replaceAll("[/]",".");
                    System.out.println(givenClassName);
                    locationList.add(givenClassName);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //System.out.println(locationList);
        return locationList.contains(s);
    }

    public static boolean rootClassLineContains(String s) {
        if (classLineList.isEmpty()) {
            classLineList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("searchForMethodName"))));
                String line = reader.readLine();
                while (line != null) {
                    System.out.println(" LINE="+line);
                    String[] arr = line.split("#", 2);
                    System.out.println("From Agent arr[0] ="+arr[0]);
                    String givenClassName = arr[0].replaceAll("[/]",".");
                    System.out.println("givenClassName ="+givenClassName);
                    //String methodName = arr[1];
                    classLineList.add(givenClassName);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return classLineList.contains(s);
    }
    // White list consists of specific class names (not package prefixing as black list relies on)
    public static boolean rootMethodContains(String s) {
        if (rootMethodList.isEmpty()) {
            rootMethodList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("rootMethod"))));
                String line = reader.readLine();
                while (line != null) {
                    //System.out.println(" LINE="+line);
                    String[] arr = line.split("#", 2);
                    System.out.println("From Agent arr[0] ="+arr[0]);
                    String givenClassName = arr[0].replaceAll("[/]",".");
                    System.out.println("givenClassName ="+givenClassName);
                    //String methodName = arr[1];
                    rootMethodList.add(givenClassName);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println(rootMethodList);
        return rootMethodList.contains(s);
    }

    public static void premain(String agentArgs, Instrumentation inst) {

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass,
                    ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
                s = s.replaceAll("[/]",".");
                System.out.println("PREMAIN****" + System.getProperty("rootMethod") + " " + System.getProperty("locations")
                + " " + System.getProperty("methodNameForDelayAtBeginning"));
                //System.out.println(System.getProperty("locations"));
                // Use locationlist if it is defined as a property; otherwise rely on:q
                // blacklist
                if (System.getProperty("rootMethod") != null && !blackListContains(s) &&
                        System.getProperty("methodNameForDelayAtBeginning").equals("null")
                        && System.getProperty("locations").equals("null")) {
                    System.out.println("1. Trying to analyze root method" + System.getProperty("rootMethod"));
                    //System.out.println("***s="+s);
                    boolean methodExists = rootMethodContains(s);
                    if ( methodExists ) {
                        System.out.println("2. Trying to analyze root method");
                      System.out.println("rootMethod From ASM");
                      final ClassReader reader = new ClassReader(bytes);
                      final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
                      ClassVisitor visitor = new FunctionTracer(writer);
                      reader.accept(visitor, 0);
                      return writer.toByteArray();
                    }

                }
                else if (System.getProperty("searchForMethodName") != null && !blackListContains(s) ) {
                    System.out.println("Agent Get method, className=" +s);
                    boolean methodExists = rootClassLineContains(s);
                    if ( methodExists){
                        System.out.println("Class FOUND Agent Get method, className=" +s);
                        final ClassReader reader = new ClassReader(bytes);
                        final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
                        ClassVisitor visitor = new FunctionNameTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                     }
                }

                //else if (System.getProperty("findRootOfRunMethod") != null ? locationListContains(s) : !blackListContains(s)) { // Needed for finding root method of a run()
                else if (System.getProperty("findRootOfRunMethod") != null && !blackListContains(s)) { // Needed for finding root method of a run()
                    final ClassReader reader = new ClassReader(bytes);
                    final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS );
                    //System.out.println("I AM AGENT ");
                    ClassVisitor visitor = new FunctionNameTracer(writer); 
                    reader.accept(visitor, 0);
                    return writer.toByteArray();

                } else if (System.getProperty("locations") != null) {
                    System.out.println(locationListContains(s) + " " + !blackListContains(s));
                    if (locationListContains(s) && !blackListContains(s)) {
                        final ClassReader reader = new ClassReader(bytes);
                        final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS );
                        System.out.println("2. Delay Injection");
                        if (System.getProperty("methodNameForDelayAtEnd") != null || System.getProperty("methodNameForDelayAtBeginning") !=null) {
                            System.out.println("3. Delay Injection");
                            ClassVisitor visitor = new FunctionNameTracer(writer); 
                            reader.accept(visitor, 0);
                            return writer.toByteArray();

                        } else {
                            ClassVisitor visitor = new RandomClassTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        }
                    }
                }
                //System.out.println("3. Trying to analyze root method");

                return null;
            }
        });
        printStartStopTimes();
    }
    
	public static void writeTo(final String outputPath, String output) {
        if (!Files.exists(Paths.get(outputPath))) {
            try {
                Files.createFile(Paths.get(outputPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Files.write(Paths.get(outputPath), output.getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }	

    private static void printStartStopTimes() {
        final long start = System.currentTimeMillis();
        Thread hook = new Thread() {
            @Override
            public void run() {
                if (Utility.stackTraceList.size() > 0) {
					String curDir = new File("").getAbsolutePath();
					System.out.println("curDir="+curDir);
                    for (String location: Utility.stackTraceList) {
                        writeTo("./.flakesync/stackTraceListFromRunMethod.txt", location + "\n");
                    }
                }

                if (FunctionTracer.methodRangeList.size() > 0) {
                    System.out.println("SIZE is more that 0");
                    BufferedWriter bfLocations = null;
                    boolean firstElement=true;
                    try { 
                        FileWriter outputLocationsFile = new FileWriter("./.flakesync/MethodStartAndEndLine.txt");
                        bfLocations = new BufferedWriter(outputLocationsFile);

                        HashSet<String> alreadyWritten = new HashSet<>();
                        for (String location: FunctionTracer.methodRangeList) {
                            if (firstElement) {
                                    bfLocations.write(location);
                                    firstElement=false;
                                } else {
                                    if (!alreadyWritten.contains(location)) {
                                        bfLocations.write("-");
                                        bfLocations.write(location);
                                        bfLocations.write("#" + System.getProperty("methodOnly"));
                                        bfLocations.newLine();
                                        firstElement=true;
                                    }
                                }
                            alreadyWritten.add(location);
                        }
                        //bfLocations.newLine();
                        bfLocations.flush(); 
                    } catch (IOException e) {
                        System.out.println("An error occurred.");
                        e.printStackTrace();
                    }
				    finally {
				    	try {
				    		bfLocations.close();
				    	} catch (Exception e) {}
				    }
                    System.err.println("Stop at ............................. " );
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);
    }
}

