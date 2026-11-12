package org.lambdaql.engine.render;

import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.ir.ArithOp;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.engine.sem.HostEvaluator;
import org.lambdaql.query.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds HQL text from normalized conditions (ROADMAP 3.6 and 3.9).
 * <ul>
 *   <li>Association paths in a condition become {@code JOIN}s (inner join, author decision 2026-11-12, D36).
 *       A path that ends at the id of an association uses the foreign key column without a join.
 *       To find rows without the association, compare the association itself: {@code o.getCustomer() == null}.
 *       When the condition can be true for a row without the association (a null guard such as
 *       {@code o.getCustomer() == null || o.getCustomer().getName().equals(x)}), that path is a
 *       {@code LEFT JOIN} instead, so the result is the same as in Java ({@link JoinPlanner}).</li>
 *   <li>Constants written in the lambda code (numbers, strings, {@code true}/{@code false}, {@code null}) are written
 *       into the HQL as literals. Captured and computed values are always bind parameters.
 *       So the value source decides, not the value type (author decision 2026-11-12).</li>
 *   <li>A null-safe compare with a parameter becomes {@code x = :p} or {@code x is null}, depending on the value.
 *       The column is never wrapped in a function, so an index can be used (SARGable, 3.9).</li>
 *   <li>Association paths used only for sorting become {@code LEFT JOIN}s, so rows without the association
 *       are not lost (S8, D36). If a condition already joined the same path, that inner join is reused.</li>
 *   <li>Sort keys get an explicit {@code nulls first}/{@code nulls last} unless the key can never be NULL.
 *       {@code asc} puts NULL first and {@code desc} puts NULL last (D16, the same as Java
 *       {@code Comparator.nullsFirst}). Hibernate drops the clause when it is the database default (H2, MariaDB)
 *       and emulates it when the database has no such syntax (MariaDB), so the order is the same everywhere.</li>
 *   <li>A paged query gets {@code <id> asc} as the last key when the keys do not contain the id (S9),
 *       so rows do not move between pages.</li>
 * </ul>
 * One renderer instance renders one query; it collects joins and parameters.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
 */
public final class HqlRenderer {

    private final EntityModel model;
    private final HostEvaluator evaluator;
    private final Map<String, Join> joins = new LinkedHashMap<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private Object[] captured = new Object[0];
    private boolean leftJoins;
    private java.util.Set<String> whereLeftJoins = java.util.Set.of();

    /**
     * A join made for a path.
     *
     * @param alias the alias, for example {@code j1}
     * @param left  {@code true} for {@code left join} (sort-only path), {@code false} for {@code join}
     */
    private record Join(String alias, boolean left) {
    }

    /**
     * Creates a renderer for one query.
     *
     * @param model  entity information (entity names)
     * @param loader class loader used to compute host values
     */
    public HqlRenderer(EntityModel model, ClassLoader loader) {
        this.model = model;
        this.evaluator = new HostEvaluator(loader);
    }

    /**
     * One condition with the captured values of its lambda.
     *
     * @param pred     the normalized condition
     * @param captured the captured values of the lambda this condition came from
     */
    public record Bound(Pred pred, Object[] captured) {
    }

    /**
     * The sort keys with the captured values of their lambda.
     *
     * @param items    the keys in order
     * @param captured the captured values of the sort lambda (used to read run-time directions)
     */
    public record SortBound(List<SortItem> items, Object[] captured) {
    }

    /**
     * Renders {@code select e0 from Entity e0 [join ...] [where ...]}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if a host value cannot be computed or is not allowed (for example a null receiver)
     */
    public RenderedQuery select(Class<?> root, List<Bound> wheres) {
        return select(root, wheres, null, false);
    }

    /**
     * Renders {@code select e0 from Entity e0 [join ...] [where ...] [order by ...]}.
     * Paging values are not part of the HQL; the caller passes them to Hibernate.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-path"}
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @param sort   the sort keys, or {@code null}
     * @param paged  {@code true} if the query uses {@code limit} or {@code offset}; then the id is added as the last key
     *               when the keys do not contain it (S9)
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if a host value cannot be computed, or a run-time direction is null
     */
    public RenderedQuery select(Class<?> root, List<Bound> wheres, SortBound sort, boolean paged) {
        List<Pred> folded = new ArrayList<>();
        Pred all = Pred.Bool.TRUE;
        for (Bound b : wheres) {
            captured = b.captured();
            Pred f = fold(b.pred());
            folded.add(f);
            all = Pred.and(all, f);
        }
        whereLeftJoins = JoinPlanner.leftJoins(all);
        List<String> parts = new ArrayList<>();
        if (all instanceof Pred.Bool b) {
            if (!b.value()) parts.add("1=0");
        } else {
            for (int i = 0; i < wheres.size(); i++) {
                captured = wheres.get(i).captured();
                Pred f = folded.get(i);
                if (f instanceof Pred.Bool) continue;                  // TRUE here (a FALSE makes "all" FALSE)
                String s = pred(f);
                parts.add(wheres.size() > 1 && isComposite(f) ? "(" + s + ")" : s);
            }
        }
        List<String> orders = orderBy(root, sort, paged);
        StringBuilder hql = new StringBuilder("select e0 from ").append(model.entityName(root)).append(" e0");
        joins.forEach((path, j) -> hql.append(j.left() ? " left join " : " join ").append(path).append(' ').append(j.alias()));
        if (!parts.isEmpty()) hql.append(" where ").append(String.join(" and ", parts));
        if (!orders.isEmpty()) hql.append(" order by ").append(String.join(", ", orders));
        return new RenderedQuery(hql.toString(), new LinkedHashMap<>(params));
    }

    // ------------------------------------------------------------------ order by

    private List<String> orderBy(Class<?> root, SortBound sort, boolean paged) {
        List<String> out = new ArrayList<>();
        String id = paged ? model.idName(root).orElse(null) : null;
        boolean hasId = false;
        if (sort != null) {
            captured = sort.captured();
            leftJoins = true;
            for (SortItem item : sort.items()) {
                Direction d = direction(item);
                String key = value(item.key(), null);
                String nulls = sortNonNull(item.key()) ? "" : d.nullsFirst() ? " nulls first" : " nulls last";
                out.add(key + (d.ascending() ? " asc" : " desc") + nulls);
                if (id != null && item.key() instanceof Expr.Column c && c.root() == 0 && c.path().equals(List.of(id))) {
                    hasId = true;
                }
            }
            leftJoins = false;
        }
        if (id != null && !hasId) out.add("e0." + id + " asc");
        return out;
    }

    /**
     * Finds the direction of one key. Run-time values are read now (S10).
     *
     * @param item the key
     * @return the direction with the NULL position
     */
    private Direction direction(SortItem item) {
        if (item.kind() == SortItem.Kind.SORT) {
            if (eval(item.arg()) instanceof Direction d) return d;
            throw new LambdaAnalysisException("정렬 방향(Direction)이 null입니다: " + item);
        }
        boolean asc = item.kind() == SortItem.Kind.ASC;
        if (item.arg() == null) return asc ? Direction.ASC : Direction.DESC;
        Object v = eval(item.arg());
        boolean first = switch (v) {
            case Boolean b -> b;
            case Number n -> n.intValue() != 0;
            case null, default -> throw new LambdaAnalysisException("nullsFirst 값이 null입니다: " + item);
        };
        if (asc) return first ? Direction.ASC_NULLS_FIRST : Direction.ASC_NULLS_LAST;
        return first ? Direction.DESC_NULLS_FIRST : Direction.DESC_NULLS_LAST;
    }

    /**
     * Checks if a sort key can never be NULL, so it needs no {@code nulls} clause.
     * A column reached through an association can be NULL (left join), even if the column itself is not.
     *
     * @param e the key
     * @return {@code true} if the key is never NULL
     */
    private static boolean sortNonNull(Expr e) {
        return switch (e) {
            case Expr.Column c -> c.nonNull() && !c.association().contains(true);
            case Expr.Const c -> !c.isNull();
            case Expr.Arith a -> sortNonNull(a.left()) && sortNonNull(a.right()) && a.op() != ArithOp.DIV && a.op() != ArithOp.REM;
            case Expr.Neg n -> sortNonNull(n.operand());
            default -> false;
        };
    }

    // ------------------------------------------------------------------ folding

    /**
     * Decides the parts that use only captured values and constants, the way Java does: left to right,
     * and an {@code &&} / {@code ||} stops at the first part that decides it.
     * So {@code min == null || o.getPrice() > min} with {@code min == null} gives TRUE, and the compare
     * with the null value is never rendered (Java never runs it either).
     * The parts before the deciding part are still checked, because Java runs them first
     * (for example {@code o.getPrice() > min || min == null} throws, like Java).
     *
     * @param p a normalized condition
     * @return the condition without captured-only parts, or a {@link Pred.Bool}
     */
    private Pred fold(Pred p) {
        return switch (p) {
            case Pred.Cmp c when isHostLike(c.left()) && isHostLike(c.right()) ->
                    bool(javaCompare(c.op(), eval(c.left()), eval(c.right()), c.nullSafe()));
            case Pred.IsNull n when isHostLike(n.operand()) -> bool(eval(n.operand()) == null);
            case Pred.Truth t when isHostLike(t.operand()) -> bool(truthValue(eval(t.operand()), t));
            case Pred.Not n -> {
                Pred x = fold(n.operand());
                yield x instanceof Pred.Bool b ? bool(!b.value()) : x == n.operand() ? n : new Pred.Not(x);
            }
            case Pred.And a -> junction(a.items(), true);
            case Pred.Or o -> junction(o.items(), false);
            default -> p;
        };
    }

    private Pred junction(List<Pred> items, boolean and) {
        List<Pred> kept = new ArrayList<>();
        for (Pred item : items) {
            Pred f = fold(item);
            if (f instanceof Pred.Bool b) {
                if (b.value() == and) continue;         // TRUE in AND, FALSE in OR: no effect
                checkRunnable(kept);                    // Java ran the parts before this one
                return b;                               // FALSE in AND, TRUE in OR: decides
            }
            kept.add(f);
        }
        if (kept.isEmpty()) return bool(and);
        if (kept.size() == 1) return kept.get(0);
        return and ? new Pred.And(kept) : new Pred.Or(kept);
    }

    /**
     * Renders parts only to find errors (for example a null value in {@code >}), then removes
     * the parameters and joins that this made.
     *
     * @param parts the parts Java runs before a deciding part
     */
    private void checkRunnable(List<Pred> parts) {
        if (parts.isEmpty()) return;
        int paramCount = params.size();
        java.util.Set<String> joinKeys = new java.util.HashSet<>(joins.keySet());
        for (Pred part : parts) pred(part);
        new ArrayList<>(params.keySet()).subList(paramCount, params.size()).forEach(params::remove);
        joins.keySet().retainAll(joinKeys);
    }

    private static Pred bool(boolean v) {
        return v ? Pred.Bool.TRUE : Pred.Bool.FALSE;
    }

    private static boolean truthValue(Object v, Pred where) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        throw new LambdaAnalysisException("null Boolean 값을 조건으로 씁니다(Java에서는 NullPointerException): " + where);
    }

    // ------------------------------------------------------------------ conditions

    private String pred(Pred p) {
        return switch (p) {
            case Pred.Bool b -> b.value() ? "1=1" : "1=0";
            case Pred.And a -> join(a.items(), " and ");
            case Pred.Or o -> join(o.items(), " or ");
            case Pred.Cmp c -> cmp(c.op(), c.left(), c.right(), c.nullSafe());
            case Pred.IsNull n -> isNull(n.operand(), true);
            case Pred.Truth t -> truth(t.operand(), true);
            case Pred.In in -> in(in.value(), in.values(), true);
            case Pred.Not n -> switch (n.operand()) {
                case Pred.IsNull x -> isNull(x.operand(), false);
                case Pred.Truth t -> truth(t.operand(), false);
                case Pred.In in -> in(in.value(), in.values(), false);
                case Pred.Cmp c -> cmp(c.op().negate(), c.left(), c.right(), c.nullSafe());
                default -> "not (" + pred(n.operand()) + ")";
            };
        };
    }

    private String join(List<Pred> items, String sep) {
        List<String> out = new ArrayList<>();
        for (Pred item : items) {
            String s = pred(item);
            out.add(isComposite(item) ? "(" + s + ")" : s);
        }
        return String.join(sep, out);
    }

    private static boolean isComposite(Pred p) {
        return p instanceof Pred.And || p instanceof Pred.Or;
    }

    private String cmp(CmpOp op, Expr a, Expr b, boolean nullSafe) {
        if (isHostLike(a) && isHostLike(b)) {
            return javaCompare(op, eval(a), eval(b), nullSafe) ? "1=1" : "1=0";
        }
        if (nullSafe) {
            if (isHostLike(b)) return nullSafeWithValue(op, a, b);
            if (isHostLike(a)) return nullSafeWithValue(op, b, a);
            boolean nonNull = nonNull(a) || nonNull(b);
            String l = value(a, null), r = value(b, null);
            if (nonNull) return l + " " + op.symbol() + " " + r;
            return l + (op == CmpOp.EQ ? " is not distinct from " : " is distinct from ") + r;
        }
        return value(a, peerType(b)) + " " + op.symbol() + " " + value(b, peerType(a));
    }

    private String nullSafeWithValue(CmpOp op, Expr column, Expr host) {
        Object v = eval(host);
        String col = value(column, null);
        if (v == null) return col + (op == CmpOp.EQ ? " is null" : " is not null");
        String param = host instanceof Expr.Const c ? literal(c, peerType(column)) : bind(v, peerType(column));
        if (op == CmpOp.EQ) return col + " = " + param;
        return nonNull(column) ? col + " <> " + param : "(" + col + " <> " + param + " or " + col + " is null)";
    }

    private String isNull(Expr e, boolean positive) {
        if (isHostLike(e)) return (eval(e) == null) == positive ? "1=1" : "1=0";
        return value(e, null) + (positive ? " is null" : " is not null");
    }

    private String truth(Expr e, boolean positive) {
        if (isHostLike(e)) {
            return truthValue(eval(e), new Pred.Truth(e)) == positive ? "1=1" : "1=0";
        }
        String x = value(e, null);
        if (positive) return x + " = true";
        return nonNull(e) ? x + " = false" : x + " is distinct from true";
    }

    private String in(Expr value, Expr values, boolean positive) {
        Object v = eval(values);
        if (v == null) throw new LambdaAnalysisException("IN에 쓸 컬렉션이 null입니다(Java에서는 NullPointerException)");
        Collection<?> c = v instanceof Collection<?> col ? col : List.of((Object[]) v);
        if (c.stream().anyMatch(Objects::isNull)) {
            throw new LambdaAnalysisException("IN에 쓸 컬렉션에 null 원소가 있습니다(ROADMAP D29)");
        }
        if (c.isEmpty()) return positive ? "1=0" : "1=1";
        String x = value(value, null);
        List<Object> list = new ArrayList<>();
        for (Object o : c) list.add(HostEvaluator.convert(o, peerType(value)));
        String param = bindRaw(list);
        if (positive) return x + " in (" + param + ")";
        return nonNull(value) ? x + " not in (" + param + ")" : "(" + x + " is null or " + x + " not in (" + param + "))";
    }

    // ------------------------------------------------------------------ values

    private String value(Expr e, Class<?> peer) {
        return switch (e) {
            case Expr.Column c -> path(c);
            case Expr.Host h -> {
                Object v = eval(h);
                if (v == null) throw new LambdaAnalysisException("null 값과 크기를 비교할 수 없습니다(Java에서는 NullPointerException): " + h);
                yield bind(v, peer);
            }
            case Expr.Const c -> literal(c, peer);
            case Expr.Arith a -> a.op() == ArithOp.REM
                    ? "mod(" + value(a.left(), peer) + ", " + value(a.right(), peer) + ")"
                    : "(" + value(a.left(), peer) + " " + a.op().symbol() + " " + value(a.right(), peer) + ")";
            case Expr.Neg n -> "-(" + value(n.operand(), peer) + ")";
            case Expr.Coalesce c -> "coalesce(" + value(c.first(), peer) + ", " + value(c.second(), peerType(c.first())) + ")";
            default -> throw new LambdaAnalysisException("HQL로 렌더링할 수 없는 값입니다: " + e);
        };
    }

    /**
     * Writes a constant from the lambda code as an HQL literal.
     * The value is converted to the type of the other side first (for example {@code 1} to a {@code Boolean} column
     * gives {@code true}, {@code 100} to a {@code Double} column gives {@code 100.0}).
     * Values that have no safe literal form (NaN, infinity) are bound instead.
     *
     * @param c    the constant
     * @param peer Java type of the other side, or {@code null}
     * @return the HQL literal
     */
    private String literal(Expr.Const c, Class<?> peer) {
        Object v = c.value();
        if (v == null) return "null";
        if (peer == Boolean.class && v instanceof Integer i) return i != 0 ? "true" : "false";
        if (peer == Character.class && v instanceof Integer i) return quote(String.valueOf((char) i.intValue()));
        Object x = HostEvaluator.convert(v, peer);
        return switch (x) {
            case String s -> quote(s);
            case Boolean b -> b ? "true" : "false";
            case Long l -> l + "L";
            case Integer i -> i.toString();
            case Short sh -> sh.toString();
            case Byte b -> b.toString();
            case Double d when Double.isFinite(d) -> java.math.BigDecimal.valueOf(d).toPlainString() + (d == Math.rint(d) && !java.math.BigDecimal.valueOf(d).toPlainString().contains(".") ? ".0" : "");
            case Float f when Float.isFinite(f) -> new java.math.BigDecimal(f.toString()).toPlainString() + "F";
            case java.math.BigDecimal bd -> bd.toPlainString() + "BD";
            case Character ch -> quote(String.valueOf(ch));
            case Enum<?> en -> en.getDeclaringClass().getName() + "." + en.name();
            default -> bind(x, peer);
        };
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private String path(Expr.Column c) {
        String expr = "e" + c.root();
        List<String> names = c.path();
        for (int i = 0; i < names.size(); i++) {
            boolean last = i == names.size() - 1;
            boolean fkOnly = c.idPath() && i == names.size() - 2;
            if (!last && c.association().get(i) && !fkOnly) {
                String key = expr + "." + names.get(i);
                boolean left = leftJoins || whereLeftJoins.contains(JoinPlanner.key(c.root(), names.subList(0, i + 1)));
                expr = joins.computeIfAbsent(key, k -> new Join("j" + (joins.size() + 1), left)).alias();
            } else {
                expr = expr + "." + names.get(i);
            }
        }
        return expr;
    }

    // ------------------------------------------------------------------ helpers

    private String bind(Object v, Class<?> type) {
        return bindRaw(HostEvaluator.convert(v, type));
    }

    private String bindRaw(Object v) {
        String name = "p" + (params.size() + 1);
        params.put(name, v);
        return ":" + name;
    }

    private Object eval(Expr e) {
        if (e instanceof Expr.Host h) {
            Object v = evaluator.eval(h, captured);
            if (v == null && h.nonNullRequired()) {
                throw new LambdaAnalysisException("null 값에 equals를 호출합니다(Java에서는 NullPointerException): " + h.expr());
            }
            return v;
        }
        return evaluator.eval(e, captured);
    }

    private static boolean isHostLike(Expr e) {
        return switch (e) {
            case Expr.Column c -> false;
            case Expr.Arith a -> isHostLike(a.left()) && isHostLike(a.right());
            case Expr.Neg n -> isHostLike(n.operand());
            case Expr.Coalesce c -> isHostLike(c.first()) && isHostLike(c.second());
            default -> true;
        };
    }

    private static boolean nonNull(Expr e) {
        return switch (e) {
            case Expr.Column c -> c.nonNull();
            case Expr.Const c -> !c.isNull();
            default -> false;
        };
    }

    private static Class<?> peerType(Expr e) {
        return e instanceof Expr.Column c ? c.javaType() : e instanceof Expr.Host h ? h.javaType() : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean javaCompare(CmpOp op, Object a, Object b, boolean nullSafe) {
        if (op == CmpOp.EQ) return Objects.equals(norm(a), norm(b));
        if (op == CmpOp.NE) return !Objects.equals(norm(a), norm(b));
        if (a == null || b == null) throw new LambdaAnalysisException("null 값과 크기를 비교할 수 없습니다");
        int c = a instanceof Number x && b instanceof Number y
                ? Double.compare(x.doubleValue(), y.doubleValue())
                : ((Comparable) a).compareTo(b);
        return switch (op) {
            case LT -> c < 0;
            case LE -> c <= 0;
            case GT -> c > 0;
            case GE -> c >= 0;
            default -> false;
        };
    }

    private static Object norm(Object v) {
        return v instanceof Number n && !(v instanceof java.math.BigDecimal) ? (Object) n.doubleValue() : v;
    }
}
