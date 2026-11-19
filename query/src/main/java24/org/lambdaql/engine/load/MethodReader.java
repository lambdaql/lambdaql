package org.lambdaql.engine.load;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction.ArgumentConstantInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.ConstantInstruction.LoadConstantInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one method of a class file with {@code java.lang.classfile} (Java 24 and later; ROADMAP D52).
 * <p>
 * This class is in {@code META-INF/versions/24} of the Multi-Release JAR and replaces the ASM reader of the same name
 * on Java 24 and later. The class file API of the running JDK always reads the class files of that JDK, so a new
 * JDK does not need a new ASM release.
 * <p>
 * The instructions are copied into the same ASM tree ({@link MethodNode}) that the ASM reader makes, so the decoder
 * reads both in the same way. The ASM tree classes are plain data and do not check the class file version.
 * The copy follows what ASM's {@code ClassReader} gives with {@code SKIP_FRAMES}:
 * <ul>
 *   <li>Short and wide forms get the normal opcode: {@code iload_1} becomes {@code ILOAD 1}, {@code ldc_w} and
 *       {@code ldc2_w} become {@code LDC}, {@code goto_w} becomes {@code GOTO}.</li>
 *   <li>Labels become {@link LabelNode}s at the same places, and line numbers become {@link LineNumberNode}s.</li>
 *   <li>Frames, local variable tables and try-catch blocks are not copied (the decoder does not use them).</li>
 * </ul>
 */
final class MethodReader {

    /**
     * The name of this reader, given by {@link BytecodeLoader#readerName()}.
     * A method, not a constant: javac would copy a constant into the caller, and the caller would not see the
     * other class of the Multi-Release JAR.
     *
     * @return {@code "classfile"}
     */
    static String name() {
        return "classfile";
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
     * @throws IllegalArgumentException if the class file is broken
     */
    static MethodNode read(byte[] classFile, String methodName, String descriptor) {
        ClassModel model = ClassFile.of().parse(classFile);
        for (MethodModel m : model.methods()) {
            if (!m.methodName().equalsString(methodName) || !m.methodType().equalsString(descriptor)) continue;
            MethodNode node = new MethodNode(Opcodes.ASM9, m.flags().flagsMask(), methodName, descriptor, null, null);
            m.code().ifPresent(code -> copy(code, node.instructions));
            return node;
        }
        return null;
    }

    /**
     * Copies the instructions, labels and line numbers of a method body.
     *
     * @param code the method body
     * @param out  the instruction list to fill
     */
    private static void copy(CodeModel code, InsnList out) {
        Map<Label, LabelNode> labels = new HashMap<>();
        for (CodeElement e : code) {
            switch (e) {
                case LabelTarget t -> out.add(label(labels, t.label()));
                case LineNumber ln -> {
                    LabelNode start = new LabelNode();
                    out.add(start);
                    out.add(new LineNumberNode(ln.line(), start));
                }
                case Instruction i -> out.add(instruction(i, labels));
                default -> {
                    // local variable tables, try-catch blocks, character ranges: not used by the decoder
                }
            }
        }
    }

    private static LabelNode label(Map<Label, LabelNode> labels, Label l) {
        return labels.computeIfAbsent(l, k -> new LabelNode());
    }

    /**
     * Makes the ASM node of one instruction.
     *
     * @param i      the instruction
     * @param labels labels seen so far (jump targets ahead are added)
     * @return the node, with the opcode ASM would give
     */
    private static AbstractInsnNode instruction(Instruction i, Map<Label, LabelNode> labels) {
        int op = i.opcode().bytecode();
        return switch (i) {
            case LoadInstruction x -> new VarInsnNode(load(x.typeKind()), x.slot());
            case StoreInstruction x -> new VarInsnNode(load(x.typeKind()) + (Opcodes.ISTORE - Opcodes.ILOAD), x.slot());
            case IncrementInstruction x -> new IincInsnNode(x.slot(), x.constant());
            case ArgumentConstantInstruction x -> new IntInsnNode(op, x.constantValue());
            case LoadConstantInstruction x -> new LdcInsnNode(constant(x.constantEntry()));
            case BranchInstruction x -> new JumpInsnNode(x.opcode() == Opcode.GOTO_W ? Opcodes.GOTO : op, label(labels, x.target()));
            case FieldInstruction x -> new FieldInsnNode(op, x.owner().asInternalName(), x.name().stringValue(), x.type().stringValue());
            case InvokeInstruction x -> new MethodInsnNode(op, x.owner().asInternalName(), x.name().stringValue(),
                    x.type().stringValue(), x.isInterface());
            case InvokeDynamicInstruction x -> {
                var bootstrap = x.invokedynamic().bootstrap();
                yield new InvokeDynamicInsnNode(x.name().stringValue(), x.type().stringValue(),
                        handle(bootstrap.bootstrapMethod()), constants(bootstrap.arguments()));
            }
            case TypeCheckInstruction x -> new TypeInsnNode(op, x.type().asInternalName());
            case NewObjectInstruction x -> new TypeInsnNode(Opcodes.NEW, x.className().asInternalName());
            case NewReferenceArrayInstruction x -> new TypeInsnNode(Opcodes.ANEWARRAY, x.componentType().asInternalName());
            case NewPrimitiveArrayInstruction x -> new IntInsnNode(Opcodes.NEWARRAY, x.typeKind().newarrayCode());
            case NewMultiArrayInstruction x -> new MultiANewArrayInsnNode(x.arrayType().asInternalName(), x.dimensions());
            case TableSwitchInstruction x -> {
                LabelNode[] targets = new LabelNode[x.highValue() - x.lowValue() + 1];
                java.util.Arrays.fill(targets, label(labels, x.defaultTarget()));
                for (SwitchCase c : x.cases()) targets[c.caseValue() - x.lowValue()] = label(labels, c.target());
                yield new TableSwitchInsnNode(x.lowValue(), x.highValue(), label(labels, x.defaultTarget()), targets);
            }
            case LookupSwitchInstruction x -> {
                List<SwitchCase> cases = x.cases();
                int[] keys = new int[cases.size()];
                LabelNode[] targets = new LabelNode[cases.size()];
                for (int k = 0; k < keys.length; k++) {
                    keys[k] = cases.get(k).caseValue();
                    targets[k] = label(labels, cases.get(k).target());
                }
                yield new LookupSwitchInsnNode(label(labels, x.defaultTarget()), keys, targets);
            }
            case DiscontinuedInstruction.JsrInstruction x -> new JumpInsnNode(Opcodes.JSR, label(labels, x.target()));
            case DiscontinuedInstruction.RetInstruction x -> new VarInsnNode(Opcodes.RET, x.slot());
            // no operand: constants such as iconst_1, math, conversions, stack, array, return, throw, monitor
            default -> new InsnNode(op);
        };
    }

    /**
     * The normal load opcode of a value type ({@code ILOAD}, {@code LLOAD}, {@code FLOAD}, {@code DLOAD}, {@code ALOAD}).
     *
     * @param kind the value type
     * @return the opcode
     */
    private static int load(TypeKind kind) {
        return switch (kind) {
            case LONG -> Opcodes.LLOAD;
            case FLOAT -> Opcodes.FLOAD;
            case DOUBLE -> Opcodes.DLOAD;
            case REFERENCE -> Opcodes.ALOAD;
            default -> Opcodes.ILOAD;   // int, short, char, byte, boolean
        };
    }

    /**
     * Turns a constant pool entry into the value ASM gives for it.
     *
     * @param e the entry
     * @return {@code Integer}, {@code Long}, {@code Float}, {@code Double}, {@code String}, {@link Type},
     *         {@link Handle} or {@link ConstantDynamic}
     */
    private static Object constant(LoadableConstantEntry e) {
        return switch (e) {
            case IntegerEntry x -> x.intValue();
            case LongEntry x -> x.longValue();
            case FloatEntry x -> x.floatValue();
            case DoubleEntry x -> x.doubleValue();
            case StringEntry x -> x.stringValue();
            case ClassEntry x -> Type.getObjectType(x.asInternalName());
            case MethodTypeEntry x -> Type.getMethodType(x.descriptor().stringValue());
            case MethodHandleEntry x -> handle(x);
            case ConstantDynamicEntry x -> new ConstantDynamic(x.name().stringValue(), x.type().stringValue(),
                    handle(x.bootstrap().bootstrapMethod()), constants(x.bootstrap().arguments()));
        };
    }

    private static Object[] constants(List<LoadableConstantEntry> entries) {
        Object[] out = new Object[entries.size()];
        for (int k = 0; k < out.length; k++) out[k] = constant(entries.get(k));
        return out;
    }

    private static Handle handle(MethodHandleEntry e) {
        MemberRefEntry ref = e.reference();
        return new Handle(e.kind(), ref.owner().asInternalName(), ref.name().stringValue(), ref.type().stringValue(),
                ref instanceof InterfaceMethodRefEntry);
    }
}
