package org.lambdaql.engine.load;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads one method of a class file with ASM (Java 21 to 23; ROADMAP D52).
 * <p>
 * The jar is a Multi-Release JAR. On Java 24 and later the JVM loads another class with the same name from
 * {@code META-INF/versions/24}, which reads the class file with {@code java.lang.classfile} and gives the same
 * {@link MethodNode}. Both classes must keep the same members ({@link #name()} and {@link #read}).
 * <p>
 * Frames are skipped ({@code SKIP_FRAMES}); labels and line numbers are kept.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
 * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="reader-name"}
 */
final class MethodReader {

    /**
     * The name of this reader, given by {@link BytecodeLoader#readerName()}.
     * A method, not a constant: javac would copy a constant into the caller, and the caller would not see the
     * other class of the Multi-Release JAR.
     *
     * @return {@code "asm"}
     */
    static String name() {
        return "asm";
    }

    private MethodReader() {
    }

    /**
     * Reads one method from the bytes of a class file.
     *
     * @param classFile  the bytes of the class file
     * @param methodName method name, for example {@code lambda$find$0}
     * @param descriptor JVM method descriptor, for example {@code (Lcom/acme/Order;)Z}
     * @return the method with its instructions, or {@code null} if the class has no such method
     * @throws IllegalArgumentException if the class file is broken or its version is newer than this ASM knows
     */
    static MethodNode read(byte[] classFile, String methodName, String descriptor) {
        ClassReader reader = new ClassReader(classFile);
        MethodNode[] found = new MethodNode[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (found[0] == null && name.equals(methodName) && desc.equals(descriptor)) {
                    found[0] = new MethodNode(Opcodes.ASM9, access, name, desc, signature, exceptions);
                    return found[0];
                }
                return null;
            }
        }, ClassReader.SKIP_FRAMES);
        return found[0];
    }
}
