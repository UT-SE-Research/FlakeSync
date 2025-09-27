package edu.utexas.ece.flakesync.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class InjectDelayWithStackTraceTracer extends ClassVisitor {

    public static Set<String> locations = new HashSet<>();

    public static Set<String> providedLocations = new HashSet<>();
    public static String testName = "";

    private String className;

    static {
        testName = System.getProperty(".test");
        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    providedLocations.add(line);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public InjectDelayWithStackTraceTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn = this.className;

        final String containingMethod = cn + "." + name + desc;
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
            int lineNumber;

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn + "#" + lineNumber;
                // If locations are provided, delay only at those locations
                if (System.getProperty("locations") != null && providedLocations.contains(location)
                        && !Agent.blackListContains(className) ) {
                    String methodSignature = "(Ljava/lang/String;Ljava/lang/String;)V"; // sending testName as parameter
                    super.visitLdcInsn(testName);
                    super.visitLdcInsn(location);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/flakesync/agent/Utility", "delay", methodSignature, false);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) {
                String location = cn + "#" + lineNumber;
                // If locations are provided, delay only at those locations
                if (!Agent.blackListContains(className)) { // To double check if the classname is in the blacklist
                    if ((System.getProperty("locations") != null) && (providedLocations.contains(location))) {
                        if (testName != "") {
                            // Sending testName as parameter
                            String methodSignature = "(Ljava/lang/String;Ljava/lang/String;)V";
                            super.visitLdcInsn(testName);
                            super.visitLdcInsn(location);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "edu/utexas/ece/flakesync/agent/Utility", "delay", methodSignature, false);
                        } else {
                            String methodSignature = "()V"; // just delay
                            super.visitLdcInsn(location);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "edu/utexas/ece/flakesync/agent/Utility", "delay", methodSignature, false);
                        }
                    }
                }
                super.visitInsn(opcode);
            }
        };
    }
}
