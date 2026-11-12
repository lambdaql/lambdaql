package org.lambdaql.engine.render;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chooses {@code join} or {@code left join} for the association paths of a condition (ROADMAP D36).
 * <p>
 * The default is {@code join} (inner): {@code o.getCustomer().getName().equals(x)} cannot be true for an order
 * without a customer (Java would even throw {@code NullPointerException}), so dropping those rows is right.
 * <p>
 * But a null guard can keep those rows: {@code o.getCustomer() == null || o.getCustomer().getName().equals(x)}
 * is {@code true} in Java for an order without a customer. An inner join would lose that order.
 * So a path becomes {@code left join} when the condition can be true for a row where that association is missing.
 * <p>
 * How it is checked: for a missing association, the null check of the association ({@code o.customer is null})
 * is true, and conditions on columns behind it ({@code j1.name = :p1}) are "unknown", because Java never reads them
 * on that row without an exception. All other conditions can be true or false. If some choice of them makes the
 * whole condition true (three-valued logic), the row must stay, so the join must be a left join.
 * When a missing parent association can keep rows, its child joins are left joins too.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="null-guard"}
 */
final class JoinPlanner {

    /** Above this number of free conditions the check is skipped and left join is used (always correct). */
    private static final int MAX_FREE_ATOMS = 12;

    private JoinPlanner() {
    }

    /**
     * Finds the association paths that need {@code left join}.
     *
     * @param condition all conditions of the query, joined with AND
     * @return keys of the paths that need a left join, made by {@link #key(int, List)}
     */
    static Set<String> leftJoins(Pred condition) {
        Set<List<Object>> prefixes = new LinkedHashSet<>();
        for (Expr.Column c : columns(condition)) prefixes.addAll(joinPrefixes(c));
        Set<String> out = new LinkedHashSet<>();
        Map<List<Object>, Boolean> keeps = new HashMap<>();
        for (List<Object> p : prefixes) {
            int root = (Integer) p.get(0);
            @SuppressWarnings("unchecked") List<String> names = (List<String>) p.get(1);
            boolean left = false;
            for (int len = 1; len <= names.size() && !left; len++) {
                List<String> part = names.subList(0, len);
                if (!prefixes.contains(List.of(root, part))) continue;
                left = keeps.computeIfAbsent(List.of(root, part), k -> canKeepMissing(condition, root, part));
            }
            if (left) out.add(key(root, names));
        }
        return out;
    }

    /**
     * Makes the key of a join path.
     *
     * @param root  the root entity index
     * @param names the property names up to and including the association
     * @return for example {@code 0:customer.address}
     */
    static String key(int root, List<String> names) {
        return root + ":" + String.join(".", names);
    }

    // ------------------------------------------------------------------ check

    private static boolean canKeepMissing(Pred condition, int root, List<String> missing) {
        List<Pred> free = new ArrayList<>();
        for (Pred atom : atoms(condition)) {
            if (fixed(atom, root, missing) == null && !free.contains(atom)) free.add(atom);
        }
        if (free.size() > MAX_FREE_ATOMS) return true;
        for (long bits = 0; bits < (1L << free.size()); bits++) {
            Map<Pred, Boolean> values = new HashMap<>();
            for (int i = 0; i < free.size(); i++) values.put(free.get(i), (bits & (1L << i)) != 0);
            if (eval(condition, values, root, missing) == Value.TRUE) return true;
        }
        return false;
    }

    private enum Value { TRUE, FALSE, UNKNOWN }

    private static Value eval(Pred p, Map<Pred, Boolean> free, int root, List<String> missing) {
        return switch (p) {
            case Pred.Bool b -> b.value() ? Value.TRUE : Value.FALSE;
            case Pred.Not n -> switch (eval(n.operand(), free, root, missing)) {
                case TRUE -> Value.FALSE;
                case FALSE -> Value.TRUE;
                case UNKNOWN -> Value.UNKNOWN;
            };
            case Pred.And a -> {
                Value out = Value.TRUE;
                for (Pred item : a.items()) {
                    Value v = eval(item, free, root, missing);
                    if (v == Value.FALSE) yield Value.FALSE;
                    if (v == Value.UNKNOWN) out = Value.UNKNOWN;
                }
                yield out;
            }
            case Pred.Or o -> {
                Value out = Value.FALSE;
                for (Pred item : o.items()) {
                    Value v = eval(item, free, root, missing);
                    if (v == Value.TRUE) yield Value.TRUE;
                    if (v == Value.UNKNOWN) out = Value.UNKNOWN;
                }
                yield out;
            }
            default -> {
                Value f = fixed(p, root, missing);
                yield f != null ? f : free.get(p) ? Value.TRUE : Value.FALSE;
            }
        };
    }

    /**
     * Gives the value of a single condition on a row where the association {@code missing} is NULL
     * (and its parent exists).
     *
     * @return TRUE / FALSE / UNKNOWN, or {@code null} if the condition does not depend on the association
     */
    private static Value fixed(Pred atom, int root, List<String> missing) {
        if (atom instanceof Pred.IsNull n && n.operand() instanceof Expr.Column c && c.root() == root
                && c.association().get(c.path().size() - 1)) {
            if (c.path().equals(missing)) return Value.TRUE;               // o.customer is null
            if (startsWith(missing, c.path())) return Value.FALSE;        // the parent exists
        }
        for (Expr.Column c : columns(atom)) {
            if (c.root() == root && c.path().size() > missing.size() && startsWith(c.path(), missing)) {
                return Value.UNKNOWN;                                    // j1.name: never read in Java
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private static List<List<Object>> joinPrefixes(Expr.Column c) {
        List<List<Object>> out = new ArrayList<>();
        List<String> names = c.path();
        for (int i = 0; i < names.size() - 1; i++) {
            boolean fkOnly = c.idPath() && i == names.size() - 2;
            if (c.association().get(i) && !fkOnly) out.add(List.of(c.root(), List.copyOf(names.subList(0, i + 1))));
        }
        return out;
    }

    private static boolean startsWith(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    private static List<Pred> atoms(Pred p) {
        List<Pred> out = new ArrayList<>();
        collectAtoms(p, out);
        return out;
    }

    private static void collectAtoms(Pred p, List<Pred> out) {
        switch (p) {
            case Pred.And a -> a.items().forEach(i -> collectAtoms(i, out));
            case Pred.Or o -> o.items().forEach(i -> collectAtoms(i, out));
            case Pred.Not n -> collectAtoms(n.operand(), out);
            case Pred.Bool b -> { }
            default -> out.add(p);
        }
    }

    private static List<Expr.Column> columns(Pred p) {
        List<Expr.Column> out = new ArrayList<>();
        switch (p) {
            case Pred.And a -> a.items().forEach(i -> out.addAll(columns(i)));
            case Pred.Or o -> o.items().forEach(i -> out.addAll(columns(i)));
            case Pred.Not n -> out.addAll(columns(n.operand()));
            case Pred.Bool b -> { }
            case Pred.Cmp c -> {
                collect(c.left(), out);
                collect(c.right(), out);
            }
            case Pred.IsNull n -> collect(n.operand(), out);
            case Pred.Truth t -> collect(t.operand(), out);
            case Pred.In in -> collect(in.value(), out);
        }
        return out;
    }

    private static void collect(Expr e, List<Expr.Column> out) {
        switch (e) {
            case Expr.Column c -> out.add(c);
            case Expr.Arith a -> {
                collect(a.left(), out);
                collect(a.right(), out);
            }
            case Expr.Neg n -> collect(n.operand(), out);
            case Expr.Coalesce c -> {
                collect(c.first(), out);
                collect(c.second(), out);
            }
            default -> { }
        }
    }
}
