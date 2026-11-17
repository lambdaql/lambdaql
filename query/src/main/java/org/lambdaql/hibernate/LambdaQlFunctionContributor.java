package org.lambdaql.hibernate;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.lambdaql.SqlFunction;
import org.lambdaql.engine.sem.SqlFunctions;
import org.lambdaql.i18n.LqlMessage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the {@link SqlFunction} methods of the classes in the Hibernate setting {@code lambdaql.functions.classes}
 * (ROADMAP M11, D31). Hibernate finds this class with {@code ServiceLoader}; without the setting it does nothing.
 * <p>
 * Registration is optional. It keeps the case of the SQL name, lets Hibernate check the argument count,
 * and is needed for a function whose SQL name is not its query name ({@code @SqlFunction(sql = ...)}).
 * In Spring Boot: {@code spring.jpa.properties.lambdaql.functions.classes=com.acme.OrderFns}.
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/FunctionTest.java">FunctionTest</a>):
 * {@snippet class="org.lambdaql.query.FunctionTest" region="declare"}
 */
public class LambdaQlFunctionContributor implements FunctionContributor {

    /** The setting with the class names, separated by commas. */
    public static final String CLASSES = "lambdaql.functions.classes";

    /** Makes the contributor (called by {@code ServiceLoader}). */
    public LambdaQlFunctionContributor() {
    }

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        var services = contributions.getServiceRegistry();
        Object setting = services.requireService(ConfigurationService.class).getSettings().get(CLASSES);
        if (setting == null || setting.toString().isBlank()) return;
        ClassLoaderService loader = services.requireService(ClassLoaderService.class);
        SqmFunctionRegistry registry = contributions.getFunctionRegistry();
        Map<String, List<Method>> byKey = new LinkedHashMap<>();
        for (String name : setting.toString().split(",")) {
            if (name.isBlank()) continue;
            Class<?> type = loader.classForName(name.trim());
            List<Method> methods = new ArrayList<>(List.of(type.getDeclaredMethods()));
            // Kotlin: the functions are methods of the interface companion
            for (Class<?> inner : type.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Companion")) methods.addAll(List.of(inner.getDeclaredMethods()));
            }
            for (Method m : methods) {
                if (!m.isAnnotationPresent(SqlFunction.class)) continue;
                byKey.computeIfAbsent(SqlFunctions.describe(m).key(), k -> new ArrayList<>()).add(m);
            }
        }
        for (Map.Entry<String, List<Method>> e : byKey.entrySet()) {
            SqlFunctions.Info f = SqlFunctions.describe(e.getValue().get(0));
            if (!SqlFunctions.isRegistered(f.key()) && registry.findFunctionDescriptor(f.key()) != null) {
                throw LqlMessage.FUNCTION_BUILTIN_NAME.exception(f.key());     // FN8: a built-in would win silently
            }
            int min = Integer.MAX_VALUE, max = 0;
            for (Method m : e.getValue()) {
                min = Math.min(min, m.getParameterCount());
                max = Math.max(max, m.getParameterCount());
            }
            var builder = registry.namedDescriptorBuilder(f.key(), f.sql())
                    .setInvariantType(contributions.getTypeConfiguration().getBasicTypeForJavaType(box(f.method().getReturnType())));
            if (min == max) builder.setExactArgumentCount(min);
            else builder.setArgumentCountBetween(min, max);
            builder.register();
            SqlFunctions.markRegistered(f.key());
        }
    }

    private static Class<?> box(Class<?> c) {
        if (c == double.class) return Double.class;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        return c;
    }
}
