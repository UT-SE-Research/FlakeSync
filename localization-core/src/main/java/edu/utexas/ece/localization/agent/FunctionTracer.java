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

public class FunctionTracer extends ClassVisitor {

    private String className;
    private int flag=0;
    public static Set<String> locations = new HashSet<>();
    public static List<String> methodRangeList = new ArrayList<String>();  

    public static Set<String> providedLocations = new HashSet<>();
    public static String testName;
    //public static int givenRootLine;

    static {
        /*if (System.getProperty("givenRootLine") != null) {
            givenRootLine = Integer.parseInt(System.getProperty("givenRootLine"));
            System.out.println("SHANTO givenRootLine= "+givenRootLine);
        }*/

        if (System.getProperty("rootMethod") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("rootMethod"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split(":", 2); // Because We are keeping className#lineNumber, only splitting testName
                    providedLocations.add(arr[0]);
                    testName=arr[1];
                    // read next line
                    System.out.println("HI Line ="+line); // Expecting ClassName#LineNumber
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public FunctionTracer(ClassVisitor cv) {
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
        //System.out.println("cnn= " +cn);

        //final String methodName = cn + "." + name + desc;
        final String fullMethodName = cn + "#" + name;
        //System.out.println("From ASM method name="+name + ",desc="+desc);

        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
            private int startLineNumber = -1;
            private int endLineNumber = -1;

           @Override
           public void visitLineNumber(int line, Label start) {
                if (System.getProperty("rootMethod") != null &&  providedLocations.contains(fullMethodName) && startLineNumber == -1) {
                    //String methodSignature = "(Ljava/lang/String;Ljava/lang/String;)V"; // sending testName as parameter
                    String location = cn + "#" + line;
                    startLineNumber = line;
                    //methodRangeList.add(location);
                }
                endLineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitEnd() {
                if (System.getProperty("rootMethod") != null &&  providedLocations.contains(fullMethodName)) {
                    //String methodSignature = "(Ljava/lang/String;Ljava/lang/String;)V"; // sending testName as parameter
                    System.out.println("Method: " + fullMethodName);
                    System.out.println("Start Line: " + startLineNumber);
                    System.out.println("End Line: " + endLineNumber);
                    String startLocation = cn + "#" + startLineNumber;
                    String endLocation = cn + "#" + endLineNumber;
                    methodRangeList.add(startLocation);
                    methodRangeList.add(endLocation);
                }
                super.visitEnd();
            }
        };
    }
}
