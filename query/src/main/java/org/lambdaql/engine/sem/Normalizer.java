package org.lambdaql.engine.sem;

import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.decode.NegationMinimizer;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.lambdaql.engine.ir.Pred;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The semantic step: turns a decoded condition into SQL meaning.
 * <ul>
 *   <li>Getter chains on an entity become {@link Expr.Column} paths ({@code o.getCustomer().getName()} → {@code customer.name}).</li>
 *   <li>Parts that do not use the entity become {@link Expr.Host} values (computed in Java, bound as parameters).</li>
 *   <li>Boxing and unboxing calls are skipped.</li>
 *   <li>{@code equals}, {@code Objects.equals} and Kotlin {@code Intrinsics.areEqual} become null-safe compares.</li>
 *   <li>{@code a.compareTo(b) > 0} and Kotlin {@code Intrinsics.compare(a, b) > 0} become {@code a > b}.</li>
 *   <li>{@code ids.contains(o.getId())} (captured collection) becomes {@link Pred.In} (ROADMAP D29).</li>
 * </ul>
 * Anything else that uses the entity is rejected with a clear error.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/NormalizerTest.java">NormalizerTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.NormalizerTest" region="normalize"}
 */
public final class Normalizer {

    private static final Set<String> BOXES = Set.of("java/lang/Integer", "java/lang/Long", "java/lang/Double",
            "java/lang/Float", "java/lang/Short", "java/lang/Byte", "java/lang/Boolean", "java/lang/Character",
            "java/lang/Number");
    private static final String INTRINSICS = "kotlin/jvm/internal/Intrinsics";

    private final List<Class<?>> roots;
    private final EntityModel model;
    private final ClassLoader loader;
    private final String where;

    private Normalizer(List<Class<?>> roots, EntityModel model, ClassLoader loader, String where) {
        this.roots = roots;
        this.model = model;
        this.loader = loader;
        this.where = where;
    }

    /**
     * Normalizes a decoded condition.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/NormalizerTest.java">NormalizerTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.NormalizerTest" region="normalize"}
     *
     * @param pred   the condition from {@code ConditionReducer}
     * @param roots  entity class of each lambda parameter ({@code roots.get(0)} is the main entity)
     * @param model  entity information
     * @param loader class loader for classes named in the bytecode
     * @param where  a name for error messages
     * @return the condition with only columns, host values, constants and math
     * @throws LambdaAnalysisException if the condition uses something that cannot become SQL
     */
    public static Pred normalize(Pred pred, List<Class<?>> roots, EntityModel model, ClassLoader loader, String where) {
        Normalizer n = new Normalizer(roots, model, loader, where);
        return NegationMinimizer.minimize(n.pred(pred));
    }

    /**
     * Normalizes a value expression (for select, sort keys and so on).
     *
     * @param value  the decoded value
     * @param roots  entity class of each lambda parameter
     * @param model  entity information
     * @param loader class loader
     * @param where  a name for error messages
     * @return the normalized value
     * @throws LambdaAnalysisException if the value cannot become SQL
     */
    public static Expr normalizeValue(Expr value, List<Class<?>> roots, EntityModel model, ClassLoader loader, String where) {
        Normalizer n = new Normalizer(roots, model, loader, where);
        return n.done(n.value(value));
    }

    // ------------------------------------------------------------------ conditions

    private Pred pred(Pred p) {
        return switch (p) {
            case Pred.Cmp c -> cmp(c.op(), c.left(), c.right(), c.nullSafe());
            case Pred.IsNull n -> isNull(n.operand());
            case Pred.Truth t -> truth(t.operand());
            case Pred.In in -> new Pred.In(done(value(in.value())), host(in.values(), Collection.class, true));
            case Pred.And a -> {
                Pred out = Pred.Bool.TRUE;
                for (Pred item : a.items()) out = Pred.and(out, pred(item));
                yield out;
            }
            case Pred.Or o -> {
                Pred out = Pred.Bool.FALSE;
                for (Pred item : o.items()) out = Pred.or(out, pred(item));
                yield out;
            }
            case Pred.Not n -> Pred.not(pred(n.operand()));
            case Pred.Bool b -> b;
        };
    }

    private Pred cmp(CmpOp op, Expr left, Expr right, boolean nullSafe) {
        // a.compareTo(b) op 0   and   0 op a.compareTo(b)
        if (isZero(right) && compareCall(left) != null) {
            Expr[] ab = compareCall(left);
            return cmp(op, ab[0], ab[1], false);
        }
        if (isZero(left) && compareCall(right) != null) {
            Expr[] ab = compareCall(right);
            return cmp(op.flip(), ab[0], ab[1], false);
        }
        Expr a = done(value(left));
        Expr b = done(value(right));
        if (isNullLiteral(b)) return op == CmpOp.EQ ? new Pred.IsNull(a) : nullCheck(op, a);
        if (isNullLiteral(a)) return op == CmpOp.EQ ? new Pred.IsNull(b) : nullCheck(op, b);
        if (a instanceof Expr.Column ca && b instanceof Expr.Host hb) b = retype(hb, ca.javaType());
        if (b instanceof Expr.Column cb && a instanceof Expr.Host ha) a = retype(ha, cb.javaType());
        rejectEntityCompare(a, b);
        return new Pred.Cmp(op, a, b, nullSafe);
    }

    private Pred nullCheck(CmpOp op, Expr e) {
        if (op == CmpOp.NE) return Pred.not(new Pred.IsNull(e));
        throw error("null과는 =, != 만 비교할 수 있습니다");
    }

    private Pred isNull(Expr operand) {
        return new Pred.IsNull(done(value(operand)));
    }

    private Pred truth(Expr e) {
        if (e instanceof Expr.Invoke inv) {
            MethodRef m = inv.method();
            // a.equals(b)
            if (!m.isStatic() && m.name().equals("equals") && m.descriptor().equals("(Ljava/lang/Object;)Z")) {
                Expr a = done(value(inv.receiver()));
                if (a instanceof Expr.Host h) a = new Expr.Host(h.expr(), h.javaType(), true);
                Expr b = done(value(inv.args().get(0)));
                return eq(a, b);
            }
            // Objects.equals(a, b), Kotlin Intrinsics.areEqual(a, b)
            if (m.isStatic() && inv.args().size() == 2
                    && (m.is("java/util/Objects", "equals") || m.is(INTRINSICS, "areEqual"))) {
                return eq(done(value(inv.args().get(0))), done(value(inv.args().get(1))));
            }
            // captured.contains(x) and Kotlin CollectionsKt.contains(captured, x)
            if (!m.isStatic() && m.name().equals("contains") && m.descriptor().equals("(Ljava/lang/Object;)Z")
                    && !usesEntity(inv.receiver())) {
                return new Pred.In(done(value(inv.args().get(0))), host(inv.receiver(), Collection.class, true));
            }
            if (m.isStatic() && m.is("kotlin/collections/CollectionsKt", "contains") && inv.args().size() == 2
                    && !usesEntity(inv.args().get(0))) {
                return new Pred.In(done(value(inv.args().get(1))), host(inv.args().get(0), Collection.class, true));
            }
        }
        return new Pred.Truth(done(value(e)));
    }

    private Pred eq(Expr a, Expr b) {
        if (isNullLiteral(b)) return new Pred.IsNull(a);
        if (isNullLiteral(a)) return new Pred.IsNull(b);
        if (a instanceof Expr.Column ca && b instanceof Expr.Host hb) b = retype(hb, ca.javaType());
        if (b instanceof Expr.Column cb && a instanceof Expr.Host ha) a = retype(ha, cb.javaType());
        rejectEntityCompare(a, b);
        return new Pred.Cmp(CmpOp.EQ, a, b, true);
    }

    // ------------------------------------------------------------------ values

    /** A value while normalizing: either finished, or an entity path that is not a column yet. */
    private sealed interface V permits Done, Path {
    }

    /** A finished value. */
    private record Done(Expr expr) implements V {
    }

    /**
     * An entity or embeddable reached by a path.
     *
     * @param root     lambda parameter index
     * @param names    property names so far
     * @param assoc    association flag per name
     * @param type     the class at the end of the path
     * @param kind     ROOT (the parameter itself), ASSOCIATION or EMBEDDED
     * @param nonNull  {@code true} if the association can never be null
     */
    private record Path(int root, List<String> names, List<Boolean> assoc, Class<?> type, String kind, boolean nonNull)
            implements V {
    }

    private V value(Expr e) {
        if (!usesEntity(e)) {
            // constants written in the lambda code stay literals; captured and computed values are bound
            if (e instanceof Expr.Const c && !(c.value() instanceof Type)) return new Done(c);
            Object enumConstant = enumConstant(e);
            if (enumConstant != null) return new Done(new Expr.Const(enumConstant, e.type()));
            return new Done(host(e, classOf(e.type()), false));
        }
        return switch (e) {
            case Expr.Param p -> {
                if (p.index() >= roots.size()) throw error("엔티티가 아닌 람다 인자입니다: " + p);
                yield new Path(p.index(), List.of(), List.of(), roots.get(p.index()), "ROOT", true);
            }
            case Expr.Convert c -> value(c.operand());
            case Expr.Invoke inv -> invoke(inv);
            case Expr.Arith a -> new Done(new Expr.Arith(a.op(), done(value(a.left())), done(value(a.right())), a.type()));
            case Expr.Neg n -> new Done(new Expr.Neg(done(value(n.operand())), n.type()));
            case Expr.Coalesce c -> new Done(new Expr.Coalesce(done(value(c.first())), done(value(c.second())), c.type()));
            case Expr.GetField f -> throw error("엔티티 필드를 직접 읽을 수 없습니다. getter를 쓰세요: " + f.name());
            default -> throw error("SQL로 바꿀 수 없는 식입니다: " + e);
        };
    }

    private V invoke(Expr.Invoke inv) {
        MethodRef m = inv.method();
        // boxing: Integer.valueOf(x)  /  unboxing: x.intValue()
        if (m.isStatic() && m.name().equals("valueOf") && BOXES.contains(m.owner()) && inv.args().size() == 1
                && m.argumentTypes()[0].getSort() != Type.OBJECT) {
            return value(inv.args().get(0));
        }
        if (!m.isStatic() && inv.args().isEmpty() && m.name().endsWith("Value") && BOXES.contains(m.owner())) {
            return value(inv.receiver());
        }
        if (!m.isStatic() && inv.args().isEmpty() && inv.receiver() != null) {
            V recv = value(inv.receiver());
            if (recv instanceof Path p) return step(p, m);
        }
        throw error("엔티티 값에 대해 지원하지 않는 메서드 호출입니다: " + m.ownerSimpleName() + "." + m.name());
    }

    private V step(Path p, MethodRef getter) {
        EntityModel.Property prop = findProperty(p.type(), getter.name())
                .orElseThrow(() -> error("엔티티 속성을 찾을 수 없습니다: " + p.type().getSimpleName() + "." + getter.name()));
        List<String> names = new ArrayList<>(p.names());
        names.add(prop.name());
        List<Boolean> assoc = new ArrayList<>(p.assoc());
        return switch (prop.kind()) {
            case BASIC -> {
                assoc.add(false);
                boolean idPath = prop.id() && p.kind().equals("ASSOCIATION");
                yield new Done(new Expr.Column(p.root(), names, assoc, prop.javaType(), prop.nonNull(), idPath));
            }
            case EMBEDDED -> {
                assoc.add(false);
                yield new Path(p.root(), names, assoc, prop.javaType(), "EMBEDDED", prop.nonNull());
            }
            case ASSOCIATION -> {
                assoc.add(true);
                yield new Path(p.root(), names, assoc, prop.javaType(), "ASSOCIATION", prop.nonNull());
            }
            case COLLECTION -> throw error("컬렉션 속성은 경로로 쓸 수 없습니다(서브쿼리를 쓰세요): " + prop.name());
        };
    }

    private Optional<EntityModel.Property> findProperty(Class<?> owner, String method) {
        List<String> candidates = new ArrayList<>();
        if (method.startsWith("get") && method.length() > 3) candidates.add(decapitalize(method.substring(3)));
        if (method.startsWith("is") && method.length() > 2) {
            candidates.add(decapitalize(method.substring(2)));
            candidates.add(method); // Kotlin property named isActive
        }
        candidates.add(method); // record-style accessor
        for (String c : candidates) {
            Optional<EntityModel.Property> p = model.property(owner, c);
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }

    private Expr done(V v) {
        if (v instanceof Done d) return d.expr();
        Path p = (Path) v;
        if (p.names().isEmpty()) throw error("엔티티 자체를 비교할 수 없습니다(v0.2). id를 비교하세요");
        if (p.kind().equals("EMBEDDED")) throw error("값 객체 전체를 비교할 수 없습니다. 필드를 비교하세요: " + String.join(".", p.names()));
        // an association used as a value: its foreign key (o.customer), for IS NULL checks
        return new Expr.Column(p.root(), p.names(), p.assoc(), p.type(), p.nonNull(), false);
    }

    // ------------------------------------------------------------------ helpers

    private Expr host(Expr e, Class<?> type, boolean nonNullRequired) {
        if (usesEntity(e)) throw error("앱에서 계산할 값에 엔티티가 들어 있습니다: " + e);
        return new Expr.Host(e, type, nonNullRequired);
    }

    private Expr retype(Expr.Host h, Class<?> columnType) {
        boolean numeric = Number.class.isAssignableFrom(columnType);
        return numeric ? new Expr.Host(h.expr(), columnType, h.nonNullRequired()) : h;
    }

    private void rejectEntityCompare(Expr a, Expr b) {
        for (Expr e : new Expr[]{a, b}) {
            if (e instanceof Expr.Column c && model.isEntity(c.javaType())
                    && !(isNullLiteral(a) || isNullLiteral(b))) {
                throw error("엔티티끼리 비교할 수 없습니다(v0.2). id를 비교하세요: " + c);
            }
        }
    }

    private static Expr[] compareCall(Expr e) {
        if (!(e instanceof Expr.Invoke inv)) return null;
        MethodRef m = inv.method();
        if (!m.isStatic() && m.name().equals("compareTo") && inv.args().size() == 1 && m.returnType() == Type.INT_TYPE) {
            return new Expr[]{inv.receiver(), inv.args().get(0)};
        }
        if (m.isStatic() && m.name().equals("compare") && inv.args().size() == 2 && m.returnType() == Type.INT_TYPE
                && (m.owner().equals(INTRINSICS) || BOXES.contains(m.owner()))) {
            return new Expr[]{inv.args().get(0), inv.args().get(1)};
        }
        return null;
    }

    /**
     * Returns the enum constant if the expression is {@code SomeEnum.VALUE} (a static field of an enum of the same type).
     *
     * @param e an expression
     * @return the enum constant, or {@code null}
     */
    private Object enumConstant(Expr e) {
        if (!(e instanceof Expr.GetField f) || f.receiver() != null) return null;
        if (!f.type().getInternalName().equals(f.owner())) return null;
        try {
            Class<?> c = Class.forName(f.owner().replace('/', '.'), false, loader);
            if (!c.isEnum()) return null;
            for (Object constant : c.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(f.name())) return constant;
            }
        } catch (ClassNotFoundException ignore) {
            // not loadable: treat as a normal value
        }
        return null;
    }

    private static boolean isZero(Expr e) {
        return e instanceof Expr.Const c && c.value() instanceof Integer i && i == 0;
    }

    private static boolean isNullLiteral(Expr e) {
        return e instanceof Expr.Const c && c.isNull();
    }

    /**
     * Checks if an expression uses a lambda parameter (an entity).
     *
     * @param e the expression
     * @return {@code true} if a {@link Expr.Param} is inside
     */
    static boolean usesEntity(Expr e) {
        return switch (e) {
            case Expr.Param p -> true;
            case Expr.Invoke i -> (i.receiver() != null && usesEntity(i.receiver())) || i.args().stream().anyMatch(Normalizer::usesEntity);
            case Expr.GetField f -> f.receiver() != null && usesEntity(f.receiver());
            case Expr.Arith a -> usesEntity(a.left()) || usesEntity(a.right());
            case Expr.Neg n -> usesEntity(n.operand());
            case Expr.Convert c -> usesEntity(c.operand());
            case Expr.CmpResult c -> usesEntity(c.left()) || usesEntity(c.right());
            case Expr.NewObject n -> n.args().stream().anyMatch(Normalizer::usesEntity);
            case Expr.ArrayLength a -> usesEntity(a.array());
            case Expr.Coalesce c -> usesEntity(c.first()) || usesEntity(c.second());
            case Expr.Column c -> true;
            default -> false;
        };
    }

    private Class<?> classOf(Type t) {
        return switch (t.getSort()) {
            case Type.BOOLEAN -> Boolean.class;
            case Type.CHAR -> Character.class;
            case Type.BYTE -> Byte.class;
            case Type.SHORT -> Short.class;
            case Type.INT -> Integer.class;
            case Type.FLOAT -> Float.class;
            case Type.LONG -> Long.class;
            case Type.DOUBLE -> Double.class;
            case Type.ARRAY -> Object[].class;
            default -> {
                try {
                    yield Class.forName(t.getClassName(), false, loader);
                } catch (ClassNotFoundException e) {
                    yield Object.class;
                }
            }
        };
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) return s;
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1)) && Character.isUpperCase(s.charAt(0))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private LambdaAnalysisException error(String message) {
        return new LambdaAnalysisException(message + " — " + where);
    }
}
