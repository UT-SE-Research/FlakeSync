/*
The MIT License (MIT)
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman
Copyright (c) 2025 August Shi



Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package edu.utexas.ece.flakesync.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StackTraceTracer extends ClassVisitor {
    static String codeUnderTestClass = "";
    static String codeUnderTestClassName = "";
    static String codeToIntroduceVariable = "";
    static int codeUnderTestLineNumber = 0;

    private String className;
    private boolean visitedField;
    private boolean delayed = false;

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
                if ((System.getProperty("stackTraceCollect") != null) && cn_dot.equals(codeUnderTestClassName)
                        &&  (lineNumber == codeUnderTestLineNumber)) {
                    delayed = true;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/flakesync/agent/Utility",
                        "delay", "()V", false);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) {
                String location = cn_dot + "#" + lineNumber;
                if ((System.getProperty("stackTraceCollect") != null) && cn_dot.equals(codeUnderTestClassName)
                        && (lineNumber == codeUnderTestLineNumber)) {
                    delayed = true;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "edu/utexas/ece/flakesync/agent/Utility",
                        "delay", "()V", false);
                }
                super.visitInsn(opcode);
            }
        };
    }
}
