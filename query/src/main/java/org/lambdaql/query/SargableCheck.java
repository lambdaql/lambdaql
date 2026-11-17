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
 *   <li>A function on a column ({@code discount(o.getPrice()) > 100}): LQL-5001 at WARN, unless Oracle has a
 *       function-based index with the same expression.</li>
 *   <li>A function on values only, compared with a column ({@code o.getPrice() > discount(base)}): LQL-5007 at WARN on
 *       MariaDB when the function is NOT DETERMINISTIC.</li>
 *   <li>When the metadata cannot be read (no permission ...): LQL-5008 at INFO; the column case still warns, the
 *       value case is not checked.</li>
 * </ul>
 */
final class SargableCheck {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SargableCheck.class);
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> SKIP_LOGGED = ConcurrentHashMap.newKeySet();

    /** One finding: a function on a column, or a value-side function next to a column. */
    private record Finding(Expr.Func fn, Expr.Column column, Class<?> entity, boolean onColumn) {
    }

    private SargableCheck() {
    }

    static void check(EntityManager em, Pred pred, List<Class<?>> roots, String where) {
        if (org.lambdaql.engine.sem.SqlFunctions.calls(pred).isEmpty()) return;
        if (!LOG.isWarnEnabled()) return;
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
                if (f.onColumn()) {
                    if (product.contains("oracle") && hasFunctionIndex(c, session, f)) continue;
                    LOG.warn("{}", LqlMessage.SARGABLE_COLUMN_FUNCTION.format(f.fn().key(), column, where));
                } else if (product.contains("maria") || product.contains("mysql")) {
                    Boolean det = deterministic(c, f.fn());
                    if (Boolean.FALSE.equals(det)) LOG.warn("{}", LqlMessage.SARGABLE_NONDETERMINISTIC.format(f.fn().key(), where));
                }
            }
        });
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
        String want = normalize(f.fn().sql()) + "(" + normalize(tableColumn[1]) + ")";
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
                    if (expr.equals(want) || (dot > 0 && expr.indexOf('(') > dot && expr.substring(dot + 1).equals(want))) found = true;
                }
            }
        } catch (SQLException e) {
            skipped(f.fn().key(), e);
            return false;
        }
        CACHE.put(key, found);
        return found;
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
