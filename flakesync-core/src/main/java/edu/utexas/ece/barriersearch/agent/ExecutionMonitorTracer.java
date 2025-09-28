package edu.utexas.ece.barriersearch.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class ExecutionMonitorTracer extends ClassVisitor {
    static String codeUnderTestClass = "";
    static String codeUnderTestClassName = "";
    static String codeToIntroduceVariable = "";
    static int failureReproducingPoint = 0;
    static int codeUnderTestLineNumber = 0;

    private String className;

    public ExecutionMonitorTracer(ClassVisitor cv, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        codeUnderTestClassName = codeToIntroduceVariable.split("#")[0];
        failureReproducingPoint = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
        codeUnderTestLineNumber = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
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
            String  classLine;
            int methodStartLineNum;
            boolean lineCounted = false;

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn_dot + "#" + line;
                lineCounted = false;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn_dot + "#" + lineNumber;
                System.out.println("OUTSIDE: " + cn_dot + codeUnderTestClassName + " " + lineNumber + " "
                    + failureReproducingPoint + !lineCounted);
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failureReproducingPoint && !lineCounted ) {
                    System.out.println("visitMethodInsn, counter");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/flakesync/agent/Utility",
                        "counter", "()V", false);
                    lineCounted = true;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) {
                String location = cn_dot + "#" + lineNumber;
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failureReproducingPoint && !lineCounted) {
                    System.out.println("visitInsn, counter");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/flakesync/agent/Utility",
                        "counter", "()V", false);
                    lineCounted = true;
                }
                super.visitInsn(opcode);
            }
        };
    }
}
