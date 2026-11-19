package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import org.hibernate.Session;
import org.lambdaql.InOut;
import org.lambdaql.engine.sem.EntityModel;
import org.lambdaql.engine.sem.HostEvaluator;
import org.lambdaql.engine.sem.ProcedureReader;
import org.lambdaql.i18n.LqlMessage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs procedure calls and function calls (ROADMAP M11, D30, PR2-PR6, PR9). Not public API.
 * <p>
 * The parameters are read from the JDBC metadata once per procedure (PR2, PR14) and checked against the stub:
 * the stub arguments are the IN and INOUT parameters in the database order, and the result parts are the OUT and
 * INOUT values (scalars) and the result sets or REF CURSORs (lists) in the database order (PR3).
 */
final class ProcedureRunner {

    /** One database parameter: mode 1 IN, 2 INOUT, 4 OUT; {@code cursor} for an Oracle REF CURSOR. */
    record DbParam(String name, int mode, boolean cursor, int sqlType, String typeName) {
        DbParam(String name, int mode, boolean cursor) {
            this(name, mode, cursor, java.sql.Types.OTHER, null);
        }
    }

    /** One part of the result: a scalar from an OUT/INOUT parameter, or a list from a result set. */
    private record Slot(boolean list, Type type) {
    }

    private static final Map<String, List<DbParam>> META = new ConcurrentHashMap<>();
    private static final java.util.Set<String> NAMES_LOGGED = ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ProcedureRunner.class);

    private ProcedureRunner() {
    }

    // ------------------------------------------------------------------ procedures

    static Object call(EntityManager em, EntityModel model, ProcedureReader.ProcCall call, Object[] values, boolean exec) {
        flush(em);
        Session session = em.unwrap(Session.class);
        Type ret = call.stub().getGenericReturnType();
        // the metadata is read when the user may read it; otherwise the stub alone gives the parameter layout
        List<DbParam> read = session.doReturningWork(c -> metadataOrNull(c, call));
        List<DbParam> params = read != null ? read
                : session.doReturningWork(c -> inferred(call, ret == void.class ? List.of() : slots(ret),
                        c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("oracle")));
        if (read != null) checkArguments(call, params);
        List<DbParam> outs = params.stream().filter(p -> p.mode() != 1 && !p.cursor()).toList();
        long cursors = params.stream().filter(DbParam::cursor).count();
        if (exec || ret == void.class) {
            if (!outs.isEmpty() || cursors > 0) throw LqlMessage.PROC_EXEC_HAS_OUT.exception(call.fullName());
        }
        if (ret == void.class) {
            session.doWork(c -> runJdbc(c, call, params, values, List.of()));
            return null;
        }
        List<Slot> slots = slots(ret);
        if (slots.size() == 1 && slots.get(0).list() && model.isEntity(raw(element(slots.get(0).type())))) {
            return entityList(em, call, params, values, raw(element(slots.get(0).type())));
        }
        long scalars = slots.stream().filter(s -> !s.list()).count();
        if (scalars != outs.size()) {
            throw LqlMessage.PROC_RESULT_SHAPE.exception(call.stub(), outs.size() + " OUT/INOUT, " + scalars + " scalar parts");
        }
        Object[] parts = session.doReturningWork(c -> runJdbc(c, call, params, values, slots));
        if (exec) return null;
        return assemble(call, ret, slots, parts);
    }

    private static void checkArguments(ProcedureReader.ProcCall call, List<DbParam> params) {
        List<DbParam> ins = params.stream().filter(p -> p.mode() == 1 || p.mode() == 2).toList();
        var stubParams = call.stub().getParameters();
        if (ins.size() != stubParams.length) {
            throw LqlMessage.PROC_SIGNATURE_MISMATCH.exception(call.fullName(),
                    "IN/INOUT " + ins.size() + " in the database, " + stubParams.length + " stub arguments");
        }
        checkNames(call, ins, stubParams);
        for (int i = 0; i < ins.size(); i++) {
            checkType(call, i, ins.get(i), stubParams[i].getType());
            boolean inout = stubParams[i].isAnnotationPresent(InOut.class);
            if (inout != (ins.get(i).mode() == 2)) {
                throw LqlMessage.PROC_SIGNATURE_MISMATCH.exception(call.fullName(),
                        "argument " + (i + 1) + " (" + ins.get(i).name() + ") is " + (ins.get(i).mode() == 2 ? "INOUT" : "IN")
                                + " in the database; mark INOUT arguments with @InOut");
            }
        }
    }

    /** The kind of a value, as far as an argument type check needs it (PR2). */
    private enum Kind { NUMBER, TEXT, TIME, BOOLEAN, BINARY }

    /** The kind of a database parameter type, or null when it is not one of the plain kinds (cursor, object, unknown). */
    private static Kind kind(int sqlType) {
        return switch (sqlType) {
            case java.sql.Types.TINYINT, java.sql.Types.SMALLINT, java.sql.Types.INTEGER, java.sql.Types.BIGINT,
                 java.sql.Types.DECIMAL, java.sql.Types.NUMERIC, java.sql.Types.REAL, java.sql.Types.FLOAT,
                 java.sql.Types.DOUBLE -> Kind.NUMBER;
            case java.sql.Types.CHAR, java.sql.Types.VARCHAR, java.sql.Types.LONGVARCHAR, java.sql.Types.NCHAR,
                 java.sql.Types.NVARCHAR, java.sql.Types.LONGNVARCHAR, java.sql.Types.CLOB, java.sql.Types.NCLOB -> Kind.TEXT;
            case java.sql.Types.DATE, java.sql.Types.TIME, java.sql.Types.TIMESTAMP, java.sql.Types.TIME_WITH_TIMEZONE,
                 java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> Kind.TIME;
            case java.sql.Types.BOOLEAN, java.sql.Types.BIT -> Kind.BOOLEAN;
            case java.sql.Types.BINARY, java.sql.Types.VARBINARY, java.sql.Types.LONGVARBINARY, java.sql.Types.BLOB -> Kind.BINARY;
            default -> null;
        };
    }

    /** The kind of a stub argument type, or null when it is not one of the plain kinds. */
    private static Kind kind(Class<?> type) {
        Class<?> t = box(type);
        if (Number.class.isAssignableFrom(t)) return Kind.NUMBER;
        if (t == String.class || t == Character.class) return Kind.TEXT;
        if (t == Boolean.class) return Kind.BOOLEAN;
        if (t == byte[].class) return Kind.BINARY;
        if (java.time.temporal.Temporal.class.isAssignableFrom(t) || java.util.Date.class.isAssignableFrom(t)) return Kind.TIME;
        return null;
    }

    /**
     * PR2 types: a stub argument must have the kind of the database parameter (a number for NUMBER/INT, a string for
     * VARCHAR, a date or time for DATE/TIMESTAMP). A boolean also fits a number parameter (MariaDB BOOLEAN is TINYINT(1),
     * Oracle before 23 has no BOOLEAN). Unknown kinds on either side (enum, object types, cursors) are not checked.
     */
    private static void checkType(ProcedureReader.ProcCall call, int index, DbParam db, Class<?> stub) {
        Kind d = kind(db.sqlType()), j = kind(stub);
        if (d == null || j == null || d == j) return;
        if (j == Kind.BOOLEAN && d == Kind.NUMBER) return;
        throw LqlMessage.PROC_SIGNATURE_MISMATCH.exception(call.fullName(),
                "argument " + (index + 1) + " (" + db.name() + ") is " + db.typeName() + " in the database, "
                        + stub.getSimpleName() + " in the stub");
    }

    /**
     * PR2 names: the names are compared without case, {@code _}, and a prefix that all database names share
     * ({@code p_}, {@code in_} ...). Only the same names in another order is an error (an argument mix-up is then
     * almost certain); other names are a different naming rule and pass.
     */
    private static void checkNames(ProcedureReader.ProcCall call, List<DbParam> ins, java.lang.reflect.Parameter[] stub) {
        if (ins.size() < 2) return;
        String prefix = commonPrefix(ins.stream().map(p -> String.valueOf(p.name()).toLowerCase(Locale.ROOT)).toList());
        List<String> db = ins.stream().map(p -> key(String.valueOf(p.name()), prefix)).toList();
        List<String> java = new ArrayList<>();
        for (java.lang.reflect.Parameter p : stub) {
            org.lambdaql.DbParam named = p.getAnnotation(org.lambdaql.DbParam.class);
            if (named != null) java.add(key(named.value(), prefix));
            else if (p.isNamePresent()) java.add(key(p.getName(), ""));
            else {
                if (sameTypeNeighbours(stub) && LOG.isInfoEnabled() && NAMES_LOGGED.add(call.fullName())) {
                    LOG.info("{}", LqlMessage.PROC_NAMES_UNAVAILABLE.format(call.fullName()));
                }
                return;
            }
        }
        if (java.equals(db)) return;
        if (new java.util.HashSet<>(java).equals(new java.util.HashSet<>(db)) && java.size() == new java.util.HashSet<>(java).size()) {
            throw LqlMessage.PROC_ARG_ORDER.exception(call.fullName(), java, db);
        }
    }

    private static boolean sameTypeNeighbours(java.lang.reflect.Parameter[] stub) {
        for (int i = 1; i < stub.length; i++) if (box(stub[i].getType()) == box(stub[i - 1].getType())) return true;
        return false;
    }

    /** The longest prefix that all names share and that ends with {@code _}, for example {@code p_}. */
    private static String commonPrefix(List<String> names) {
        String first = names.get(0);
        int end = first.length();
        for (String n : names) {
            int i = 0;
            while (i < end && i < n.length() && n.charAt(i) == first.charAt(i)) i++;
            end = i;
        }
        int cut = first.lastIndexOf('_', end - 1);
        return cut < 0 ? "" : first.substring(0, cut + 1);
    }

    private static String key(String name, String prefix) {
        String n = name.toLowerCase(Locale.ROOT);
        if (!prefix.isEmpty() && n.startsWith(prefix) && n.length() > prefix.length()) n = n.substring(prefix.length());
        return n.replace("_", "");
    }

    private static Object[] runJdbc(Connection c, ProcedureReader.ProcCall call, List<DbParam> params, Object[] values,
                                    List<Slot> slots) throws SQLException {
        List<Slot> scalarSlots = slots.stream().filter(s -> !s.list()).toList();
        List<Slot> listSlots = slots.stream().filter(Slot::list).toList();
        String marks = String.join(",", java.util.Collections.nCopies(params.size(), "?"));
        try (CallableStatement cs = c.prepareCall("{call " + call.fullName() + "(" + marks + ")}")) {
            Class<?>[] argTypes = call.stub().getParameterTypes();
            int arg = 0, scalar = 0;
            for (int i = 0; i < params.size(); i++) {
                DbParam p = params.get(i);
                int pos = i + 1;
                if (p.mode() == 1 || p.mode() == 2) {
                    bind(cs, pos, values[arg], argTypes[arg]);
                    arg++;
                }
                if (p.cursor()) cs.registerOutParameter(pos, Types.REF_CURSOR);
                else if (p.mode() != 1) cs.registerOutParameter(pos, sqlType(raw(scalarSlots.get(scalar++).type())));
            }
            List<Object> lists = new ArrayList<>();
            List<Object> implicit = new ArrayList<>();
            boolean has = cs.execute();
            while (true) {
                if (has) {
                    try (ResultSet rs = cs.getResultSet()) {
                        int index = (int) params.stream().filter(DbParam::cursor).count() + implicit.size();
                        if (index < listSlots.size()) implicit.add(rows(rs, element(listSlots.get(index).type()), call));
                    }
                } else if (cs.getUpdateCount() == -1) {
                    break;
                }
                has = cs.getMoreResults();
            }
            List<Object> scalars = new ArrayList<>();
            scalar = 0;
            for (int i = 0; i < params.size(); i++) {
                DbParam p = params.get(i);
                if (p.cursor()) {
                    int index = lists.size();
                    try (ResultSet rs = (ResultSet) cs.getObject(i + 1)) {
                        lists.add(rows(rs, element(listSlots.get(index).type()), call));
                    }
                } else if (p.mode() != 1) {
                    scalars.add(convert(cs.getObject(i + 1), raw(scalarSlots.get(scalar++).type()), call));
                }
            }
            lists.addAll(implicit);
            if (lists.size() != listSlots.size()) {
                throw LqlMessage.PROC_RESULT_SHAPE.exception(call.stub(), lists.size() + " result sets, " + listSlots.size() + " list parts");
            }
            Object[] parts = new Object[slots.size()];
            int s = 0, l = 0;
            for (int i = 0; i < slots.size(); i++) parts[i] = slots.get(i).list() ? lists.get(l++) : scalars.get(s++);
            return parts;
        }
    }

    private static List<?> entityList(EntityManager em, ProcedureReader.ProcCall call, List<DbParam> params, Object[] values,
                                      Class<?> entity) {
        StoredProcedureQuery q = em.createStoredProcedureQuery(call.fullName(), entity);
        Class<?>[] argTypes = call.stub().getParameterTypes();
        int arg = 0;
        for (int i = 0; i < params.size(); i++) {
            DbParam p = params.get(i);
            if (p.cursor()) {
                q.registerStoredProcedureParameter(i + 1, void.class, ParameterMode.REF_CURSOR);
            } else if (p.mode() == 1) {
                q.registerStoredProcedureParameter(i + 1, box(argTypes[arg]), ParameterMode.IN);
                q.setParameter(i + 1, values[arg++]);
            } else {
                throw LqlMessage.PROC_RESULT_SHAPE.exception(call.stub(), "OUT parameters with an entity list");
            }
        }
        return q.getResultList();
    }

    private static Object assemble(ProcedureReader.ProcCall call, Type ret, List<Slot> slots, Object[] parts) {
        Class<?> type = raw(ret);
        if (!isComposite(type)) return parts[0];
        Constructor<?> ctor = constructor(type);
        try {
            ctor.trySetAccessible();
            return ctor.newInstance(parts);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(LqlMessage.RESULT_OBJECT_FAILED.format(ctor), e);
        }
    }

    // ------------------------------------------------------------------ functions (PR6)

    static Object function(EntityManager em, ProcedureReader.FnCall call, Object[] values) {
        flush(em);
        Class<?> ret = call.fn().method().getReturnType();
        Session session = em.unwrap(Session.class);
        boolean oracle = session.doReturningWork(c -> c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("oracle"));
        if (oracle) {
            // Oracle: {? = call f(?)}, so functions that change data also work (a select would give ORA-14551)
            return session.doReturningWork(c -> {
                String marks = String.join(",", java.util.Collections.nCopies(values.length, "?"));
                try (CallableStatement cs = c.prepareCall("{? = call " + call.fn().sql() + "(" + marks + ")}")) {
                    cs.registerOutParameter(1, sqlType(box(ret)));
                    Class<?>[] types = call.fn().method().getParameterTypes();
                    for (int i = 0; i < values.length; i++) bind(cs, i + 2, values[i], types[i]);
                    cs.execute();
                    return convert(cs.getObject(1), box(ret), call.fn().method());
                }
            });
        }
        StringBuilder hql = new StringBuilder("select function('").append(call.fn().key()).append("' as ").append(call.fn().asType());
        for (int i = 0; i < values.length; i++) hql.append(", :p").append(i + 1);
        var q = em.createQuery(hql.append(')').toString());
        for (int i = 0; i < values.length; i++) q.setParameter("p" + (i + 1), values[i]);
        return convert(q.getSingleResult(), box(ret), call.fn().method());
    }

    // ------------------------------------------------------------------ metadata (PR2)

    /** Test hook: makes the metadata lookups fail as without permission. Not public API. */
    static volatile boolean simulateNoMetadata;

    /**
     * Reads the parameters from the JDBC metadata, or returns null (and logs LQL-5005 at INFO once per procedure)
     * when the lookup itself fails, for example without permission on the dictionary views.
     */
    private static List<DbParam> metadataOrNull(Connection c, ProcedureReader.ProcCall call) throws SQLException {
        try {
            if (simulateNoMetadata) throw new SQLException("simulated: no permission");
            return metadata(c, call);
        } catch (SQLException e) {
            if (LOG.isInfoEnabled() && NAMES_LOGGED.add("meta:" + call.fullName())) {
                LOG.info("{}", LqlMessage.PROC_METADATA_UNAVAILABLE.format(call.fullName(), e.getMessage()));
            }
            return null;
        }
    }

    /**
     * The parameter layout from the stub alone: IN and INOUT in stub order, then one OUT per scalar result part that
     * is not an INOUT value, then (Oracle) one REF CURSOR per list part.
     */
    private static List<DbParam> inferred(ProcedureReader.ProcCall call, List<Slot> slots, boolean oracle) {
        List<DbParam> out = new ArrayList<>();
        int inouts = 0;
        for (java.lang.reflect.Parameter p : call.stub().getParameters()) {
            boolean inout = p.isAnnotationPresent(InOut.class);
            if (inout) inouts++;
            out.add(new DbParam(p.getName(), inout ? 2 : 1, false));
        }
        long scalars = slots.stream().filter(sl -> !sl.list()).count();
        for (long i = inouts; i < scalars; i++) out.add(new DbParam("out" + i, 4, false));
        if (oracle) {
            long lists = slots.stream().filter(Slot::list).count();
            for (long i = 0; i < lists; i++) out.add(new DbParam("cursor" + i, 4, true));
        }
        return out;
    }

    private static List<DbParam> metadata(Connection c, ProcedureReader.ProcCall call) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        String product = md.getDatabaseProductName().toLowerCase(Locale.ROOT);
        String key = md.getURL() + "|" + md.getUserName() + "|" + call.fullName();
        List<DbParam> hit = META.get(key);
        if (hit != null) return hit;
        boolean oracle = product.contains("oracle"), maria = product.contains("maria") || product.contains("mysql");
        String name = maria ? call.name() : call.name().toUpperCase(Locale.ROOT);
        String catalog, schema;
        if (oracle) {
            catalog = call.pkg() == null ? "" : call.pkg().toUpperCase(Locale.ROOT);
            schema = call.schema() == null ? md.getUserName() : call.schema().toUpperCase(Locale.ROOT);
        } else if (maria) {
            catalog = call.schema() == null ? c.getCatalog() : call.schema();
            schema = null;
        } else {
            catalog = null;
            schema = call.schema() == null ? c.getSchema() : call.schema().toUpperCase(Locale.ROOT);
        }
        String esc = md.getSearchStringEscape();
        String pattern = esc == null ? name : name.replace("_", esc + "_");
        try (ResultSet rs = md.getProcedures(catalog, schema, pattern)) {
            if (!rs.next()) throw LqlMessage.PROC_NOT_FOUND.exception(call.fullName());
        }
        if (oracle && call.pkg() != null) {
            // an Oracle package can hold several procedures with the same name: their columns would be mixed
            try (var ps = c.prepareStatement("select count(distinct nvl(overload, '0')) from all_arguments"
                    + " where owner = ? and package_name = ? and object_name = ?")) {
                if (simulateNoMetadata) throw new SQLException("simulated: no permission");
                ps.setString(1, schema);
                ps.setString(2, catalog);
                ps.setString(3, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 1) throw LqlMessage.PROC_OVERLOADED.exception(call.fullName());
                }
            } catch (SQLException e) {
                // no permission on the dictionary view: go on without the overload check
                if (LOG.isInfoEnabled() && NAMES_LOGGED.add("overload:" + call.fullName())) {
                    LOG.info("{}", LqlMessage.PROC_OVERLOAD_CHECK_SKIPPED.format(call.fullName(), e.getMessage()));
                }
            }
        }
        List<DbParam> params = new ArrayList<>();
        try (ResultSet rs = md.getProcedureColumns(catalog, schema, pattern, "%")) {
            while (rs.next()) {
                int type = rs.getInt("COLUMN_TYPE");
                if (type == DatabaseMetaData.procedureColumnResult || type == DatabaseMetaData.procedureColumnReturn) continue;
                String typeName = String.valueOf(rs.getString("TYPE_NAME"));
                boolean cursor = typeName.toUpperCase(Locale.ROOT).contains("CURSOR");
                int mode = type == DatabaseMetaData.procedureColumnInOut ? 2 : type == DatabaseMetaData.procedureColumnOut ? 4 : 1;
                params.add(new DbParam(rs.getString("COLUMN_NAME"), mode, cursor, rs.getInt("DATA_TYPE"), typeName));
            }
        }
        List<DbParam> out = List.copyOf(params);
        META.put(key, out);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static void flush(EntityManager em) {
        // PR9: Hibernate does not flush before a procedure call; flush only inside a transaction with changes
        if (em.isJoinedToTransaction() && em.unwrap(Session.class).isDirty()) em.flush();
    }

    private static List<Slot> slots(Type ret) {
        Class<?> type = raw(ret);
        if (List.class.isAssignableFrom(type)) return List.of(new Slot(true, ret));
        if (!isComposite(type)) return List.of(new Slot(false, ret));
        Type[] parts = constructor(type).getGenericParameterTypes();
        return Arrays.stream(parts).map(t -> new Slot(List.class.isAssignableFrom(raw(t)), t)).toList();
    }

    /** Records and Kotlin data classes are result rows; other classes are single values. */
    private static boolean isComposite(Class<?> type) {
        return type.isRecord() || (type.isAnnotationPresent(kotlinMetadata()) && !type.getName().startsWith("java."));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends java.lang.annotation.Annotation> kotlinMetadata() {
        try {
            return (Class<? extends java.lang.annotation.Annotation>) Class.forName("kotlin.Metadata");
        } catch (ClassNotFoundException e) {
            return Deprecated.class;   // no Kotlin: never a Kotlin class
        }
    }

    private static Constructor<?> constructor(Class<?> type) {
        if (type.isRecord()) {
            Class<?>[] types = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new);
            try {
                return type.getDeclaredConstructor(types);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(LqlMessage.RESULT_CONSTRUCTOR_NOT_FOUND.format(type.getName()), e);
            }
        }
        Constructor<?> best = null;
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.isSynthetic()) continue;
            if (best == null || c.getParameterCount() > best.getParameterCount()) best = c;
        }
        if (best == null) throw new IllegalStateException(LqlMessage.RESULT_CONSTRUCTOR_NOT_FOUND.format(type.getName()));
        return best;
    }

    /** Column names of a row type: record components, or the fields of a Kotlin data class in declaration order. */
    private static List<String> names(Class<?> type) {
        if (type.isRecord()) return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
        return Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).map(Field::getName).toList();
    }

    private static List<Object> rows(ResultSet rs, Type elementType, Object where) throws SQLException {
        Class<?> type = raw(elementType);
        List<Object> out = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        if (!isComposite(type)) {
            while (rs.next()) out.add(convert(rs.getObject(1), box(type), where));
            return out;
        }
        Constructor<?> ctor = constructor(type);
        Class<?>[] types = ctor.getParameterTypes();
        List<String> names = names(type);
        int[] columns = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String want = normalize(names.get(i));
            for (int col = 1; col <= md.getColumnCount(); col++) {
                if (normalize(md.getColumnLabel(col)).equals(want)) columns[i] = col;
            }
            if (columns[i] == 0) throw LqlMessage.PROC_RESULT_SHAPE.exception(where, "no column for " + names.get(i));
        }
        ctor.trySetAccessible();
        while (rs.next()) {
            Object[] args = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                Object v = rs.getObject(columns[i]);
                if (v == null && types[i].isPrimitive()) throw new IllegalStateException(LqlMessage.PROC_NULL_INTO_PRIMITIVE.format(where));
                args[i] = convert(v, box(types[i]), where);
            }
            try {
                out.add(ctor.newInstance(args));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(LqlMessage.RESULT_OBJECT_FAILED.format(ctor), e);
            }
        }
        return out;
    }

    private static String normalize(String s) {
        return s.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static Object convert(Object v, Class<?> target, Object where) {
        if (v == null) {
            if (target.isPrimitive()) throw new IllegalStateException(LqlMessage.PROC_NULL_INTO_PRIMITIVE.format(where));
            return null;
        }
        if (v instanceof java.sql.Timestamp t) {
            if (target == LocalDateTime.class) return t.toLocalDateTime();
            if (target == LocalDate.class) return t.toLocalDateTime().toLocalDate();
        }
        if (v instanceof java.sql.Date d && target == LocalDate.class) return d.toLocalDate();
        if (target == Object.class) return v;
        return HostEvaluator.convert(v, box(target));
    }

    private static void bind(CallableStatement cs, int pos, Object value, Class<?> type) throws SQLException {
        if (value == null) cs.setNull(pos, sqlType(box(type)));
        else cs.setObject(pos, value);
    }

    private static int sqlType(Class<?> c) {
        if (c == Integer.class || c == int.class) return Types.INTEGER;
        if (c == Long.class || c == long.class) return Types.BIGINT;
        if (c == Double.class || c == double.class) return Types.DOUBLE;
        if (c == Float.class) return Types.REAL;
        if (c == Short.class) return Types.SMALLINT;
        if (c == BigDecimal.class) return Types.NUMERIC;
        if (c == String.class) return Types.VARCHAR;
        if (c == Boolean.class || c == boolean.class) return Types.BOOLEAN;
        if (c == LocalDate.class) return Types.DATE;
        if (c == LocalDateTime.class) return Types.TIMESTAMP;
        return Types.OTHER;
    }

    private static Type element(Type list) {
        if (list instanceof ParameterizedType p) return p.getActualTypeArguments()[0];
        return Object.class;
    }

    private static Class<?> raw(Type t) {
        if (t instanceof Class<?> c) return c;
        if (t instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        if (t instanceof java.lang.reflect.WildcardType w) return raw(w.getUpperBounds()[0]);
        return Object.class;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == boolean.class) return Boolean.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        return c;
    }
}
