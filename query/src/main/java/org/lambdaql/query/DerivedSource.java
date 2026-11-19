package org.lambdaql.query;

import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.render.HqlRenderer;
import org.lambdaql.engine.sem.SelectReader;
import org.lambdaql.i18n.LqlMessage;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The inner query of a derived table or CTE (ROADMAP M8-2, J5, J6). Not public API. */
final class DerivedSource {

    private final ValueQuery<?> inner;
    private final String cteName;
    final Class<?> rowType;

    DerivedSource(ValueQuery<?> inner, String cteName) {
        this.inner = inner;
        this.cteName = cteName;
        Class<?> row = inner.rowClass();
        if (row == null || !(row.isRecord() || isKotlinData(row))) {
            throw LqlMessage.DERIVED_ROW_TYPE.exception(row == null ? "a single value" : row.getName());
        }
        this.rowType = row;
        inner.context().registerRow(row);
    }

    /** Writes the inner query into the outer renderer: {@code from (select ...) e0} or {@code with t as (...)}. */
    void apply(HqlRenderer outer) {
        outer.withSource(table(outer));
    }

    /** The table text: {@code (select ...)}, or the name of the CTE that it adds to the statement. */
    String table(HqlRenderer outer) {
        // a CTE is written once per statement, however often it is read (the same Cte object)
        if (cteName != null) return outer.addCte(this, cteName, () -> inner.renderInner(outer.nested(), columnNames(rowType)));
        return "(" + inner.renderInner(outer.nested(), columnNames(rowType)) + ")";
    }

    /** The source of a join with this derived table or CTE (ROADMAP D44 extension). */
    HqlRenderer.JoinSource joinSource() {
        return this::table;
    }

    /** The select of all row columns, built into the row type. */
    SelectReader.Projection rowProjection() {
        List<Expr> columns = new ArrayList<>();
        Constructor<?> ctor = constructor(rowType);
        Class<?>[] types = ctor.getParameterTypes();
        List<String> names = columnNames(rowType);
        for (int i = 0; i < names.size(); i++) {
            columns.add(new Expr.Column(0, List.of(names.get(i)), List.of(false), box(types[i]), false, false));
        }
        return new SelectReader.Projection(columns, Type.getInternalName(rowType), Type.getConstructorDescriptor(ctor));
    }

    static List<String> columnNames(Class<?> row) {
        if (row.isRecord()) return Arrays.stream(row.getRecordComponents()).map(RecordComponent::getName).toList();
        return Arrays.stream(row.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).map(Field::getName).toList();
    }

    private static Constructor<?> constructor(Class<?> row) {
        if (row.isRecord()) {
            try {
                return row.getDeclaredConstructor(Arrays.stream(row.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(LqlMessage.RESULT_CONSTRUCTOR_NOT_FOUND.format(row.getName()), e);
            }
        }
        Constructor<?> best = null;
        for (Constructor<?> c : row.getDeclaredConstructors()) {
            if (!c.isSynthetic() && (best == null || c.getParameterCount() > best.getParameterCount())) best = c;
        }
        return best;
    }

    private static boolean isKotlinData(Class<?> c) {
        for (var a : c.getAnnotations()) if (a.annotationType().getName().equals("kotlin.Metadata")) return true;
        return false;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == boolean.class) return Boolean.class;
        return c;
    }
}
