package edu.utexas.ece.barrierSearch.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
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
                String codeUnderTest=codeToIntroduceVariable.split("#")[0]; // code-undet-test class
                final ClassReader reader = new ClassReader(bytes);
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS );
                ClassVisitor visitor;

                if (System.getProperty("stackTraceCollect") != null && System.getProperty("stackTraceCollect").equals("true") && !blackListContains(s)) { // Going to add delay and collect the stacktrace
                    System.out.println("FROM STACKTRACE ***************************"+codeToIntroduceVariable);
                    visitor = new StackTraceTracer(writer, codeToIntroduceVariable);
                    reader.accept(visitor, 0);
                    return writer.toByteArray();
                }
                else if (System.getProperty("executionMonitor") != null && System.getProperty("executionMonitor").equals("flag") && !blackListContains(s)) {
                    synchronized (edu.utexas.ece.barrierSearch.agent.Utility.class) {
                        System.out.println("Starting execution monitor steps");
                        visitor = new ExecutionMonitorTracer(writer, codeToIntroduceVariable);
                        reader.accept(visitor, 0);
                    }
                    return writer.toByteArray();
                }
                else if (System.getProperty("searchMethodEndLine") != null && System.getProperty("searchMethodEndLine").equals("search") && !blackListContains(s)) {
                    //synchronized (edu.utexas.ece.barrierSearch.agent.Utility.class) {
                        System.out.println("Starting search method steps");
                        visitor = new MethodEndLineTracer(writer, codeToIntroduceVariable);
                        reader.accept(visitor, 0);
                    //}
                    return writer.toByteArray();
                }
                else if (!blackListContains(s)){
                    String yieldPointInfo = System.getProperty("YieldingPoint"); // YIELDING_POINT may or may not be a test_method's location
                    String tcls=yieldPointInfo.split("#")[0]; // test-class
                    //RandomClassTracer.yieldEntered = false;
                    //RandomClassTracer.delayed = false;
                    //RandomClassTracer.updateFlag = false;
                    if ((s.equals(codeUnderTest) || s.equals(tcls))) {  // Need substring match, test-class name is not coming here
                        if(s.equals(tcls)) System.out.println("ELSE****ALLOWED CLASS="+s +",yieldPointInfo="+tcls+",codeUnderTest="+codeUnderTest);
                        visitor = new RandomClassTracer(writer, yieldPointInfo, codeToIntroduceVariable);
                        reader.accept(visitor, 0);

                        if (RandomClassTracer.methodAndLine != null) {
                            try {
                                java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter("./.flakesync/SearchedMethodANDLine.txt"));
                                bf.write(RandomClassTracer.methodAndLine +"\n");
                                bf.flush();
                            } catch (Exception ex) {}

                        }

                        try{
                            java.io.BufferedWriter bfFlag = new java.io.BufferedWriter(new java.io.FileWriter("./.flakesync/FlagDelayANDUpdateANDYielding.txt"));
                            bfFlag.write("Delay="+ RandomClassTracer.delayed +"\n");
                            bfFlag.write("Update="+ RandomClassTracer.updateFlag +"\n");
                            bfFlag.write("Yield="+ RandomClassTracer.yieldEntered +"\n");
                            bfFlag.flush();

                        } catch (Exception ex) {}
                        //System.out.println("FROM AGENT="+RandomClassTracer.methodAndLine);
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

                //System.out.println("FROM AGENT and run="+RandomClassTracer.methodAndLine);
                //if (RandomClassTracer.methodAndLine != null && RandomClassTracer.methodAndLine != "") {
                // }
                if (MethodEndLineTracer.methodEndLine != null) {
                    try {
                        java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter("./.flakesync/SearchedMethodEndLine.txt"));
                        bf.write("methodEndLine="+MethodEndLineTracer.methodEndLine +"\n");
                        bf.flush();

                    } catch (Exception ex) {}
                }


                if (Utility.executionCount > 0) {
                    try {
                        java.io.BufferedWriter bf = new java.io.BufferedWriter(new java.io.FileWriter("./.flakesync/ExecutionMonitor.txt"));
                        bf.write("#execution="+Utility.executionCount +"\n");
                        bf.flush();

                    } catch (Exception ex) {}
                }
            }
        };

        Runtime.getRuntime().addShutdownHook(hook);
    }
}