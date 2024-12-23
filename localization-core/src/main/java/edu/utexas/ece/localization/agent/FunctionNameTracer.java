package edu.utexas.ece.localization.agent;

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

public class FunctionNameTracer extends ClassVisitor {

    private String className;
    private int flag=0;
    public static Set<String> locations = new HashSet<>();
    public static List<String> methodRangeList = new ArrayList<String>();  

    public static Set<String> providedLocations = new HashSet<>();
    public static Set<String> LocationList = new HashSet<>(); // Only used from analyzeRootMethod.sh
    public static String testName;

    static {
        if (System.getProperty("searchForMethodName") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("searchForMethodName"))));
                String line = reader.readLine();
                while (line != null) {
                    providedLocations.add(line);
                    // read next line
                    //System.out.println("Line ="+line); // Expecting ClassName#LineNumber
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        
        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    System.out.println("HMMM=line "+line);
                    LocationList.add(line);
                    // read next line
                    //System.out.println("Line ="+line); // Expecting ClassName#LineNumber
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public FunctionNameTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        //System.out.println("***CLassName ="+className);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn = this.className;
        final String methName=name;

        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
           int lineNumber;
           String classLine="";
           @Override
           public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn + "#" + line;

                //System.out.println("visitLineNumebr fullMethodName="+fullMethodName);
                if (System.getProperty("searchForMethodName") != null &&  providedLocations.contains(classLine)) {
                    //System.out.println("visitLineNumber ** Function Tracer ="+classLine);
                    System.out.println("CONTAINING-METHOD-NAME ="+methName); // DO NOT REMOVE THIS LINE; THIS is needed from script
                }
                super.visitLineNumber(line, start);
            }
            @Override
            public void visitCode() { // To inject delay at the beginning of a method
                if (System.getProperty("methodNameForDelayAtBeginning") != null ) {
                    //System.out.println("*** I AM visitCode, from FunctionNameTracer ** =");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/localization/agent/Utility", "injectDelay", "()V", false);
                }
                super.visitCode();
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (System.getProperty("findRootOfRunMethod") != null ) {
                    //System.out.println("I AM visitMethodInsn ="+ owner + ", name="+ name );
                    if (owner.contains("Thread") && name.equals("start")) {
                        //System.out.println("FOUND START");
                        super.visitInsn(Opcodes.DUP);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/localization/agent/Utility", "stack", "(Ljava/lang/Thread;)V", false);
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf); 
            }

            @Override
            public void visitEnd() {
                if (LocationList.contains(classLine) && (methName.equals(System.getProperty("methodNameForDelayAtEnd")))) {
                    //System.out.println("*** I AM visitEnd ** =");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/localization/agent/Utility", "injectDelay", "()V", false);
                 }
                super.visitEnd();
            }

        };
    }
}
