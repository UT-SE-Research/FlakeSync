package edu.utexas.ece.barrierSearch.agent;

import flakesync.Constants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

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

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass,
                                    ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
                if (s == null) return null;
                s = s.replaceAll("[/]",".");

                String codeToIntroduceVariable = System.getProperty("CodeToIntroduceVariable");
                String codeUnderTest=codeToIntroduceVariable.split("#")[0]; // code-under-test class
                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS );
                ClassVisitor visitor;

                String mode = System.getProperty("agentmode");
                if (mode.equals("BARRIER_ST") && !blackListContains(s)) {
                    //System.out.println("running for stacktrace");
                    visitor = new StackTraceTracer(writer, codeToIntroduceVariable);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                } else if (mode.equals("EXEC_MONITOR") && !blackListContains(s)) {
                    synchronized (edu.utexas.ece.barrierSearch.agent.Utility.class) {
                        visitor = new ExecutionMonitorTracer(writer, codeToIntroduceVariable);
                        reader.accept(visitor, 0);
                    }
                    return writer.toByteArray();
                } else if ((mode.equals("DOWNWARD_MVN"))
                        && !blackListContains(s)) {
                    synchronized (edu.utexas.ece.barrierSearch.agent.Utility.class) {
                        System.out.println("Starting search method steps");
                        visitor = new MethodEndLineTracer(writer, codeToIntroduceVariable);
                        reader.accept(visitor, 0);
                    }
                    return writer.toByteArray();
                }
                else if(mode.equals("ADD_YIELD_PT1") || mode.equals("ADD_YIELD_PT2")){
                    String yieldPointInfo = System.getProperty("YieldingPoint"); // YIELDING_POINT may or may not be a test_method's location
                    String tcls=yieldPointInfo.split("#")[0]; // test-class

                    if ((s.equals(codeUnderTest) || s.equals(tcls)) && !blackListContains(s)) { // Need substring match, test-class name is not coming here
                        visitor = new RandomClassTracer(writer, yieldPointInfo, codeToIntroduceVariable);
                        reader.accept(visitor, 0);

                        if (RandomClassTracer.methodAndLine != null) {
                            System.out.println("Entered block to make file");
                            try {
                                java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter(
                                        String.valueOf(Constants.getSearchMethodANDLineFilepath(".",
                                                System.getProperty(".test")))
                                ));
                                bf.write(RandomClassTracer.methodAndLine +"\n");
                                bf.flush();
                            } catch (Exception ex) {}

                        }

                        try{
                            java.io.BufferedWriter bfFlag = new java.io.BufferedWriter(new java.io.FileWriter(
                                    String.valueOf(Constants.getYieldResultFilepath(".",
                                            System.getProperty(".test")))
                            ));
                            bfFlag.write("Delay="+ RandomClassTracer.delayed +"\n");
                            bfFlag.write("Update="+ RandomClassTracer.updateFlag +"\n");
                            bfFlag.write("Yield="+ RandomClassTracer.yieldEntered +"\n");
                            bfFlag.flush();

                        } catch (Exception ex) {}
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
                        bf.write("methodEndLine="+MethodEndLineTracer.methodEndLine +"\n");
                        bf.flush();

                    } catch (Exception ex) {}
                }


                if (Utility.executionCount > 0) {
                    try {
                        java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter(
                                String.valueOf(Constants.getThresholdFilepath(".", System.getProperty(".test")))
                        ));
                        bf.write("#execution="+Utility.executionCount +"\n");
                        bf.flush();

                    } catch (Exception ex) {}
                }
            }
        };

        Runtime.getRuntime().addShutdownHook(hook);
    }

   /*private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("JVM shutting down...");
            }
        });
    }
    private static void writeTo(final String outputPath, String output) {
        System.err.println("output from writeTo="+output);
        if (!Files.exists(Paths.get(outputPath))) {
            try {
                //System.err.println("outputPath="+outputPath);
                Files.createFile(Paths.get(outputPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            //System.err.println("outputPath="+outputPath + ",output="+output);
            Files.write(Paths.get(outputPath), output.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
    private static void printStartStopTimes() {
        final long start = System.currentTimeMillis();
        //Thread hook = new Thread() {
        //    @Override
        //    public void run() {
                //try{
                System.err.println("1st Stop at .............................,methodAndLine = " + RandomClassTracer.methodAndLine );
                String methAndLine=RandomClassTracer.methodAndLine;
                System.err.println("Stop at .............................,methodAndLine = " + methAndLine );
                String curDir = new File("").getAbsolutePath();
                System.err.println("************"+curDir);
                writeTo(curDir + "MethodAndLine.txt", methAndLine + "\n");
                //} catch (Exception e) {
               //e.printStackTrace();
                //}
            }
       // };
        //Runtime.getRuntime().addShutdownHook(hook);
   // }*/
}