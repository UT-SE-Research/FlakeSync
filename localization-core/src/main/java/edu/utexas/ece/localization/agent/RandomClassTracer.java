package edu.utexas.ece.localization.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class RandomClassTracer extends ClassVisitor {

    public static Set<String> locations = new HashSet<>();

    public static Set<String> providedLocations = new HashSet<>();
    public static String testName = "";

    private String className;

    static {
        if (System.getProperty("locations") != null) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(System.getProperty("locations"))));
                String line = reader.readLine();
                System.out.println(line);
                while (line != null) {
                    if (line.contains(":")) {
                        String[] arr = line.split(":", 2);
                        providedLocations.add(arr[0]);
                        testName = arr[1];
                    } else {
                        // This will only be needed from analyzeRoot.sh,
                        // because in that case only a linenumber with the class name is given
                        providedLocations.add(line);
                    }
                    line = reader.readLine();
                }
                reader.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public RandomClassTracer(ClassVisitor cv) {
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
                if (System.getProperty("locations") != null && providedLocations.contains(location)
                        && !Agent.blackListContains(className) ) {
                    // Do not remove this line, because analyzeRootMethod will use this
                    System.out.println("HI-DELAYING= " + location + ", size=" + providedLocations.size()
                        + ",location=" + location);
                    String methodSignature = "(Ljava/lang/String;Ljava/lang/String;)V"; // sending testName as parameter
                    super.visitLdcInsn(testName);
                    super.visitLdcInsn(location);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "edu/utexas/ece/localization/agent/Utility", "delay", methodSignature, false);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitInsn(int opcode) {
                String location = cn + "#" + lineNumber;
                System.out.println("From RandomClassTraced *** location =" + location);
                // If locations are provided, delay only at those locations
                if (!Agent.blackListContains(className)) { // To double check if the classname is in the blacklist
                    if ((System.getProperty("locations") != null) && (providedLocations.contains(location))) {
                        System.out.println("****Enter-DELAYING " + location + " FOR TESTNAME " + testName );
                        if (testName != "") {
                            // Sending testName as parameter
                            String methodSignature = "(Ljava/lang/String;Ljava/lang/String;)V";
                            super.visitLdcInsn(testName);
                            super.visitLdcInsn(location);
                            // Do not remove this line, because analyzeRootMethod will use this
                            System.out.println("HI-DELAYING= " + location + ", size=" + providedLocations.size()
                                + ",location=" + location);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "edu/utexas/ece/localization/agent/Utility", "delay", methodSignature, false);
                        } else {
                            String methodSignature = "(Ljava/lang/String;)V"; // sending testName as parameter
                            super.visitLdcInsn(location);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "edu/utexas/ece/localization/agent/Utility", "onlyDelay", methodSignature, false);
                        }
                    }
                }
                super.visitInsn(opcode);
            }
        };
    }
}
