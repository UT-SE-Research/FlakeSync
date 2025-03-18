package barriersearch.agent;

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
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class Agent {

    private static List<String> blackList;

    static {
        blackList = new ArrayList<>();
        try {
            // Get the file url, not working in JAR file.
            ClassLoader classloader = Agent.class.getClassLoader();
            InputStream is = classloader.getResourceAsStream("blacklist.txt");
            if (is == null) {
                System.err.println("blacklist.txt not found");
            } else {
                // Failed if files have whitespaces or special characters
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
                String codeUnderTest = codeToIntroduceVariable.split("#")[0]; // code-undet-test class
                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                ClassVisitor visitor;

                // Going to add delay and collect the stacktrace
                if (!blackListContains(className) && System.getProperty("stackTraceCollect") != null) {
                    visitor = new StackTraceTracer(writer, codeToIntroduceVariable);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else if (!blackListContains(className) && System.getProperty("executionMonitor") != null) {
                    visitor = new ExecutionMonitorTracer(writer, codeToIntroduceVariable);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else if (!blackListContains(className) && System.getProperty("searchMethodEndLine") != null) {
                    visitor = new MethodEndLineTracer(writer, codeToIntroduceVariable);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else {
                    // YIELDING_POINT may or may not be a test_method's location
                    String yieldPointInfo = System.getProperty("YieldingPoint");
                    String tcls = yieldPointInfo.split("#")[0]; // test-class
                    // Need substring match, test-class name is not coming here
                    if (!blackListContains(className) && (className.equals(codeUnderTest) || className.equals(tcls))) {
                        System.out.println("ELSE****ALLOWED CLASS=" + className + ",yieldPointInfo="
                            + tcls + ",codeUnderTest=" + codeUnderTest);
                        visitor = new RandomClassTracer(writer, yieldPointInfo, codeToIntroduceVariable);
                        reader.accept(visitor, 0);

                        if (RandomClassTracer.methodAndLine != null) {
                            try {
                                BufferedWriter bf = new java.io.BufferedWriter(
                                    new java.io.FileWriter("SearchedMethodANDLine.txt"));
                                bf.write(RandomClassTracer.methodAndLine + "\n");
                                bf.flush();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }

                        try {
                            BufferedWriter bfFlag = new java.io.BufferedWriter(
                                new java.io.FileWriter("FlagDelayANDUpdateANDYielding.txt"));
                            bfFlag.write("Delay=" + RandomClassTracer.delayed + "\n");
                            bfFlag.write("Update=" + RandomClassTracer.updateFlag + "\n");
                            bfFlag.write("Yield=" + RandomClassTracer.yieldEntered + "\n");
                            bfFlag.flush();

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        return writer.toByteArray();
                    }
                }
                return null;

            }
        });

        printStartStopTimes(); // Will print in a file
    }

    private static void printStartStopTimes() {
        Thread hook = new Thread() {
            @Override
            public void run() {
                if (MethodEndLineTracer.methodEndLine != null) {
                    try {
                        BufferedWriter bf = new BufferedWriter(new FileWriter("SearchedMethodEndLine.txt"));
                        bf.write("methodEndLine=" + MethodEndLineTracer.methodEndLine + "\n");
                        bf.flush();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }


                if (Utility.executionCount > 0) {
                    try {
                        BufferedWriter bf = new BufferedWriter(new FileWriter("ExecutionMonitor.txt"));
                        bf.write("#execution=" + Utility.executionCount + "\n");
                        bf.flush();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(hook);
    }
}
