package barriersearch.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StackTraceTracer extends ClassVisitor {

    static String codeUnderTestClass = "";
    static String codeUnderTestClassName = "";
    static String codeToIntroduceVariable = "";
    static int codeUnderTestLineNumber = 0;

    boolean entered = false;

    // For Achilles project only
    boolean withinLambda = false;
    boolean delayed = false;

    private String className;
    private boolean visitedField;

    public StackTraceTracer(ClassVisitor cv, String codeToIntroduceVariable) {
        super(Opcodes.ASM9, cv);
        this.codeToIntroduceVariable = codeToIntroduceVariable;
        codeUnderTestClassName = codeToIntroduceVariable.split("#")[0];
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
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
            int lineNumber;

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;
                String location = cn_dot + "#" + lineNumber;
                if ((System.getProperty("stackTraceCollect") != null)
                        && cn_dot.equals(codeUnderTestClassName) && lineNumber == codeUnderTestLineNumber) {
                    delayed = true;
                    System.out.println("visitMethodInsn.....,Need to inject delay,location" + location
                        + "delay=" + System.getProperty("delay"));
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/barrierSearch/agent/Utility", "delay", "()V", false);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) { // The lines which are non-method-call
                String location = cn_dot + "#" + lineNumber;
                if ((System.getProperty("stackTraceCollect") != null)
                        && cn_dot.equals(codeUnderTestClassName) && lineNumber == codeUnderTestLineNumber) {
                    delayed = true;
                    System.out.println("visitInsn............;cn=" + cn + ",location=" + location
                        + ",delay=" + System.getProperty("delay"));
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/barrierSearch/agent/Utility", "delay", "()V", false);
                }
                super.visitInsn(opcode);
            }
        };
    }
}
