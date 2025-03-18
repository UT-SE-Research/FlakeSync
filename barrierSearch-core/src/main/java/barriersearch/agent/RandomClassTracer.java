package barriersearch.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;

public class RandomClassTracer extends ClassVisitor {

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
    static int failure_reproducing_point = 0;

    static int codeUnderTestLineNumber = 0;

    boolean withinLambda;

    private String className;
    private boolean visitedField;

    // For Achilles project only
    private boolean isMatchWithSpecialTests;
    private String[] specialTestArr =
        {"info.archinnov.achilles.test.integration.tests.ClusteredEntityIT#should_persist_with_ttl",
        "info.archinnov.achilles.test.integration.tests.ClusteredEntityIT#should_update_with_ttl"};

    public RandomClassTracer(ClassVisitor cv, String testClassInfo, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.testClassInfo = testClassInfo;
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        // We need to split this because this is a boundary. We will call update after any line executes after
        codeUnderTestClassName = codeToIntroduceVariable.split("#")[0];
        failure_reproducing_point = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
        codeUnderTestLineNumber = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);

        String testName = System.getProperty("test");
        isMatchWithSpecialTests = Arrays.asList(specialTestArr).contains(testName);
        if (System.getProperty("OverheadCalculate") != null) { // This is only needed for RQ4 of paper writing
            delayed = true;
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
        final String cn_dot = cn.replace("/",".");
        final String containingMethod = cn + "." + name + desc;
        final String methName = name;
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
            int lineNumber;
            int flag = 0;
            String  classLine;
            int methodStartLineNum;

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn_dot + "#" + line;
                if (flag == 0) {
                    methodStartLineNum = lineNumber;
                    flag = 1;
                }
                if (System.getProperty("searchForMethodName") != null &&  testClassInfo.equals(classLine)) {
                    methodAndLine = methName + "#" + methodStartLineNum;
                }

                 // Because one line may contain lots of bytecode instructions,
                 // we will only inject delay when all the instruction of that line will be finished;
                 // update must have happened after delay
                if (isMatchWithSpecialTests) {
                    if (System.getProperty("searchForMethodName") == null
                            && lineNumber > (failure_reproducing_point + 1) && !updateFlag && delayed) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                        updateFlag = true;
                    }
                } else if (System.getProperty("searchForMethodName") == null
                        && lineNumber > failure_reproducing_point && !updateFlag && delayed) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                    updateFlag = true;
                }
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn_dot + "#" + lineNumber;
                if (System.getProperty("searchForMethodName") == null) {
                    //These 2 branches should be mutually exclusive

                    // For code under test, first we need to add delay
                    if (cn_dot.equals(codeUnderTestClassName)
                            && lineNumber == failure_reproducing_point && System.getProperty("OverheadCalculate") == null) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/barrierSearch/agent/Utility", "delay", "()V", false);
                        delayed = true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    } else if (location.equals(testClassInfo) && !yieldEntered) { // for testClassInfo full param
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/barrierSearch/agent/Utility", "yield", "()V", false);
                        yieldEntered = true;
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    } else {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }

            @Override
            public void visitInsn(int opcode) { // The lines which are non-methodcall; not sure yet, may add this later
                String location = cn_dot + "#" + lineNumber;
                if (System.getProperty("searchForMethodName") == null) {
                    // For code under test, first we need to add delay
                    if (cn_dot.equals(codeUnderTestClassName)
                            && lineNumber == failure_reproducing_point && System.getProperty("OverheadCalculate") == null) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/barrierSearch/agent/Utility", "delay", "()V", false);
                        delayed = true;
                        super.visitInsn(opcode);
                    } else if (location.equals(testClassInfo) && !yieldEntered) { // for testClassInfo full param
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/barrierSearch/agent/Utility", "yield", "()V", false);
                        yieldEntered = true;
                        super.visitInsn(opcode);
                    } else {
                        super.visitInsn(opcode);
                    }
                } else {
                    super.visitInsn(opcode);
                }
            }

            @Override
            public void visitEnd() { // This one is mainly needed for update
                if (System.getProperty("searchForMethodName") == null && cn_dot.equals(codeUnderTestClassName)
                        && lineNumber > failure_reproducing_point && !updateFlag && delayed) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/barrierSearch/agent/Utility", "update", "()V", false); // Updating after the line
                    updateFlag = true;
                }
                super.visitEnd();
            }
        };
    }
}
