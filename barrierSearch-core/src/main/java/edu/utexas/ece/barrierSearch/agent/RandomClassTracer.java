package edu.utexas.ece.barrierSearch.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;

public class RandomClassTracer extends ClassVisitor {
    private String className;
    private boolean visitedField;
    static String codeUnderTestClass="";
    static String codeUnderTestClassName="";
    static String codeToIntroduceVariable="";
    static String testClassInfo="";
    static int testLine=0;
    static int delayCount=0;
    static int failure_reproducing_point=0;

    static int codeUnderTestLineNumber=0;
    public static boolean updateFlag = false;
    public static boolean visitMethodInsn = false;
    public static boolean yieldEntered = false;
    public static boolean delayed=false;
    private boolean isMatchWithSpecialTests;
    private String[] specialTestArr = {"info.archinnov.achilles.test.integration.tests.ClusteredEntityIT#should_persist_with_ttl", "info.archinnov.achilles.test.integration.tests.ClusteredEntityIT#should_update_with_ttl"};
    // For Achilles project only
    boolean withinLambda = false;
    public static String methodAndLine;


    public RandomClassTracer(ClassVisitor cv, String testClassInfo, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.testClassInfo= testClassInfo;
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        codeUnderTestClassName=codeToIntroduceVariable.split("#")[0]; // We need to split this because this is a boundary. We will call update after anyline executes afte
        failure_reproducing_point=Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
        codeUnderTestLineNumber=Integer.parseInt(codeToIntroduceVariable.split("#")[1]);

        String testName = System.getProperty("test");
        isMatchWithSpecialTests = Arrays.asList(specialTestArr).contains(testName);
        if (System.getProperty("OverheadCalculate") != null) { // This is only needed for RQ4 of paper writing
            delayed=true;
        }
    }
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        //System.out.println("VISIT*********");
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn = this.className;
        final String cn_dot=cn.replace("/",".");
        final String containingMethod = cn + "." + name + desc;
        final String methName=name;
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
            int lineNumber;
            int flag=0;
            String  classLine;
            int methodStartLineNum;
            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn_dot + "#" + line;
                if (flag == 0) {
                    methodStartLineNum=lineNumber;
                    flag=1;
                }
                System.out.println("visitLineNumber,methodAndLine="+methodAndLine);
                if (System.getProperty("searchForMethodName") != null &&  testClassInfo.equals(classLine)) {
                    methodAndLine=methName+"#"+methodStartLineNum;
                    System.out.println("visitLineNumber,methodAndLine="+methodAndLine);
                }

                //System.out.println("Outside visitLineNumber,lineNumber"+lineNumber+",cn_dot=" + cn_dot);

                if ( isMatchWithSpecialTests ) { // Because oneline may contain lots of byte-code ins, we will only inject delay when all the instruction of that line will be finished; update must be happened after delay
                    if (System.getProperty("searchForMethodName") == null && lineNumber > (failure_reproducing_point + 1) &&  !updateFlag && delayed) {
                        System.out.println("I am special match *****");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                        System.out.println("visitLineNumber Updating =,lineNumber"+lineNumber+",cn_dot=" + cn_dot);
                        updateFlag=true;
                    }
                }
                else if (System.getProperty("searchForMethodName") == null && lineNumber > failure_reproducing_point &&  !updateFlag && delayed) { // Because oneline may contain lots of byte-code ins, we will only inject delay when all the instruction of that line will be finished; update must be happened after delay
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                    System.out.println("visitLineNumber Updating =,lineNumber"+lineNumber+",cn_dot=" + cn_dot);
                    updateFlag=true;
                }
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn_dot + "#" + lineNumber;
                System.out.println("100. location="+location + " " + yieldEntered);
                if (System.getProperty("searchForMethodName") == null || !System.getProperty("searchForMethodName").equals("search")) {
                    //These 2 brances should be mutually exclusive
                    System.out.println("****visitMethodInsn,location="+location + "testCLassInfo=" + testClassInfo);

                    if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point && System.getProperty("OverheadCalculate") == null) { // for code under test, First we need to add delay;
                        System.out.println("visitMethodInsn,Delaying,cn_dot="+cn_dot + ", failure_reproducing_point="+failure_reproducing_point + ",delayed="+delayed);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "delay", "()V", false);
                        delayed=true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                        updateFlag=true;
                    }
                    else if (location.equals(testClassInfo) && !yieldEntered) { // for testClassInfo full param
                        System.out.println("Yielding FROM RANDOMTRACER........=,location="+location +",testClassInfo="+testClassInfo);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "yield", "()V", false);
                        yieldEntered=true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                    else {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                }
                else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            @Override
            public void visitInsn(int opcode){ //The lines which are non-methodcall, Not sure yet, may add this later
                String location = cn_dot + "#" + lineNumber;
                if (System.getProperty("searchForMethodName") == null || !System.getProperty("searchForMethodName").equals("search")) {

                    System.out.println("visitLineNumber,***," + testClassInfo + ",location="+location + ", yieldEntered="+yieldEntered);
                    if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point && System.getProperty("OverheadCalculate") == null) { // for code under test, First we need to add delay
                        System.out.println("visitInsn,Delaying,cn_dot="+cn_dot + ", failure_reproducing_point="+failure_reproducing_point + ",delayed="+delayed);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "delay", "()V", false);
                        delayed=true;
                        super.visitInsn(opcode);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                        updateFlag=true;
                    }
                    else if (location.equals(testClassInfo) && !yieldEntered) { // for testClassInfo full param
                        System.out.println("visitInsn Yielding......., opcode="+opcode);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "yield", "()V", false);
                        yieldEntered=true;
                        super.visitInsn(opcode);
                        System.out.println("visitInsn, after Yielding......., lineNumber=" + lineNumber + ", testLine="+testLine);
                    }
                    else {
                        System.out.println("I am else");
                        super.visitInsn(opcode);
                    }
                }
                else {
                    super.visitInsn(opcode);
                }
            }
            /*@Override
            public void visitEnd() { // This one is mainly needed for update
                if ((System.getProperty("searchForMethodName") == null || !System.getProperty("searchForMethodName").equals("search")) && cn_dot.equals(codeUnderTestClassName) && lineNumber > failure_reproducing_point && !updateFlag && delayed) {
                    System.out.println("Trying to update the test code.........");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                    updateFlag=true;
                }
                super.visitEnd();
            }*/
        };
    }
}