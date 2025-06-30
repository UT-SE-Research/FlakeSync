package edu.utexas.ece.barrierSearch.agent;

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


    private String className;
    private String methName = "NA";

    public MethodEndLineTracer(ClassVisitor cv, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        //System.out.println("Inside MethodLineTracer(): " + codeToIntroduceVariable);
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        codeUnderTestClassName = codeToIntroduceVariable.split("#")[0];
        failure_reproducing_point = Integer.parseInt(codeToIntroduceVariable.split("#")[1]);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        //System.out.println("Inside visit(): " + name);
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
            int lineNumber;
            private String methName = "NA";

            @Override
            public void visitLineNumber(int line, Label start) {
                //System.out.println("Inside visitLineNumber(): " + cn_dot + line);
                lineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                //System.out.println("Inside visitMethodInsn(): " + cn_dot);
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point) {
                    methName = methodName;
                    System.out.println("FOUND THE LOCATION Inside visitMethodInsn(): " + methodName);
                    methodEndLine = cn_dot + "." + methName + "#" + lineNumber;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) { // The lines which are non-methodcall; not sure yet, may add this later
                // For code under test, First we need to add delay
                //System.out.println("Inside visitInsn():" + cn_dot);
                if (cn_dot.equals(codeUnderTestClassName) && lineNumber == failure_reproducing_point) {
                    System.out.println("FOUND THE LOCATION Inside visitMethodInsn(): " + methodName);
                    methName = methodName;
                    methodEndLine = cn_dot + "." + methName + "#" + lineNumber;
                }
                super.visitInsn(opcode);
            }

            @Override
            public void visitEnd() {
                System.out.println("Inside visitEnd(): " + methName);
                if (cn_dot.equals(codeUnderTestClassName) && !methName.equals("NA") && methName.equals(methodName) ) {
                    methodEndLine = cn_dot + "." + methName + "#" + lineNumber;
                }
                super.visitEnd();
            }
        };
    }
}
