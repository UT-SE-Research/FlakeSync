package edu.utexas.ece.flakesync.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FunctionNameTracer extends ClassVisitor {

    public static Set<String> locations = new HashSet<>();
    public static List<String> methodRangeList = new ArrayList<String>();

    public static Set<String> providedLocations = new HashSet<>();
    public static Set<String> locationList = new HashSet<>(); // Only used from analyzeRootMethod.sh
    public static String testName;

    private String className;
    private int flag = 0;

    static {
        if (System.getProperty("searchForMethodName") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(
                        new File(System.getProperty("searchForMethodName"))));
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

        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    locationList.add(line);
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public FunctionNameTracer(ClassVisitor cv) {
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
        final String methName = name;

        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
            int lineNumber;
            String classLine = "";

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn + "#" + line;

                if (System.getProperty("searchForMethodName") != null &&  providedLocations.contains(classLine)) {
                    System.out.println("CONTAINING-METHOD-NAME =" + methName); // DO NOT REMOVE THIS LINE, NEEDED FOR SCRIPT
                }
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitCode() { // To inject delay at the beginning of a method
                if (System.getProperty("methodNameForDelayAtBeginning") != null ) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/flakesync/agent/Utility", "delay", "()V", false);
                }
                super.visitCode();
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (System.getProperty("findRootOfRunMethod") != null ) {
                    if (owner.contains("Thread") && name.equals("start")) {
                        super.visitInsn(Opcodes.DUP);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "edu/utexas/ece/flakesync/agent/Utility", "stack", "(Ljava/lang/Thread;)V", false);
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitEnd() {
                if (locationList.contains(classLine) && (methName.equals(System.getProperty("methodNameForDelayAtEnd")))) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/flakesync/agent/Utility", "delay", "()V", false);
                }
                super.visitEnd();
            }

        };
    }
}
