package org.lambdaql.engine.load;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two class file readers give the same decoder input (ROADMAP D52).
 * <p>
 * Every method of the lambdaQL classes and of the Java and Kotlin test classes is read twice: by
 * {@link BytecodeLoader} (the reader of this JVM) and by ASM's {@code ClassReader} directly. The instructions are
 * compared one by one (opcode, operands, jump targets, source lines). On Java 21 both sides are ASM; the test that
 * matters runs with the jar on JDK 24 or later ({@code testClassFile}), where {@link BytecodeLoader} uses
 * {@code java.lang.classfile}.
 */
class ReaderEquivalenceTest {

    @Test
    void sameInstructionsAsAsm() throws Exception {
        List<Path> roots = new ArrayList<>();
        roots.add(location(BytecodeLoader.class));          // main classes (a folder or the jar)
        roots.add(location(ReaderEquivalenceTest.class));   // Java tests
        try {
            roots.add(location(Class.forName("org.lambdaql.query.KotlinQueryTest")));   // Kotlin tests
        } catch (ClassNotFoundException e) {
            // the Kotlin tests are not on the class path (for example a single test run from an IDE)
        }
        ClassLoader loader = getClass().getClassLoader();
        int methods = 0;
        for (Path root : roots) {
            for (String internalName : classNames(root)) {
                byte[] bytes;
                try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
                    if (in == null) continue;
                    bytes = in.readAllBytes();
                }
                ClassNode asm = new ClassNode(Opcodes.ASM9);
                new ClassReader(bytes).accept(asm, ClassReader.SKIP_FRAMES);
                for (MethodNode expected : asm.methods) {
                    MethodNode actual = BytecodeLoader.load(loader, internalName, expected.name, expected.desc);
                    assertEquals(dump(expected), dump(actual), internalName + "." + expected.name + expected.desc);
                    methods++;
                }
            }
        }
        assertTrue(methods > 1000, "methods compared: " + methods);
    }

    private static Path location(Class<?> c) throws URISyntaxException {
        return Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** The internal names of the lambdaQL classes in a class folder or a jar (versioned entries are skipped). */
    private static List<String> classNames(Path root) throws IOException {
        if (Files.isDirectory(root)) return classNamesUnder(root);
        try (FileSystem jar = FileSystems.newFileSystem(root)) {
            return classNamesUnder(jar.getPath("/"));
        }
    }

    private static List<String> classNamesUnder(Path base) throws IOException {
        Path dir = base.resolve("org/lambdaql");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.map(p -> base.relativize(p).toString().replace('\\', '/'))
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> n.substring(0, n.length() - ".class".length()))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Writes the instructions as text: one line per real instruction, with its source line and operands.
     * Labels are written as the index of the next real instruction, so label objects do not matter.
     */
    private static String dump(MethodNode m) {
        AbstractInsnNode[] insns = m.instructions.toArray();
        Map<LabelNode, Integer> target = new HashMap<>();
        int real = 0;
        for (AbstractInsnNode n : insns) {
            if (n instanceof LabelNode l) target.put(l, real);
            else if (n.getOpcode() >= 0) real++;
        }
        StringBuilder out = new StringBuilder();
        int line = -1;
        for (AbstractInsnNode n : insns) {
            if (n instanceof LineNumberNode ln) line = ln.line;
            if (n.getOpcode() < 0) continue;
            out.append(line).append(' ').append(n.getOpcode());
            switch (n) {
                case VarInsnNode x -> out.append(' ').append(x.var);
                case IntInsnNode x -> out.append(' ').append(x.operand);
                case IincInsnNode x -> out.append(' ').append(x.var).append(' ').append(x.incr);
                case LdcInsnNode x -> out.append(' ').append(x.cst.getClass().getSimpleName()).append(' ').append(x.cst);
                case TypeInsnNode x -> out.append(' ').append(x.desc);
                case FieldInsnNode x -> out.append(' ').append(x.owner).append('.').append(x.name).append(' ').append(x.desc);
                case MethodInsnNode x -> out.append(' ').append(x.owner).append('.').append(x.name).append(x.desc)
                        .append(' ').append(x.itf);
                case InvokeDynamicInsnNode x -> out.append(' ').append(x.name).append(x.desc).append(' ').append(x.bsm)
                        .append(' ').append(Arrays.toString(x.bsmArgs));
                case JumpInsnNode x -> out.append(" -> ").append(target.get(x.label));
                case TableSwitchInsnNode x -> out.append(' ').append(x.min).append(' ').append(x.max)
                        .append(" -> ").append(target.get(x.dflt)).append(' ').append(x.labels.stream().map(target::get).toList());
                case LookupSwitchInsnNode x -> out.append(' ').append(x.keys)
                        .append(" -> ").append(target.get(x.dflt)).append(' ').append(x.labels.stream().map(target::get).toList());
                case MultiANewArrayInsnNode x -> out.append(' ').append(x.desc).append(' ').append(x.dims);
                default -> {
                }
            }
            out.append('\n');
        }
        return out.toString();
    }
}
