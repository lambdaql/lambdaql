package org.lambdaql.engine.load;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaAnalysisException;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the bytecode of one method from a class file.
 * <p>
 * Only the wanted method becomes a {@link MethodNode}; other methods are skipped (no full class tree,
 * see ROADMAP 2.6.1). Line numbers are kept, so error messages can show the source line.
 * <p>
 * Two readers give the same {@link MethodNode} (ROADMAP D52). The jar is a Multi-Release JAR:
 * <ul>
 *   <li>Java 21 to 23: ASM reads the class file. ASM must know the class file version, so a class compiled by a
 *       newer JDK needs a newer ASM.</li>
 *   <li>Java 24 and later: {@code java.lang.classfile} of the running JDK reads the class file (from
 *       {@code META-INF/versions/24} of the jar). It always reads the class files of that JDK.</li>
 * </ul>
 * The decoder reads the {@link MethodNode} the same way in both cases.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
 * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="load-body"}
 */
public final class BytecodeLoader {

    private BytecodeLoader() {
    }

    /**
     * Reads one method.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="load-missing"}
     *
     * @param loader            class loader that can see the class file
     * @param internalClassName internal class name, for example {@code com/acme/OrderRepo}
     * @param methodName        method name, for example {@code lambda$find$0}
     * @param descriptor        JVM method descriptor, for example {@code (Lcom/acme/Order;)Z}
     * @return the method with its instructions
     * @throws LambdaAnalysisException if the class file or the method is not found, or cannot be read
     */
    public static MethodNode load(ClassLoader loader, String internalClassName, String methodName, String descriptor) {
        String resource = internalClassName + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw LqlMessage.CLASS_FILE_NOT_FOUND.exception(resource);
            }
            MethodNode found;
            try {
                found = MethodReader.read(in.readAllBytes(), methodName, descriptor);
            } catch (IllegalArgumentException e) {
                // a broken class file, or (ASM only) a class file version that this ASM does not know
                throw LqlMessage.CLASS_FILE_UNREADABLE.exceptionCausedBy(null, e, resource);
            }
            if (found == null) {
                throw LqlMessage.METHOD_NOT_FOUND.exception(internalClassName.replace('/', '.') + "." + methodName + descriptor);
            }
            return found;
        } catch (IOException e) {
            throw LqlMessage.CLASS_FILE_UNREADABLE.exceptionCausedBy(null, e, resource);
        }
    }

    /**
     * Reads the body method of a lambda.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="load-body"}
     *
     * @param ref the lambda
     * @return the body method
     * @throws LambdaAnalysisException if the body cannot be read
     */
    public static MethodNode load(LambdaRef ref) {
        return load(ref.classLoader(), ref.implClass(), ref.implMethodName(), ref.implMethodDescriptor());
    }

    /**
     * Tells which reader reads the class files in this JVM.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/load/LoadExamplesTest.java">LoadExamplesTest</a>):
     * {@snippet class="org.lambdaql.engine.load.LoadExamplesTest" region="reader-name"}
     *
     * @return {@code "asm"} on Java 21 to 23, {@code "classfile"} ({@code java.lang.classfile}) on Java 24 and later
     *         when lambdaQL runs from its jar
     */
    public static String readerName() {
        return MethodReader.name();
    }
}
