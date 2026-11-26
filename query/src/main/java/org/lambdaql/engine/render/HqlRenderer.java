package org.lambdaql.engine.render;

import org.lambdaql.i18n.LqlMessage;
import org.lambdaql.LambdaAnalysisException;
import org.lambdaql.engine.ir.ArithOp;
import org.lambdaql.engine.ir.Assignment;
import org.lambdaql.engine.ir.CmpOp;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.Pred;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.engine.sem.HostEvaluator;
import org.lambdaql.query.Direction;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
 * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
 */
public final class HqlRenderer {

    private final EntityModel model;
    private final HostEvaluator evaluator;
    private final Map<String, Join> joins = new LinkedHashMap<>();
    private final Map<String, Object> params;
    /** Alias of root entities ({@code e0}); a nested select of a DML statement uses {@code x0}. */
    private final String rootPrefix;
    /** Alias prefix of joins ({@code j1}); a nested select of a DML statement uses {@code k1}. */
    private final String joinPrefix;
    /** The renderer of the outer query when this one renders a subquery (ROADMAP M8), else null. */
    private HqlRenderer parent;
    /** The query root of the subquery this renderer renders, or -1. */
    private int ownRoot = -1;
    private Class<?> ownRootClass;      // the entity of ownRoot (a subquery root)
    private Map<Integer, Class<?>> ownJoined = Map.of();   // roots joined inside a subquery (D51)
    /** A subquery renderer that read a column of an outer root (a correlated subquery). */
    private boolean correlated;
    /** The target entity while rendering an UPDATE (U22), else null. */
    private Class<?> updateRoot;
    private Class<?> mainRootClass;     // the entity (or row type) of root 0

    /** The entity class of a root, for reading the mapping of a path (A4). Null when it is not known. */
    private Class<?> rootClass(int root) {
        if (root == ownRoot && ownRootClass != null) return ownRootClass;
        if (ownJoined.containsKey(root)) return ownJoined.get(root);
        if (parent != null) return parent.rootClass(root);
        if (root == 0) return mainRootClass;
        if (root >= 1 && root <= entityJoins.size()) return entityJoins.get(root - 1).entity();
        return null;
    }

    /**
     * A5: a sort key that is a unique, never-NULL column of the main entity ({@code @Column(unique = true)} or Hibernate
     * {@code @NaturalId}) orders every row, so paging needs no id tie-breaker. A nullable unique column can hold NULL in
     * many rows, so it does not count.
     */
    private static boolean uniqueKey(Class<?> root, Expr.Column c) {
        if (c.path().size() != 1 || !c.nonNull() || root == null) return false;
        String name = c.path().get(0);
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Class<?> k = root; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (f.getName().equals(name) && unique(f.getAnnotations())) return true;
            }
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if ((m.getName().equals("get" + cap) || m.getName().equals("is" + cap)) && unique(m.getAnnotations())) return true;
            }
        }
        return false;
    }

    private static boolean unique(java.lang.annotation.Annotation[] annotations) {
        for (java.lang.annotation.Annotation a : annotations) {
            if (a instanceof jakarta.persistence.Column col && col.unique()) return true;
            if (a.annotationType().getName().equals("org.hibernate.annotations.NaturalId")) return true;
        }
        return false;
    }

    private boolean allStepsRequired(Expr.Column c) {
        for (int i = 0; i < c.path().size() - 1; i++) {
            if (c.association().get(i) && !requiredStep(c, i)) return false;
        }
        return true;
    }

    /**
     * Checks if step {@code i} of a path is an association that can never be missing ({@code optional = false}):
     * then a sort or select path through it keeps every row with an inner join too (ROADMAP D48, A4).
     */
    private boolean requiredStep(Expr.Column c, int i) {
        Class<?> owner = rootClass(c.root());
        List<String> names = c.path();
        for (int k = 0; k < i && owner != null; k++) {
            owner = model.property(owner, names.get(k)).map(EntityModel.Property::javaType).orElse(null);
        }
        if (owner == null) return false;
        return model.property(owner, names.get(i))
                .map(p -> p.kind() == EntityModel.Kind.ASSOCIATION && p.nonNull()).orElse(false);
    }
    private Object[] captured = new Object[0];
    private boolean leftJoins;
    /** The target entity while rendering UPDATE/DELETE (association paths become subqueries), else null. */
    private Class<?> dmlRoot;
    private int subqueries;
    private boolean collectGuards;
    /** Association prefixes used by a DML condition: key → the foreign key column to check for NOT NULL. */
    private final Map<String, Expr.Column> dmlGuards = new LinkedHashMap<>();
    /** Entities read by subqueries of this statement, kept on the top renderer (U17). */
    private final java.util.Set<Class<?>> subqueryReads = new java.util.HashSet<>();
    private java.util.Set<String> whereLeftJoins = java.util.Set.of();

    /**
     * A join made for a path.
     *
     * @param alias the alias, for example {@code j1}
     * @param left  {@code true} for {@code left join} (sort-only path), {@code false} for {@code join}
     */
    private record Join(String alias, boolean left, int root) {
    }

    /**
     * An entity join of an explicit join query (ROADMAP D44): {@code [left] join Customer e1 on ...}.
     *
     * @param entity the joined entity class (root 1, 2 ... in join order)
     * @param left   {@code true} for {@code left join}
     * @param on     the join condition, with the captured values of its lambda; null for {@code cross join} (D45)
     */
    public record JoinBound(Class<?> entity, boolean left, Bound on, JoinSource source) {
        /**
         * An entity join.
         *
         * @param entity the joined entity class
         * @param left   {@code true} for a left join
         * @param on     the join condition, or null for a cross join
         */
        public JoinBound(Class<?> entity, boolean left, Bound on) {
            this(entity, left, on, null);
        }
    }

    /**
     * Writes the table of a joined derived table or CTE (ROADMAP D44 extension): {@code (select ...)}, or the name of a
     * CTE that it adds with {@link #addCte}. Not public API.
     */
    @FunctionalInterface
    public interface JoinSource {
        /**
         * Writes the table.
         *
         * @param outer the renderer of the statement
         * @return the text after {@code join}
         */
        String render(HqlRenderer outer);
    }

    private List<JoinBound> entityJoins = List.of();
    private String source;          // a derived table "(select ...)" or a CTE name, instead of the entity name
    private final Map<String, String> ctes = new LinkedHashMap<>();   // "with t as (...), t2 as (...)" before the select
    private final Map<Object, String> cteNames = new java.util.IdentityHashMap<>();   // CTE source -> name (reuse)
    private boolean distinctRows;   // select distinct e0 (explicit join results)
    private List<String> aliases = List.of();

    /**
     * Makes a renderer for the inner query of a derived table or CTE (ROADMAP M8-2). It shares the parameters with this
     * renderer and uses other alias prefixes ({@code d0}, {@code dj1}), so the names never clash.
     *
     * @return the inner renderer
     */
    public HqlRenderer nested() {
        return new HqlRenderer(model, evaluator, params, "d", "dj");
    }

    /**
     * Reads from a derived table or a CTE instead of an entity (ROADMAP M8-2).
     *
     * A CTE is added with {@link #addCte} first.
     *
     * @param source {@code (select ...)} for a derived table, or the CTE name
     * @return this renderer
     */
    public HqlRenderer withSource(String source) {
        this.source = source;
        return this;
    }

    /**
     * Adds a CTE to the {@code with} part of the statement.
     *
     * @param name the wanted name; a number is added when it is already used ({@code t}, {@code t2}, ...)
     * @param body the query of the CTE
     * @return the name to read the CTE by
     */
    public String addCte(String name, String body) {
        return addCte(null, name, () -> body);
    }

    /**
     * Adds a CTE once per source: the same CTE used twice in one statement is written once and read by one name.
     *
     * @param key  the CTE source (same object, same CTE), or null for a new CTE every time
     * @param name the wanted name
     * @param body writes the query of the CTE; called only the first time for a key
     * @return the name to read the CTE by
     */
    public String addCte(Object key, String name, java.util.function.Supplier<String> body) {
        HqlRenderer top = this;
        while (top.parent != null) top = top.parent;
        if (key != null && top.cteNames.containsKey(key)) return top.cteNames.get(key);
        String text = body.get();
        String unique = name;
        for (int i = 2; top.ctes.containsKey(unique); i++) unique = name + i;
        top.ctes.put(unique, text);
        if (key != null) top.cteNames.put(key, unique);
        return unique;
    }

    /**
     * Makes an entity select {@code select distinct e0} and its count {@code count(distinct e0)}: the rows of the main
     * entity without the repeats that a join gives (ROADMAP D44 extension).
     *
     * @param distinct {@code true} for distinct rows
     * @return this renderer
     */
    public HqlRenderer withDistinct(boolean distinct) {
        this.distinctRows = distinct;
        return this;
    }

    /**
     * Names the selected values ({@code select a as customerId, ...}), for the inner query of a derived table or CTE (J6).
     *
     * @param aliases the names in select order
     * @return this renderer
     */
    public HqlRenderer withAliases(List<String> aliases) {
        this.aliases = List.copyOf(aliases);
        return this;
    }

    private RenderedQuery finish(StringBuilder hql) {
        if (ctes.isEmpty()) return new RenderedQuery(hql.toString(), new LinkedHashMap<>(params));
        List<String> defs = new ArrayList<>();
        ctes.forEach((name, body) -> defs.add(name + " as (" + body + ")"));
        String text = "with " + String.join(", ", defs) + " " + hql;
        return new RenderedQuery(text, new LinkedHashMap<>(params));
    }

    /**
     * Adds the entity joins of an explicit join query (ROADMAP D44). The joined entities are roots 1, 2 ...
     *
     * @param entityJoins the joins in order
     * @return this renderer
     */
    public HqlRenderer withJoins(List<JoinBound> entityJoins) {
        this.entityJoins = List.copyOf(entityJoins);
        return this;
    }

    /**
     * Writes {@code from Entity e0}, the path joins of root 0, then each entity join with its condition and the path
     * joins of that root (a path join must come after the join of its root).
     */
    private String fromClause(Class<?> root) {
        List<String> ons = new ArrayList<>();
        boolean saved = leftJoins;
        leftJoins = true;               // a path in an ON condition must not drop rows of the main entity
        for (JoinBound j : entityJoins) {
            if (j.on() == null) {
                ons.add(null);
                continue;
            }
            captured = j.on().captured();
            Pred f = fold(j.on().pred());
            ons.add(f instanceof Pred.Bool b ? (b.value() ? "1=1" : "1=0") : pred(f));
        }
        leftJoins = saved;
        StringBuilder from = new StringBuilder(" from ").append(source != null ? source : model.entityName(root))
                .append(' ').append(aliasOf(mainRoot()));
        pathJoins(from, mainRoot());
        for (int k = 0; k < entityJoins.size(); k++) {
            JoinBound j = entityJoins.get(k);
            String table = j.source() != null ? j.source().render(this) : model.entityName(j.entity());
            if (ons.get(k) == null) {
                from.append(" cross join ").append(table).append(' ').append(aliasOf(k + 1));
            } else {
                from.append(j.left() ? " left join " : " join ").append(table).append(' ')
                        .append(aliasOf(k + 1)).append(" on ").append(ons.get(k));
            }
            pathJoins(from, k + 1);
        }
        return from.toString();
    }

    private void pathJoins(StringBuilder from, int root) {
        joins.forEach((path, j) -> {
            if (j.root() == root || (root == mainRoot() && j.root() > entityJoins.size()))
                from.append(j.left() ? " left join " : " join ").append(path).append(' ').append(j.alias());
        });
    }

    /**
     * Creates a renderer for one query.
     *
     * @param model  entity information (entity names)
     * @param loader class loader used to compute host values
     */
    public HqlRenderer(EntityModel model, ClassLoader loader) {
        this(model, new HostEvaluator(loader), new LinkedHashMap<>(), "e", "j");
    }

    /** A renderer for a nested select that shares the parameters of its parent statement. */
    private HqlRenderer(EntityModel model, HostEvaluator evaluator, Map<String, Object> params, String rootPrefix, String joinPrefix) {
        this.model = model;
        this.evaluator = evaluator;
        this.params = params;
        this.rootPrefix = rootPrefix;
        this.joinPrefix = joinPrefix;
    }

    /**
     * Which rows an UPDATE/DELETE changes, besides its conditions.
     *
     * @param sorts the order for {@code limit} (the id is added as the last key, S9)
     * @param limit the most rows to change, or {@code null} for no limit
     * @param id    the id of the only row to change (the "one" protection), or {@code null}
     */
    public record DmlScope(List<SortBound> sorts, Integer limit, Object id) {
        /** Every row that matches. */
        public static final DmlScope ALL = new DmlScope(List.of(), null, null);
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
     * The sort keys of one sort lambda, with the captured values of that lambda.
     * A {@code SortSpec} made of several lambdas gives one {@code SortBound} per lambda.
     *
     * @param items    the keys in order
     * @param captured the captured values of the sort lambda (used to read run-time directions)
     * @param reversed {@code true} to reverse every key (direction and NULL position), for {@code SortSpec.reversed()}
     */
    public record SortBound(List<SortItem> items, Object[] captured, boolean reversed) {
        /**
         * Makes sort keys that are not reversed.
         *
         * @param items    the keys in order
         * @param captured the captured values of the sort lambda
         */
        public SortBound(List<SortItem> items, Object[] captured) {
            this(items, captured, false);
        }

        /**
         * Returns the same keys in the opposite order direction.
         *
         * @return a copy with {@code reversed} flipped
         */
        public SortBound reverse() {
            return new SortBound(items, captured, !reversed);
        }
    }

    /**
     * Renders {@code select e0 from Entity e0 [join ...] [where ...]}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SelectQueryTest.java">SelectQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SelectQueryTest" region="to-hql"}
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if a host value cannot be computed or is not allowed (for example a null receiver)
     */
    public RenderedQuery select(Class<?> root, List<Bound> wheres) {
        return select(root, wheres, List.of(), false);
    }

    /**
     * Renders {@code select count(e0) from Entity e0 [join ...] [where ...]}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="count"}
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @return the HQL and its parameters; the result is one {@code Long}
     */
    public RenderedQuery count(Class<?> root, List<Bound> wheres) {
        return render(distinctRows ? "count(distinct e0)" : "count(e0)", root, wheres, List.of(), false);
    }

    /**
     * Renders {@code select count(*) from Entity e0 [join ...] [where ...]}: every row, for {@code count(o -> o)}
     * (ROADMAP D69).
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @return the HQL and its parameters; the result is one {@code Long}
     */
    public RenderedQuery countAll(Class<?> root, List<Bound> wheres) {
        return render("count(*)", root, wheres, List.of(), false);
    }

    /**
     * Renders {@code select 1 from Entity e0 [join ...] [where ...]}; the caller reads at most one row.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ResultQueryTest.java">ResultQueryTest</a>):
     * {@snippet class="org.lambdaql.query.ResultQueryTest" region="exists"}
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @return the HQL and its parameters; the result rows are {@code Integer} 1
     */
    public RenderedQuery exists(Class<?> root, List<Bound> wheres) {
        return render("1", root, wheres, List.of(), false);
    }

    /**
     * Renders {@code select e0 from Entity e0 [join ...] [where ...] [order by ...]}.
     * Paging values are not part of the HQL; the caller passes them to Hibernate.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortQueryTest.java">SortQueryTest</a>):
     * {@snippet class="org.lambdaql.query.SortQueryTest" region="sort-path"}
     *
     * @param root   the main entity class
     * @param wheres the conditions; they are joined with AND
     * @param sorts  the sort keys, one entry per sort lambda (empty for no sort)
     * @param paged  {@code true} if the query uses {@code limit} or {@code offset}; then the id is added as the last key
     *               when the keys do not contain it (S9)
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if a host value cannot be computed, or a run-time direction is null
     */
    public RenderedQuery select(Class<?> root, List<Bound> wheres, List<SortBound> sorts, boolean paged) {
        return render((distinctRows ? "distinct " : "") + rootPrefix + "0", root, wheres, sorts, paged);
    }

    private RenderedQuery render(String select, Class<?> root, List<Bound> wheres, List<SortBound> sorts, boolean paged) {
        mainRootClass = root;
        List<String> parts = whereParts(wheres);
        List<String> orders = orderBy(root, sorts, paged);
        StringBuilder hql = new StringBuilder("select ").append(select).append(fromClause(root));
        if (!parts.isEmpty()) hql.append(" where ").append(String.join(" and ", parts));
        if (!orders.isEmpty()) hql.append(" order by ").append(String.join(", ", orders));
        return finish(hql);
    }

    /**
     * Renders {@code select count(*) ...}: for a derived table or CTE, where the row has no entity to count.
     *
     * @param root   the row type
     * @param wheres the conditions
     * @return the HQL and its parameters; the result is one {@code Long}
     */
    public RenderedQuery countRows(Class<?> root, List<Bound> wheres) {
        return render("count(*)", root, wheres, List.of(), false);
    }

    /**
     * Folds and renders the conditions (joined later with AND). Also decides the join kinds (D36).
     *
     * @param wheres the conditions
     * @return the parts; {@code ["1=0"]} if a condition is always false; empty if there is no condition
     */
    private List<String> whereParts(List<Bound> wheres) {
        List<Pred> folded = new ArrayList<>();
        Pred all = Pred.Bool.TRUE;
        for (Bound b : wheres) {
            captured = b.captured();
            Pred f = fold(b.pred());
            folded.add(f);
            all = Pred.and(all, f);
        }
        // D54 (revised 2026-11-23): one rule for Java and Kotlin. An association step is an inner join unless a row
        // without the association can make the whole condition true; a comparison through a step written with !!,
        // without a mark or in Java counts only for rows that have the association (it gets "is not null" when it
        // could be true on NULL columns). See JoinPlanner.plan.
        JoinPlanner.WherePlan plan = JoinPlanner.plan(all, this::requiredStep);
        whereLeftJoins = plan.lefts();
        for (int i = 0; i < folded.size(); i++) folded.set(i, JoinPlanner.guard(folded.get(i), plan));
        List<String> parts = new ArrayList<>();
        if (all instanceof Pred.Bool b) {
            if (!b.value()) parts.add("1=0");
            return parts;
        }
        for (int i = 0; i < wheres.size(); i++) {
            captured = wheres.get(i).captured();
            Pred f = folded.get(i);
            if (f instanceof Pred.Bool) continue;                  // TRUE here (a FALSE makes "all" FALSE)
            String s = pred(f);
            parts.add(wheres.size() > 1 && isComposite(f) ? "(" + s + ")" : s);
        }
        return parts;
    }

    // ------------------------------------------------------------------ DML (ROADMAP M10)

    /**
     * The assignments of one assignment lambda, with the captured values of that lambda.
     *
     * @param items    the assignments in order
     * @param captured the captured values (for {@code values(list, ...)}, the list element is added at the end)
     */
    public record AssignBound(List<Assignment> items, Object[] captured) {
    }

    /**
     * Renders {@code update [versioned] Entity e0 set ... [where ...]}.
     * <p>
     * Association paths become correlated scalar subqueries ({@code (select s1.name from Customer s1 where s1.id = fk(e0.customer))}),
     * so the statement uses only its own table (ROADMAP U9; MariaDB cannot read the target table in a subquery).
     * In the condition, a path that an inner join would use (D36) also gets {@code e0.customer.id is not null},
     * so the rows are the same as {@code qb.from(...).where(same lambda)}.
     * An entity with {@code @Version} gets {@code update versioned}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update"}
     *
     * @param root   the entity class
     * @param sets   the assignments of every {@code set()} call
     * @param wheres the conditions ({@code all()} gives none)
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if a captured value cannot be used (for example null for a column that is never NULL)
     */
    public RenderedQuery update(Class<?> root, List<AssignBound> sets, List<Bound> wheres) {
        return update(root, sets, wheres, DmlScope.ALL);
    }

    /**
     * Renders an UPDATE that changes only some of the matching rows.
     * <ul>
     *   <li>With a limit: {@code where e0.id in (select t.id from (select x0.id as id from Entity x0 ... order by ... limit n) t)}.
     *       The nested select uses the select rules (joins, D36), so the rows are the first {@code n} rows of
     *       {@code qb.from(X).where(same).orderBy(same)}. The derived table {@code t} is needed by MariaDB, which cannot
     *       read the target table or use {@code limit} directly in an {@code in} subquery (measured on all three databases).</li>
     *   <li>With an id: {@code where e0.id = :p and (the conditions)}: exactly that row, if it still matches.</li>
     * </ul>
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="update-limit"}
     *
     * @param root   the entity class
     * @param sets   the assignments
     * @param wheres the conditions
     * @param scope  the limit or the single id
     * @return the HQL and its parameters
     */
    public RenderedQuery update(Class<?> root, List<AssignBound> sets, List<Bound> wheres, DmlScope scope) {
        dmlRoot = root;
        updateRoot = root;
        List<String> parts = new ArrayList<>();
        for (AssignBound b : sets) {
            captured = b.captured();
            for (Assignment a : b.items()) parts.add(target(a.target(), "e0.") + " = " + assignValue(a));
        }
        String where = scopedWhere(root, wheres, scope);
        StringBuilder hql = new StringBuilder("update ").append(model.versioned(root) ? "versioned " : "")
                .append(model.entityName(root)).append(" e0 set ").append(String.join(", ", parts));
        if (!where.isEmpty()) hql.append(" where ").append(where);
        return new RenderedQuery(hql.toString(), new LinkedHashMap<>(params));
    }

    /**
     * Renders {@code delete from Entity e0 [where ...]}, with the same path rules as {@link #update}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="delete"}
     *
     * @param root   the entity class
     * @param wheres the conditions ({@code all()} gives none)
     * @return the HQL and its parameters
     */
    public RenderedQuery delete(Class<?> root, List<Bound> wheres) {
        return delete(root, wheres, DmlScope.ALL);
    }

    /**
     * Renders a DELETE that removes only some of the matching rows (see {@link #update(Class, List, List, DmlScope)}).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="delete-limit"}
     *
     * @param root   the entity class
     * @param wheres the conditions
     * @param scope  the limit or the single id
     * @return the HQL and its parameters
     */
    public RenderedQuery delete(Class<?> root, List<Bound> wheres, DmlScope scope) {
        dmlRoot = root;
        String where = scopedWhere(root, wheres, scope);
        String entity = model.entityName(root);
        String id = model.idName(root).orElse(null);
        if (!where.isEmpty() && scope.limit() == null && id != null && readsTarget(root)) {
            // U17: MariaDB cannot delete from a table that a subquery reads (error 1093). The matching ids are read
            // through a derived table first, the same rows as the plain form on every database.
            String hql = "delete from " + entity + " d0 where d0." + id + " in (select t.id from (select e0." + id
                    + " as id from " + entity + " e0 where " + where + ") t)";
            return new RenderedQuery(hql, new LinkedHashMap<>(params));
        }
        String hql = "delete from " + entity + " e0" + (where.isEmpty() ? "" : " where " + where);
        return new RenderedQuery(hql, new LinkedHashMap<>(params));
    }

    /**
     * Renders {@code select e0.id from Entity e0 ... where ...}: the ids of the matching rows,
     * read first by the "one" protection (at most 2 are read).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="execute-one"}
     *
     * @param root   the entity class
     * @param wheres the conditions
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if the entity has a composite id
     */
    public RenderedQuery ids(Class<?> root, List<Bound> wheres) {
        return render(rootPrefix + "0." + idOf(root), root, wheres, List.of(), false);
    }

    private String idOf(Class<?> root) {
        return model.idName(root).orElseThrow(() -> LqlMessage.COMPOSITE_KEY_LIMIT.exception(root.getName()));
    }

    /** Checks if a subquery of this statement reads the target entity or an entity of the same hierarchy (U17). */
    private boolean readsTarget(Class<?> root) {
        for (Class<?> c : subqueryReads) {
            if (c != null && (c.isAssignableFrom(root) || root.isAssignableFrom(c))) return true;
        }
        return false;
    }

    /**
     * U22: tells whether the subquery reads the target entity of the UPDATE being rendered (or an entity of the same
     * hierarchy), in its own FROM or its joins.
     *
     * @param q the subquery
     * @return {@code true} when the statement is an UPDATE and the subquery reads its target
     */
    private boolean readsUpdateTarget(Expr.Subquery q) {
        HqlRenderer top = this;
        while (top.parent != null) top = top.parent;
        Class<?> target = top.updateRoot;
        if (target == null) return false;
        List<Class<?>> read = new ArrayList<>();
        read.add(q.entity());
        q.joins().forEach(j -> read.add(j.entity()));
        for (Class<?> c : read) {
            if (c != null && (c.isAssignableFrom(target) || target.isAssignableFrom(c))) return true;
        }
        return false;
    }

    /** Records an entity read by a subquery on the top renderer (U17). */
    private void noteRead(Class<?> entity) {
        HqlRenderer top = this;
        while (top.parent != null) top = top.parent;
        top.subqueryReads.add(entity);
    }

    /** The class reached after the association step {@code i} of a column path. */
    private Class<?> stepClass(Class<?> owner, List<String> names, int i) {
        Class<?> t = owner;
        for (int j = 0; j <= i && t != null; j++) {
            Class<?> from = t;
            t = model.property(from, names.get(j)).map(EntityModel.Property::javaType).orElse(null);
        }
        return t;
    }

    private String scopedWhere(Class<?> root, List<Bound> wheres, DmlScope scope) {
        if (scope.limit() != null) {
            HqlRenderer inner = new HqlRenderer(model, evaluator, params, "x", "k");
            String id = idOf(root);
            String nested = inner.render("x0." + id + " as id", root, wheres, scope.sorts(), true).hql() + " limit " + scope.limit();
            return "e0." + id + " in (select t.id from (" + nested + ") t)";
        }
        String where = dmlWhere(wheres);
        if (scope.id() == null) return where;
        String byId = "e0." + idOf(root) + " = " + bindRaw(scope.id());
        return where.isEmpty() ? byId : byId + " and " + where;
    }

    /**
     * Renders {@code insert into Entity (a, b) values (...), (...)}: one row per entry.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert"}
     *
     * @param root the entity class
     * @param rows the rows; every row has the same target columns
     * @return the HQL and its parameters
     */
    public RenderedQuery insertValues(Class<?> root, List<AssignBound> rows) {
        List<String> tuples = new ArrayList<>();
        for (AssignBound row : rows) tuples.add("(" + rowValues(row) + ")");
        String hql = "insert into " + model.entityName(root) + " (" + targetList(rows.get(0)) + ") values " + String.join(", ", tuples);
        return new RenderedQuery(hql, new LinkedHashMap<>(params));
    }

    /**
     * Renders an upsert of one row: {@code insert into Entity e0 (...) values (...) on conflict(key) do update set ...}
     * (ROADMAP D40). Hibernate turns it into {@code merge} (H2, Oracle) or {@code on duplicate key update} (MariaDB).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="upsert"}
     *
     * @param root    the entity class
     * @param row     the inserted row
     * @param key     the conflict key column
     * @param updates the update on conflict; {@code null} means "the inserted values of every column except the key"
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if the row does not assign the key
     */
    public RenderedQuery upsert(Class<?> root, AssignBound row, Expr.Column key, AssignBound updates) {
        String keyName = String.join(".", key.path());
        requireKey(row, key);
        List<String> sets = new ArrayList<>();
        if (updates == null) {
            for (Assignment a : row.items()) {
                if (a.target().path().equals(key.path())) continue;
                String name = String.join(".", a.target().path());
                sets.add(name + " = excluded." + name);
            }
            if (sets.isEmpty()) return insertIgnore(root, row, key);        // only the key: nothing to update
        }
        String values = rowValues(row);
        if (updates != null) {
            dmlRoot = root;
            captured = updates.captured();
            for (Assignment a : updates.items()) sets.add(target(a.target(), "") + " = " + assignValue(a));
        }
        String hql = "insert into " + model.entityName(root) + " e0 (" + targetList(row) + ") values (" + values + ")"
                + " on conflict(" + keyName + ") do update set " + String.join(", ", sets);
        return new RenderedQuery(hql, new LinkedHashMap<>(params));
    }

    /**
     * Renders "insert unless the key exists" for one row (ROADMAP D40):
     * {@code insert into Entity (...) select ... where not exists (select 1 from Entity x where x.key = ...)}.
     * Hibernate's {@code on conflict do nothing} becomes a plain insert on H2 and Oracle (measured), so lambdaQL
     * writes this form itself. It is not atomic: two sessions inserting the same key at the same time can still
     * get a duplicate key error.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-ignore"}
     *
     * @param root the entity class
     * @param row  the inserted row
     * @param key  the key column
     * @return the HQL and its parameters
     */
    public RenderedQuery insertIgnore(Class<?> root, AssignBound row, Expr.Column key) {
        Assignment k = requireKey(row, key);
        String values = rowValues(row);
        captured = row.captured();
        String keyValue = assignValue(k);
        String entity = model.entityName(root);
        String hql = "insert into " + entity + " (" + targetList(row) + ") select " + values
                + " where not exists (select 1 from " + entity + " x where x." + String.join(".", key.path()) + " = " + keyValue + ")";
        return new RenderedQuery(hql, new LinkedHashMap<>(params));
    }

    /**
     * Renders {@code insert into Target (a, b) select e0.x, j1.y from Source e0 left join ... where ...}.
     * Paths in the select list use explicit left joins, so source rows without the association stay (ROADMAP U10).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/DmlQueryTest.java">DmlQueryTest</a>):
     * {@snippet class="org.lambdaql.query.DmlQueryTest" region="insert-select"}
     *
     * @param target  the target entity class
     * @param source  the source entity class
     * @param assigns target columns and values; the values use the source as root 0
     * @param wheres  conditions on the source
     * @return the HQL and its parameters
     */
    public RenderedQuery insertSelect(Class<?> target, Class<?> source, AssignBound assigns, List<Bound> wheres) {
        return insertSelect(target, source, assigns, wheres, null, List.of());
    }

    /**
     * Renders INSERT ... SELECT with group keys and having conditions (ROADMAP M9):
     * {@code insert into Summary (customerId, total) select e0.customer.id, sum(e0.price) from Order e0 group by e0.customer.id}.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="insert-group"}
     *
     * @param target  the target entity class
     * @param source  the source entity class
     * @param assigns target columns and values; the values use the source as root 0
     * @param wheres  conditions on the source
     * @param groupBy the group keys, or null
     * @param havings conditions on the groups
     * @return the HQL and its parameters
     */
    public RenderedQuery insertSelect(Class<?> target, Class<?> source, AssignBound assigns, List<Bound> wheres,
                                      ValueBound groupBy, List<Bound> havings) {
        List<String> parts = whereParts(wheres);
        captured = assigns.captured();
        leftJoins = true;
        List<String> select = new ArrayList<>();
        for (Assignment a : assigns.items()) select.add(value(a.value(), a.target().javaType()));
        List<String> keys = groupKeys(groupBy);
        List<String> having = havingParts(havings);
        leftJoins = false;
        StringBuilder hql = new StringBuilder("insert into ").append(model.entityName(target)).append(" (")
                .append(targetList(assigns)).append(") select ").append(String.join(", ", select))
                .append(" from ").append(model.entityName(source)).append(" e0");
        joins.forEach((path, j) -> hql.append(j.left() ? " left join " : " join ").append(path).append(' ').append(j.alias()));
        if (!parts.isEmpty()) hql.append(" where ").append(String.join(" and ", parts));
        if (!keys.isEmpty()) hql.append(" group by ").append(String.join(", ", keys));
        if (!having.isEmpty()) hql.append(" having ").append(String.join(" and ", having));
        return new RenderedQuery(hql.toString(), new LinkedHashMap<>(params));
    }

    private List<String> groupKeys(ValueBound groupBy) {
        List<String> keys = new ArrayList<>();
        if (groupBy == null) return keys;
        captured = groupBy.captured();
        for (Expr e : groupBy.exprs()) keys.add(value(e, null));
        return keys;
    }

    private List<String> havingParts(List<Bound> havings) {
        List<String> having = new ArrayList<>();
        for (Bound h : havings) {
            captured = h.captured();
            Pred f = fold(h.pred());
            if (f instanceof Pred.Bool b) {
                if (!b.value()) having.add("1=0");
                continue;
            }
            String x = pred(f);
            having.add(havings.size() > 1 && isComposite(f) ? "(" + x + ")" : x);
        }
        return having;
    }

    private Assignment requireKey(AssignBound row, Expr.Column key) {
        for (Assignment a : row.items()) {
            if (a.target().path().equals(key.path())) return a;
        }
        throw LqlMessage.CONFLICT_KEY_MISSING.exception(String.join(".", key.path()));
    }

    private String targetList(AssignBound row) {
        List<String> names = new ArrayList<>();
        for (Assignment a : row.items()) names.add(String.join(".", a.target().path()));
        return String.join(", ", names);
    }

    private String rowValues(AssignBound row) {
        captured = row.captured();
        List<String> values = new ArrayList<>();
        for (Assignment a : row.items()) values.add(assignValue(a));
        return String.join(", ", values);
    }

    private String target(Expr.Column c, String alias) {
        return alias + String.join(".", c.path());
    }

    /**
     * Renders the value of an assignment. A captured null becomes {@code null}
     * (an error for a column that is never NULL, U6); a captured entity for an association is bound as is
     * (Hibernate uses its id, U11).
     */
    private String assignValue(Assignment a) {
        Expr v = a.value();
        if (v instanceof Expr.Const c && c.isNull()) return "null";
        if (isHostLike(v) && !(v instanceof Expr.Const)) {
            Object x = eval(v);
            if (x == null) {
                if (a.target().nonNull()) {
                    throw LqlMessage.ASSIGN_NULL_NOT_NULL.exception(String.join(".", a.target().path()));
                }
                return "null";
            }
            boolean association = a.target().association().get(a.target().association().size() - 1);
            return association ? bindRaw(x) : bind(x, a.target().javaType());
        }
        return value(v, a.target().javaType());
    }

    /**
     * Renders the condition of UPDATE/DELETE: association paths become subqueries, and inner-join paths get
     * a NOT NULL check of their foreign key.
     */
    private String dmlWhere(List<Bound> wheres) {
        collectGuards = true;
        List<String> parts = whereParts(wheres);
        collectGuards = false;
        if (parts.equals(List.of("1=0"))) return "1=0";
        for (Map.Entry<String, Expr.Column> g : dmlGuards.entrySet()) {
            if (!whereLeftJoins.contains(g.getKey())) parts.add(path(g.getValue()) + " is not null");
        }
        return String.join(" and ", parts);
    }

    /**
     * Renders a column that goes through an association as a correlated scalar subquery (UPDATE/DELETE only).
     *
     * @param c   the column
     * @param hop index of the first association that needs a join
     * @return for example {@code (select s1.name from Customer s1 where s1.id = fk(e0.customer))}
     */
    private String subqueryPath(Expr.Column c, int hop) {
        List<String> names = c.path();
        Class<?> owner = dmlRoot;
        for (int i = 0; i <= hop; i++) owner = model.property(owner, names.get(i)).orElseThrow().javaType();
        String id = model.idName(owner).orElseThrow(() -> LqlMessage.COMPOSITE_KEY_DML_PATH.exception());
        String alias = "s" + (++subqueries);
        List<String> prefix = names.subList(0, hop + 1);
        // fk(...) reads the foreign key column of the outer row. The plain form "e0.customer.id" makes Hibernate 7
        // add an unused join to the subquery on MariaDB ("from customers c1_0, customers c2_0"), which multiplies the
        // rows (measured with Hibernate 7.4 on H2, MariaDB and Oracle; the SQL is the same on H2 and Oracle).
        String fk = "fk(e" + c.root() + "." + String.join(".", prefix) + ")";
        if (collectGuards) {
            List<String> fkPath = new ArrayList<>(prefix);
            fkPath.add(id);
            List<Boolean> fkAssoc = new ArrayList<>(c.association().subList(0, hop + 1));
            fkAssoc.add(false);
            dmlGuards.putIfAbsent(JoinPlanner.key(c.root(), prefix),
                    new Expr.Column(c.root(), fkPath, fkAssoc, Object.class, false, true));
        }
        noteRead(owner);
        StringBuilder from = new StringBuilder(model.entityName(owner)).append(' ').append(alias);
        String expr = alias;
        for (int i = hop + 1; i < names.size(); i++) {
            boolean last = i == names.size() - 1;
            boolean fkOnly = c.idPath() && i == names.size() - 2;
            if (!last && c.association().get(i) && !fkOnly) {
                String inner = "s" + (++subqueries);
                noteRead(stepClass(dmlRoot, names, i));
                from.append(" left join ").append(expr).append('.').append(names.get(i)).append(' ').append(inner);
                expr = inner;
            } else {
                expr = expr + "." + names.get(i);
            }
        }
        return "(select " + expr + " from " + from + " where " + alias + "." + id + " = " + fk + ")";
    }

    // ------------------------------------------------------------------ projection (ROADMAP M9)

    /**
     * Values from one lambda (select values or group keys), with the captured values of that lambda.
     *
     * @param exprs    the normalized values
     * @param captured the captured values of the lambda
     */
    public record ValueBound(List<Expr> exprs, Object[] captured) {
    }

    /**
     * Renders {@code select [distinct] a, b from Entity e0 [joins] [where] [group by] [having] [order by]} (ROADMAP M9).
     * Paths in select, group by and having use left joins (rule J1), so rows without the association stay.
     * The id tie-breaker for paging (S9) is added only without grouping and distinct.
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/ProjectionTest.java">ProjectionTest</a>):
     * {@snippet class="org.lambdaql.query.ProjectionTest" region="group-by"}
     *
     * @param root     the entity class
     * @param wheres   the conditions
     * @param select   the selected values
     * @param groupBy  the group keys, or null
     * @param havings  the having conditions
     * @param sorts    the order
     * @param distinct {@code true} for {@code select distinct}
     * @param paged    {@code true} when the query uses limit or offset
     * @return the HQL and its parameters
     * @throws LambdaAnalysisException if {@code distinct} sorts by a value that is not selected (rule J7)
     */
    public RenderedQuery values(Class<?> root, List<Bound> wheres, ValueBound select, ValueBound groupBy, List<Bound> havings,
                                List<SortBound> sorts, boolean distinct, boolean paged) {
        mainRootClass = root;
        List<String> parts = whereParts(wheres);
        leftJoins = true;
        captured = select.captured();
        List<String> cols = new ArrayList<>();
        for (Expr e : select.exprs()) cols.add(value(e, null));
        List<String> plainCols = List.copyOf(cols);
        if (!aliases.isEmpty()) {
            for (int i = 0; i < cols.size() && i < aliases.size(); i++) cols.set(i, cols.get(i) + " as " + aliases.get(i));
        }
        List<String> keys = groupKeys(groupBy);
        List<String> having = havingParts(havings);
        leftJoins = false;
        boolean grouped = groupBy != null || select.exprs().stream().anyMatch(e -> e instanceof Expr.Aggregate);
        List<String> orders = orderBy(root, sorts, paged && !grouped && !distinct);
        if (distinct) {
            for (String o : orders) {
                if (plainCols.stream().noneMatch(c -> o.startsWith(c + " "))) {
                    throw LqlMessage.DISTINCT_SORT_NOT_SELECTED.exception(o);
                }
            }
        }
        StringBuilder hql = new StringBuilder("select ").append(distinct ? "distinct " : "").append(String.join(", ", cols))
                .append(fromClause(root));
        if (!parts.isEmpty()) hql.append(" where ").append(String.join(" and ", parts));
        if (!keys.isEmpty()) hql.append(" group by ").append(String.join(", ", keys));
        if (!having.isEmpty()) hql.append(" having ").append(String.join(" and ", having));
        if (!orders.isEmpty()) hql.append(" order by ").append(String.join(", ", orders));
        return finish(hql);
    }

    // ------------------------------------------------------------------ subqueries (ROADMAP M8)

    /** The alias of a query root: {@code e0} for the main entity, {@code s1} for a subquery root. */
    private String aliasOf(int root) {
        if (root == ownRoot || ownJoined.containsKey(root)) return "s" + root;
        if (parent != null) return parent.aliasOf(root);
        return rootPrefix + root;
    }

    private int mainRoot() {
        return ownRoot >= 0 ? ownRoot : 0;
    }

    /**
     * Renders a subquery in brackets. Its own paths get their own joins; the outer roots are referenced by alias
     * (correlation). The order is kept only with a limit (S14); a subquery with a limit inside {@code in} is wrapped in
     * a derived table, because MariaDB cannot use {@code limit} directly there (error 1235).
     *
     * <p>Example (from the test <a href="https://github.com/lambdaql/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SubqueryTest.java">SubqueryTest</a>):
     * {@snippet class="org.lambdaql.query.SubqueryTest" region="exists"}
     *
     * @param q          the subquery
     * @param notInValue for {@code !values.contains(x)}: the rendered outer value {@code x}; the subquery then gets
     *                   {@code and <value> = x} for NOT EXISTS (Q2). Otherwise null
     * @return the HQL in brackets
     */
    private String subquery(Expr.Subquery q, String notInValue) {
        noteRead(q.entity());
        q.joins().forEach(j -> noteRead(j.entity()));
        HqlRenderer inner = new HqlRenderer(model, evaluator, params, "s", "s" + q.root() + "j");
        inner.parent = this;
        inner.ownRoot = q.root();
        inner.ownRootClass = q.entity();
        Map<Integer, Class<?>> joined = new LinkedHashMap<>();
        q.joins().forEach(j -> joined.put(j.root(), j.entity()));
        inner.ownJoined = joined;
        inner.captured = captured;
        Object[] cap = captured;
        // the entity joins inside the subquery (D51): join Entity sN on ...
        List<String> joinTexts = new ArrayList<>();
        for (Expr.Subquery.Join j : q.joins()) {
            inner.leftJoins = true;          // a path in an ON condition must not drop rows (as in fromClause)
            List<String> on = inner.whereParts(List.of(new Bound(j.on(), cap)));
            inner.leftJoins = false;
            joinTexts.add((j.left() ? " left join " : " join ") + model.entityName(j.entity()) + " s" + j.root()
                    + " on " + (on.isEmpty() ? "1=1" : String.join(" and ", on)));
        }
        List<String> parts = inner.whereParts(q.wheres().stream().map(p -> new Bound(p, cap)).toList());
        String alias = "s" + q.root();
        String select = notInValue != null ? "1" : switch (q.kind()) {
            case EXISTS -> "1";
            case VALUES -> inner.value(q.select(), null);
            case SCALAR -> switch (q.agg()) {
                case NONE -> inner.value(q.select(), null);
                case COUNT -> "count(" + alias + ")";
                default -> q.agg().name().toLowerCase(java.util.Locale.ROOT) + "(" + inner.value(q.select(), null) + ")";
            };
        };
        if (notInValue != null) parts.add(inner.value(q.select(), null) + " = " + notInValue);
        List<String> orders = q.limit() == null ? List.of()
                : inner.orderBy(q.entity(), q.sorts().isEmpty() ? List.of() : List.of(new SortBound(q.sorts(), cap)), true);
        // U22: in an UPDATE, a subquery that reads the target table and does not use the outer row is read through a
        // derived table, (select tN.v from (select ... as v ...) tN). MySQL rejects reading the target table of an
        // UPDATE in a subquery (error 1093); Hibernate 7 adds such a derived table on MySQL itself, Hibernate 6.6 does
        // not (measured on MySQL 8.4). The rows are the same on every database.
        boolean selfUpdate = notInValue == null && !inner.correlated && readsUpdateTarget(q);
        boolean wrap = (q.kind() == Expr.Subquery.Kind.VALUES && q.limit() != null && notInValue == null) || selfUpdate;
        StringBuilder hql = new StringBuilder("select ").append(select).append(wrap ? " as v" : "")
                .append(" from ").append(model.entityName(q.entity())).append(' ').append(alias);
        // path joins right after the root they start from, so an ON condition can use them (D51)
        java.util.function.IntConsumer pathsOf = r -> inner.joins.forEach((path, j) -> {
            if (j.root() == r) hql.append(j.left() ? " left join " : " join ").append(path).append(' ').append(j.alias());
        });
        pathsOf.accept(q.root());
        for (int k = 0; k < q.joins().size(); k++) {
            hql.append(joinTexts.get(k));
            pathsOf.accept(q.joins().get(k).root());
        }
        inner.joins.forEach((path, j) -> {
            if (j.root() != q.root() && !joined.containsKey(j.root()))
                hql.append(j.left() ? " left join " : " join ").append(path).append(' ').append(j.alias());
        });
        if (!parts.isEmpty()) hql.append(" where ").append(String.join(" and ", parts));
        if (!orders.isEmpty()) hql.append(" order by ").append(String.join(", ", orders));
        if (q.limit() != null) hql.append(" limit ").append(q.limit());
        captured = cap;
        if (wrap) return "(select t" + q.root() + ".v from (" + hql + ") t" + q.root() + ")";
        return "(" + hql + ")";
    }

    // ------------------------------------------------------------------ order by

    private List<String> orderBy(Class<?> root, List<SortBound> sorts, boolean paged) {
        List<String> out = new ArrayList<>();
        // no orderBy: no order by at all; the order of the rows is the developer's choice (ROADMAP D73)
        boolean sorted = sorts.stream().anyMatch(sort -> !sort.items().isEmpty());
        String id = paged && sorted ? model.idName(root).orElse(null) : null;
        boolean hasId = false;
        for (SortBound sort : sorts) {
            captured = sort.captured();
            leftJoins = true;
            for (SortItem item : sort.items()) {
                Direction d = sort.reversed() ? direction(item).reversed() : direction(item);
                String key = value(item.key(), null);
                String nulls = sortNonNull(item.key()) ? "" : d.nullsFirst() ? " nulls first" : " nulls last";
                out.add(key + (d.ascending() ? " asc" : " desc") + nulls);
                if (id != null && item.key() instanceof Expr.Column c && c.root() == mainRoot()
                        && (c.path().equals(List.of(id)) || uniqueKey(root, c))) {
                    hasId = true;       // the key already gives every row its own place (S9, A5)
                }
            }
            leftJoins = false;
        }
        if (id != null && !hasId) out.add(aliasOf(mainRoot()) + "." + id + " asc");
        if (id != null && !distinctRows) {
            // a join can repeat the main row, so every joined root adds its id too (D44, D45);
            // distinct rows do not repeat, and a distinct select can only sort by its own columns
            for (int k = 0; k < entityJoins.size(); k++) {
                String jid = model.idName(entityJoins.get(k).entity()).orElse(null);
                String key = aliasOf(k + 1) + "." + jid + " asc";
                if (jid != null && !out.contains(key)) out.add(key);
            }
        }
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
            throw LqlMessage.SORT_DIRECTION_NULL.exception(item);
        }
        boolean asc = item.kind() == SortItem.Kind.ASC;
        if (item.arg() == null) return asc ? Direction.ASC : Direction.DESC;
        Object v = eval(item.arg());
        boolean first = switch (v) {
            case org.lambdaql.query.Nulls n -> n == org.lambdaql.query.Nulls.FIRST;
            case Boolean b -> b;
            case Number n -> n.intValue() != 0;
            case null, default -> throw LqlMessage.NULLS_FIRST_NULL.exception(item);
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
    private boolean sortNonNull(Expr e) {
        return switch (e) {
            // a non-null value is still NULL behind a missing association, unless every association step is required (A4)
            case Expr.Column c -> c.nonNull() && allStepsRequired(c);
            case Expr.Const c -> !c.isNull();
            case Expr.Case c -> c.values().stream().allMatch(this::sortNonNull) && sortNonNull(c.otherwise());
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
        throw LqlMessage.NULL_BOOLEAN_CONDITION.exception(where);
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
            case Pred.Like l -> like(l);
            case Pred.Not n -> switch (n.operand()) {
                case Pred.IsNull x -> isNull(x.operand(), false);
                case Pred.Truth t -> truth(t.operand(), false);
                case Pred.In in -> in(in.value(), in.values(), false);
                case Pred.Cmp c -> cmp(c.op().negate(), c.left(), c.right(), c.nullSafe());
                case Pred.Like l -> like((Pred.Like) Pred.not(l));
                default -> "not (" + pred(n.operand()) + ")";
            };
        };
    }

    /**
     * Renders a value chosen by conditions (ROADMAP D62): {@code case when c1 then v1 ... else v end}.
     *
     * <p>Example: {@code o.isActive() ? 1 : 2} renders {@code case when e0.active = true then 1 else 2 end}.
     *
     * @param c    the case
     * @param peer the type of the other side of a comparison, for literals (may be null)
     * @return the HQL
     */
    private String caseWhen(Expr.Case c, Class<?> peer) {
        StringBuilder sb = new StringBuilder("case");
        for (int i = 0; i < c.conds().size(); i++) {
            sb.append(" when ").append(pred(c.conds().get(i))).append(" then ").append(value(c.values().get(i), peer));
        }
        return sb.append(" else ").append(value(c.otherwise(), peer)).append(" end").toString();
    }

    /**
     * Renders a text match as {@code like} with the escape character {@code !}: {@code %}, {@code _} and {@code !}
     * in the text are escaped, so {@code startsWith("50%")} finds only texts that start with "50%".
     * A constant becomes a literal; a captured value is bound. A captured {@code null} is an error (Java would throw).
     *
     * <p>Example: {@code o.getName().startsWith(p)} with {@code p = "Ki"} renders {@code e0.name like :p1 escape '!'}
     * with the value {@code Ki%}; with Kotlin {@code ignoreCase = true}: {@code lower(e0.name) like :p1 escape '!'}.
     *
     * @param l the match
     * @return the HQL
     */
    private String like(Pred.Like l) {
        String x = value(l.value(), null);
        Object t = l.text() instanceof Expr.Const c ? c.value() : eval(l.text());
        if (t == null) throw LqlMessage.LIKE_TEXT_NULL.exception(l.toString());
        String text = t.toString();
        if (l.ignoreCase()) {
            x = "lower(" + x + ")";
            text = text.toLowerCase(java.util.Locale.ROOT);
        }
        String esc = text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String pattern = switch (l.kind()) {
            case PREFIX -> esc + "%";
            case SUFFIX -> "%" + esc;
            case ANY -> "%" + esc + "%";
        };
        String rhs = l.text() instanceof Expr.Const
                ? literal(new Expr.Const(pattern, org.objectweb.asm.Type.getType(String.class)), String.class)
                : bindRaw(pattern);
        return x + (l.negated() ? " not like " : " like ") + rhs + " escape '!'";
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
            // two entity values: the SQL meaning (D48); a row with a NULL value does not match
            String text = value(a, null) + " " + op.symbol() + " " + value(b, null);
            if (!nonNull(a) || !nonNull(b)) sqlMeaning(text);
            return text;
        }
        if ((op == CmpOp.EQ || op == CmpOp.NE) && ((isHostLike(a) && eval(a) == null) || (isHostLike(b) && eval(b) == null))) {
            String nulled = value(a, peerType(b)) + " " + op.symbol() + " " + value(b, peerType(a));
            nullCaptured(nulled, (isHostLike(a) && autoUnboxed(a)) || (isHostLike(b) && autoUnboxed(b)));
            return nulled;
        }
        String text = value(a, peerType(b)) + " " + op.symbol() + " " + value(b, peerType(a));
        if ((op == CmpOp.EQ || op == CmpOp.NE) && !isHostLike(a) && !isHostLike(b) && (!nonNull(a) || !nonNull(b))) sqlMeaning(text);
        return text;
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HqlRenderer.class);

    /**
     * Logs a comparison with a null captured value (LQL-4045): at DEBUG when the null came through javac's automatic
     * unboxing (the author did not write a method call on it), else at WARN (D48).
     */
    private static void nullCaptured(String text, boolean autoUnboxed) {
        if (autoUnboxed) {
            if (LOG.isDebugEnabled()) LOG.debug("{}", LqlMessage.NULL_CAPTURED_COMPARE.format(text));
        } else if (LOG.isWarnEnabled()) {
            LOG.warn("{}", LqlMessage.NULL_CAPTURED_COMPARE.format(text));
        }
    }

    private static boolean autoUnboxed(Expr e) {
        return e instanceof Expr.Host h && h.autoUnboxed();
    }
    private static final java.util.Set<String> MEANING_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> MIXED_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** D48: entity values are compared with the SQL meaning; tell (DEBUG, once per condition text) when NULL rows drop out. */
    private static void sqlMeaning(String condition) {
        if (LOG.isDebugEnabled() && MEANING_LOGGED.add(condition)) LOG.debug("{}", LqlMessage.SQL_NULL_MEANING.format(condition));
    }

    private String nullSafeWithValue(CmpOp op, Expr column, Expr host) {
        Object v = eval(host);
        String col = value(column, null);
        if (v == null) {
            // D48: the SQL meaning, = NULL / <> NULL match no row; the value is known only now, so warn when it runs
            String text = col + " " + op.symbol() + " " + bindRaw(null);
            nullCaptured(text, autoUnboxed(host));
            return text;
        }
        String param = host instanceof Expr.Const c ? literal(c, peerType(column)) : bind(v, peerType(column));
        if (op == CmpOp.EQ) return col + " = " + param;
        // an entity value compared with a non-null value: the SQL meaning (D48), a NULL column does not match
        if (!nonNull(column)) sqlMeaning(col + " <> " + param);
        return col + " <> " + param;
    }

    private String isNull(Expr e, boolean positive) {
        if (isHostLike(e)) return (eval(e) == null) == positive ? "1=1" : "1=0";
        return value(e, null) + (positive ? " is null" : " is not null");
    }

    private String truth(Expr e, boolean positive) {
        if (e instanceof Expr.Subquery q && q.kind() == Expr.Subquery.Kind.EXISTS) {
            return (positive ? "exists " : "not exists ") + subquery(q, null);
        }
        if (isHostLike(e)) {
            return truthValue(eval(e), new Pred.Truth(e)) == positive ? "1=1" : "1=0";
        }
        String x = value(e, null);
        if (positive) return x + " = true";
        // the SQL meaning (D48): a NULL Boolean is not false
        if (!nonNull(e)) sqlMeaning(x + " = false");
        return x + " = false";
    }

    /**
     * Reads the values of a captured IN operand: a collection, an array (also of primitives), or any other
     * {@code Iterable}, such as a Kotlin progression ({@code 5 downTo 1}, {@code 1..9 step 2}).
     *
     * <p>Example (Kotlin): {@code it.status in 5 downTo 1} reads as {@code e0.status in (5, 4, 3, 2, 1)}.
     *
     * @param v the captured value, not null
     * @return the values in order
     */
    private static Collection<?> inValues(Object v) {
        if (v instanceof Collection<?> col) return col;
        if (v instanceof Object[] array) return Arrays.asList(array);
        List<Object> list = new ArrayList<>();
        if (v.getClass().isArray()) {
            for (int i = 0; i < java.lang.reflect.Array.getLength(v); i++) list.add(java.lang.reflect.Array.get(v, i));
            return list;
        }
        if (v instanceof Iterable<?> it) {
            for (Object o : it) list.add(o);
            return list;
        }
        throw LqlMessage.INTERNAL_ERROR.exception("IN operand " + v.getClass().getName());
    }

    private String in(Expr value, Expr values, boolean positive) {
        if (values instanceof Expr.Subquery q) {
            String x = value(value, null);
            // D48: the SQL meaning, NOT IN; it gives no rows when the subquery has a NULL
            if (!positive) sqlMeaning(x + " not in (subquery)");
            return x + (positive ? " in " : " not in ") + subquery(q, null);
        }
        Object v = eval(values);
        if (v == null) throw LqlMessage.IN_COLLECTION_NULL.exception();
        Collection<?> c = inValues(v);
        if (c.stream().anyMatch(Objects::isNull)) {
            throw LqlMessage.IN_COLLECTION_NULL_ELEMENT.exception();
        }
        if (c.isEmpty()) return positive ? "1=0" : "1=1";
        String x = value(value, null);
        List<Object> list = new ArrayList<>();
        Class<?> peer = peerType(value);
        // an element with no exact whole-number form (2.5) for a whole-number column: the whole list is bound as
        // decimals, so the database compares 2.5 itself (Hibernate would cut it to 2; D48 B8)
        boolean exact = HostEvaluator.integral(peer)
                && c.stream().anyMatch(o -> o instanceof Number n && HostEvaluator.exactWhole(n, peer) == null);
        if (exact) {
            List<String> literals = new ArrayList<>();
            for (Object o : c) literals.add(o instanceof Number n ? decimalLiteral(n) : bind(o, peer));
            String text = String.join(", ", literals);
            if (positive) return x + " in (" + text + ")";
            if (!nonNull(value)) sqlMeaning(x + " not in (...)");
            return x + " not in (" + text + ")";
        }
        for (Object o : c) list.add(HostEvaluator.convert(o, peer));
        String param = bindRaw(list);
        if (positive) return x + " in (" + param + ")";
        // D48: the SQL meaning; a NULL value is not "not in" the list
        if (!nonNull(value)) sqlMeaning(x + " not in (...)");
        return x + " not in (" + param + ")";
    }

    // ------------------------------------------------------------------ values

    private String value(Expr e, Class<?> peer) {
        return switch (e) {
            case Expr.Column c -> path(c);
            case Expr.Root r -> aliasOf(r.root());                       // an entity in a select: select e1 (D70)
            case Expr.Host h -> {
                Object v = eval(h);
                if (v == null) {
                    // D48: a null captured value is bound as NULL (SQL meaning: no row matches); known only now
                    String p = bindRaw(null);
                    nullCaptured(h.expr() + " -> " + p, h.autoUnboxed());
                    yield p;
                }
                yield bind(v, peer);
            }
            case Expr.Const c -> literal(c, peer);
            case Expr.Arith a -> a.op() == ArithOp.REM
                    ? "mod(" + value(a.left(), peer) + ", " + value(a.right(), peer) + ")"
                    : "(" + value(a.left(), peer) + " " + a.op().symbol() + " " + value(a.right(), peer) + ")";
            case Expr.Neg n -> "-(" + value(n.operand(), peer) + ")";
            case Expr.Coalesce c -> "coalesce(" + value(c.first(), peer) + ", " + value(c.second(), peerType(c.first())) + ")";
            case Expr.Case c -> caseWhen(c, peer);
            case Expr.Subquery q -> subquery(q, null);
            case Expr.Aggregate a -> switch (a.fn()) {
                case COUNT_ALL -> "count(*)";
                case COUNT_DISTINCT -> "count(distinct " + value(a.arg(), null) + ")";
                default -> a.fn().name().toLowerCase(java.util.Locale.ROOT) + "(" + value(a.arg(), null) + ")";
            };
            // a database function: always function('<key>' as <type>, ...) (ROADMAP M11, D31)
            // a standard HQL function from a String method: lower(e0.name), substring(e0.name, 1, 3) (ROADMAP D74)
            case Expr.Func f when f.builtin() -> {
                StringBuilder out = new StringBuilder(f.key()).append('(');
                for (int k = 0; k < f.args().size(); k++) out.append(k == 0 ? "" : ", ").append(value(f.args().get(k), null));
                yield out.append(')').toString();
            }
            case Expr.Func f -> {
                StringBuilder out = new StringBuilder("function('").append(f.key().replace("'", "''")).append("' as ").append(f.asType());
                for (Expr arg : f.args()) out.append(", ").append(value(arg, null));
                yield out.append(')').toString();
            }
            default -> throw LqlMessage.VALUE_NOT_RENDERABLE.exception(e);
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
        String expr = aliasOf(c.root());
        List<String> names = c.path();
        if (parent != null && c.root() != ownRoot && !ownJoined.containsKey(c.root())) {
            // a column of an outer root: this subquery and the ones between it and that root are correlated
            for (HqlRenderer r = this; r.parent != null && r.ownRoot != c.root() && !r.ownJoined.containsKey(c.root()); r = r.parent) {
                r.correlated = true;
            }
            for (int i = 0; i < names.size() - 1; i++) {
                boolean fkOnly = c.idPath() && i == names.size() - 2;
                if (c.association().get(i) && !fkOnly) {
                    throw LqlMessage.SUBQUERY_OUTER_PATH.exception(String.join(".", names));
                }
            }
            return expr + "." + String.join(".", names);
        }
        if (dmlRoot != null) {
            for (int i = 0; i < names.size() - 1; i++) {
                boolean fkOnly = c.idPath() && i == names.size() - 2;
                if (c.association().get(i) && !fkOnly) return subqueryPath(c, i);
            }
            return expr + "." + String.join(".", names);
        }
        for (int i = 0; i < names.size(); i++) {
            boolean last = i == names.size() - 1;
            boolean fkOnly = c.idPath() && i == names.size() - 2;
            if (!last && c.association().get(i) && !fkOnly) {
                String key = expr + "." + names.get(i);
                boolean left = (leftJoins && !requiredStep(c, i)) || whereLeftJoins.contains(JoinPlanner.key(c.root(), names.subList(0, i + 1)));
                int owner = c.root();
                if (parent != null) noteRead(stepClass(rootClass(c.root()), names, i));
                expr = joins.computeIfAbsent(key, k -> new Join(joinPrefix + (joins.size() + 1), left, owner)).alias();
            } else {
                expr = expr + "." + names.get(i);
            }
        }
        return expr;
    }

    // ------------------------------------------------------------------ helpers

    private String bind(Object v, Class<?> type) {
        Object x = HostEvaluator.convert(v, type);
        // 2.5 or 1e20 for a whole-number column: written as a decimal literal, so the database compares the value
        // itself. Hibernate types the parameter as the column type and would refuse or cut it (D48 B8; Hibernate 7
        // also refuses a parameter bound with an explicit BigDecimal type). Only a number is written, never text.
        if (HostEvaluator.integral(type) && x instanceof Number n && !type.isInstance(x)) return decimalLiteral(n);
        return bindRaw(x);
    }

    /** A number as an HQL decimal literal, for example {@code 2.5BD} (D48 B8). */
    private static String decimalLiteral(Number n) {
        return java.util.Objects.requireNonNull(HostEvaluator.decimal(n)).toPlainString() + "BD";
    }

    private String bindRaw(Object v) {
        String name = "p" + (params.size() + 1);
        params.put(name, v);
        return ":" + name;
    }

    private Object eval(Expr e) {
        if (e instanceof Expr.Host h) {
            // p.equals(column) is a comparison, the same as column.equals(p): a null p is bound as NULL (D48)
            return evaluator.eval(h, captured);
        }
        return evaluator.eval(e, captured);
    }

    private static boolean isHostLike(Expr e) {
        return switch (e) {
            case Expr.Column c -> false;
            case Expr.Subquery q -> false;
            case Expr.Aggregate a -> false;
            case Expr.Func f -> false;
            case Expr.Arith a -> isHostLike(a.left()) && isHostLike(a.right());
            case Expr.Neg n -> isHostLike(n.operand());
            case Expr.Coalesce c -> isHostLike(c.first()) && isHostLike(c.second());
            case Expr.Case c -> false;
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
        if (a == null || b == null) throw LqlMessage.NULL_ORDER_COMPARE.exception(op);
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
