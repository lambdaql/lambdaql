package org.lambdaql.query;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.i18n.LqlMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outside sorts allowed for one entity (ROADMAP D43). A column is given as a lambda, so the compiler checks it:
 * {@code allow(o -> o.getPrice())} allows the name {@code price}; {@code alias("newest", o -> o.getCreateAt())} lets the
 * outside name {@code newest} sort by {@code createAt} (the path itself is then not an outside name).
 * A path through an association ({@code o -> o.getCustomer().getName()}, name {@code customer.name}) is allowed only
 * when it is listed.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
 * {@snippet class="org.lambdaql.query.SortPolicyTest" region="rules"}
 *
 * @param <T> the entity type
 */
public final class SortRules<T> {

    private record Entry(String name, LambdaRef key, String path) {
    }

    private final Class<T> type;
    private final List<Entry> entries = new ArrayList<>();
    private volatile Map<String, String> resolved;

    private SortRules(Class<T> type) {
        this.type = type;
    }

    /**
     * Starts the rules of one entity.
     *
     * @param type the entity class
     * @param <T>  the entity type
     * @return empty rules
     */
    public static <T> SortRules<T> of(Class<T> type) {
        return new SortRules<>(type);
    }

    /**
     * Allows a column under its own path name: {@code allow(o -> o.getPrice())} allows {@code price}.
     *
     * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
     * {@snippet class="org.lambdaql.query.SortPolicyTest" region="rules"}
     *
     * @param key the column
     * @return these rules
     */
    public SortRules<T> allow(SortKey<T> key) {
        entries.add(new Entry(null, QueryContext.read(key), null));
        resolved = null;
        return this;
    }

    /**
     * Allows a column under an outside name: {@code alias("newest", o -> o.getCreateAt())}.
     *
     * @param name the outside name (what the client sends)
     * @param key  the column
     * @return these rules
     */
    public SortRules<T> alias(String name, SortKey<T> key) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException(LqlMessage.SORT_PATH_EMPTY.format());
        entries.add(new Entry(name.trim(), QueryContext.read(key), null));
        resolved = null;
        return this;
    }

    /**
     * Allows property paths given as strings, for configuration files and annotations
     * ({@code lambdaql.sort.allow.Order=price,customer.name}). In code, prefer {@link #allow(SortKey)}: the compiler checks it.
     * The paths are checked against the entity when they are first used.
     *
     * @param paths the paths, for example {@code price} or {@code customer.name}
     * @return these rules
     */
    public SortRules<T> allowPath(String... paths) {
        for (String p : paths) {
            if (p == null || p.isBlank()) continue;
            entries.add(new Entry(null, null, p.trim()));
        }
        resolved = null;
        return this;
    }

    /**
     * Allows a property path given as a string under an outside name, for configuration files and annotations
     * ({@code lambdaql.sort.alias.Order.newest=updateAt}). In code, prefer {@link #alias(String, SortKey)}.
     *
     * @param name the outside name
     * @param path the path
     * @return these rules
     */
    public SortRules<T> aliasPath(String name, String path) {
        if (name == null || name.isBlank() || path == null || path.isBlank()) {
            throw new IllegalArgumentException(LqlMessage.SORT_PATH_EMPTY.format());
        }
        entries.add(new Entry(name.trim(), null, path.trim()));
        resolved = null;
        return this;
    }

    /**
     * Reads the {@link org.lambdaql.Sortable} fields of an entity into rules.
     *
     * @param type the entity class
     * @param <T>  the entity type
     * @return the rules, or null when no field is annotated
     */
    static <T> SortRules<T> fromAnnotations(Class<T> type) {
        SortRules<T> r = of(type);
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) add(r, f.getName(), f.getAnnotation(org.lambdaql.Sortable.class));
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                String prop = n.startsWith("get") && n.length() > 3 ? Character.toLowerCase(n.charAt(3)) + n.substring(4)
                        : n.startsWith("is") && n.length() > 2 ? Character.toLowerCase(n.charAt(2)) + n.substring(3) : n;
                add(r, prop, m.getAnnotation(org.lambdaql.Sortable.class));
            }
        }
        return r.entries.isEmpty() ? null : r;
    }

    private static void add(SortRules<?> r, String property, org.lambdaql.Sortable a) {
        if (a == null) return;
        String path = a.path().isBlank() ? property : property + "." + a.path().trim();
        if (a.value().isBlank()) r.allowPath(path);
        else r.aliasPath(a.value(), path);
    }

    /**
     * Returns the entity class of these rules.
     *
     * @return the entity class
     */
    public Class<T> type() {
        return type;
    }

    /** Checks if a name is an outside name of these rules (no analysis needed). */
    boolean isAlias(String name) {
        for (Entry e : entries) if (name.equals(e.name())) return true;
        return false;
    }

    /** Outside name to path, read once from the lambdas. */
    Map<String, String> names(QueryContext context) {
        Map<String, String> r = resolved;
        if (r != null) return r;
        Map<String, String> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            Expr key = e.key() != null ? context.analyzeSortKey(e.key(), type)
                    : org.lambdaql.engine.sem.SortReader.readPath(e.path(), type, context.model());
            if (!(key instanceof Expr.Column c)) throw LqlMessage.SORT_RULE_NOT_PATH.exception(key);
            String path = String.join(".", c.path());
            out.put(e.name() == null ? path : e.name(), path);
        }
        resolved = Map.copyOf(out);
        return resolved;
    }
}
