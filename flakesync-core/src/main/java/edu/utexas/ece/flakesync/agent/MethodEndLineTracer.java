package edu.utexas.ece.flakesync.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodEndLineTracer extends ClassVisitor {

    public static String methodEndLine;

    static String codeUnderTestClass = "";
    static String codeUnderTestClassName = "";
    static String codeToIntroduceVariable = "";
    static int failure_reproducing_point = 0;
    static int codeUnderTestLineNumber = 0;

    int lineNumber;
    private String className;
    private String methName = "NA";

    public MethodEndLineTracer(ClassVisitor cv, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        codeUnderTestClassName = codeToIntroduceVariable.split("#")[0]; // We need to split this because this is a boundary
        failure_reproducing_point = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn = this.className;
        final String cn_dot = cn.replace("/", ".");
        final String containingMethod = cn + "." + name + desc;
        final String methodName = name;

        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point) {
                    methName = methodName;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) { // The lines which are non-methodcall; not sure yet, may add this later
                // For code under test, First we need to add delay
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point) {
                    methName = methodName;
                }
                super.visitInsn(opcode);
            }

            @Override
            public void visitEnd() {
                if (cn_dot.equals(codeUnderTestClassName) && !methName.equals("NA") && methName.equals(methodName) ) {
                    methodEndLine = cn_dot + "." + methName + "#" + lineNumber;
                }
                super.visitEnd();
            }
        };
    }
}
