package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.i18n.LqlMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warns about database functions that keep a condition from using an index (ROADMAP M11, PR8, SARGable rule 3.9).
 * Not public API. It runs once per condition lambda, when the analysis goes into the cache.
 * <ul>
 *   <li>A function on a column ({@code discount(o.getPrice()) > 100}): LQL-5001 at WARN, unless Oracle or PostgreSQL
 *       has a function-based (expression) index with the same expression.</li>
 *   <li>A function on values only, compared with a column ({@code o.getPrice() > discount(base)}): LQL-5007 at WARN
 *       when the function is NOT DETERMINISTIC (MariaDB, MySQL) or VOLATILE (PostgreSQL). Measured: such a function is
 *       computed for every row and the index on the column is not used (MariaDB 10.11, MySQL 8.4, PostgreSQL 17).</li>
 *   <li>When the metadata cannot be read (no permission ...): LQL-5008 at INFO; the column case still warns, the
 *       value case is not checked.</li>
 * </ul>
 */
final class SargableCheck {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SargableCheck.class);
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> SKIP_LOGGED = ConcurrentHashMap.newKeySet();
    /** Oracle conversions that may wrap the column in a function-based index expression (numeric columns). */
    private static final List<String> IMPLICIT_CONVERSIONS = List.of("TO_NUMBER", "TO_BINARY_DOUBLE", "TO_BINARY_FLOAT");

    /** One finding: a function on a column, or a value-side function next to a column. */
    private record Finding(Expr.Func fn, Expr.Column column, Class<?> entity, boolean onColumn) {
    }

    private SargableCheck() {
    }

    static void check(EntityManager em, Pred pred, List<Class<?>> roots, String where, SargableMode mode) {
        if (mode == SargableMode.OFF) return;
        boolean strict = mode == SargableMode.STRICT;
        arithmetic(pred, where, strict);                 // needs no database
        if (org.lambdaql.engine.sem.SqlFunctions.calls(pred).isEmpty()) return;
        if (!strict && !LOG.isWarnEnabled()) return;
        Map<Integer, Class<?>> rootTypes = new HashMap<>();
        for (int i = 0; i < roots.size(); i++) rootTypes.put(i, roots.get(i));
        List<Finding> findings = new ArrayList<>();
        walk(pred, rootTypes, findings);
        if (findings.isEmpty()) return;
        Session session = em.unwrap(Session.class);
        session.doWork(c -> {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            for (Finding f : findings) {
                String column = String.join(".", f.column().path());
                boolean postgres = product.contains("postgresql");
                if (f.onColumn()) {
                    if (product.contains("oracle") && hasFunctionIndex(c, session, f)) continue;
                    if (postgres && hasExpressionIndex(c, session, f)) continue;
                    report(LqlMessage.SARGABLE_COLUMN_FUNCTION.format(f.fn().key(), column, where), strict);
                } else if (product.contains("maria") || product.contains("mysql")) {
                    Boolean det = deterministic(c, f.fn());
                    if (Boolean.FALSE.equals(det)) report(LqlMessage.SARGABLE_NONDETERMINISTIC.format(f.fn().key(), where), strict);
                } else if (postgres) {
                    if (Boolean.TRUE.equals(volatileFunction(c, f.fn()))) {
                        report(LqlMessage.SARGABLE_NONDETERMINISTIC.format(f.fn().key(), where), strict);
                    }
                }
            }
        });
    }

    /** WARN in {@link SargableMode#WARN}; in {@link SargableMode#STRICT} the analysis fails instead. */
    private static void report(String finding, boolean strict) {
        if (strict) throw new org.lambdaql.NonSargableException(finding);
        LOG.warn("{}", finding);
    }

    // ------------------------------------------------------------------ arithmetic on columns

    /**
     * Arithmetic on a column (ROADMAP PR8, 2026-11-19): one column with values only ({@code o.getQty() * 2 > n})
     * can move to the value side ({@code o.getQty() > n / 2.0}: a decimal division keeps the meaning, {@code n / 2}
     * would be integer division), so it is reported like a function (LQL-5012, WARN or STRICT). Arithmetic between columns or with {@code %} cannot move, so it is only a DEBUG note (LQL-5013).
     */
    private static void arithmetic(Pred p, String where, boolean strict) {
        switch (p) {
            case Pred.And a -> a.items().forEach(i -> arithmetic(i, where, strict));
            case Pred.Or o -> o.items().forEach(i -> arithmetic(i, where, strict));
            case Pred.Not n -> arithmetic(n.operand(), where, strict);
            case Pred.Cmp c -> {
                caseSide(c.left(), where, strict);
                caseSide(c.right(), where, strict);
                arithmeticSide(c.left(), c.right(), where, strict);
                arithmeticSide(c.right(), c.left(), where, strict);
                subqueryArithmetic(c.left(), where, strict);
                subqueryArithmetic(c.right(), where, strict);
            }
            case Pred.In in -> {
                caseSide(in.value(), where, strict);
                if (computed(in.value())) expression(in.value(), where);
                subqueryArithmetic(in.values(), where, strict);
            }
            case Pred.Truth t -> subqueryArithmetic(t.operand(), where, strict);
            case Pred.Like l -> {
                // Kotlin ignoreCase = true: lower(column) like ..., an ordinary index on the column is not used
                if (l.ignoreCase()) {
                    List<String> names = distinct(columns(l.value())).stream().map(c -> String.join(".", c.path())).toList();
                    report(LqlMessage.SARGABLE_COLUMN_EXPRESSION.format(String.join(", ", names), where), strict);
                }
            }
            case Pred.IsNull n -> { }
            case Pred.Bool b -> { }
        }
    }

    /**
     * A compared value chosen by conditions on columns ({@code case when e0.active = true then 1 else 2 end},
     * ROADMAP D62): the database computes it per row, so an index on those columns is not used. Reported at WARN
     * (LQL-5013), or the analysis fails in {@link SargableMode#STRICT}.
     *
     * <p>Example: {@code o.getStatus() == (o.isActive() ? 1 : 2)} warns about {@code active}.
     *
     * @param side   one side of a comparison
     * @param where  the lambda, for the message
     * @param strict whether the analysis fails instead
     */
    private static void caseSide(Expr side, String where, boolean strict) {
        Expr e = side;
        while (e instanceof Expr.Convert c) e = c.operand();
        if (!(e instanceof Expr.Case)) return;
        List<String> names = distinct(columns(e)).stream().map(c -> String.join(".", c.path())).toList();
        if (!names.isEmpty()) report(LqlMessage.SARGABLE_COLUMN_EXPRESSION.format(String.join(", ", names), where), strict);
    }

    private static void subqueryArithmetic(Expr e, String where, boolean strict) {
        if (e instanceof Expr.Subquery q) q.wheres().forEach(w -> arithmetic(w, where, strict));
    }

    private static void arithmeticSide(Expr self, Expr other, String where, boolean strict) {
        if (!computed(self)) return;
        List<Expr.Column> cols = distinct(columns(self));
        if (cols.size() == 1 && columns(other).isEmpty() && movable(self)) {
            report(LqlMessage.SARGABLE_COLUMN_ARITHMETIC.format(String.join(".", cols.get(0).path()), where), strict);
        } else {
            expression(self, where);
        }
    }

    private static void expression(Expr e, String where) {
        if (!LOG.isDebugEnabled()) return;
        List<String> names = distinct(columns(e)).stream().map(c -> String.join(".", c.path())).toList();
        LOG.debug("{}", LqlMessage.SARGABLE_COLUMN_EXPRESSION.format(String.join(", ", names), where));
    }

    /** Arithmetic with a column inside and no database function (a function is reported on its own). */
    private static boolean computed(Expr e) {
        return (e instanceof Expr.Arith || e instanceof Expr.Neg) && !columns(e).isEmpty() && !hasFunc(e);
    }

    /** Only + - * / and negation: an operation that a reader can undo on the value side. */
    private static boolean movable(Expr e) {
        return switch (e) {
            case Expr.Arith a -> a.op() != org.lambdaql.engine.ir.ArithOp.REM && movable(a.left()) && movable(a.right());
            case Expr.Neg n -> movable(n.operand());
            default -> true;
        };
    }

    private static boolean hasFunc(Expr e) {
        return switch (e) {
            case Expr.Func f -> true;
            case Expr.Arith a -> hasFunc(a.left()) || hasFunc(a.right());
            case Expr.Neg n -> hasFunc(n.operand());
            case Expr.Coalesce c -> hasFunc(c.first()) || hasFunc(c.second());
            case Expr.Case c -> c.exprs().stream().anyMatch(SargableCheck::hasFunc);
            default -> false;
        };
    }

    private static List<Expr.Column> distinct(List<Expr.Column> cols) {
        List<Expr.Column> out = new ArrayList<>();
        for (Expr.Column c : cols) {
            if (out.stream().noneMatch(x -> x.root() == c.root() && x.path().equals(c.path()))) out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------------ finding the patterns

    private static void walk(Pred p, Map<Integer, Class<?>> roots, List<Finding> out) {
        switch (p) {
            case Pred.And a -> a.items().forEach(i -> walk(i, roots, out));
            case Pred.Or o -> o.items().forEach(i -> walk(i, roots, out));
            case Pred.Not n -> walk(n.operand(), roots, out);
            case Pred.Cmp c -> {
                side(c.left(), c.right(), roots, out);
                side(c.right(), c.left(), roots, out);
            }
            case Pred.Truth t -> columnFunctions(t.operand(), roots, out);
            case Pred.IsNull n -> columnFunctions(n.operand(), roots, out);
            case Pred.In in -> {
                columnFunctions(in.value(), roots, out);
                subqueries(in.values(), roots, out);
            }
            case Pred.Like l -> columnFunctions(l.value(), roots, out);
            case Pred.Bool b -> { }
        }
    }

    private static void side(Expr self, Expr other, Map<Integer, Class<?>> roots, List<Finding> out) {
        columnFunctions(self, roots, out);
        subqueries(self, roots, out);
        if (self instanceof Expr.Func f && columns(f).isEmpty() && other instanceof Expr.Column c) {
            out.add(new Finding(f, c, roots.get(c.root()), false));
        }
    }

    /** Functions anywhere in a value that have a column inside. */
    private static void columnFunctions(Expr e, Map<Integer, Class<?>> roots, List<Finding> out) {
        switch (e) {
            case Expr.Func f -> {
                List<Expr.Column> cols = columns(f);
                if (!cols.isEmpty()) out.add(new Finding(f, cols.get(0), roots.get(cols.get(0).root()), true));
            }
            case Expr.Arith a -> {
                columnFunctions(a.left(), roots, out);
                columnFunctions(a.right(), roots, out);
            }
            case Expr.Neg n -> columnFunctions(n.operand(), roots, out);
            case Expr.Coalesce c -> {
                columnFunctions(c.first(), roots, out);
                columnFunctions(c.second(), roots, out);
            }
            case Expr.Case c -> c.exprs().forEach(x -> columnFunctions(x, roots, out));
            default -> { }
        }
    }

    private static void subqueries(Expr e, Map<Integer, Class<?>> roots, List<Finding> out) {
        if (e instanceof Expr.Subquery q) {
            Map<Integer, Class<?>> inner = new HashMap<>(roots);
            inner.put(q.root(), q.entity());
            for (Pred w : q.wheres()) walk(w, inner, out);
        }
    }

    private static List<Expr.Column> columns(Expr e) {
        List<Expr.Column> out = new ArrayList<>();
        collect(e, out);
        return out;
    }

    private static void collect(Expr e, List<Expr.Column> out) {
        switch (e) {
            case Expr.Column c -> out.add(c);
            case Expr.Func f -> f.args().forEach(a -> collect(a, out));
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

    // ------------------------------------------------------------------ database metadata

    /** Oracle: is there a function-based index {@code FN(COLUMN)} on the table of the column? */
    private static boolean hasFunctionIndex(Connection c, Session session, Finding f) {
        if (f.fn().args().size() != 1 || !(f.fn().args().get(0) instanceof Expr.Column col) || col.association().contains(true)
                || f.entity() == null) {
            return false;
        }
        String[] tableColumn = HibernateFunctions.tableAndColumn(session.getSessionFactory(), f.entity(), String.join(".", col.path()));
        if (tableColumn == null) return false;
        String fn = normalize(f.fn().sql());
        String column = normalize(tableColumn[1]);
        String want = fn + "(" + column + ")";
        // Oracle stores an implicit type conversion in the index expression when the column type is not the type of
        // the function argument, for example FN(TO_NUMBER("PRICE")) for a BINARY_DOUBLE column (Hibernate 7 maps
        // Double to BINARY_DOUBLE) and a NUMBER argument. The query FN(price) uses that index too (measured with
        // EXPLAIN PLAN on Oracle 23.26.3: INDEX RANGE SCAN without a hint).
        Set<String> accepted = new java.util.HashSet<>();
        accepted.add(want);
        for (String conversion : IMPLICIT_CONVERSIONS) accepted.add(fn + "(" + conversion + "(" + column + "))");
        String key = "ix|" + tableColumn[0] + "|" + want;
        Boolean hit = CACHE.get(key);
        if (hit != null) return hit;
        boolean found = false;
        try (PreparedStatement ps = c.prepareStatement("select column_expression from user_ind_expressions where table_name = ?")) {
            ps.setString(1, tableColumn[0].toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String expr = normalize(String.valueOf(rs.getString(1)));
                    int dot = expr.indexOf('.');
                    if (accepted.contains(expr) || (dot > 0 && expr.indexOf('(') > dot && accepted.contains(expr.substring(dot + 1)))) {
                        found = true;
                    }
                }
            }
        } catch (SQLException e) {
            skipped(f.fn().key(), e);
            return false;
        }
        CACHE.put(key, found);
        return found;
    }

    /**
     * PostgreSQL: is there an expression index {@code fn(column)} on the table of the column?
     * {@code pg_get_expr} gives the expression as PostgreSQL stores it, for example {@code sq_fn_discount(price)} or,
     * when the argument type differs, {@code sq_fn_discount((price)::numeric)}. Casts and parentheses around the column
     * are ignored; a schema in front of the function name is ignored too.
     */
    private static boolean hasExpressionIndex(Connection c, Session session, Finding f) {
        if (f.fn().args().size() != 1 || !(f.fn().args().get(0) instanceof Expr.Column col) || col.association().contains(true)
                || f.entity() == null) {
            return false;
        }
        String[] tableColumn = HibernateFunctions.tableAndColumn(session.getSessionFactory(), f.entity(), String.join(".", col.path()));
        if (tableColumn == null) return false;
        String fn = normalize(f.fn().sql());
        fn = fn.substring(fn.lastIndexOf('.') + 1);
        String want = fn + "(" + normalize(tableColumn[1]) + ")";
        String key = "pgix|" + tableColumn[0] + "|" + want;
        Boolean hit = CACHE.get(key);
        if (hit != null) return hit;
        boolean found = false;
        try (PreparedStatement ps = c.prepareStatement("select pg_get_expr(i.indexprs, i.indrelid) from pg_index i"
                + " join pg_class t on t.oid = i.indrelid where t.relname = ? and i.indexprs is not null"
                + " and pg_table_is_visible(t.oid)")) {
            // unquoted names are folded to lower case by PostgreSQL
            ps.setString(1, tableColumn[0].toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String expr = normalize(String.valueOf(rs.getString(1))).replaceAll("::[A-Z_ ]+(\\([0-9, ]*\\))?", "");
                    expr = expr.replaceAll("\\(\\(([A-Z0-9_]+)\\)\\)", "($1)");
                    int open = expr.indexOf('(');
                    int dot = expr.lastIndexOf('.', open);
                    if (dot >= 0) expr = expr.substring(dot + 1);
                    if (expr.equals(want)) found = true;
                }
            }
        } catch (SQLException e) {
            skipped(f.fn().key(), e);
            return false;
        }
        CACHE.put(key, found);
        return found;
    }

    /** PostgreSQL: is the function VOLATILE (the default)? Null when the metadata cannot be read. */
    private static Boolean volatileFunction(Connection c, Expr.Func fn) {
        String key = "pgvol|" + fn.sql();
        Boolean hit = CACHE.get(key);
        if (hit != null) return hit;
        String name = fn.sql();
        try (PreparedStatement ps = c.prepareStatement("select p.provolatile from pg_proc p where p.proname = ?"
                + " and pg_function_is_visible(p.oid)")) {
            ps.setString(1, name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                Boolean result = null;
                // overloads: warn only when every visible function of that name is VOLATILE
                while (rs.next()) result = (result == null || result) && "v".equals(rs.getString(1));
                if (result == null) return null;
                CACHE.put(key, result);
                return result;
            }
        } catch (SQLException e) {
            skipped(fn.key(), e);
            return null;
        }
    }

    /** MariaDB: is the function DETERMINISTIC? Null when the metadata cannot be read. */
    private static Boolean deterministic(Connection c, Expr.Func fn) {
        String key = "det|" + fn.sql();
        Boolean hit = CACHE.get(key);
        if (hit != null) return hit;
        try (PreparedStatement ps = c.prepareStatement("select is_deterministic from information_schema.routines"
                + " where routine_schema = database() and routine_name = ? and routine_type = 'FUNCTION'")) {
            String name = fn.sql();
            ps.setString(1, name.substring(name.lastIndexOf('.') + 1));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                boolean det = "YES".equalsIgnoreCase(rs.getString(1));
                CACHE.put(key, det);
                return det;
            }
        } catch (SQLException e) {
            skipped(fn.key(), e);
            return null;
        }
    }

    private static void skipped(String fn, SQLException e) {
        if (LOG.isInfoEnabled() && SKIP_LOGGED.add(fn)) LOG.info("{}", LqlMessage.SARGABLE_CHECK_SKIPPED.format(fn, e.getMessage()));
    }

    private static String normalize(String s) {
        return s.replace("\"", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }
}
