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
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
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

    // ------------------------------------------------------------------ where paths (D54, revised 2026-11-23)

    /**
     * The joins of a where condition and the steps that need a "the association exists" check.
     *
     * @param lefts  keys of the association steps that are left joins; the others are inner joins
     * @param guards for each left step, the column of that association (for {@code e0.customer is not null})
     */
    record WherePlan(Set<String> lefts, Map<String, Expr.Column> guards) {
    }

    /**
     * Chooses the join of every association step of a where condition (ROADMAP D54, revised 2026-11-23).
     * <p>
     * One rule for Java and Kotlin: a step is an inner join unless a row without that association can make the whole
     * condition true; then it is a left join. On such a row:
     * <ul>
     *   <li>a check of the association itself ({@code it.customer == null}) is true;</li>
     *   <li>a comparison through the step written with Kotlin {@code ?.} reads NULL columns (SQL meaning:
     *       {@code j1.name = 'Kim'} is unknown, {@code j1.name is null} is true);</li>
     *   <li>a comparison through the step written with {@code !!}, without a mark, or in Java is false: it is meant
     *       only for rows that have the association. When it could be true on NULL columns ({@code is null},
     *       {@code coalesce}), the renderer adds {@code e0.customer is not null} to it (see {@link #guard}).</li>
     * </ul>
     * Example: {@code it.customer!!.name == "Kim"} is an inner join; {@code it.status == 1 || it.customer!!.name == "Kim"}
     * is a left join (the order without a customer stays when its status is 1); {@code it.customer?.name == "Kim"
     * || it.customer!!.grade > 3} is an inner join (neither side can be true without a customer).
     *
     * @param condition all conditions of the query, joined with AND
     * @param required  tells whether step {@code i} of a column is an {@code optional = false} association (always inner)
     * @return the joins
     */
    static WherePlan plan(Pred condition, java.util.function.BiPredicate<Expr.Column, Integer> required) {
        Pred nnf = nnf(condition, false);
        Map<String, Expr.Column> stepColumns = new java.util.LinkedHashMap<>();
        Set<String> requiredSteps = new LinkedHashSet<>();
        for (Expr.Column c : columns(nnf)) {
            List<String> path = c.path();
            for (int i = 0; i < path.size() - 1; i++) {
                boolean fkOnly = c.idPath() && i == path.size() - 2;
                if (!c.association().get(i) || fkOnly) continue;
                String key = key(c.root(), path.subList(0, i + 1));
                stepColumns.putIfAbsent(key, prefixColumn(c, i + 1));
                if (required.test(c, i)) requiredSteps.add(key);
            }
        }
        List<String> keys = new ArrayList<>(stepColumns.keySet());
        keys.sort(java.util.Comparator.comparingInt(String::length));
        Set<String> lefts = new LinkedHashSet<>();
        for (String key : keys) {
            if (requiredSteps.contains(key)) continue;
            Expr.Column step = stepColumns.get(key);
            boolean parentLeft = false;
            for (int len = 1; len < step.path().size(); len++) parentLeft |= lefts.contains(key(step.root(), step.path().subList(0, len)));
            if (parentLeft || canKeepWithout(nnf, step.root(), step.path())) lefts.add(key);
        }
        Map<String, Expr.Column> guards = new java.util.LinkedHashMap<>();
        for (String key : lefts) guards.put(key, stepColumns.get(key));
        return new WherePlan(lefts, guards);
    }

    /**
     * Adds "the association exists" to the comparisons that need it (see {@link #plan}): a comparison through a
     * left-joined step written without {@code ?.} that could be true on the NULL columns of a missing association.
     * Other parts of the condition are not changed.
     *
     * <p>Example: with a left join of {@code customer}, {@code it.customer!!.name == null} becomes
     * {@code e0.customer is not null and j1.name is null}; {@code it.customer!!.name == "Kim"} stays as it is.
     *
     * @param part one where condition
     * @param plan the joins of the whole condition
     * @return the condition with the checks
     */
    static Pred guard(Pred part, WherePlan plan) {
        if (plan.guards().isEmpty()) return part;
        Pred nnf = nnf(part, false);
        Pred out = addGuards(nnf, plan);
        return out.equals(nnf) ? part : out;
    }

    private static Pred addGuards(Pred p, WherePlan plan) {
        return switch (p) {
            case Pred.And a -> a.items().stream().map(i -> addGuards(i, plan)).reduce(Pred.Bool.TRUE, Pred::and);
            case Pred.Or o -> o.items().stream().map(i -> addGuards(i, plan)).reduce(Pred.Bool.FALSE, Pred::or);
            case Pred.Bool b -> b;
            default -> {
                // the deepest step that needs the check is enough: its column is NULL when a parent is missing too
                Expr.Column need = null;
                for (Map.Entry<String, Expr.Column> g : plan.guards().entrySet()) {
                    Expr.Column step = g.getValue();
                    if (!strict(p, step.root(), step.path())) continue;
                    Value v = sqlValue(p, step.root(), step.path());
                    if ((v == null || v == Value.TRUE) && (need == null || step.path().size() > need.path().size())) need = step;
                }
                yield need == null ? p : Pred.and(Pred.not(new Pred.IsNull(need)), p);
            }
        };
    }

    /** Tells whether a row without the association {@code missing} can make the condition (in NNF) true. */
    private static boolean canKeepWithout(Pred nnf, int root, List<String> missing) {
        List<Pred> free = new ArrayList<>();
        for (Pred lit : literals(nnf)) {
            if (literalValue(lit, root, missing) == null && !free.contains(lit)) free.add(lit);
        }
        if (free.size() > MAX_FREE_ATOMS) return true;
        for (long bits = 0; bits < (1L << free.size()); bits++) {
            Map<Pred, Boolean> values = new HashMap<>();
            for (int i = 0; i < free.size(); i++) values.put(free.get(i), (bits & (1L << i)) != 0);
            if (evalNnf(nnf, values, root, missing) == Value.TRUE) return true;
        }
        return false;
    }

    private static Value evalNnf(Pred p, Map<Pred, Boolean> free, int root, List<String> missing) {
        return switch (p) {
            case Pred.Bool b -> b.value() ? Value.TRUE : Value.FALSE;
            case Pred.And a -> {
                Value out = Value.TRUE;
                for (Pred item : a.items()) {
                    Value v = evalNnf(item, free, root, missing);
                    if (v == Value.FALSE) yield Value.FALSE;
                    if (v == Value.UNKNOWN) out = Value.UNKNOWN;
                }
                yield out;
            }
            case Pred.Or o -> {
                Value out = Value.FALSE;
                for (Pred item : o.items()) {
                    Value v = evalNnf(item, free, root, missing);
                    if (v == Value.TRUE) yield Value.TRUE;
                    if (v == Value.UNKNOWN) out = Value.UNKNOWN;
                }
                yield out;
            }
            default -> {
                Value f = literalValue(p, root, missing);
                yield f != null ? f : free.get(p) ? Value.TRUE : Value.FALSE;
            }
        };
    }

    /**
     * The value of a literal (a single condition, or NOT of one) on a row without the association {@code missing}.
     * A comparison through a step written without {@code ?.} is FALSE when it could be true there: the renderer adds
     * the existence check to it.
     *
     * @return TRUE / FALSE / UNKNOWN, or {@code null} if it does not depend on the association or can be anything
     */
    private static Value literalValue(Pred lit, int root, List<String> missing) {
        Value v = sqlValue(lit, root, missing);
        if (strict(lit, root, missing) && (v == null || v == Value.TRUE)) return Value.FALSE;
        return v;
    }

    /** The SQL value of a literal on a row where the association is missing (its columns are NULL). */
    private static Value sqlValue(Pred lit, int root, List<String> missing) {
        if (lit instanceof Pred.Not n) {
            Value v = fixed(n.operand(), root, missing, true);
            if (v == null) return null;
            return switch (v) {
                case TRUE -> Value.FALSE;
                case FALSE -> Value.TRUE;
                case UNKNOWN -> Value.UNKNOWN;
            };
        }
        return fixed(lit, root, missing, true);
    }

    /**
     * Tells whether the literal reads a column behind {@code missing} through a step written without {@code ?.}
     * ({@code !!}, no mark, or Java) at {@code missing} or after it.
     */
    private static boolean strict(Pred lit, int root, List<String> missing) {
        for (Expr.Column c : columns(lit)) {
            if (c.root() != root || c.path().size() <= missing.size() || !startsWith(c.path(), missing)) continue;
            for (int i = missing.size() - 1; i < c.path().size() - 1; i++) {
                boolean fkOnly = c.idPath() && i == c.path().size() - 2;
                if (c.association().get(i) && !fkOnly && !c.safeStep(i)) return true;
            }
        }
        return false;
    }

    /** The association column of the first {@code len} steps of a path ({@code e0.customer}). */
    private static Expr.Column prefixColumn(Expr.Column c, int len) {
        return new Expr.Column(c.root(), c.path().subList(0, len), c.association().subList(0, len), Object.class,
                false, false, c.safeCall().subList(0, len));
    }

    /** Negation normal form: NOT only directly on a single condition. */
    private static Pred nnf(Pred p, boolean neg) {
        return switch (p) {
            case Pred.Not n -> nnf(n.operand(), !neg);
            case Pred.And a -> a.items().stream().map(i -> nnf(i, neg)).reduce(neg ? Pred.Bool.FALSE : Pred.Bool.TRUE, neg ? Pred::or : Pred::and);
            case Pred.Or o -> o.items().stream().map(i -> nnf(i, neg)).reduce(neg ? Pred.Bool.TRUE : Pred.Bool.FALSE, neg ? Pred::and : Pred::or);
            case Pred.Bool b -> neg ? Pred.not(b) : b;
            default -> neg ? Pred.not(p) : p;
        };
    }

    private static List<Pred> literals(Pred p) {
        List<Pred> out = new ArrayList<>();
        switch (p) {
            case Pred.And a -> a.items().forEach(i -> out.addAll(literals(i)));
            case Pred.Or o -> o.items().forEach(i -> out.addAll(literals(i)));
            case Pred.Bool b -> { }
            default -> out.add(p);
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
            case Pred.Like l -> nullTolerant(l.value());
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
            case Expr.Case c -> true;
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
            case Pred.Like l -> collect(l.value(), out);
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
            case Expr.Case c -> c.exprs().forEach(x -> collect(x, out));
            default -> { }
        }
    }
}
