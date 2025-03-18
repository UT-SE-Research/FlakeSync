package edu.utexas.ece.flakesync.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RandomClassTracer extends ClassVisitor {

    private String className;

    private static List<String> whiteList = new ArrayList<>();

    public static Set<String> locations = new HashSet<>();

    public static Set<String> providedLocations = new HashSet<>();

    static {
        System.out.println("************ " + System.getProperty("locations") + " ******************");
        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    System.out.println(line);
                    providedLocations.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // White list consists of specific class names, same format as outputted by the EnterExitClassTracer logic
    public static boolean whiteListContains(String s) {
        System.out.println("Checking if concurrentmethods exists: " + System.getProperty("concurrentmethods"));
        if (whiteList.isEmpty()) {
            whiteList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("concurrentmethods"))));
                String line = reader.readLine();
                while (line != null) {
                    whiteList.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return whiteList.contains(s);
    }

    public RandomClassTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
        System.out.println("************ " + System.getProperty("locations") + " ******************");
        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    System.out.println(line);
                    providedLocations.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
                System.out.println("in visitMethodInst: " + location);

                // If locations are provided, delay only at those locations
                if (System.getProperty("locations") != null) {
                    System.out.println("A");
                    if (providedLocations.contains(location)) {
                        System.out.println("B");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/flakesync/agent/Utility", "delay", "()V", false);
                    }
                }

                // Insert some random delay call right before invoking the method, with some probability
                else if (whiteListContains(containingMethod)) {
                    System.out.println("C");
                    locations.add(location);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/flakesync/agent/Utility", "delay", "()V", false);
                }
                System.out.println("D");
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        };
    }
}
