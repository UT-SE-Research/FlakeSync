/* Put the license back with different names and current year*/
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class Agent {

    private static List<String> blackList;
    private static List<String> whiteList = new ArrayList<>();

    //Pass these as property variables instead of hard-coding them here
    private static String OUTPUT_DIR_NAME = ".flakesync";
    private static String CONCURRENT_METHODS = "ResultMethods.txt";
    private static String LOCATIONS = "Locations.txt";


    static {
        //Set up the blacklist
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
        //Set up the whitelist
        if (whiteList.isEmpty() && System.getProperty("whitelist") != null) {
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
                className = className.replaceAll("[/]", ".");

                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                ClassVisitor visitor;
                if (!blackListContains(className)) {
                    if (System.getProperty("agentmode").equals("CONCURRENT_METHODS")) {
                        visitor = new ConcurrentMethodsClassTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                    } else if (whiteListContains(className)) {
                        if (System.getProperty("agentmode").equals("ALL_LOCATIONS")) {
                            //System.out.println(System.getProperty("concurrentmethods"));
                            //System.out.println(System.getProperty("whitelist"));
                            visitor = new InjectDelayClassTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        } else if (System.getProperty("agentmode").equals("DELTA_DEBUG")) {
                            visitor = new DeltaDebugClassTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        }
                    }
                }
                return null;
            }
        });
        Paths.get(OUTPUT_DIR_NAME).toFile().mkdirs();
        setupShutdownWriter();
    }

    private static void setupShutdownWriter() {
        Thread hook = new Thread() {
            @Override
            public void run() {

                BufferedWriter bfMethods = null;
                BufferedWriter bfLocations = null;

                try {
                    if (System.getProperty("agentmode").equals("CONCURRENT_METHODS")) {
                        try {
                            Paths.get(OUTPUT_DIR_NAME, CONCURRENT_METHODS).toFile().createNewFile();
                            Path fp = Paths.get(OUTPUT_DIR_NAME, CONCURRENT_METHODS);
                            File omf = new File(fp.toUri());
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
                    } else if (System.getProperty("agentmode").equals("ALL_LOCATIONS")) {
                        Paths.get(OUTPUT_DIR_NAME, LOCATIONS).toFile().createNewFile();
                        Path fp = Paths.get(OUTPUT_DIR_NAME, LOCATIONS);
                        File locsFile = new File(fp.toUri());
                        FileWriter outputLocationsFile = new FileWriter(locsFile);
                        bfLocations = new BufferedWriter(outputLocationsFile);

                        synchronized (InjectDelayClassTracer.locations) {
                            for (String location : InjectDelayClassTracer.locations) {
                                bfLocations.write(location + "&" + System.getProperty("delay"));
                                bfLocations.newLine();
                            }
                        }
                        bfLocations.flush();
                    }
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
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);
    }
}

