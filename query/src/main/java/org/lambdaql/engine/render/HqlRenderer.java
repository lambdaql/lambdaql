package org.lambdaql.engine.render;

import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.ir.ArithOp;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.engine.sem.HostEvaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds HQL text from normalized conditions (ROADMAP 3.6 and 3.9).
 * <ul>
 *   <li>Association paths in a condition become {@code JOIN}s (inner join, author decision 2026-11-12).
 *       A path that ends at the id of an association uses the foreign key column without a join.
 *       To find rows without the association, compare the association itself: {@code o.getCustomer() == null}.</li>
 *   <li>Constants written in the lambda code (numbers, strings, {@code true}/{@code false}, {@code null}) are written
 *       into the HQL as literals. Captured and computed values are always bind parameters.
 *       So the value source decides, not the value type (author decision 2026-11-12).</li>
 *   <li>A null-safe compare with a parameter becomes {@code x = :p} or {@code x is null}, depending on the value.
 *       The column is never wrapped in a function, so an index can be used (SARGable, 3.9).</li>
 * </ul>
 * One renderer instance renders one query; it collects joins and parameters.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
 */
public final class HqlRenderer {

    private final EntityModel model;
    private final HostEvaluator evaluator;
    private final Map<String, String> joins = new LinkedHashMap<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private Object[] captured = new Object[0];

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
     * Renders {@code select e0 from Entity e0 [left join ...] [where ...]}.
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
        List<String> parts = new ArrayList<>();
        for (Bound b : wheres) {
            captured = b.captured();
            String s = pred(b.pred());
            if (!s.equals("1=1")) parts.add(wheres.size() > 1 && isComposite(b.pred()) ? "(" + s + ")" : s);
        }
        StringBuilder hql = new StringBuilder("select e0 from ").append(model.entityName(root)).append(" e0");
        joins.forEach((path, alias) -> hql.append(" join ").append(path).append(' ').append(alias));
        if (!parts.isEmpty()) hql.append(" where ").append(String.join(" and ", parts));
        return new RenderedQuery(hql.toString(), new LinkedHashMap<>(params));
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
            Object v = eval(e);
            boolean t = v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0;
            return t == positive ? "1=1" : "1=0";
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
                expr = joins.computeIfAbsent(key, k -> "j" + (joins.size() + 1));
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
