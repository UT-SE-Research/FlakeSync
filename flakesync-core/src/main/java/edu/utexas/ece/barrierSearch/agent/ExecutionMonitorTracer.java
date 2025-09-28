package edu.utexas.ece.barrierSearch.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class ExecutionMonitorTracer extends ClassVisitor {
    private String className;
    static String codeUnderTestClass="";
    static String codeUnderTestClassName="";
    static String codeToIntroduceVariable="";
    static int failure_reproducing_point=0;
    static int codeUnderTestLineNumber=0;

    public ExecutionMonitorTracer(ClassVisitor cv, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        codeUnderTestClassName=codeToIntroduceVariable.split("#")[0]; // We need to split this because this is a boundary
        failure_reproducing_point=Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
        codeUnderTestLineNumber=Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
    }
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
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
            String  classLine;
            int methodStartLineNum;
            boolean lineCounted=false;
            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                classLine = cn_dot + "#" + line;
                lineCounted=false;
                super.visitLineNumber(line, start);
            }
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn_dot + "#" + lineNumber;
                System.out.println("OUTSIDE: " + cn_dot + codeUnderTestClassName + " " + lineNumber + " " + failure_reproducing_point + !lineCounted);
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point && !lineCounted ) {
                    System.out.println("visitMethodInsn, counter");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "counter", "()V", false); // Updating after the line
                    lineCounted = true;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode){ //The lines which are non-methodcall, Not sure yet, may add this later
                String location = cn_dot + "#" + lineNumber;
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point && !lineCounted) { // for code under test, First we need to add delay
                    System.out.println("visitInsn, counter");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/barrierSearch/agent/Utility", "counter", "()V", false); // Updating after the line
                    lineCounted = true;
                }
                super.visitInsn(opcode);
            }

        };
    }
}