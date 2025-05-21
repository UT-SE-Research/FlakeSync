package edu.utexas.ece.localization.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
            } else {
                // failed if files have whitespaces or special characters
                InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader);
                String line = reader.readLine();
                while (line != null) {
                    blackList.add(line);
                    line = reader.readLine();
                }
                reader.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static boolean blackListContains(String name) {
        for (String prefix : blackList) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // White list consists of specific class names (not package prefixing as black list relies on)
    public static boolean locationListContains(String name) {
        if (locationList.isEmpty()) {
            locationList = new ArrayList<>();
            try {
                System.out.println("Location arg is given: " + System.getProperty("locations"));
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split("#", 2);
                    String givenClassName = arr[0].replaceAll("[/]",".");
                    System.out.println(givenClassName);
                    locationList.add(givenClassName);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return locationList.contains(name);
    }

    public static boolean rootClassLineContains(String name) {
        if (classLineList.isEmpty()) {
            classLineList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(
                    new File(System.getProperty("searchForMethodName"))));
                String line = reader.readLine();
                while (line != null) {
                    System.out.println("LINE=" + line);
                    String[] arr = line.split("#", 2);
                    System.out.println("From Agent arr[0] =" + arr[0]);
                    String givenClassName = arr[0].replaceAll("[/]", ".");
                    System.out.println("givenClassName =" + givenClassName);
                    classLineList.add(givenClassName);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        return classLineList.contains(name);
    }

    // White list consists of specific class names (not package prefixing as black list relies on)
    public static boolean rootMethodContains(String name) {
        if (rootMethodList.isEmpty()) {
            rootMethodList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("rootMethod"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split("#", 2);
                    System.out.println("From Agent arr[0] =" + arr[0]);
                    String givenClassName = arr[0].replaceAll("[/]", ".");
                    System.out.println("givenClassName =" + givenClassName);
                    rootMethodList.add(givenClassName);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        System.out.println(rootMethodList);
        return rootMethodList.contains(name);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
                className = className.replaceAll("[/]",".");

                // Use locationlist if it is defined as a property; otherwise rely on blacklist
                if (System.getProperty("rootMethod") != null && !blackListContains(className)
                        && System.getProperty("methodNameForDelayAtBeginning").equals("null")
                        && System.getProperty("locations").equals("null")) {
                    boolean methodExists = rootMethodContains(className);
                    if (methodExists) {
                        final ClassReader reader = new ClassReader(bytes);
                        final ClassWriter writer = new ClassWriter(reader,
                            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                        ClassVisitor visitor = new FunctionTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                    }

                } else if (System.getProperty("searchForMethodName") != null && !blackListContains(className)) {
                    boolean methodExists = rootClassLineContains(className);
                    if (methodExists) {
                        final ClassReader reader = new ClassReader(bytes);
                        final ClassWriter writer = new ClassWriter(reader,
                            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                        ClassVisitor visitor = new FunctionNameTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                    }
                } else if (System.getProperty("findRootOfRunMethod") != null && !blackListContains(className)) {
                    // Needed for finding root method of a run()
                    final ClassReader reader = new ClassReader(bytes);
                    final ClassWriter writer = new ClassWriter(reader,
                        ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    ClassVisitor visitor = new FunctionNameTracer(writer);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else if (!System.getProperty("locations").equals("null")) {
                    if (locationListContains(className) && !blackListContains(className)) {
                        final ClassReader reader = new ClassReader(bytes);
                        final ClassWriter writer = new ClassWriter(reader,
                            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                        if (System.getProperty("methodNameForDelayAtEnd") != null
                                || System.getProperty("methodNameForDelayAtBeginning") != null) {
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
                return null;
            }
        });
        printStartStopTimes();
    }

    public static void writeTo(final String outputPath, String output) {
        if (!Files.exists(Paths.get(outputPath))) {
            try {
                Files.createFile(Paths.get(outputPath));
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        try {
            Files.write(Paths.get(outputPath), output.getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
        } catch (IOException ioe) {
            ioe.printStackTrace(System.err);
        }
    }

    private static void printStartStopTimes() {
        final long start = System.currentTimeMillis();
        Thread hook = new Thread() {
            @Override
            public void run() {
                if (Utility.stackTraceList.size() > 0) {
                    String curDir = new File("").getAbsolutePath();
                    System.out.println("curDir=" + curDir);
                    for (String location: Utility.stackTraceList) {
                        writeTo("./.flakesync/stackTraceListFromRunMethod.txt", location + "\n");
                    }
                }

                if (FunctionTracer.methodRangeList.size() > 0) {
                    System.out.println("SIZE is more that 0");
                    BufferedWriter bfLocations = null;
                    boolean firstElement = true;
                    try {
                        FileWriter outputLocationsFile = new FileWriter("./.flakesync/MethodStartAndEndLine.txt");
                        bfLocations = new BufferedWriter(outputLocationsFile);

                        HashSet<String> alreadyWritten = new HashSet<>();
                        for (String location: FunctionTracer.methodRangeList) {
                            if (firstElement) {
                                    bfLocations.write(location);
                                    firstElement = false;
                                } else {
                                    if (!alreadyWritten.contains(location)) {
                                        bfLocations.write("-");
                                        bfLocations.write(location);
                                        bfLocations.write("#" + System.getProperty("methodOnly"));
                                        bfLocations.newLine();
                                        firstElement = true;
                                    }
                                }
                            alreadyWritten.add(location);
                        }

                        //bfLocations.newLine();
                        bfLocations.flush();
                    } catch (IOException ioe) {
                        System.out.println("An error occurred.");
                        ioe.printStackTrace();
                    } finally {
                        try {
                            bfLocations.close();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    System.err.println("Stop at ............................. " );
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);
    }
}
