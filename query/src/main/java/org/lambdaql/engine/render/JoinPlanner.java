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

    // ------------------------------------------------------------------ Kotlin safe calls (D54)

    /**
     * How the association steps of a where condition are written (ROADMAP D54).
     *
     * @param safe   steps written with the Kotlin safe call {@code ?.} that may become a left join
     * @param plain  steps written without {@code ?.} ({@code !!}, a non-null Kotlin type, or Java): inner joins
     * @param forced steps written with {@code ?.} that are inner joins anyway: a later step of the same path is an
     *               inner join ({@code it.customer?.address!!.city}), or the association is {@code optional = false}
     * @param names  the property path of every step key, for messages
     */
    record StepUses(Set<String> safe, Set<String> plain, Set<String> forced, Map<String, String> names) {

        /**
         * The steps that are written with {@code ?.} everywhere and are not forced to an inner join.
         *
         * @return keys made by {@link #key(int, List)}
         */
        Set<String> candidates() {
            Set<String> out = new LinkedHashSet<>(safe);
            out.removeAll(plain);
            out.removeAll(forced);
            return out;
        }

        /**
         * The steps written with {@code ?.} in one place and without it in another (WARN LQL-5014).
         *
         * @return keys made by {@link #key(int, List)}
         */
        Set<String> mixed() {
            Set<String> out = new LinkedHashSet<>(safe);
            out.retainAll(plain);
            return out;
        }
    }

    /**
     * Reads how each association step of the condition is written.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt">KotlinQueryTest</a>):
     * {@code it.status == 1 || it.customer?.name == "Kim"} gives {@code safe = [0:customer]}; adding
     * {@code && it.customer!!.grade > 1} also gives {@code plain = [0:customer]}, so the step is mixed.
     *
     * @param condition all conditions of the query, joined with AND
     * @param required  tells whether step {@code i} of a column is an {@code optional = false} association
     * @return the uses of the steps
     */
    static StepUses stepUses(Pred condition, java.util.function.BiPredicate<Expr.Column, Integer> required) {
        Set<String> safe = new LinkedHashSet<>(), plain = new LinkedHashSet<>(), forced = new LinkedHashSet<>();
        Map<String, String> names = new HashMap<>();
        for (Expr.Column c : columns(condition)) {
            List<String> path = c.path();
            boolean laterInner = false;
            for (int i = path.size() - 2; i >= 0; i--) {
                boolean fkOnly = c.idPath() && i == path.size() - 2;
                if (!c.association().get(i) || fkOnly) continue;
                String key = key(c.root(), path.subList(0, i + 1));
                names.put(key, String.join(".", path.subList(0, i + 1)));
                if (!c.safeStep(i)) {
                    plain.add(key);
                    laterInner = true;
                } else if (laterInner || required.test(c, i)) {
                    forced.add(key);
                    laterInner = true;
                } else {
                    safe.add(key);
                }
            }
        }
        return new StepUses(safe, plain, forced, names);
    }

    /**
     * Finds the {@code ?.} steps that must be left joins: those where a row without the association can make the
     * whole condition true. Other {@code ?.} steps stay inner joins, which gives the same rows (ROADMAP D54).
     * On a row without the association, a null check of the association or of a column behind it is TRUE (the left
     * join gives NULL there), a condition with {@code coalesce}, a database function or a null-safe comparison on such a
     * column can be anything, and other conditions on such a column are UNKNOWN. A child step of a left join is a
     * left join too.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/kotlin/org/lambdaql/query/KotlinQueryTest.kt">KotlinQueryTest</a>):
     * {@code it.customer?.name == "Kim"} gives no left join; {@code it.status == 1 || it.customer?.name == "Kim"} and
     * {@code it.customer?.name == null} give {@code 0:customer}.
     *
     * @param condition  all conditions of the query, joined with AND
     * @param candidates the steps from {@link StepUses#candidates()}
     * @return the keys of the steps that need a left join
     */
    static Set<String> safeCallLeftJoins(Pred condition, Set<String> candidates) {
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(java.util.Comparator.comparingInt(String::length));
        Set<String> out = new LinkedHashSet<>();
        for (String key : sorted) {
            int colon = key.indexOf(':');
            int root = Integer.parseInt(key.substring(0, colon));
            List<String> names = List.of(key.substring(colon + 1).split("\\."));
            boolean parentLeft = false;
            for (int len = 1; len < names.size(); len++) parentLeft |= out.contains(key(root, names.subList(0, len)));
            if (parentLeft || canKeepMissing(condition, root, names, true)) out.add(key);
        }
        return out;
    }

    // ------------------------------------------------------------------ check

    private static boolean canKeepMissing(Pred condition, int root, List<String> missing) {
        return canKeepMissing(condition, root, missing, false);
    }

    /**
     * Tells whether some row without the association {@code missing} makes the condition true.
     *
     * @param kotlin {@code false}: the Java null guard rule (columns behind the association are never read);
     *               {@code true}: the left join rule of D54 (columns behind the association are NULL)
     */
    private static boolean canKeepMissing(Pred condition, int root, List<String> missing, boolean kotlin) {
        List<Pred> free = new ArrayList<>();
        for (Pred atom : atoms(condition)) {
            if (fixed(atom, root, missing, kotlin) == null && !free.contains(atom)) free.add(atom);
        }
        if (free.size() > MAX_FREE_ATOMS) return true;
        for (long bits = 0; bits < (1L << free.size()); bits++) {
            Map<Pred, Boolean> values = new HashMap<>();
            for (int i = 0; i < free.size(); i++) values.put(free.get(i), (bits & (1L << i)) != 0);
            if (eval(condition, values, root, missing, kotlin) == Value.TRUE) return true;
        }
        return false;
    }

    private enum Value { TRUE, FALSE, UNKNOWN }

    private static Value eval(Pred p, Map<Pred, Boolean> free, int root, List<String> missing, boolean kotlin) {
        return switch (p) {
            case Pred.Bool b -> b.value() ? Value.TRUE : Value.FALSE;
            case Pred.Not n -> switch (eval(n.operand(), free, root, missing, kotlin)) {
                case TRUE -> Value.FALSE;
                case FALSE -> Value.TRUE;
                case UNKNOWN -> Value.UNKNOWN;
            };
            case Pred.And a -> {
                Value out = Value.TRUE;
                for (Pred item : a.items()) {
                    Value v = eval(item, free, root, missing, kotlin);
                    if (v == Value.FALSE) yield Value.FALSE;
                    if (v == Value.UNKNOWN) out = Value.UNKNOWN;
                }
                yield out;
            }
            case Pred.Or o -> {
                Value out = Value.FALSE;
                for (Pred item : o.items()) {
                    Value v = eval(item, free, root, missing, kotlin);
                    if (v == Value.TRUE) yield Value.TRUE;
                    if (v == Value.UNKNOWN) out = Value.UNKNOWN;
                }
                yield out;
            }
            default -> {
                Value f = fixed(p, root, missing, kotlin);
                yield f != null ? f : free.get(p) ? Value.TRUE : Value.FALSE;
            }
        };
    }

    /**
     * Gives the value of a single condition on a row where the association {@code missing} is NULL
     * (and its parent exists).
     *
     * @param kotlin {@code true} for the left join rule of D54: a column behind the association is NULL, so its
     *               {@code is null} is TRUE, and a condition that can be true on NULL ({@code coalesce}, a database
     *               function, a null-safe comparison) is free
     * @return TRUE / FALSE / UNKNOWN, or {@code null} if the condition does not depend on the association
     *         (or, with {@code kotlin}, can be anything)
     */
    private static Value fixed(Pred atom, int root, List<String> missing, boolean kotlin) {
        if (atom instanceof Pred.IsNull n && n.operand() instanceof Expr.Column c && c.root() == root
                && c.association().get(c.path().size() - 1)) {
            if (c.path().equals(missing)) return Value.TRUE;               // o.customer is null
            if (startsWith(missing, c.path())) return Value.FALSE;        // the parent exists
        }
        boolean behind = false;
        for (Expr.Column c : columns(atom)) {
            if (c.root() == root && c.path().size() > missing.size() && startsWith(c.path(), missing)) behind = true;
        }
        if (!behind) return null;
        if (!kotlin) return Value.UNKNOWN;                                // j1.name: never read in Java
        if (nullTolerant(atom)) return null;                              // coalesce(j1.name, 'x') = 'x' can be true
        if (atom instanceof Pred.IsNull) return Value.TRUE;               // j1.name is null: the left join gives NULL
        return Value.UNKNOWN;                                             // j1.name = 'Kim' on NULL
    }

    /**
     * A condition that can be true when a column in it is NULL: coalesce, a database function, or a null-safe compare
     * of two entity values ({@code is not distinct from}). A null-safe compare with a captured value or a constant is
     * written as {@code =} or {@code <>} (D48), which is never true on NULL.
     */
    private static boolean nullTolerant(Pred atom) {
        return switch (atom) {
            case Pred.Cmp c -> (c.nullSafe() && !value(c.left()) && !value(c.right())) || nullTolerant(c.left()) || nullTolerant(c.right());
            case Pred.IsNull n -> nullTolerant(n.operand());
            case Pred.Truth t -> nullTolerant(t.operand());
            case Pred.In in -> nullTolerant(in.value());
            default -> true;
        };
    }

    /** A value computed in Java or written in the lambda (not an entity value). */
    private static boolean value(Expr e) {
        return e instanceof Expr.Host || e instanceof Expr.Const;
    }

    private static boolean nullTolerant(Expr e) {
        return switch (e) {
            case Expr.Coalesce c -> true;
            case Expr.Func f -> true;
            case Expr.Arith a -> nullTolerant(a.left()) || nullTolerant(a.right());
            case Expr.Neg n -> nullTolerant(n.operand());
            default -> false;
        };
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
