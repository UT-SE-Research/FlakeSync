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
import java.util.List;

public class Agent {

    private static String delay;
    private static List<String> blackList;
    private static List<String> whiteList = new ArrayList<>();


    static {
        //Set up the blacklist
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

                //Set up delay
                delay = System.getProperty("delay");

                ClassVisitor visitor;
                if (!blackListContains(className)) {
                    if (System.getProperty("agentmode").equals("CONCURRENT_METHODS")) {
                        visitor = new ConcurrentMethodsClassTracer(writer);
                        reader.accept(visitor, 0);
                        return writer.toByteArray();
                    } else if (whiteListContains(className)) {
                        if (System.getProperty("agentmode").equals("ALL_LOCATIONS")) {
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
        Paths.get(Constants.DEFAULT_FLAKESYNC_DIR).toFile().mkdirs();
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
                    } else if (System.getProperty("agentmode").equals("ALL_LOCATIONS")) {
                        Path fp = Constants.getAllLocationsFilepath(
                                System.getProperty(".test"));

                        File locsFile = new File(fp.toUri());
                        FileWriter outputLocationsFile = new FileWriter(locsFile);
                        bfLocations = new BufferedWriter(outputLocationsFile);
                        bfLocations.write(delay);
                        synchronized (InjectDelayClassTracer.locations) {
                            for (String location : InjectDelayClassTracer.locations) {
                                bfLocations.write(location);
                                bfLocations.newLine();
                            }
                        }
                        bfLocations.flush();
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
