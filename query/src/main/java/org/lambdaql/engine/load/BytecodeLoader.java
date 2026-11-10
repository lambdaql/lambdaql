package org.lambdaql.engine.load;

import org.lambdaql.analyzer.LambdaAnalysisException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the bytecode of one method from a class file.
 * <p>
 * Only the wanted method becomes a {@link MethodNode}; other methods are skipped (no full class tree,
 * see ROADMAP 2.6.1). Line numbers are kept, so error messages can show the source line.
 *
 * <p>Example:
 * <pre>{@code
 * MethodNode body = BytecodeLoader.load(SerializedLambdaExtractor.extract(where));
 * body.instructions.size();
 * }</pre>
 */
public final class BytecodeLoader {

    private BytecodeLoader() {
    }

    /**
     * Reads one method.
     *
     * @param loader            class loader that can see the class file
     * @param internalClassName internal class name, for example {@code com/acme/OrderRepo}
     * @param methodName        method name, for example {@code lambda$find$0}
     * @param descriptor        JVM method descriptor, for example {@code (Lcom/acme/Order;)Z}
     * @return the method with its instructions
     * @throws LambdaAnalysisException if the class file or the method is not found, or cannot be read
     *
     * <p>Example:
     * <pre>{@code
     * MethodNode m = BytecodeLoader.load(getClass().getClassLoader(),
     *         "com/acme/OrderRepo", "lambda$find$0", "(Lcom/acme/Order;)Z");
     * }</pre>
     */
    public static MethodNode load(ClassLoader loader, String internalClassName, String methodName, String descriptor) {
        String resource = internalClassName + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new LambdaAnalysisException("클래스 파일을 찾을 수 없습니다: " + resource);
            }
            ClassReader reader = new ClassReader(in);
            MethodNode[] found = new MethodNode[1];
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (name.equals(methodName) && desc.equals(descriptor)) {
                        found[0] = new MethodNode(Opcodes.ASM9, access, name, desc, signature, exceptions);
                        return found[0];
                    }
                    return null;
                }
            }, ClassReader.SKIP_FRAMES);
            if (found[0] == null) {
                throw new LambdaAnalysisException("메서드를 찾을 수 없습니다: "
                        + internalClassName.replace('/', '.') + "." + methodName + descriptor);
            }
            return found[0];
        } catch (IOException e) {
            throw new LambdaAnalysisException("클래스 파일을 읽을 수 없습니다: " + resource, e);
        }
    }

    /**
     * Reads the body method of a lambda.
     *
     * @param ref the lambda
     * @return the body method
     * @throws LambdaAnalysisException if the body cannot be read
     *
     * <p>Example: {@code BytecodeLoader.load(SerializedLambdaExtractor.extract(where))}
     */
    public static MethodNode load(LambdaRef ref) {
        return load(ref.classLoader(), ref.implClass(), ref.implMethodName(), ref.implMethodDescriptor());
    }
}
