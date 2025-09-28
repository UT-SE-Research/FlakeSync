package edu.utexas.ece.barriersearch.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;

public class DelayAndYieldInjector extends ClassVisitor {
    public static boolean updateFlag = false;
    public static boolean visitMethodInsn = false;
    public static boolean yieldEntered = false;
    public static boolean delayed = false;
    public static String methodAndLine;

    static String codeUnderTestClass = "";
    static String codeUnderTestClassName = "";
    static String codeToIntroduceVariable = "";
    static String testClassInfo = "";
    static int testLine = 0;
    static int delayCount = 0;
    static int failureReproducingPoint = 0;

    private String className;
    private boolean visitedField;

    private boolean isMatchWithSpecialTests;
    private String[] specialTestArr =
        {"info.archinnov.achilles.test.integration.tests.ClusteredEntityIT#should_persist_with_ttl",
        "info.archinnov.achilles.test.integration.tests.ClusteredEntityIT#should_update_with_ttl"};

    public DelayAndYieldInjector(ClassVisitor cv, String testClassInfo, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.testClassInfo = testClassInfo;
        this.codeToIntroduceVariable = codeToIntroduceVariable;

        // Parse out the name of the class where we inject delay
        codeUnderTestClassName = codeToIntroduceVariable.split("#")[0];
        // Get the line number where to inject delay
        failureReproducingPoint = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);

        String testName = System.getProperty(".test");
        isMatchWithSpecialTests = Arrays.asList(specialTestArr).contains(testName);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn_dot = this.className.replace("/",".");
        final String containingMethod = this.className + "." + name + desc;
        final String methName = name;

        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
            int lineNumber;
            int flag = 0;
            String classLine;
            int methodStartLineNum;

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn_dot + "#" + line;
                if (flag == 0) {
                    methodStartLineNum = lineNumber;
                    flag = 1;
                }

                System.out.println("visitLineNumber,methodAndLine=" + methodAndLine);
                if (System.getProperty("searchForMethodName") != null && testClassInfo.equals(classLine)) {
                    methodAndLine = methName + "#" + methodStartLineNum;
                    System.out.println("visitLineNumber,methodAndLine=" + methodAndLine);
                }

                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn_dot + "#" + lineNumber;
                System.out.println("100. location=" + location + " " + yieldEntered);

                if (System.getProperty("searchForMethodName") == null
                        || !System.getProperty("searchForMethodName").equals("search")) {
                    // These 2 branches should be mutually exclusive
                    System.out.println("****visitMethodInsn,location=" + location + "testCLassInfo=" + testClassInfo);

                    // For code under test, first we need to add delay
                    if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failureReproducingPoint) {
                        System.out.println("visitMethodInsn,Delaying,cn_dot=" + cn_dot + ", failure_reproducing_point="
                            + failureReproducingPoint + ",delayed=" + delayed);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility",
                            "delay", "()V", false);
                        delayed = true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    } else if (cn_dot.equals(codeUnderTestClassName) && lineNumber > failureReproducingPoint
                            && !updateFlag) {
                        // Updating after the line
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility",
                            "update", "()V", false);
                        updateFlag = true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    // For test code, need to insert at yield point
                    } else if (location.equals(testClassInfo) && !yieldEntered) {
                        System.out.println("Yielding FROM RANDOMTRACER........=,location=" + location
                            + ",testClassInfo=" + testClassInfo);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility",
                            "yield", "()V", false);
                        yieldEntered = true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    } else {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }
        };
    }
}
