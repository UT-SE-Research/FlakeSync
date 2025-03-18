package edu.utexas.ece.flakesync.agent;

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
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class Agent {

    private static List<String> blackList;
    private static List<String> whiteList = new ArrayList<>();

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
                    // read next line
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
    public static boolean whiteListContains(String className) {
        if (whiteList.isEmpty()) {
            whiteList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("whitelist"))));
                String line = reader.readLine();
                while (line != null) {
                    whiteList.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return whiteList.contains(className);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
                className = className.replaceAll("[/]",".");

                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                ClassVisitor visitor;
                if (!blackListContains(className) && (System.getProperty("whitelist") == null)) {
                    System.out.println("no whitelist given and going to execute EnterExit");
                    visitor = new EnterExitClassTracer(writer);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else if ((System.getProperty("whitelist") != null)
                    ? whiteListContains(className) : !blackListContains(className)) {
                    // Use whitelist if it is defined as a property,
                    // otherwise rely on blacklist, whitelist will only be given if we want to run flakesync detector
                    System.out.println("whitelist check true");
                    // If the concurrentmethods option is not set (meaning we do not know what the concurrent methods are),
                    // use the EnterExitClassTracer to find them
                    visitor = new RandomClassTracer(writer);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                }
                return null;
            }
        });
        Paths.get(".flakesync").toFile().mkdirs();
        printStartStopTimes();
    }

    private static void printStartStopTimes() {
        final long start = System.currentTimeMillis();
        Thread hook = new Thread() {
            @Override
            public void run() {
                List<String> conflictingListPair = new ArrayList();
                List<String> trapListPair = new ArrayList();
                long timePassed = System.currentTimeMillis() - start;
                System.err.println("Stop at ............................. " );
                //int size = Utility.resultInterception.size();
                //System.out.println(" Total # Conflicting items are = " + size);
                //System.out.println();
                BufferedWriter bf = null;
                BufferedWriter bfTrap = null;
                BufferedWriter bfLocations = null;
                BufferedWriter bfThreads = null;
                BufferedWriter bfConcurrentMethodsPairs = null;

                try {
                    //When are the files being overwritten???? Check the execution to see where this is happening
                    System.out.println("Found AGENT PRINT****");
                    if (edu.utexas.ece.flakesync.agent.Utility.methodsRunConcurrently.size() > 0) {
                        BufferedWriter bfMethods = null;
                        try {
                            System.out.println("Found CONCURRENT****");
                            File omf = new File("./.flakesync/ResultMethods.txt");
                            FileWriter outputMethodsFile = new FileWriter(omf);
                            bfMethods = new BufferedWriter(outputMethodsFile);
                            synchronized (Utility.methodsRunConcurrently) {
                                for (String meth : Utility.methodsRunConcurrently) {
                                    bfMethods.write(meth);
                                    bfMethods.newLine();
                                }
                            }
                            bfMethods.flush();
                        } finally {
                            bfMethods.close();
                        }
                    } else {
                        System.out.println(":(");
                    }

                    if (RandomClassTracer.locations.size() > 0) {
                        File locsFile = new File("./.flakesync/Locations.txt");
                        FileWriter outputLocationsFile = new FileWriter(locsFile);
                        bfLocations = new BufferedWriter(outputLocationsFile);

                        for (String location : RandomClassTracer.locations) {
                            bfLocations.write(location + "&" + System.getProperty("delay"));
                            bfLocations.newLine();
                        }
                        bfLocations.flush();
                    }
                    File otc = new File("./.flakesync/ThreadCountList.txt");
                    FileWriter outputThreadCount = new FileWriter(otc);
                    bfThreads = new BufferedWriter(outputThreadCount);

                    int totalThreadCount = Utility.threadCountListFromUtility.size();
                    String threadCountNo = Integer.toString(totalThreadCount);
                    System.out.println(" Total # Thread Count = " + totalThreadCount);
                    bfThreads.write(threadCountNo);
                    bfThreads.newLine();
                    bfThreads.flush();
                } catch (IOException ioe) {
                    System.out.println("An error occurred.");
                    ioe.printStackTrace();
                } finally {
                    try {
                        bf.close();
                        bfTrap.close();
                        bfLocations.close();
                        bfThreads.close();
                        bfConcurrentMethodsPairs.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                int size = conflictingListPair.size();
                System.out.println(" Total # Conflicting items are = " + size);
                System.out.println(conflictingListPair);
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);
    }
}

