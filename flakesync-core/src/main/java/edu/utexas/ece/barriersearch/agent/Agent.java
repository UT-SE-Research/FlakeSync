package edu.utexas.ece.barriersearch.agent;

import edu.utexas.ece.flakesync.agent.Utility;
import flakesync.Constants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class Agent {
    private static List<String> blackList;

    static {
        blackList = new ArrayList<>();
        try {
            // get the file url, not working in JAR file.
            ClassLoader classloader = Agent.class.getClassLoader();
            InputStream is = classloader.getResourceAsStream("blacklist.txt");
            if (is == null) {
                System.err.println("blacklist.txt not found");
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

    public static boolean blackListContains(String className) {
        for (String prefix : blackList) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
                if (className == null) {
                    return null;
                }
                className = className.replaceAll("[/]",".");

                String codeToIntroduceVariable = System.getProperty("CodeToIntroduceVariable");
                String codeUnderTest = codeToIntroduceVariable.split("#")[0]; // code-under-test class
                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES
                    | ClassWriter.COMPUTE_MAXS);
                ClassVisitor visitor;

                String mode = System.getProperty("agentmode");
                if (mode.equals("BARRIER_ST") && !blackListContains(className)) {
                    visitor = new StackTraceTracer(writer, codeToIntroduceVariable);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else if (mode.equals("EXEC_MONITOR") && !blackListContains(className)) {
                    synchronized (Utility.class) {
                        visitor = new ExecutionMonitorTracer(writer, codeToIntroduceVariable);
                        reader.accept(visitor, 0);
                    }
                    return writer.toByteArray();
                } else if ((mode.equals("DOWNWARD_MVN")) && !blackListContains(className)) {
                    synchronized (Utility.class) {
                        visitor = new MethodEndLineTracer(writer, codeToIntroduceVariable);
                        reader.accept(visitor, 0);
                    }
                    return writer.toByteArray();
                } else if (mode.equals("ADD_YIELD_PT1") || mode.equals("ADD_YIELD_PT2")) {
                    // YIELDING_POINT may or may not be a test_method's location
                    String yieldPointInfo = System.getProperty("YieldingPoint");
                    String tcls = yieldPointInfo.split("#")[0]; // test-class

                    // Need substring match, test-class name is not coming here
                    if ((className.equals(codeUnderTest) || className.equals(tcls)) && !blackListContains(className)) {
                        visitor = new DelayAndYieldInjector(writer, yieldPointInfo, codeToIntroduceVariable);
                        reader.accept(visitor, 0);

                        if (DelayAndYieldInjector.methodAndLine != null) {
                            try {
                                BufferedWriter bf = new BufferedWriter(new FileWriter(
                                        String.valueOf(Constants.getSearchMethodANDLineFilepath(".",
                                                System.getProperty(".test")))
                                ));
                                bf.write(DelayAndYieldInjector.methodAndLine + "\n");
                                bf.flush();
                            } catch (IOException ioe) {
                                ioe.printStackTrace();
                            }
                        }

                        try {
                            BufferedWriter bfFlag = new BufferedWriter(new FileWriter(
                                    String.valueOf(Constants.getYieldResultFilepath(".",
                                            System.getProperty(".test")))
                            ));
                            bfFlag.write("Delay=" + DelayAndYieldInjector.delayed + "\n");
                            bfFlag.write("Update=" + DelayAndYieldInjector.updateFlag + "\n");
                            bfFlag.write("Yield=" + DelayAndYieldInjector.yieldEntered + "\n");
                            bfFlag.flush();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                        return writer.toByteArray();
                    }
                }
                return null;

            }
        });

        printStartStopTimes();
    }

    private static void printStartStopTimes() {
        Thread hook = new Thread() {
            @Override
            public void run() {
                if (MethodEndLineTracer.methodEndLine != null) {
                    try {
                        java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter(
                                String.valueOf(Constants.getSearchMethodEndLineFilepath(".",
                                        System.getProperty(".test")))
                        ));
                        bf.write("methodEndLine=" + MethodEndLineTracer.methodEndLine + "\n");
                        bf.flush();

                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }


                if (Utility.executionCount > 0) {
                    try {
                        java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter(
                            String.valueOf(Constants.getThresholdFilepath(".", System.getProperty(".test")))
                        ));
                        bf.write("#execution=" + Utility.executionCount + "\n");
                        bf.flush();

                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        };

        Runtime.getRuntime().addShutdownHook(hook);
    }
}
