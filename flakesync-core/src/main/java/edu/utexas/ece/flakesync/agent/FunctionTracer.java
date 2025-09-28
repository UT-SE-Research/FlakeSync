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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FunctionTracer extends ClassVisitor {

    public static Set<String> locations = new HashSet<>();
    public static List<String> methodRangeList = new ArrayList<String>();

    public static Set<String> providedLocations = new HashSet<>();
    public static String testName;

    private String className;
    private int flag = 0;

    static {
        if (System.getProperty("rootMethod") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("rootMethod"))));
                String line = reader.readLine();
                while (line != null) {
                    String[] arr = line.split(":", 2); // We are keeping className#lineNumber, only splitting testName
                    providedLocations.add(arr[0]);
                    testName = arr[1];
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public FunctionTracer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final String cn = this.className;

        final String fullMethodName = cn + "#" + name;

        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
            private int startLineNumber = -1;
            private int endLineNumber = -1;

            @Override
            public void visitLineNumber(int line, Label start) {
                if (System.getProperty("rootMethod") != null && providedLocations.contains(fullMethodName)
                        && startLineNumber == -1) {
                    String location = cn + "#" + line;
                    startLineNumber = line;
                }
                endLineNumber = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitEnd() {
                if (System.getProperty("rootMethod") != null &&  providedLocations.contains(fullMethodName)) {
                    String startLocation = cn + "#" + startLineNumber;
                    String endLocation = cn + "#" + endLineNumber;
                    methodRangeList.add(startLocation);
                    methodRangeList.add(endLocation);
                }
                super.visitEnd();
            }
        };
    }
}
