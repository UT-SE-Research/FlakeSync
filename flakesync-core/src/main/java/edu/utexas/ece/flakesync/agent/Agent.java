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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Agent {

    private static String delay;
    private static List<String> blackList;
    private static List<String> whiteList = new ArrayList<>();
    private static List<String> locationList = new ArrayList<String>();
    private static List<String> rootMethodList = new ArrayList<String>();
    private static List<String> classLineList = new ArrayList<String>();

    static {
        // Set up the blacklist
        blackList = new ArrayList<>();
        try {
            // get the file url, not working in JAR file.
            ClassLoader classloader = Agent.class.getClassLoader();
            InputStream is = classloader.getResourceAsStream("blacklist.txt");

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

    // White list consists of specific class names (not package prefixing as black list relies on)
    public static boolean locationListContains(String name) {
        if (locationList.isEmpty()) {
            locationList = new ArrayList<String>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split("#", 2);
                    String givenClassName = arr[0].replaceAll("[/]",".");
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

    // White list consists of specific class names (not package prefixing as black list relies on)
    private static boolean rootMethodContains(String name) {
        if (rootMethodList.isEmpty()) {
            rootMethodList = new ArrayList<String>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("rootMethod"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split("#", 2);
                    String givenClassName = arr[0].replaceAll("[/]", ".");
                    rootMethodList.add(givenClassName);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return rootMethodList.contains(name);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {

                className = className.replaceAll("[/]", ".");

                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                // Set up delay
                delay = System.getProperty("delay");
                String mode = System.getProperty("agentmode");

                ClassVisitor visitor;
                if (!blackListContains(className)) {
                    if (mode.equals("CONCURRENT_METHODS")) {
                        visitor = new ConcurrentMethodsClassTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                    } else if (mode.equals("ROOT_METHOD_ANALYSIS")) { // Root Method Analysis
                        boolean methodExists = rootMethodContains(className);
                        if (methodExists) {
                            visitor = new FunctionTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        }
                    } else if (mode.equals("DELAY_INJECTION_BY_METHOD")) {
                        visitor = new FunctionNameTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                    } else if (mode.equals("DELAY_INJECTION_BY_LOC")) {
                        if (locationListContains(className)) {
                            visitor = new InjectDelayWithStackTraceTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        }
                    // These modes rely on white list
                    } else if (whiteListContains(className)) {
                        if (mode.equals("ALL_LOCATIONS")) {
                            visitor = new InjectDelayClassTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        } else if (mode.equals("DELTA_DEBUG")) {
                            visitor = new DeltaDebugClassTracer(writer);
                            reader.accept(visitor, 0);
                            return writer.toByteArray();
                        }
                    }
                }
                return null;
            }
        });
        Paths.get(Constants.DEFAULT_FLAKESYNC_DIR).toFile().mkdirs();
        setupShutdownWriter();
    }

    private static void setupShutdownWriter() {
        Thread hook = new Thread() {
            @Override
            public void run() {

                BufferedWriter bfMethods = null;
                BufferedWriter bfLocations = null;

                String mode = System.getProperty("agentmode");

                try {
                    if (mode.equals("CONCURRENT_METHODS")) {
                        Path fp = Constants.getConcurrentMethodsFilepath(
                                System.getProperty(".test"));
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
                    } else if (mode.equals("ALL_LOCATIONS")) {
                        Path fp = Constants.getAllLocationsFilepath(
                                System.getProperty(".test"));
                        File locsFile = new File(fp.toUri());
                        FileWriter outputLocationsFile = new FileWriter(locsFile);
                        bfLocations = new BufferedWriter(outputLocationsFile);
                        bfLocations.write(delay);
                        bfLocations.newLine();
                        synchronized (InjectDelayClassTracer.locations) {
                            for (String location : InjectDelayClassTracer.locations) {
                                bfLocations.write(location);
                                bfLocations.newLine();
                            }
                        }
                        bfLocations.flush();
                    } else if (mode.equals("ROOT_METHOD_ANALYSIS")) {
                        if (FunctionTracer.methodRangeList.size() > 0) {
                            boolean firstElement = true;
                            try {
                                String path = String.valueOf(Constants.getMethodStartEndLineFile(".",
                                    System.getProperty(".test")));
                                FileWriter outputLocationsFile = new FileWriter(path);
                                bfLocations = new BufferedWriter(outputLocationsFile);

                                Set<String> alreadyWritten = new HashSet<>();
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

                                bfLocations.flush();
                            } catch (IOException ioe) {
                                ioe.printStackTrace();
                            } finally {
                                try {
                                    bfLocations.close();
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } finally {
                    try {
                        if (bfLocations != null) {
                            bfLocations.close();
                        }
                        if (bfMethods != null) {
                            bfMethods.close();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);
    }
}
