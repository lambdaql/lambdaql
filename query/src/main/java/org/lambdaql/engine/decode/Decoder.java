package org.lambdaql.engine.decode;

import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.ir.ArithOp;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.LambdaRef;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the bytecode of a lambda body and builds a decision graph ({@link Node}).
 * <p>
 * How it works: it "runs" the instructions with symbols instead of real values.
 * The JVM stack holds {@link Expr} trees. When it meets a conditional jump, it makes a {@link Node.Test}
 * whose condition is "the jump is taken", then follows both paths.
 * The same state (instruction position + stack + local variables) always gives the same node,
 * so short-circuit code becomes a graph with shared nodes.
 * <p>
 * Kotlin patterns are cleaned up here:
 * <ul>
 *   <li>{@code a?.b} (safe call) is read as the path {@code a.b} (ROADMAP D23).</li>
 *   <li>{@code a ?: b} (elvis) becomes {@link Expr.Coalesce}.</li>
 *   <li>{@code a!!} and Kotlin null checks ({@code Intrinsics.check*}) are skipped.</li>
 * </ul>
 * Not supported (an error is thrown): loops, {@code switch}, {@code throw}, string {@code +},
 * lambdas inside the lambda, and calls that return nothing.
 *
 * <p>Example:
 * <pre>{@code
 * LambdaRef ref = SerializedLambdaExtractor.extract((Where<Order>) o -> o.getStatus() == 1 && o.isActive());
 * Node graph = Decoder.decodePredicate(ref, BytecodeLoader.load(ref));
 * Pred where = ConditionReducer.reduce(graph, ref.describe());
 * // where: (param0.getStatus[] = 1 && truth(param0.isActive[]))
 * }</pre>
 */
public final class Decoder implements Opcodes {

    private static final String KOTLIN_INTRINSICS = "kotlin/jvm/internal/Intrinsics";

    private final LambdaRef ref;
    private final MethodNode method;
    private final AbstractInsnNode[] insns;
    private final Map<LabelNode, Integer> labelIndex = new HashMap<>();
    private final Map<State, Node> memo = new HashMap<>();
    private final boolean predicate;
    private int uninitCounter;

    private Decoder(LambdaRef ref, MethodNode method, boolean predicate) {
        this.ref = ref;
        this.method = method;
        this.insns = method.instructions.toArray();
        this.predicate = predicate;
        for (int i = 0; i < insns.length; i++) {
            if (insns[i] instanceof LabelNode l) labelIndex.put(l, i);
        }
    }

    /**
     * Decodes a condition lambda (one that returns {@code boolean}).
     *
     * @param ref    the lambda
     * @param method the body method of the lambda
     * @return the decision graph; every end is {@link Node.Leaf#TRUE} or {@link Node.Leaf#FALSE}
     * @throws LambdaAnalysisException if the body uses something that is not supported
     *
     * <p>Example: {@code Decoder.decodePredicate(ref, BytecodeLoader.load(ref))}
     */
    public static Node decodePredicate(LambdaRef ref, MethodNode method) {
        return new Decoder(ref, method, true).run();
    }

    /**
     * Decodes a value lambda (select, orderBy key and so on).
     *
     * @param ref    the lambda
     * @param method the body method of the lambda
     * @return the decision graph; a lambda without branches gives one {@link Node.Value}
     * @throws LambdaAnalysisException if the body uses something that is not supported
     *
     * <p>Example: for {@code o -> o.getPrice()} the result is {@code Value(param0.getPrice[])}.
     */
    public static Node decodeValue(LambdaRef ref, MethodNode method) {
        return new Decoder(ref, method, false).run();
    }

    // ------------------------------------------------------------------ state

    /**
     * Placeholder for an object after {@code NEW} and before its constructor runs.
     *
     * @param type class of the object
     * @param id   unique number, so two {@code NEW}s of the same class are different
     */
    private record Uninit(Type type, int id) {
    }

    /**
     * Key for sharing nodes.
     *
     * @param pc     instruction index
     * @param stack  operand stack ({@link Expr} or {@link Uninit})
     * @param locals local variables by slot
     */
    private record State(int pc, List<Object> stack, List<Expr> locals) {
    }

    private Node run() {
        List<Expr> locals = initialLocals();
        return explore(0, List.of(), locals, -1);
    }

    /**
     * Fills the local variable slots at method start.
     * Captured values come first, then lambda parameters. {@code long}/{@code double} take two slots.
     *
     * @return values by slot (unused slots are null)
     */
    private List<Expr> initialLocals() {
        List<Expr> locals = new ArrayList<>();
        int captured = ref.capturedCount();
        int capIndex = 0;
        if (ref.implIsInstance()) {
            set(locals, 0, new Expr.Captured(capIndex++, Type.getObjectType(ref.implClass())));
        }
        int slot = ref.implIsInstance() ? 1 : 0;
        int paramIndex = 0;
        for (Type t : Type.getArgumentTypes(method.desc)) {
            Expr e = capIndex < captured ? new Expr.Captured(capIndex++, t) : new Expr.Param(paramIndex++, t);
            set(locals, slot, e);
            slot += t.getSize();
        }
        return locals;
    }

    private static void set(List<Expr> locals, int slot, Expr e) {
        while (locals.size() <= slot) locals.add(null);
        locals.set(slot, e);
    }

    // ------------------------------------------------------------------ walking

    /**
     * Runs instructions from {@code pc} until a jump or a return, and returns the node for that point.
     *
     * @param pc       instruction index to start at
     * @param stackIn  operand stack at that point
     * @param localsIn local variables at that point
     * @param lineIn   current source line, or -1
     * @return the node (shared if the same state was seen before)
     */
    private Node explore(int pc, List<Object> stackIn, List<Expr> localsIn, int lineIn) {
        State key = new State(pc, stackIn, localsIn);
        Node cached = memo.get(key);
        if (cached != null) return cached;

        List<Object> stack = new ArrayList<>(stackIn);
        List<Expr> locals = new ArrayList<>(localsIn);
        int line = lineIn;
        int i = pc;
        while (true) {
            if (i >= insns.length) throw error("메서드 끝을 지나쳤습니다", line);
            AbstractInsnNode n = insns[i];
            int op = n.getOpcode();
            if (n instanceof LineNumberNode ln) {
                line = ln.line;
                i++;
                continue;
            }
            if (op < 0) { // label or frame
                i++;
                continue;
            }

            // Kotlin safe call (?.) and elvis (?:)
            if (op == DUP) {
                int nx = nextReal(i + 1);
                if (nx >= 0 && insns[nx] instanceof JumpInsnNode j) {
                    Integer handled = kotlinNullPattern(nx, j, stack, locals);
                    if (handled != null) {
                        i = handled;
                        continue;
                    }
                }
            }

            switch (op) {
                // ---- constants
                case ACONST_NULL -> stack.add(Expr.Const.ofNull());
                case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 ->
                        stack.add(new Expr.Const(op - ICONST_0, Type.INT_TYPE));
                case LCONST_0, LCONST_1 -> stack.add(new Expr.Const((long) (op - LCONST_0), Type.LONG_TYPE));
                case FCONST_0, FCONST_1, FCONST_2 -> stack.add(new Expr.Const((float) (op - FCONST_0), Type.FLOAT_TYPE));
                case DCONST_0, DCONST_1 -> stack.add(new Expr.Const((double) (op - DCONST_0), Type.DOUBLE_TYPE));
                case BIPUSH, SIPUSH -> stack.add(new Expr.Const(((IntInsnNode) n).operand, Type.INT_TYPE));
                case LDC -> stack.add(ldc(((LdcInsnNode) n).cst, line));

                // ---- local variables
                case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> {
                    int v = ((VarInsnNode) n).var;
                    Expr e = v < locals.size() ? locals.get(v) : null;
                    if (e == null) throw error("초기화되지 않은 지역 변수 " + v, line);
                    stack.add(e);
                }
                case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> set(locals, ((VarInsnNode) n).var, popExpr(stack, line));
                case IINC -> {
                    IincInsnNode inc = (IincInsnNode) n;
                    Expr cur = locals.get(inc.var);
                    set(locals, inc.var, new Expr.Arith(ArithOp.ADD, cur, new Expr.Const(inc.incr, Type.INT_TYPE), Type.INT_TYPE));
                }

                // ---- stack
                case POP -> pop(stack, line);
                case POP2 -> {
                    Object top = pop(stack, line);
                    if (!isWide(top)) pop(stack, line);
                }
                case DUP -> stack.add(peek(stack, line));
                case DUP_X1 -> {
                    Object a = pop(stack, line), b = pop(stack, line);
                    stack.add(a);
                    stack.add(b);
                    stack.add(a);
                }
                case DUP2 -> {
                    Object a = pop(stack, line);
                    if (isWide(a)) {
                        stack.add(a);
                        stack.add(a);
                    } else {
                        Object b = pop(stack, line);
                        stack.add(b);
                        stack.add(a);
                        stack.add(b);
                        stack.add(a);
                    }
                }
                case SWAP -> {
                    Object a = pop(stack, line), b = pop(stack, line);
                    stack.add(a);
                    stack.add(b);
                }

                // ---- math
                case IADD, LADD, FADD, DADD -> arith(stack, ArithOp.ADD, line);
                case ISUB, LSUB, FSUB, DSUB -> arith(stack, ArithOp.SUB, line);
                case IMUL, LMUL, FMUL, DMUL -> arith(stack, ArithOp.MUL, line);
                case IDIV, LDIV, FDIV, DDIV -> arith(stack, ArithOp.DIV, line);
                case IREM, LREM, FREM, DREM -> arith(stack, ArithOp.REM, line);
                case INEG, LNEG, FNEG, DNEG -> {
                    Expr a = popExpr(stack, line);
                    stack.add(new Expr.Neg(a, a.type()));
                }
                case I2L, F2L, D2L -> convert(stack, Type.LONG_TYPE, line);
                case I2F, L2F, D2F -> convert(stack, Type.FLOAT_TYPE, line);
                case I2D, L2D, F2D -> convert(stack, Type.DOUBLE_TYPE, line);
                case L2I, F2I, D2I -> convert(stack, Type.INT_TYPE, line);
                case I2B -> convert(stack, Type.BYTE_TYPE, line);
                case I2C -> convert(stack, Type.CHAR_TYPE, line);
                case I2S -> convert(stack, Type.SHORT_TYPE, line);
                case LCMP, FCMPL, DCMPL -> cmp(stack, false, line);
                case FCMPG, DCMPG -> cmp(stack, true, line);

                // ---- objects
                case NEW -> stack.add(new Uninit(Type.getObjectType(((TypeInsnNode) n).desc), uninitCounter++));
                case CHECKCAST -> {
                    Expr a = popExpr(stack, line);
                    stack.add(new Expr.Convert(a, Type.getObjectType(((TypeInsnNode) n).desc)));
                }
                case GETFIELD -> {
                    FieldInsnNode f = (FieldInsnNode) n;
                    stack.add(new Expr.GetField(popExpr(stack, line), f.owner, f.name, Type.getType(f.desc)));
                }
                case GETSTATIC -> {
                    FieldInsnNode f = (FieldInsnNode) n;
                    stack.add(new Expr.GetField(null, f.owner, f.name, Type.getType(f.desc)));
                }
                case ARRAYLENGTH -> stack.add(new Expr.ArrayLength(popExpr(stack, line)));
                case INVOKEVIRTUAL, INVOKEINTERFACE, INVOKESTATIC, INVOKESPECIAL -> invoke((MethodInsnNode) n, stack, line);
                case INVOKEDYNAMIC -> throw error(indyMessage((InvokeDynamicInsnNode) n), line);

                // ---- jumps
                case GOTO -> {
                    int target = target(((JumpInsnNode) n).label, i, line);
                    return remember(key, explore(target, snapshot(stack), snapshot(locals), line));
                }
                case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT,
                     IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL -> {
                    JumpInsnNode j = (JumpInsnNode) n;
                    Pred atom = jumpAtom(op, stack, line);
                    int target = target(j.label, i, line);
                    List<Object> st = snapshot(stack);
                    List<Expr> lc = snapshot(locals);
                    Node onTrue = explore(target, st, lc, line);
                    Node onFalse = explore(i + 1, st, lc, line);
                    return remember(key, new Node.Test(atom, onTrue, onFalse, line));
                }

                // ---- returns
                case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN -> {
                    Expr v = popExpr(stack, line);
                    return remember(key, leaf(v, line));
                }

                case TABLESWITCH, LOOKUPSWITCH -> throw error("switch(Kotlin when 등)는 아직 지원하지 않습니다", line);
                case ATHROW -> throw error("예외를 던지는 경로는 지원하지 않습니다", line);
                case RETURN -> throw error("값을 반환하지 않는 람다는 조건/값 람다가 아닙니다", line);
                default -> throw error("지원하지 않는 명령어: " + opName(op), line);
            }
            i++;
        }
    }

    /**
     * Makes a read-only copy of a list. Unlike {@code List.copyOf}, it allows {@code null}
     * (the second slot of a {@code long}/{@code double} local variable is empty).
     *
     * @param list the list to copy
     * @param <T>  element type
     * @return a read-only copy
     */
    private static <T> List<T> snapshot(List<T> list) {
        return java.util.Collections.unmodifiableList(new ArrayList<>(list));
    }

    private Node remember(State key, Node node) {
        memo.put(key, node);
        return node;
    }

    /**
     * Makes the end node for a return instruction.
     *
     * @param v    the returned value
     * @param line source line
     * @return {@code TRUE}/{@code FALSE} for the constants 1/0; a {@code Truth} test for other boolean values;
     *         a {@link Node.Value} for value lambdas
     */
    private Node leaf(Expr v, int line) {
        if (predicate) {
            if (v instanceof Expr.Const c && c.value() instanceof Integer k) {
                if (k == 0) return Node.Leaf.FALSE;
                if (k == 1) return Node.Leaf.TRUE;
            }
            if (v instanceof Expr.CmpResult) throw error("비교 결과(-1/0/1)를 그대로 반환할 수 없습니다", line);
            return new Node.Test(new Pred.Truth(v), Node.Leaf.TRUE, Node.Leaf.FALSE, line);
        }
        return new Node.Value(v);
    }

    // ------------------------------------------------------------------ Kotlin null patterns

    /**
     * Handles a Kotlin null pattern that starts with {@code DUP} and a null jump.
     * <ul>
     *   <li>{@code dup; ifnull L; ...; L: pop; aconst_null} — safe call: keep only the non-null path</li>
     *   <li>{@code dup; ifnonnull L; pop; aconst_null; goto M; L: ...} — safe call, other order</li>
     *   <li>{@code dup; ifnonnull L; pop; <push one value>; L:} — elvis: push {@code coalesce(x, value)}</li>
     * </ul>
     *
     * @param jumpAt index of the jump instruction after {@code DUP}
     * @param j      the jump instruction
     * @param stack  current stack (changed for elvis)
     * @param locals current local variables
     * @return the next instruction index to run, or {@code null} if this is not one of the patterns
     *
     * <p>Example (Kotlin): {@code it.customer?.name == "Kim"} reads as {@code customer.name == "Kim"}.
     */
    private Integer kotlinNullPattern(int jumpAt, JumpInsnNode j, List<Object> stack, List<Expr> locals) {
        int op = j.getOpcode();
        if (op != IFNULL && op != IFNONNULL) return null;
        int target = labelIndex.get(j.label);
        if (op == IFNULL) {
            int a = nextReal(target);
            int b = a >= 0 ? nextReal(a + 1) : -1;
            if (a >= 0 && b >= 0 && insns[a].getOpcode() == POP && insns[b].getOpcode() == ACONST_NULL) {
                return jumpAt + 1; // keep x on the stack and go on with the non-null path
            }
            return null;
        }
        int a = nextReal(jumpAt + 1);
        int b = a >= 0 ? nextReal(a + 1) : -1;
        if (a < 0 || b < 0 || insns[a].getOpcode() != POP) return null;
        if (insns[b].getOpcode() == ACONST_NULL) {
            return target; // safe call: go to the non-null path (L)
        }
        // elvis: one instruction pushes the default value and then L comes right away
        if (target <= b || !onlyLabelsBetween(b + 1, target)) return null;
        Expr dflt = simplePush(insns[b], locals);
        if (dflt == null) return null;
        Expr x = popExpr(stack, -1);
        stack.add(new Expr.Coalesce(x, dflt, x.type()));
        return target;
    }

    private boolean onlyLabelsBetween(int from, int to) {
        for (int k = from; k < to; k++) {
            if (insns[k].getOpcode() >= 0) return false;
        }
        return true;
    }

    /**
     * Reads a single instruction that pushes one simple value (constant or local variable).
     *
     * @param n      the instruction
     * @param locals current local variables
     * @return the value, or {@code null} if the instruction is not a simple push
     */
    private Expr simplePush(AbstractInsnNode n, List<Expr> locals) {
        int op = n.getOpcode();
        if (op == LDC) return ldc(((LdcInsnNode) n).cst, -1);
        if (op >= ICONST_M1 && op <= ICONST_5) return new Expr.Const(op - ICONST_0, Type.INT_TYPE);
        if (op == BIPUSH || op == SIPUSH) return new Expr.Const(((IntInsnNode) n).operand, Type.INT_TYPE);
        if (op == ALOAD) {
            int v = ((VarInsnNode) n).var;
            return v < locals.size() ? locals.get(v) : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ instructions

    /**
     * Makes the "jump is taken" condition and pops its operands.
     *
     * @param op    the jump opcode
     * @param stack current stack
     * @param line  source line
     * @return the condition
     *
     * <p>Example: {@code if_icmpne} on {@code (status, 1)} gives {@code Cmp(NE, status, 1)};
     * {@code ifeq} on a boolean getter gives {@code Not(Truth(active))}.
     */
    private Pred jumpAtom(int op, List<Object> stack, int line) {
        switch (op) {
            case IFNULL -> {
                return new Pred.IsNull(popExpr(stack, line));
            }
            case IFNONNULL -> {
                return Pred.not(new Pred.IsNull(popExpr(stack, line)));
            }
            case IF_ACMPEQ, IF_ACMPNE -> {
                Expr b = popExpr(stack, line), a = popExpr(stack, line);
                return new Pred.Cmp(op == IF_ACMPEQ ? CmpOp.EQ : CmpOp.NE, a, b, true);
            }
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                Expr b = popExpr(stack, line), a = popExpr(stack, line);
                return new Pred.Cmp(cmpOp(op - IF_ICMPEQ), a, b, false);
            }
            default -> { // IFEQ..IFLE
                Expr v = popExpr(stack, line);
                CmpOp cop = cmpOp(op - IFEQ);
                if (v instanceof Expr.CmpResult c) {
                    return new Pred.Cmp(cop, c.left(), c.right(), false);
                }
                if (v.type().getSort() == Type.BOOLEAN && (cop == CmpOp.EQ || cop == CmpOp.NE)) {
                    Pred truth = new Pred.Truth(v);
                    return cop == CmpOp.NE ? truth : Pred.not(truth);
                }
                return new Pred.Cmp(cop, v, new Expr.Const(0, Type.INT_TYPE), false);
            }
        }
    }

    /**
     * Maps the jump offset (0 = EQ ... 5 = LE, the JVM order) to an operator.
     *
     * @param k offset from {@code IFEQ} or {@code IF_ICMPEQ}
     * @return the operator
     */
    private static CmpOp cmpOp(int k) {
        return switch (k) {
            case 0 -> CmpOp.EQ;
            case 1 -> CmpOp.NE;
            case 2 -> CmpOp.LT;
            case 3 -> CmpOp.GE;
            case 4 -> CmpOp.GT;
            case 5 -> CmpOp.LE;
            default -> throw new IllegalArgumentException("cmp " + k);
        };
    }

    /**
     * Handles a method call: pops the arguments and the receiver, then pushes an {@link Expr.Invoke}.
     * A constructor call turns the {@code NEW} placeholder into an {@link Expr.NewObject}.
     *
     * @param m     the call instruction
     * @param stack current stack
     * @param line  source line
     */
    private void invoke(MethodInsnNode m, List<Object> stack, int line) {
        boolean isStatic = m.getOpcode() == INVOKESTATIC;
        MethodRef mref = new MethodRef(m.owner, m.name, m.desc, isStatic, m.itf);
        Type[] argTypes = Type.getArgumentTypes(m.desc);
        List<Expr> args = new ArrayList<>(argTypes.length);
        for (int k = 0; k < argTypes.length; k++) args.add(0, popExpr(stack, line));

        if (m.getOpcode() == INVOKESPECIAL && m.name.equals("<init>")) {
            Object target = pop(stack, line);
            if (!(target instanceof Uninit u)) throw error("생성자 호출 대상이 NEW가 아닙니다", line);
            Expr created = new Expr.NewObject(u.type(), mref, args);
            for (int k = 0; k < stack.size(); k++) {
                if (stack.get(k).equals(u)) stack.set(k, created);
            }
            return;
        }
        Expr receiver = isStatic ? null : popExpr(stack, line);
        Type ret = Type.getReturnType(m.desc);
        if (ret.getSort() == Type.VOID) {
            if (m.owner.equals(KOTLIN_INTRINSICS) && m.name.startsWith("check")) {
                return; // Kotlin !! and platform type checks: no value meaning (D23)
            }
            throw error("반환값이 없는 호출은 조건 람다에서 쓸 수 없습니다: " + m.owner.replace('/', '.') + "." + m.name, line);
        }
        stack.add(new Expr.Invoke(receiver, mref, args));
    }

    /**
     * Turns an {@code LDC} constant into an {@link Expr.Const}.
     *
     * @param cst  the constant from the class file
     * @param line source line
     * @return the constant expression
     */
    private Expr ldc(Object cst, int line) {
        if (cst instanceof Integer v) return new Expr.Const(v, Type.INT_TYPE);
        if (cst instanceof Long v) return new Expr.Const(v, Type.LONG_TYPE);
        if (cst instanceof Float v) return new Expr.Const(v, Type.FLOAT_TYPE);
        if (cst instanceof Double v) return new Expr.Const(v, Type.DOUBLE_TYPE);
        if (cst instanceof String v) return new Expr.Const(v, Type.getType(String.class));
        if (cst instanceof Type t) return new Expr.Const(t, Type.getType(Class.class));
        throw error("지원하지 않는 상수: " + cst, line);
    }

    private void arith(List<Object> stack, ArithOp op, int line) {
        Expr b = popExpr(stack, line), a = popExpr(stack, line);
        stack.add(new Expr.Arith(op, a, b, a.type()));
    }

    private void convert(List<Object> stack, Type to, int line) {
        stack.add(new Expr.Convert(popExpr(stack, line), to));
    }

    private void cmp(List<Object> stack, boolean nanGreater, int line) {
        Expr b = popExpr(stack, line), a = popExpr(stack, line);
        stack.add(new Expr.CmpResult(a, b, nanGreater));
    }

    /**
     * Finds the instruction index of a jump target. Backward jumps (loops) are not allowed.
     *
     * @param label the target label
     * @param from  index of the jump
     * @param line  source line
     * @return the target index
     * @throws LambdaAnalysisException for a backward jump
     */
    private int target(LabelNode label, int from, int line) {
        int t = labelIndex.get(label);
        if (t <= from) throw error("반복문(뒤로 가는 점프)은 지원하지 않습니다", line);
        return t;
    }

    /**
     * Finds the next real instruction (not a label, line number or frame).
     *
     * @param from index to start at
     * @return the index, or -1 if there is none
     */
    private int nextReal(int from) {
        for (int k = from; k < insns.length; k++) {
            if (insns[k].getOpcode() >= 0) return k;
        }
        return -1;
    }

    private static Object pop(List<Object> stack, int line) {
        if (stack.isEmpty()) throw new LambdaAnalysisException("스택이 비었습니다(line " + line + ")");
        return stack.remove(stack.size() - 1);
    }

    private static Object peek(List<Object> stack, int line) {
        if (stack.isEmpty()) throw new LambdaAnalysisException("스택이 비었습니다(line " + line + ")");
        return stack.get(stack.size() - 1);
    }

    private Expr popExpr(List<Object> stack, int line) {
        Object o = pop(stack, line);
        if (o instanceof Expr e) return e;
        throw error("초기화되지 않은 객체를 값으로 썼습니다", line);
    }

    private static boolean isWide(Object o) {
        return o instanceof Expr e && Expr.isWide(e.type());
    }

    /**
     * Makes a clear message for an unsupported {@code invokedynamic}.
     *
     * @param n the instruction
     * @return the message
     */
    private String indyMessage(InvokeDynamicInsnNode n) {
        Handle bsm = n.bsm;
        if (bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
            return "문자열 결합(+)은 아직 지원하지 않습니다(v0.2)";
        }
        if (bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
            return "람다 안의 람다(서브쿼리 등)는 아직 지원하지 않습니다(M8)";
        }
        return "invokedynamic은 지원하지 않습니다: " + bsm.getOwner() + "." + bsm.getName();
    }

    /**
     * Makes an error with the lambda body name and source line.
     *
     * @param message what went wrong
     * @param line    source line, or -1
     * @return the exception (the caller throws it)
     */
    private LambdaAnalysisException error(String message, int line) {
        String where = ref.describe() + (line > 0 ? " (line " + line + ")" : "");
        return new LambdaAnalysisException(message + " — " + where);
    }

    private static String opName(int op) {
        return op >= 0 && op < org.objectweb.asm.util.Printer.OPCODES.length
                ? org.objectweb.asm.util.Printer.OPCODES[op] : String.valueOf(op);
    }
}
