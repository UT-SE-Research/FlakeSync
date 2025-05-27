package edu.utexas.ece.flakesync.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ConcurrentMethodsClassTracer extends ClassVisitor {

    private String className;

    public ConcurrentMethodsClassTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        // Ignore if constructor or class initializer
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }

        final String methodName = className + "." + name + desc;

        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {

            final Label start = new Label();
            final Label end = new Label();

            @Override
            public void visitCode() {
                super.visitCode();

                // At beginning of method, insert call to help record start of method
                super.visitLdcInsn(methodName);
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "edu/utexas/ece/flakesync/agent/Utility", "recordMethodEntry", "(Ljava/lang/String;)V", false);
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN
                        || opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.RETURN) {
                    // At trying to return from method (ignore exception for now), insert call to help record end of method
                    super.visitLdcInsn(methodName);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/flakesync/agent/Utility", "recordMethodExit", "(Ljava/lang/String;)V", false);
                }
                super.visitInsn(opcode);
            }
        };
    }
}
