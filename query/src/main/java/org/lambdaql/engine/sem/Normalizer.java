package org.lambdaql.engine.sem;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaQLException;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.engine.decode.NegationMinimizer;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.MethodRef;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.load.BytecodeLoader;
import org.lambdaql.engine.load.LambdaRef;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
 *   <li>String methods on an entity value become standard HQL functions: {@code o.getName().toLowerCase()} becomes
 *       {@code lower(e0.name)}, {@code a + b} becomes {@code concat(a, b)} (ROADMAP D74).</li>
 * </ul>
 * Anything else that uses the entity is rejected with a clear error.
 *
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/NormalizerTest.java">NormalizerTest</a>):
 * {@snippet class="org.lambdaql.engine.sem.NormalizerTest" region="normalize"}
 */
public final class Normalizer {

    private static final Set<String> BOXES = Set.of("java/lang/Integer", "java/lang/Long", "java/lang/Double",
            "java/lang/Float", "java/lang/Short", "java/lang/Byte", "java/lang/Boolean", "java/lang/Character",
            "java/lang/Number");
    private static final String INTRINSICS = "kotlin/jvm/internal/Intrinsics";

    private static final String SUB = "org/lambdaql/query/Sub";
    private static final String FROM = "org/lambdaql/query/From";
    private static final String ORDERED_FROM = "org/lambdaql/query/OrderedFrom";
    private static final String VALUES = "org/lambdaql/query/Values";
    private static final String FROM2 = "org/lambdaql/query/From2";
    private static final String BY = "org/lambdaql/query/By";
    private static final String SORT_KEYS = "org/lambdaql/query/SortKeys";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Normalizer.class);

    /** Entity class of every query root, by root index; shared by a query and its subqueries. */
    private final List<Class<?>> rootTypes;
    /** Lambda parameter index of each entity parameter → query root index. */
    private final java.util.Map<Integer, Integer> paramRoot;
    /** Lambda parameter index of the subquery factory {@code sub}, or -1. */
    private final int subParam;
    /** {@code true} in select and having lambdas: {@code Agg} calls are allowed (ROADMAP M9). */
    private boolean aggregates;
    /** Whether an entity itself may be a value ({@code select((o, c) -> c)}, ROADMAP D70): only in a select lambda. */
    private boolean entities;
    private final EntityModel model;
    private final ClassLoader loader;
    private final String where;

    private Normalizer(List<Class<?>> roots, EntityModel model, ClassLoader loader, String where) {
        this.rootTypes = new ArrayList<>(roots);
        this.paramRoot = new java.util.HashMap<>();
        for (int i = 0; i < roots.size(); i++) paramRoot.put(i, i);
        this.subParam = roots.size();
        this.model = model;
        this.loader = loader;
        this.where = where;
    }

    /** A normalizer for an inner lambda of a subquery: its parameter 0 is the subquery root. */
    private Normalizer(Normalizer outer, int root, int subParam) {
        this(outer, List.of(root), subParam);
    }

    /** A normalizer for an inner lambda of a subquery with joins: parameter i is root {@code roots.get(i)} (D51). */
    private Normalizer(Normalizer outer, List<Integer> roots, int subParam) {
        this.rootTypes = outer.rootTypes;
        this.paramRoot = new java.util.HashMap<>();
        for (int i = 0; i < roots.size(); i++) this.paramRoot.put(i, roots.get(i));
        this.subParam = subParam;
        this.model = outer.model;
        this.loader = outer.loader;
        this.where = outer.where;
    }

    /**
     * Normalizes a decoded condition.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/NormalizerTest.java">NormalizerTest</a>):
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
        return normalize(pred, roots, model, loader, where, false);
    }

    /**
     * Normalizes a condition, and allows aggregate functions ({@code Agg}) when {@code aggregates} is true
     * (for {@code having}).
     *
     * @param pred       the condition
     * @param roots      entity class of each lambda parameter
     * @param model      entity information
     * @param loader     class loader
     * @param where      a name for error messages
     * @param aggregates {@code true} to allow {@code Agg} calls
     * @return the normalized condition
     */
    public static Pred normalize(Pred pred, List<Class<?>> roots, EntityModel model, ClassLoader loader, String where,
                                 boolean aggregates) {
        Normalizer n = new Normalizer(roots, model, loader, where);
        n.aggregates = aggregates;
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
        return normalizeValue(value, roots, model, loader, where, false);
    }

    /**
     * Normalizes a value, and allows aggregate functions ({@code Agg}) when {@code aggregates} is true
     * (for {@code select} and INSERT ... SELECT).
     *
     * @param value      the decoded value
     * @param roots      entity class of each lambda parameter
     * @param model      entity information
     * @param loader     class loader
     * @param where      a name for error messages
     * @param aggregates {@code true} to allow {@code Agg} calls
     * @return the normalized value
     */
    public static Expr normalizeValue(Expr value, List<Class<?>> roots, EntityModel model, ClassLoader loader, String where,
                                      boolean aggregates) {
        Normalizer n = new Normalizer(roots, model, loader, where);
        n.aggregates = aggregates;
        return n.done(n.value(value));
    }

    /**
     * Normalizes one value of a select lambda: aggregate functions are allowed, and an entity parameter itself is a
     * value ({@code select((o, c) -> c)}, {@code select((o, c) -> new OrderWithCustomer(o, c))}, ROADMAP D70). Such a
     * value becomes {@link Expr.Root} and is written as its alias ({@code select e1}).
     *
     * <p>Example: {@code (o, c) -> c} gives {@code root1}.
     *
     * @param value  the decoded value
     * @param roots  entity class of each lambda parameter
     * @param model  entity information
     * @param loader class loader
     * @param where  a name for error messages
     * @return the normalized value
     */
    public static Expr normalizeSelected(Expr value, List<Class<?>> roots, EntityModel model, ClassLoader loader, String where) {
        Normalizer n = new Normalizer(roots, model, loader, where);
        n.aggregates = true;
        n.entities = true;
        return n.done(n.value(value));
    }

    /**
     * Finds the column that a setter call writes (ROADMAP M10, rules U1 and U6).
     * <ul>
     *   <li>{@code o.setStatus(x)}: a basic column; {@code o.setAddress(a)}: a whole embedded value;
     *       {@code o.setCustomer(c)}: an association (its foreign key).</li>
     *   <li>{@code o.getAddress().setCity(x)}: a field of an embedded value.</li>
     *   <li>{@code o.getCustomer().setId(x)}: the foreign key. Other setters on an association
     *       ({@code o.getCustomer().setName(x)}) change another table and are rejected.</li>
     * </ul>
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/engine/sem/AssignmentReaderTest.java">AssignmentReaderTest</a>):
     * {@snippet class="org.lambdaql.engine.sem.AssignmentReaderTest" region="read-assign"}
     *
     * @param receiver the object the setter is called on (from the decoded lambda)
     * @param setter   the setter name, for example {@code setStatus}
     * @param roots    entity class of each lambda parameter
     * @param model    entity information
     * @param loader   class loader
     * @param where    a name for error messages
     * @return the target column
     * @throws LambdaAnalysisException if the call does not write a column of the entity
     */
    public static Expr.Column assignTarget(Expr receiver, String setter, List<Class<?>> roots, EntityModel model,
                                           ClassLoader loader, String where) {
        Normalizer n = new Normalizer(roots, model, loader, where);
        if (!setter.startsWith("set") || setter.length() <= 3) {
            throw n.error(LqlMessage.ASSIGN_ONLY_SETTERS, setter);
        }
        if (!(n.value(receiver) instanceof Path p)) {
            throw n.error(LqlMessage.SETTER_TARGET, setter);
        }
        String base = decapitalize(setter.substring(3));
        EntityModel.Property prop = model.property(p.type(), base)
                .or(() -> model.property(p.type(), "is" + setter.substring(3)))
                .orElseThrow(() -> n.error(LqlMessage.PROPERTY_NOT_FOUND, p.type().getSimpleName() + "." + setter));
        List<String> names = new ArrayList<>(p.names());
        names.add(prop.name());
        if (prop.version()) throw n.error(LqlMessage.VERSION_ASSIGN, String.join(".", names));
        List<Boolean> assoc = new ArrayList<>(p.assoc());
        if (p.kind().equals("ASSOCIATION")) {
            if (!prop.id()) {
                throw n.error(LqlMessage.ASSOCIATION_ONLY_ID, String.join(".", names));
            }
            assoc.add(false);
            return new Expr.Column(p.root(), names, assoc, prop.javaType(), prop.nonNull(), true);
        }
        return switch (prop.kind()) {
            case BASIC, EMBEDDED -> {
                assoc.add(false);
                yield new Expr.Column(p.root(), names, assoc, prop.javaType(), prop.nonNull(), false);
            }
            case ASSOCIATION -> {
                assoc.add(true);
                yield new Expr.Column(p.root(), names, assoc, prop.javaType(), prop.nonNull(), false);
            }
            case COLLECTION -> throw n.error(LqlMessage.COLLECTION_ASSIGN, prop.name());
        };
    }

    // ------------------------------------------------------------------ conditions

    private Pred pred(Pred p) {
        return switch (p) {
            case Pred.Cmp c -> cmp(c.op(), c.left(), c.right(), c.nullSafe());
            case Pred.IsNull n -> isNull(n.operand());
            case Pred.Truth t -> truth(t.operand());
            case Pred.In in -> new Pred.In(done(value(in.value())), host(in.values(), Collection.class, true));
            case Pred.Like l -> l;
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
        V va = value(left), vb = value(right);
        if (op == CmpOp.EQ || op == CmpOp.NE) {
            Pred parts = embeddedEq(va, vb, nullSafe);
            if (parts != null) return op == CmpOp.EQ ? parts : Pred.not(parts);
        }
        // a joined entity compared with null (ROADMAP D44): c == null is "no matching row", its id is null
        Expr a = va instanceof Path pa && joinedRoot(pa) && vb instanceof Done db && isNullLiteral(db.expr()) ? rootId(pa) : done(va);
        Expr b = vb instanceof Path pb && joinedRoot(pb) && va instanceof Done da && isNullLiteral(da.expr()) ? rootId(pb) : done(vb);
        if (isNullLiteral(b)) return op == CmpOp.EQ ? isNullOf(a) : nullCheck(op, a);
        if (isNullLiteral(a)) return op == CmpOp.EQ ? isNullOf(b) : nullCheck(op, b);
        if (a instanceof Expr.Column ca && b instanceof Expr.Host hb) b = retype(hb, ca.javaType());
        if (b instanceof Expr.Column cb && a instanceof Expr.Host ha) a = retype(ha, cb.javaType());
        rejectEntityCompare(a, b);
        return new Pred.Cmp(op, a, b, nullSafe);
    }

    /**
     * Makes {@code e is null}. A column that can never be NULL (id, primitive, {@code nullable = false}) and that
     * is not reached through an association gives FALSE, so Kotlin's null-safe {@code it.id == id}
     * ({@code it.id != null && it.id == id}) becomes just {@code e0.id = :p1} (SARGable, 3.9).
     * A path through an association is kept: the row may have no association.
     *
     * @param e a normalized value
     * @return the condition
     */
    private static Pred isNullOf(Expr e) {
        if (e instanceof Expr.Column c && c.nonNull() && !c.association().contains(true)) return Pred.Bool.FALSE;
        return new Pred.IsNull(e);
    }

    private Pred nullCheck(CmpOp op, Expr e) {
        if (op == CmpOp.NE) return Pred.not(isNullOf(e));
        throw error(LqlMessage.NULL_COMPARE_OPERATOR);
    }

    private Pred isNull(Expr operand) {
        V v = value(operand);
        if (v instanceof Path p && joinedRoot(p)) return isNullOf(rootId(p));     // c == null for a joined entity (D44)
        if (v instanceof Path p && p.kind().equals("EMBEDDED")) return embeddedParts(p, null, true); // B6
        return isNullOf(done(v));
    }

    private Pred truth(Expr e) {
        if (e instanceof Expr.Invoke inv) {
            MethodRef m = inv.method();
            // sub.from(X)...select(...).contains(x): x in (subquery); !contains stays NOT IN with the SQL meaning (Q2)
            if (m.owner().equals(VALUES) && m.name().equals("contains") && inv.receiver() instanceof Expr.Invoke values) {
                return new Pred.In(done(value(inv.args().get(0))), subquery(values, Expr.Subquery.Kind.VALUES));
            }
            // a.equals(b)
            if (!m.isStatic() && m.name().equals("equals") && m.descriptor().equals("(Ljava/lang/Object;)Z")) {
                V ra = value(inv.receiver()), rb = value(inv.args().get(0));
                Pred parts = embeddedEq(ra, rb, true);
                if (parts != null) return parts;
                Expr a = done(ra);
                if (a instanceof Expr.Host h) a = new Expr.Host(h.expr(), h.javaType(), true, h.autoUnboxed());
                return eq(a, done(rb));
            }
            // Objects.equals(a, b), Kotlin Intrinsics.areEqual(a, b)
            if (m.isStatic() && inv.args().size() == 2
                    && (m.is("java/util/Objects", "equals") || m.is(INTRINSICS, "areEqual"))) {
                V ra = value(inv.args().get(0)), rb = value(inv.args().get(1));
                Pred parts = embeddedEq(ra, rb, true);
                return parts != null ? parts : eq(done(ra), done(rb));
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
            // Kotlin x in arr: ArraysKt.contains(arr, x) with an array that has no entity value (ROADMAP D76)
            if (m.isStatic() && m.is("kotlin/collections/ArraysKt", "contains") && inv.args().size() == 2
                    && !usesEntity(inv.args().get(0))) {
                return new Pred.In(done(value(inv.args().get(1))), host(inv.args().get(0), Object.class, true));
            }
            // Cond.between(x, from, to): from <= x and x <= to (ROADMAP D77)
            if (m.isStatic() && m.is("org/lambdaql/Cond", "between") && inv.args().size() == 3) {
                List<Expr> a = inv.args();
                return Pred.and(cmp(CmpOp.LE, a.get(1), a.get(0), false), cmp(CmpOp.LE, a.get(0), a.get(2), false));
            }
            // Kotlin x in from..to, x in from..<to on a range of Comparable values (dates, BigDecimal ...): from <= x
            // and x <= to (x < to for ..<). A range made in the lambda gives its two ends; a captured range is read
            // with getStart() and getEndInclusive() / getEndExclusive() when the query runs (ROADMAP D77)
            if (!m.isStatic() && m.name().equals("contains") && inv.args().size() == 1 && !usesEntity(inv.receiver())
                    && (m.owner().equals("kotlin/ranges/ClosedRange") || m.owner().equals("kotlin/ranges/OpenEndRange"))) {
                boolean open = m.owner().equals("kotlin/ranges/OpenEndRange");
                Expr range = inv.receiver();
                while (range instanceof Expr.Convert c) range = c.operand();
                Expr from, to;
                if (range instanceof Expr.Invoke r && r.method().isStatic() && r.method().owner().startsWith("kotlin/ranges/RangesKt")
                        && r.method().name().equals(open ? "rangeUntil" : "rangeTo") && r.args().size() == 2) {
                    from = r.args().get(0);
                    to = r.args().get(1);
                } else {
                    String end = open ? "getEndExclusive" : "getEndInclusive";
                    from = new Expr.Invoke(range, new MethodRef(m.owner(), "getStart", "()Ljava/lang/Comparable;", false, true), List.of());
                    to = new Expr.Invoke(range, new MethodRef(m.owner(), end, "()Ljava/lang/Comparable;", false, true), List.of());
                }
                Expr x = inv.args().get(0);
                return Pred.and(cmp(CmpOp.LE, from, x, false), cmp(open ? CmpOp.LT : CmpOp.LE, x, to, false));
            }
            // a.isAfter(b), a.isBefore(b), a.isEqual(b) on java.time values: a > b, a < b, a = b (ROADMAP D75)
            if (!m.isStatic() && inv.args().size() == 1 && m.owner().startsWith("java/time/")
                    && m.returnType() == Type.BOOLEAN_TYPE && (usesEntity(inv.receiver()) || usesEntity(inv.args().get(0)))) {
                CmpOp op = switch (m.name()) {
                    case "isAfter" -> CmpOp.GT;
                    case "isBefore" -> CmpOp.LT;
                    case "isEqual" -> CmpOp.EQ;
                    default -> null;
                };
                if (op != null) return cmp(op, inv.receiver(), inv.args().get(0), false);
            }
            Pred like = like(inv);
            if (like != null) return like;
            Pred test = stringTest(inv);
            if (test != null) return test;
        }
        return new Pred.Truth(done(value(e)));
    }

    // ------------------------------------------------------------------ string functions (ROADMAP D74)

    private static final Type STRING = Type.getType(String.class);
    private static final String KOTLIN_STRINGS = "kotlin/text/StringsKt";

    /**
     * Reads a String method on an entity value as a standard HQL function.
     * Positions are moved by one, because Java counts from 0 and SQL counts from 1.
     * <ul>
     *   <li>{@code toLowerCase()}, Kotlin {@code lowercase()}: {@code lower(x)}; {@code toUpperCase()}: {@code upper(x)}</li>
     *   <li>{@code trim()}: {@code trim(x)}; {@code length()}: {@code length(x)}</li>
     *   <li>{@code substring(a)}: {@code substring(x, a + 1)}; {@code substring(a, b)}: {@code substring(x, a + 1, b - a)}</li>
     *   <li>{@code indexOf(s)}: {@code locate(s, x) - 1}; {@code replace(a, b)}: {@code replace(x, a, b)}</li>
     *   <li>{@code +}, string templates and {@code concat}: {@code concat(...)}</li>
     * </ul>
     *
     * <p>Example: {@code o.getName().substring(0, 3)} becomes {@code substring(e0.name, 1, 3)}.
     *
     * @param inv a call that uses an entity value
     * @return the function call, or {@code null} if the call is not one of these methods
     */
    private Expr stringFunction(Expr.Invoke inv) {
        MethodRef m = inv.method();
        List<Expr> a = inv.args();
        String d = m.descriptor();
        if (!m.isStatic() && inv.receiver() != null
                && (m.owner().equals("java/lang/String") || m.owner().equals("java/lang/CharSequence"))) {
            Expr x = inv.receiver();
            return switch (m.name()) {
                // the Locale argument of toLowerCase(Locale) is not used: the database has its own rules
                case "toLowerCase" -> fn("lower", STRING, x);
                case "toUpperCase" -> fn("upper", STRING, x);
                case "trim" -> d.equals("()Ljava/lang/String;") ? fn("trim", STRING, x) : null;
                case "length" -> fn("length", Type.INT_TYPE, x);
                case "substring" -> {
                    if (d.equals("(I)Ljava/lang/String;")) yield fn("substring", STRING, x, plusOne(a.get(0)));
                    if (!d.equals("(II)Ljava/lang/String;")) yield null;
                    yield Expr.Func.builtin("substring", List.of(done(value(x)), plusOne(a.get(0)), difference(a.get(1), a.get(0))), STRING);
                }
                case "indexOf" -> d.equals("(Ljava/lang/String;)I") ? locate(a.get(0), x) : null;
                case "replace" -> d.equals("(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")
                        ? fn("replace", STRING, x, a.get(0), a.get(1)) : null;
                case "concat" -> concat(List.of(x, a.get(0)));
                case "toString" -> done(value(x));
                default -> null;
            };
        }
        // Kotlin trim() is StringsKt.trim(CharSequence).toString(): toString() of a text value is the value itself
        if (!m.isStatic() && inv.receiver() != null && m.is("java/lang/Object", "toString") && isText(inv.receiver().type())) {
            return done(value(inv.receiver()));
        }
        // a piece of a + concatenation (see Decoder): String.valueOf(x)
        if (m.isStatic() && m.is("java/lang/String", "valueOf") && a.size() == 1) return textOf(a.get(0));
        if (m.isStatic() && m.owner().startsWith(KOTLIN_STRINGS)) {
            String base = m.name().endsWith("$default") ? m.name().substring(0, m.name().length() - "$default".length()) : m.name();
            Type[] t = Type.getArgumentTypes(d);
            return switch (base) {
                case "trim" -> t.length == 1 && isText(t[0]) && isText(m.returnType()) ? fn("trim", STRING, a.get(0)) : null;
                // indexOf(s, startIndex = 0, ignoreCase = false)
                case "indexOf" -> t.length >= 4 && t[1].equals(STRING) && isConstant(a.get(2), 0) && isConstant(a.get(3), 0)
                        ? locate(a.get(1), a.get(0)) : null;
                // replace(old, new, ignoreCase = false)
                case "replace" -> t.length >= 4 && t[1].equals(STRING) && t[2].equals(STRING) && isConstant(a.get(3), 0)
                        ? fn("replace", STRING, a.get(0), a.get(1), a.get(2)) : null;
                default -> null;
            };
        }
        return null;
    }

    /**
     * Reads a String test on an entity value as a condition.
     * <ul>
     *   <li>{@code isEmpty()}: {@code length(x) = 0}</li>
     *   <li>{@code isBlank()}, Kotlin {@code isBlank()}: {@code length(trim(x)) = 0}</li>
     *   <li>{@code equalsIgnoreCase(s)}, Kotlin {@code equals(s, ignoreCase = true)}: {@code lower(x) = lower(s)}</li>
     * </ul>
     *
     * <p>Example: {@code o.getName().equalsIgnoreCase(name)} becomes {@code lower(e0.name) = lower(:p1)}.
     *
     * @param inv a boolean call
     * @return the condition, or {@code null} if the call is not one of these tests
     */
    private Pred stringTest(Expr.Invoke inv) {
        MethodRef m = inv.method();
        List<Expr> a = inv.args();
        Expr zero = new Expr.Const(0, Type.INT_TYPE);
        if (!m.isStatic() && inv.receiver() != null && m.owner().equals("java/lang/String")) {
            Expr x = inv.receiver();
            return switch (m.name()) {
                case "isEmpty" -> new Pred.Cmp(CmpOp.EQ, fn("length", Type.INT_TYPE, x), zero, false);
                case "isBlank" -> new Pred.Cmp(CmpOp.EQ, Expr.Func.builtin("length", List.of(fn("trim", STRING, x)), Type.INT_TYPE), zero, false);
                case "equalsIgnoreCase" -> new Pred.Cmp(CmpOp.EQ, fn("lower", STRING, x), fn("lower", STRING, a.get(0)), false);
                default -> null;
            };
        }
        if (m.isStatic() && m.owner().startsWith(KOTLIN_STRINGS)) {
            String base = m.name().endsWith("$default") ? m.name().substring(0, m.name().length() - "$default".length()) : m.name();
            Type[] t = Type.getArgumentTypes(m.descriptor());
            if (base.equals("isBlank") && t.length == 1 && isText(t[0])) {
                return new Pred.Cmp(CmpOp.EQ, Expr.Func.builtin("length", List.of(fn("trim", STRING, a.get(0))), Type.INT_TYPE), zero, false);
            }
            // equals(other, ignoreCase): the flag must be written in the lambda
            if (base.equals("equals") && t.length >= 3 && t[0].equals(STRING) && t[1].equals(STRING)) {
                if (isConstant(a.get(2), 0)) return eq(done(value(a.get(0))), done(value(a.get(1))));
                if (isConstant(a.get(2), 1)) {
                    return new Pred.Cmp(CmpOp.EQ, fn("lower", STRING, a.get(0)), fn("lower", STRING, a.get(1)), false);
                }
            }
        }
        return null;
    }

    /** A standard HQL function of normalized arguments. */
    private Expr.Func fn(String name, Type type, Expr... args) {
        List<Expr> out = new ArrayList<>();
        for (Expr arg : args) out.add(done(value(arg)));
        return Expr.Func.builtin(name, out, type);
    }

    /** Java {@code x.indexOf(s)} counts from 0 and gives -1 when not found; SQL {@code locate} counts from 1 and gives 0. */
    private Expr locate(Expr search, Expr x) {
        return new Expr.Arith(org.lambdaql.engine.ir.ArithOp.SUB, fn("locate", Type.INT_TYPE, search, x),
                new Expr.Const(1, Type.INT_TYPE), Type.INT_TYPE);
    }

    /** A Java position (from 0) as an SQL position (from 1): a constant is moved here, other values get {@code + 1}. */
    private Expr plusOne(Expr index) {
        Expr v = done(value(index));
        if (v instanceof Expr.Const c && c.value() instanceof Integer i) return new Expr.Const(i + 1, Type.INT_TYPE);
        return new Expr.Arith(org.lambdaql.engine.ir.ArithOp.ADD, v, new Expr.Const(1, Type.INT_TYPE), Type.INT_TYPE);
    }

    /** {@code end - begin}, the length of {@code substring(begin, end)}; computed here when both are constants. */
    private Expr difference(Expr end, Expr begin) {
        Expr e = done(value(end)), b = done(value(begin));
        if (e instanceof Expr.Const ce && ce.value() instanceof Integer ie && b instanceof Expr.Const cb && cb.value() instanceof Integer ib) {
            return new Expr.Const(ie - ib, Type.INT_TYPE);
        }
        return new Expr.Arith(org.lambdaql.engine.ir.ArithOp.SUB, e, b, Type.INT_TYPE);
    }

    /**
     * Joins pieces into one {@code concat(...)}; a piece that is itself a concatenation is opened,
     * so {@code a + b + c} gives {@code concat(a, b, c)}.
     */
    private Expr concat(List<Expr> pieces) {
        List<Expr> out = new ArrayList<>();
        for (Expr piece : pieces) {
            Expr v = done(value(piece));
            if (v instanceof Expr.Func f && f.builtin() && f.key().equals("concat")) out.addAll(f.args());
            else out.add(v);
        }
        return Expr.Func.builtin("concat", out, STRING);
    }

    /**
     * An entity value in a concatenation as text: text stays as it is, other values are converted with {@code str(x)}.
     * A piece without an entity value never comes here: it is computed in Java with {@code String.valueOf}.
     */
    private Expr textOf(Expr x) {
        Expr v = done(value(x));
        return isText(v.type()) ? v : Expr.Func.builtin("str", List.of(v), STRING);
    }

    /** Checks if a value is the int constant {@code n} ({@code false} and {@code true} are 0 and 1 in bytecode). */
    private static boolean isConstant(Expr e, int n) {
        Expr x = e;
        while (x instanceof Expr.Convert c) x = c.operand();
        if (x instanceof Expr.Const c && c.value() instanceof Integer i) return i == n;
        return x instanceof Expr.Const c && c.value() instanceof Boolean b && b == (n == 1);
    }

    /**
     * Reads {@code startsWith}, {@code endsWith} and {@code contains} on an entity value as a text match: Java
     * {@code o.getName().startsWith(p)} and Kotlin {@code it.name.startsWith(p)} (also {@code ignoreCase = true}).
     * The text must be a constant or a captured value; other forms are left to the generic method rule.
     *
     * <p>Example: {@code o.getName().contains(p)} becomes {@code Like(col0.name, p, ANY, false, false)}.
     *
     * @param inv the call
     * @return the match, or {@code null} if the call is not one of these forms
     */
    private Pred like(Expr.Invoke inv) {
        MethodRef m = inv.method();
        String base = m.name().endsWith("$default") ? m.name().substring(0, m.name().length() - "$default".length()) : m.name();
        Pred.Like.Kind kind = switch (base) {
            case "startsWith" -> Pred.Like.Kind.PREFIX;
            case "endsWith" -> Pred.Like.Kind.SUFFIX;
            case "contains" -> Pred.Like.Kind.ANY;
            default -> null;
        };
        if (kind == null) return null;
        Type[] args = Type.getArgumentTypes(m.descriptor());
        Expr target, text;
        boolean ignoreCase = false;
        if (!m.isStatic() && m.owner().equals("java/lang/String") && args.length == 1 && isText(args[0])) {
            target = inv.receiver();
            text = inv.args().get(0);
        } else if (m.isStatic() && m.owner().startsWith("kotlin/text/StringsKt") && args.length >= 2 && isText(args[1])) {
            target = inv.args().get(0);
            text = inv.args().get(1);
            if (!m.name().endsWith("$default") && args.length >= 3) {
                if (!(inv.args().get(2) instanceof Expr.Const c)) return null;     // ignoreCase must be written in the lambda
                ignoreCase = c.value() instanceof Boolean b ? b : c.value() instanceof Number n && n.intValue() != 0;
            }
        } else {
            return null;
        }
        if (!usesEntity(target) || usesEntity(text)) return null;
        Expr bare = text;
        while (bare instanceof Expr.Convert c) bare = c.operand();           // Kotlin casts a constant to CharSequence
        if (bare instanceof Expr.Const) text = bare;
        Expr value = done(value(target));
        Expr t = text instanceof Expr.Const ? text : host(text, String.class, false);
        return new Pred.Like(value, t, kind, ignoreCase, false);
    }

    private static boolean isText(Type t) {
        return t.getDescriptor().equals("Ljava/lang/String;") || t.getDescriptor().equals("Ljava/lang/CharSequence;");
    }

    private Pred eq(Expr a, Expr b) {
        return eq(a, b, true);
    }

    private Pred eq(Expr a, Expr b, boolean nullSafe) {
        if (isNullLiteral(b)) return isNullOf(a);
        if (isNullLiteral(a)) return isNullOf(b);
        if (a instanceof Expr.Column ca && b instanceof Expr.Host hb) b = retype(hb, ca.javaType());
        if (b instanceof Expr.Column cb && a instanceof Expr.Host ha) a = retype(ha, cb.javaType());
        rejectEntityCompare(a, b);
        return new Pred.Cmp(CmpOp.EQ, a, b, nullSafe);
    }

    // ------------------------------------------------------------------ value objects (ROADMAP D48, B6)

    /** Reads one property of a captured value object when the query runs. */
    private static final MethodRef EMBEDDED_PROPERTY = new MethodRef("org/lambdaql/engine/sem/EmbeddedValues", "property",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", true, false);

    /**
     * Writes an equality with a value object ({@code @Embedded}) column by column (ROADMAP D48, B6):
     * {@code o.getAddress().equals(addr)} becomes {@code address.city = :p1 and address.zip = :p2}, and
     * {@code o.getAddress() == null} becomes {@code address.city is null and address.zip is null}. Each part follows
     * the SQL meaning, so a NULL part matches no value.
     *
     * @param va       one side
     * @param vb       the other side
     * @param nullSafe the null-safe flag for each part compare
     * @return the condition, or {@code null} when neither side is a value object
     */
    private Pred embeddedEq(V va, V vb, boolean nullSafe) {
        Path p = embedded(va) ? (Path) va : embedded(vb) ? (Path) vb : null;
        if (p == null) return null;
        V other = p == va ? vb : va;
        if (other instanceof Done d && isNullLiteral(d.expr())) return embeddedParts(p, null, true);
        boolean sameType = other instanceof Path o && embedded(o) && o.type().equals(p.type());
        if (!sameType && !(other instanceof Done d2 && d2.expr() instanceof Expr.Host)) {
            throw error(LqlMessage.EMBEDDABLE_COMPARE, String.join(".", p.names()));
        }
        return embeddedParts(p, other, nullSafe);
    }

    private static boolean embedded(V v) {
        return v instanceof Path p && p.kind().equals("EMBEDDED");
    }

    /**
     * Makes the per-part condition of a value object: {@code part is null} for each part when {@code other} is
     * {@code null}, else {@code part = other's part}, joined with {@code and}. Nested value objects are expanded too.
     */
    private Pred embeddedParts(Path p, V other, boolean nullSafe) {
        List<String> attrs = model.attributeNames(p.type());
        if (attrs.isEmpty()) throw error(LqlMessage.EMBEDDABLE_COMPARE, String.join(".", p.names()));
        Pred out = Pred.Bool.TRUE;
        for (String attr : attrs) {
            EntityModel.Property prop = model.property(p.type(), attr)
                    .orElseThrow(() -> error(LqlMessage.PROPERTY_NOT_FOUND, p.type().getSimpleName() + "." + attr));
            V part = stepTo(p, prop);
            Pred one;
            if (other == null) {
                one = embedded(part) ? embeddedParts((Path) part, null, nullSafe) : isNullOf(done(part));
            } else {
                V otherPart = other instanceof Path o ? stepTo(o, prop) : hostPart((Expr.Host) ((Done) other).expr(), prop);
                one = embedded(part) ? embeddedParts((Path) part, otherPart, nullSafe) : eq(done(part), done(otherPart), nullSafe);
            }
            out = Pred.and(out, one);
        }
        return out;
    }

    /** One property of a captured value object, read in Java when the query runs (null when the object is null). */
    private static V hostPart(Expr.Host h, EntityModel.Property prop) {
        Expr read = new Expr.Invoke(null, EMBEDDED_PROPERTY,
                List.of(h.expr(), new Expr.Const(prop.name(), Type.getType(String.class))));
        return new Done(new Expr.Host(read, prop.javaType(), false, false));
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
     * @param safe     per name: {@code true} if the next part was read with the Kotlin safe call {@code ?.} (D54)
     */
    private record Path(int root, List<String> names, List<Boolean> assoc, Class<?> type, String kind, boolean nonNull,
                        List<Boolean> safe) implements V {
        /** A path without safe calls so far. */
        Path(int root, List<String> names, List<Boolean> assoc, Class<?> type, String kind, boolean nonNull) {
            this(root, names, assoc, type, kind, nonNull, java.util.Collections.nCopies(names.size(), false));
        }

        /** The same path with its last part marked as checked by {@code ?.}. */
        Path safeLast() {
            if (names.isEmpty()) return this;
            List<Boolean> s = new ArrayList<>(safe);
            s.set(s.size() - 1, true);
            return new Path(root, names, assoc, type, kind, nonNull, List.copyOf(s));
        }
    }

    private V value(Expr e) {
        if (!usesEntity(e) && !callsDbFunction(e)) {
            // constants written in the lambda code stay literals; captured and computed values are bound
            if (e instanceof Expr.Const c && !(c.value() instanceof Type)) return new Done(c);
            // a boxed constant (Long.valueOf(100L) for a Long setter or a generic argument) is still a constant (D37)
            Expr.Const boxed = boxedConstant(e);
            if (boxed != null) return new Done(boxed);
            // a converted constant (Kotlin writes `price * 2` as iconst_2; i2d) is still a constant
            Expr.Const converted = convertedConstant(e);
            if (converted != null) return new Done(converted);
            Object enumConstant = enumConstant(e);
            if (enumConstant != null) return new Done(new Expr.Const(enumConstant, e.type()));
            // a value made only from captured values (str.trim(), min.intValue()) is computed in Java and its result is
            // bound; a NullPointerException there is thrown like Java (LQL-4012). The one exception: unboxing a box to its
            // own primitive (Double.doubleValue(), Integer.intValue() ...) is the same bytecode as javac's automatic
            // unboxing, so it is treated as automatic: the boxed value is bound, and a null is NULL in SQL (D48)
            Expr unboxed = autoUnboxed(e);
            if (unboxed != e) {
                if (usesEntity(unboxed)) throw error(LqlMessage.HOST_VALUE_HAS_ENTITY, unboxed);
                return new Done(new Expr.Host(unboxed, classOf(unboxed.type()), false, true));
            }
            return new Done(host(e, classOf(e.type()), false));
        }
        return switch (e) {
            case Expr.Param p -> {
                Integer root = paramRoot.get(p.index());
                if (root == null) throw error(LqlMessage.PARAM_NOT_ENTITY, p);
                yield new Path(root, List.of(), List.of(), rootTypes.get(root), "ROOT", true);
            }
            case Expr.Root r -> new Path(r.root(), List.of(), List.of(), rootTypes.get(r.root()), "ROOT", true);
            case Expr.Convert c -> value(c.operand());
            // Kotlin it.customer?.name: an association checked by ?. may become a left join in a where (D54)
            case Expr.SafeNav n -> value(n.value()) instanceof Path p && p.kind().equals("ASSOCIATION") ? p.safeLast() : value(n.value());
            case Expr.Invoke inv -> invoke(inv);
            case Expr.Arith a -> new Done(new Expr.Arith(a.op(), done(value(a.left())), done(value(a.right())), a.type()));
            case Expr.Neg n -> new Done(new Expr.Neg(done(value(n.operand())), n.type()));
            case Expr.Coalesce c -> new Done(new Expr.Coalesce(done(value(c.first())), done(value(c.second())), c.type()));
            case Expr.Case c -> new Done(new Expr.Case(c.conds().stream().map(x -> NegationMinimizer.minimize(pred(x))).toList(), c.values().stream().map(v -> done(value(v))).toList(), done(value(c.otherwise())), c.type()));
            case Expr.GetField f -> throw error(LqlMessage.FIELD_ACCESS, f.name());
            default -> throw error(LqlMessage.EXPRESSION_NOT_SQL, e);
        };
    }

    private V invoke(Expr.Invoke inv) {
        MethodRef m = inv.method();
        // a database function declared with @SqlFunction (ROADMAP M11, D31); the Kotlin Companion receiver is skipped
        java.util.Optional<SqlFunctions.Info> dbFn = SqlFunctions.find(m, loader, where);
        if (dbFn.isPresent()) {
            List<Expr> args = new ArrayList<>();
            for (Expr arg : inv.args()) args.add(done(value(arg)));
            SqlFunctions.Info f = dbFn.get();
            return new Done(new Expr.Func(f.key(), f.sql(), f.asType(), args, Type.getType(f.method().getReturnType())));
        }
        // String methods on an entity value: lower, upper, trim, length, substring, locate, replace, concat (D74)
        Expr text = stringFunction(inv);
        if (text != null) return new Done(text);
        // Agg.count(), Agg.sum(x) ...: aggregate functions (ROADMAP M9, D27)
        if (m.isStatic() && m.owner().equals("org/lambdaql/Agg")) {
            if (!aggregates) throw error(LqlMessage.AGG_IN_WHERE, m.name());
            Expr.Aggregate.Fn fn = switch (m.name()) {
                case "count" -> inv.args().isEmpty() ? Expr.Aggregate.Fn.COUNT_ALL : Expr.Aggregate.Fn.COUNT;
                case "countDistinct" -> Expr.Aggregate.Fn.COUNT_DISTINCT;
                case "sum" -> Expr.Aggregate.Fn.SUM;
                case "avg" -> Expr.Aggregate.Fn.AVG;
                case "min" -> Expr.Aggregate.Fn.MIN;
                case "max" -> Expr.Aggregate.Fn.MAX;
                default -> throw error(LqlMessage.AGG_UNSUPPORTED, m.name());
            };
            Expr arg = inv.args().isEmpty() ? null : done(value(inv.args().get(0)));
            if (arg != null && !usesEntity(arg)) throw error(LqlMessage.AGG_ARG_NO_ENTITY, m.name());
            Type type = switch (fn) {
                case COUNT_ALL, COUNT, COUNT_DISTINCT -> Type.LONG_TYPE;
                case AVG -> Type.getType(Double.class);
                default -> arg.type();
            };
            return new Done(new Expr.Aggregate(fn, arg, type));
        }
        // a subquery that gives a value: exists(), count(), max/min/avg/sum(...), first()
        if (((m.owner().equals(FROM) || m.owner().equals(FROM2)) && List.of("exists", "count", "max", "min", "avg", "sum").contains(m.name()))
                || (m.owner().equals(VALUES) && m.name().equals("first"))) {
            Expr.Subquery.Kind kind = m.name().equals("exists") ? Expr.Subquery.Kind.EXISTS : Expr.Subquery.Kind.SCALAR;
            return new Done(subquery(inv, kind));
        }
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
        // Objects.requireNonNullElse(a, b): a, or b when a is null (U2)
        if (m.isStatic() && m.is("java/util/Objects", "requireNonNullElse") && inv.args().size() == 2) {
            return new Done(new Expr.Coalesce(done(value(inv.args().get(0))), done(value(inv.args().get(1))), inv.type()));
        }
        throw error(LqlMessage.METHOD_NOT_SUPPORTED, m.ownerSimpleName() + "." + m.name());
    }

    private V step(Path p, MethodRef getter) {
        EntityModel.Property prop = findProperty(p.type(), getter.name())
                .orElseThrow(() -> error(LqlMessage.PROPERTY_NOT_FOUND, p.type().getSimpleName() + "." + getter.name()));
        return stepTo(p, prop);
    }

    private V stepTo(Path p, EntityModel.Property prop) {
        List<String> names = new ArrayList<>(p.names());
        names.add(prop.name());
        List<Boolean> assoc = new ArrayList<>(p.assoc());
        List<Boolean> safe = new ArrayList<>(p.safe());
        safe.add(false);
        return switch (prop.kind()) {
            case BASIC -> {
                assoc.add(false);
                boolean idPath = prop.id() && p.kind().equals("ASSOCIATION");
                yield new Done(new Expr.Column(p.root(), names, assoc, prop.javaType(), prop.nonNull(), idPath, safe));
            }
            case EMBEDDED -> {
                assoc.add(false);
                yield new Path(p.root(), names, assoc, prop.javaType(), "EMBEDDED", prop.nonNull(), safe);
            }
            case ASSOCIATION -> {
                assoc.add(true);
                yield new Path(p.root(), names, assoc, prop.javaType(), "ASSOCIATION", prop.nonNull(), safe);
            }
            case COLLECTION -> throw error(LqlMessage.COLLECTION_PATH, prop.name());
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

    private static boolean joinedRoot(Path p) {
        return p.names().isEmpty() && p.kind().equals("ROOT") && p.root() > 0;
    }

    private Expr rootId(Path p) {
        if (model.isRow(p.type())) throw error(LqlMessage.DERIVED_ROW_NULL, p.type().getSimpleName());
        String id = model.idName(p.type()).orElseThrow(() -> error(LqlMessage.ENTITY_COMPARE, p.type().getSimpleName()));
        EntityModel.Property prop = model.property(p.type(), id)
                .orElseThrow(() -> error(LqlMessage.PROPERTY_NOT_FOUND, p.type().getSimpleName() + "." + id));
        return new Expr.Column(p.root(), List.of(id), List.of(false), prop.javaType(), false, false);
    }

    private Expr done(V v) {
        if (v instanceof Done d) return d.expr();
        Path p = (Path) v;
        if (p.names().isEmpty() && entities && p.kind().equals("ROOT") && model.isEntity(p.type())) {
            return new Expr.Root(p.root(), org.objectweb.asm.Type.getType(p.type()));     // select e1 (D70)
        }
        if (p.names().isEmpty()) throw error(LqlMessage.ENTITY_COMPARE, String.join(".", p.names()));
        if (p.kind().equals("EMBEDDED")) throw error(LqlMessage.EMBEDDABLE_COMPARE, String.join(".", p.names()));
        // an association used as a value: its foreign key (o.customer), for IS NULL checks
        return new Expr.Column(p.root(), p.names(), p.assoc(), p.type(), p.nonNull(), false, p.safe());
    }

    // ------------------------------------------------------------------ subqueries (ROADMAP M8)

    /**
     * Reads a call chain {@code sub.from(X).where(...)...<last>} into a subquery.
     *
     * @param last the last call: exists/count/aggregate/first, or the select call before {@code contains}
     * @param kind what the subquery gives
     * @return the subquery
     */
    private Expr.Subquery subquery(Expr.Invoke last, Expr.Subquery.Kind kind) {
        List<Expr.Invoke> chain = new ArrayList<>();
        Expr e = last;
        while (true) {
            while (e instanceof Expr.Convert c) e = c.operand();
            if (!(e instanceof Expr.Invoke inv)) throw error(LqlMessage.SUBQUERY_START, e);
            chain.add(0, inv);
            if (inv.method().owner().equals(SUB) && inv.method().name().equals("from")) break;
            e = inv.receiver();
        }
        Expr.Invoke from = chain.get(0);
        Expr subArg = from.receiver();
        while (subArg instanceof Expr.Convert c) subArg = c.operand();
        if (!(subArg instanceof Expr.Param p) || p.index() != subParam) {
            throw error(LqlMessage.SUBQUERY_WRONG_SUB);
        }
        Class<?> entity = classConstant(from.args().get(0));
        if (!model.isEntity(entity)) throw error(LqlMessage.SUBQUERY_NOT_ENTITY, entity.getName());
        int root = rootTypes.size();
        rootTypes.add(entity);

        List<Pred> wheres = new ArrayList<>();
        List<org.lambdaql.engine.ir.SortItem> sorts = new ArrayList<>();
        List<Integer> roots = new ArrayList<>(List.of(root));             // the subquery entity, then joined ones (D51)
        List<Expr.Subquery.Join> joins = new ArrayList<>();
        Integer limit = null;
        Expr select = null;
        Expr.Subquery.Agg agg = Expr.Subquery.Agg.NONE;
        for (int i = 1; i < chain.size(); i++) {
            Expr.Invoke step = chain.get(i);
            String name = step.method().name();
            switch (name) {
                case "join", "leftJoin" -> {
                    Class<?> joined = classConstant(step.args().get(0));
                    if (!model.isEntity(joined)) throw error(LqlMessage.SUBQUERY_NOT_ENTITY, joined.getName());
                    int jr = rootTypes.size();
                    rootTypes.add(joined);
                    roots.add(jr);
                    Expr on = step.args().get(1);
                    while (on instanceof Expr.Convert c) on = c.operand();
                    if (!(on instanceof Expr.Lambda l)) throw error(LqlMessage.INNER_LAMBDA_NOT_INLINE, name);
                    joins.add(new Expr.Subquery.Join(jr, joined, name.equals("leftJoin"), innerWhere(l, roots)));
                }
                case "where" -> wheres.add(innerWhere(lambdaArg(step), roots));
                case "orderBy" -> sorts.addAll(innerSort(lambdaArg(step), root));
                case "limit" -> limit = intConstant(step.args().get(0));
                case "select" -> select = innerValue(lambdaArg(step), roots);
                case "exists", "first", "contains" -> { }
                case "count" -> agg = Expr.Subquery.Agg.COUNT;
                case "max", "min", "avg", "sum" -> {
                    agg = Expr.Subquery.Agg.valueOf(name.toUpperCase(java.util.Locale.ROOT));
                    select = innerValue(lambdaArg(step), roots);
                }
                default -> throw error(LqlMessage.SUBQUERY_UNSUPPORTED_CALL, name);
            }
        }
        if (kind == Expr.Subquery.Kind.SCALAR && agg == Expr.Subquery.Agg.NONE && (limit == null || limit != 1)) {
            throw error(LqlMessage.SUBQUERY_FIRST_NEEDS_LIMIT);
        }
        if ((kind == Expr.Subquery.Kind.VALUES || agg == Expr.Subquery.Agg.NONE && kind == Expr.Subquery.Kind.SCALAR) && select == null) {
            throw error(LqlMessage.SUBQUERY_NEEDS_SELECT);
        }
        if (!sorts.isEmpty() && limit == null) {
            if (LOG.isWarnEnabled()) LOG.warn("{}", LqlMessage.SUBQUERY_SORT_REMOVED.format(where));
            sorts.clear();
        }
        Type type = switch (kind) {
            case EXISTS -> Type.BOOLEAN_TYPE;
            case VALUES -> select.type();
            case SCALAR -> switch (agg) {
                case COUNT -> Type.getType(Long.class);
                case AVG -> Type.getType(Double.class);
                default -> select.type();
            };
        };
        return new Expr.Subquery(kind, root, entity, wheres, select, agg, sorts, limit, type, joins);
    }

    private Pred innerWhere(Expr.Lambda l, List<Integer> roots) {
        LambdaRef ref = innerRef(l);
        int arity = Type.getArgumentTypes(l.descriptor()).length - l.captured().size();
        Normalizer inner = new Normalizer(this, roots, arity > roots.size() ? roots.size() : -1);
        Pred decoded = org.lambdaql.engine.decode.ConditionReducer.reduce(
                org.lambdaql.engine.decode.Decoder.decodePredicate(ref, BytecodeLoader.load(ref)), ref.describe());
        return NegationMinimizer.minimize(inner.pred(substitute(decoded, outerTerms(l.captured()))));
    }

    private Expr innerValue(Expr.Lambda l, List<Integer> roots) {
        LambdaRef ref = innerRef(l);
        Normalizer inner = new Normalizer(this, roots, -1);
        var node = org.lambdaql.engine.decode.Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        if (!(node instanceof org.lambdaql.engine.decode.Node.Value v)) throw error(LqlMessage.CONDITION_IN_SUBQUERY_VALUE);
        return inner.done(inner.value(substitute(v.value(), outerTerms(l.captured()))));
    }

    private List<org.lambdaql.engine.ir.SortItem> innerSort(Expr.Lambda l, int root) {
        LambdaRef ref = innerRef(l);
        Normalizer inner = new Normalizer(this, root, -1);
        var node = org.lambdaql.engine.decode.Decoder.decodeValue(ref, BytecodeLoader.load(ref));
        if (!(node instanceof org.lambdaql.engine.decode.Node.Value v) || SortReader.choosesChain(v.value())) {
            throw error(LqlMessage.CONDITION_IN_SORT);
        }
        List<Expr> captured = outerTerms(l.captured());
        List<org.lambdaql.engine.ir.SortItem> items = new ArrayList<>();
        Expr e = v.value();
        if (!SortReader.isChain(e)) {                      // one key: x -> x.getPrice()
            Expr key = inner.done(inner.value(substitute(e, captured)));
            return List.of(new org.lambdaql.engine.ir.SortItem(key, org.lambdaql.engine.ir.SortItem.Kind.ASC, null));
        }
        while (true) {
            while (e instanceof Expr.Convert c) e = c.operand();
            if (e instanceof Expr.Invoke inv && (inv.method().owner().equals(SORT_KEYS) && inv.receiver() != null
                    || inv.method().owner().equals(BY) && inv.receiver() == null)) {
                org.lambdaql.engine.ir.SortItem.Kind kind = switch (inv.method().name()) {
                    case "asc" -> org.lambdaql.engine.ir.SortItem.Kind.ASC;
                    case "desc" -> org.lambdaql.engine.ir.SortItem.Kind.DESC;
                    case "sort" -> org.lambdaql.engine.ir.SortItem.Kind.SORT;
                    default -> throw error(LqlMessage.SORT_UNSUPPORTED_METHOD, inv.method().name());
                };
                Expr key = inner.done(inner.value(substitute(inv.args().get(0), captured)));
                Expr arg = inv.args().size() > 1 ? inner.done(inner.value(substitute(inv.args().get(1), captured))) : null;
                items.add(0, new org.lambdaql.engine.ir.SortItem(key, kind, arg));
                if (inv.receiver() == null) break;         // the first key: By.desc(...)
                e = inv.receiver();
                continue;
            }
            throw error(LqlMessage.SORT_NOT_CHAIN, e);
        }
        return items;
    }

    /** The outer lambda's values captured by an inner lambda, with outer entity parameters turned into roots. */
    private List<Expr> outerTerms(List<Expr> captured) {
        List<Expr> out = new ArrayList<>();
        for (Expr c : captured) out.add(toRoots(c));
        return out;
    }

    private Expr toRoots(Expr e) {
        return switch (e) {
            case Expr.Param p -> {
                Integer root = paramRoot.get(p.index());
                if (root == null) throw error(LqlMessage.SUBQUERY_OUTER_SUB);
                yield new Expr.Root(root, p.type());
            }
            case Expr.Invoke i -> new Expr.Invoke(i.receiver() == null ? null : toRoots(i.receiver()), i.method(),
                    i.args().stream().map(this::toRoots).toList());
            case Expr.Convert c -> new Expr.Convert(toRoots(c.operand()), c.type());
            case Expr.SafeNav n -> new Expr.SafeNav(toRoots(n.value()));
            case Expr.GetField f -> f.receiver() == null ? f : new Expr.GetField(toRoots(f.receiver()), f.owner(), f.name(), f.type());
            case Expr.Arith a -> new Expr.Arith(a.op(), toRoots(a.left()), toRoots(a.right()), a.type());
            case Expr.Neg n -> new Expr.Neg(toRoots(n.operand()), n.type());
            case Expr.Coalesce c -> new Expr.Coalesce(toRoots(c.first()), toRoots(c.second()), c.type());
            case Expr.Case c -> c.map(this::toRoots);
            default -> e;
        };
    }

    /** Replaces the inner lambda's captured slots with the outer values (one pass). */
    private static Pred substitute(Pred p, List<Expr> captured) {
        return switch (p) {
            case Pred.Cmp c -> new Pred.Cmp(c.op(), substitute(c.left(), captured), substitute(c.right(), captured), c.nullSafe());
            case Pred.IsNull n -> new Pred.IsNull(substitute(n.operand(), captured));
            case Pred.Truth t -> new Pred.Truth(substitute(t.operand(), captured));
            case Pred.In in -> new Pred.In(substitute(in.value(), captured), substitute(in.values(), captured));
            case Pred.Like l -> new Pred.Like(substitute(l.value(), captured), substitute(l.text(), captured), l.kind(), l.ignoreCase(), l.negated());
            case Pred.And a -> new Pred.And(a.items().stream().map(i -> substitute(i, captured)).toList());
            case Pred.Or o -> new Pred.Or(o.items().stream().map(i -> substitute(i, captured)).toList());
            case Pred.Not n -> new Pred.Not(substitute(n.operand(), captured));
            case Pred.Bool b -> b;
        };
    }

    private static Expr substitute(Expr e, List<Expr> captured) {
        return switch (e) {
            case Expr.Captured c -> c.index() < captured.size() ? captured.get(c.index()) : c;
            case Expr.Invoke i -> new Expr.Invoke(i.receiver() == null ? null : substitute(i.receiver(), captured), i.method(),
                    i.args().stream().map(a -> substitute(a, captured)).toList());
            case Expr.Convert c -> new Expr.Convert(substitute(c.operand(), captured), c.type());
            case Expr.SafeNav n -> new Expr.SafeNav(substitute(n.value(), captured));
            case Expr.GetField f -> f.receiver() == null ? f : new Expr.GetField(substitute(f.receiver(), captured), f.owner(), f.name(), f.type());
            case Expr.Arith a -> new Expr.Arith(a.op(), substitute(a.left(), captured), substitute(a.right(), captured), a.type());
            case Expr.Neg n -> new Expr.Neg(substitute(n.operand(), captured), n.type());
            case Expr.Coalesce c -> new Expr.Coalesce(substitute(c.first(), captured), substitute(c.second(), captured), c.type());
            case Expr.Case c -> c.map(x -> substitute(x, captured));
            case Expr.Choose c -> new Expr.Choose(substitute(c.cond(), captured), substitute(c.whenTrue(), captured), substitute(c.whenFalse(), captured), c.type());
            case Expr.CmpResult c -> new Expr.CmpResult(substitute(c.left(), captured), substitute(c.right(), captured), c.nanGreater());
            case Expr.NewObject n -> new Expr.NewObject(n.type(), n.constructor(), n.args().stream().map(a -> substitute(a, captured)).toList());
            case Expr.Lambda l -> new Expr.Lambda(l.owner(), l.method(), l.descriptor(), l.isStatic(),
                    l.captured().stream().map(a -> substitute(a, captured)).toList(), l.type());
            default -> e;
        };
    }

    /** A lambda reference for an inner lambda body, so the decoder can read it like a top-level lambda. */
    private LambdaRef innerRef(Expr.Lambda l) {
        try {
            Class<?> owner = Class.forName(l.owner().replace('/', '.'), false, loader);
            int kind = l.isStatic() ? java.lang.invoke.MethodHandleInfo.REF_invokeStatic : java.lang.invoke.MethodHandleInfo.REF_invokeSpecial;
            var sl = new java.lang.invoke.SerializedLambda(owner, l.type().getInternalName(), "inner", l.descriptor(), kind,
                    l.owner(), l.method(), l.descriptor(), l.descriptor(), new Object[l.captured().size()]);
            return new LambdaRef(owner, sl);
        } catch (ClassNotFoundException e) {
            throw LqlMessage.CLASS_NOT_FOUND.exceptionCausedBy(where, e, l.owner());
        }
    }

    private Expr.Lambda lambdaArg(Expr.Invoke step) {
        Expr a = step.args().isEmpty() ? null : step.args().get(0);
        while (a instanceof Expr.Convert c) a = c.operand();
        if (a instanceof Expr.Lambda l) return l;
        throw error(LqlMessage.INNER_LAMBDA_NOT_INLINE, step.method().name());
    }

    private Class<?> classConstant(Expr e) {
        if (e instanceof Expr.Const c && c.value() instanceof Type t) {
            try {
                return Class.forName(t.getClassName(), false, loader);
            } catch (ClassNotFoundException ex) {
                throw LqlMessage.CLASS_NOT_FOUND.exceptionCausedBy(where, ex, t.getClassName());
            }
        }
        throw error(LqlMessage.SUBQUERY_FROM_CLASS_LITERAL);
    }

    private Integer intConstant(Expr e) {
        while (e instanceof Expr.Convert c) e = c.operand();
        if (e instanceof Expr.Const c && c.value() instanceof Integer i) return i;
        throw error(LqlMessage.SUBQUERY_LIMIT_CONSTANT);
    }

    // ------------------------------------------------------------------ helpers

    private Expr host(Expr e, Class<?> type, boolean nonNullRequired) {
        if (usesEntity(e)) throw error(LqlMessage.HOST_VALUE_HAS_ENTITY, e);
        return new Expr.Host(e, type, nonNullRequired);
    }

    private Expr retype(Expr.Host h, Class<?> columnType) {
        boolean numeric = Number.class.isAssignableFrom(columnType);
        return numeric ? new Expr.Host(h.expr(), columnType, h.nonNullRequired(), h.autoUnboxed()) : h;
    }

    private void rejectEntityCompare(Expr a, Expr b) {
        for (Expr e : new Expr[]{a, b}) {
            if (e instanceof Expr.Column c && model.isEntity(c.javaType())
                    && !(isNullLiteral(a) || isNullLiteral(b))) {
                throw error(LqlMessage.ENTITY_COMPARE, c);
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
     * Returns the constant inside a boxing call such as {@code Long.valueOf(100L)}.
     *
     * @param e an expression
     * @return the constant, or {@code null} if this is not a boxed constant
     */
    private static Expr.Const boxedConstant(Expr e) {
        if (!(e instanceof Expr.Invoke inv)) return null;
        MethodRef m = inv.method();
        if (!m.isStatic() || !m.name().equals("valueOf") || !BOXES.contains(m.owner()) || inv.args().size() != 1
                || m.argumentTypes()[0].getSort() == Type.OBJECT) {
            return null;
        }
        Expr arg = inv.args().get(0);
        while (arg instanceof Expr.Convert c) arg = c.operand();
        return arg instanceof Expr.Const c && c.value() != null && !(c.value() instanceof Type) ? c : null;
    }

    /**
     * Folds a number conversion of a constant, such as {@code i2d} on {@code 2}, into a constant of the new type.
     *
     * @param e an expression
     * @return the converted constant, or {@code null} if this is not a converted number constant
     */
    private static Expr.Const convertedConstant(Expr e) {
        if (!(e instanceof Expr.Convert c)) return null;
        Expr inner = c.operand();
        if (inner instanceof Expr.Convert) inner = convertedConstant(inner);
        if (!(inner instanceof Expr.Const k) || !(k.value() instanceof Number n)) return null;
        Object v = switch (c.type().getSort()) {
            case Type.INT, Type.SHORT, Type.BYTE, Type.CHAR -> n.intValue();
            case Type.LONG -> n.longValue();
            case Type.FLOAT -> n.floatValue();
            case Type.DOUBLE -> n.doubleValue();
            default -> null;
        };
        return v == null ? null : new Expr.Const(v, c.type());
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
    /** Checks if a value calls a {@code @SqlFunction}, so it is computed by the database, not in Java (FN5). */
    private boolean callsDbFunction(Expr e) {
        return switch (e) {
            case Expr.Invoke i -> SqlFunctions.find(i.method(), loader, where).isPresent()
                    || (i.receiver() != null && callsDbFunction(i.receiver())) || i.args().stream().anyMatch(this::callsDbFunction);
            case Expr.Arith a -> callsDbFunction(a.left()) || callsDbFunction(a.right());
            case Expr.Neg n -> callsDbFunction(n.operand());
            case Expr.Convert c -> callsDbFunction(c.operand());
            case Expr.SafeNav n -> callsDbFunction(n.value());
            case Expr.Coalesce c -> callsDbFunction(c.first()) || callsDbFunction(c.second());
            case Expr.Case c -> c.exprs().stream().anyMatch(this::callsDbFunction);
            case Expr.CmpResult c -> callsDbFunction(c.left()) || callsDbFunction(c.right());
            default -> false;
        };
    }

    private static final Map<String, String> OWN_PRIMITIVE = Map.of(
            "java/lang/Integer", "intValue", "java/lang/Long", "longValue", "java/lang/Double", "doubleValue",
            "java/lang/Float", "floatValue", "java/lang/Short", "shortValue", "java/lang/Byte", "byteValue",
            "java/lang/Boolean", "booleanValue", "java/lang/Character", "charValue");

    /**
     * Removes an unboxing to the box's own primitive (and a widening after it), which is what javac adds for
     * automatic unboxing. An unboxing to another primitive ({@code Double.intValue()}) can only be written by hand and
     * stays. Returns the same expression when there is nothing to remove.
     */
    private static Expr autoUnboxed(Expr e) {
        Expr x = e;
        while (x instanceof Expr.Convert c) x = c.operand();
        if (x instanceof Expr.Invoke inv && !inv.method().isStatic() && inv.args().isEmpty() && inv.receiver() != null
                && inv.method().name().equals(OWN_PRIMITIVE.get(inv.method().owner()))) {
            return inv.receiver();
        }
        return e;
    }

    static boolean usesEntity(Expr e) {
        return switch (e) {
            case Expr.Param p -> true;
            // Agg.count() has no argument but is still computed by the database
            case Expr.Invoke i -> (i.method().isStatic() && i.method().owner().equals("org/lambdaql/Agg"))
                    || (i.receiver() != null && usesEntity(i.receiver())) || i.args().stream().anyMatch(Normalizer::usesEntity);
            case Expr.GetField f -> f.receiver() != null && usesEntity(f.receiver());
            case Expr.Arith a -> usesEntity(a.left()) || usesEntity(a.right());
            case Expr.Neg n -> usesEntity(n.operand());
            case Expr.Convert c -> usesEntity(c.operand());
            case Expr.SafeNav n -> usesEntity(n.value());
            case Expr.CmpResult c -> usesEntity(c.left()) || usesEntity(c.right());
            case Expr.NewObject n -> n.args().stream().anyMatch(Normalizer::usesEntity);
            case Expr.ArrayLength a -> usesEntity(a.array());
            case Expr.Coalesce c -> usesEntity(c.first()) || usesEntity(c.second());
            case Expr.Case c -> c.exprs().stream().anyMatch(Normalizer::usesEntity);
            case Expr.Column c -> true;
            case Expr.Root r -> true;
            case Expr.Subquery q -> true;
            case Expr.Aggregate a -> true;
            case Expr.Func f -> true;
            case Expr.ArrayOf a -> a.items().stream().anyMatch(i -> i != null && usesEntity(i));
            case Expr.Lambda l -> l.captured().stream().anyMatch(Normalizer::usesEntity);
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

    private LambdaQLException error(LqlMessage key, Object... args) {
        return key.exceptionAt(where, args);
    }
}
