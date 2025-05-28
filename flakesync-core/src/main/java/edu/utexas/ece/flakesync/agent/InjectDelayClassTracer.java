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

public class InjectDelayClassTracer extends ClassVisitor {

    public static Set<String> locations = new HashSet<>();

    private static List<String> whiteList = new ArrayList<>();

    private String className;

    public InjectDelayClassTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    // White list consists of specific class names, same format as outputted by the EnterExitClassTracer logic
    public static boolean whiteListContains(String name) {

        if (whiteList.isEmpty()) {
            whiteList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(
                        new FileReader(new File(System.getProperty("concurrentmethods"))));
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
        return whiteList.contains(name);
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

                if (whiteListContains(containingMethod)) {
                    // Insert some random delay call right before invoking the method
                    locations.add(location);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/flakesync/agent/Utility", "delay", "()V", false);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        };
    }
}
