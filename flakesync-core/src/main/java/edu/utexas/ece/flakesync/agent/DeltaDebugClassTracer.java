/*
The MIT License (MIT)
Copyright (c) 2025 August Shi
Copyright (c) 2025 Nandita Jayanthi
Copyright (c) 2025 Shanto Rahman



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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeltaDebugClassTracer extends ClassVisitor {

    public static Set<String> locations = new HashSet<>();

    public static Set<String> providedLocations = new HashSet<>();

    private static List<String> whiteList = new ArrayList<>();

    private String className;

    static {
        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                while (line != null) {
                    System.out.println(line);
                    providedLocations.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        if (whiteList.isEmpty()) {
            whiteList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(
                        new FileReader(new File(System.getProperty("concurrentmethods"))));
                String line = reader.readLine();
                while (line != null) {
                    whiteList.add(line);
                    // read next line
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public DeltaDebugClassTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    // White list consists of specific class names, same format as outputted by the EnterExitClassTracer logic
    public static boolean whiteListContains(String name) {
        return whiteList.contains(name);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn = this.className;
        final String containingMethod = cn + "." + name + desc;
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
            int lineNumber;

            @Override
            public void visitLineNumber(int line, Label start) {
                lineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                String methodName = owner + "." + name + desc;

                String location = cn + "#" + lineNumber;

                // If locations are provided, delay only at those locations
                if (System.getProperty("locations") != null) {
                    if (providedLocations.contains(location)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "edu/utexas/ece/flakesync/agent/Utility", "delay", "()V", false);
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        };
    }
}
