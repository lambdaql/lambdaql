package org.lambdaql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows an outside sort (a Spring {@code Sort}, {@code SortSpec.ofPath}) by this entity field (ROADMAP D43).
 * It is used only when the sort policy has no rules for the entity from a {@code SortPolicy} bean or properties.
 * <ul>
 *   <li>{@code @Sortable} allows the field name, for example {@code price}.</li>
 *   <li>{@code @Sortable("newest")} allows the outside name {@code newest} for this field (an alias).</li>
 *   <li>On an association field, {@code @Sortable(path = "name")} allows {@code customer.name}.</li>
 * </ul>
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/query/src/test/java/org/lambdaql/query/SortPolicyTest.java">SortPolicyTest</a>):
 * {@snippet class="org.lambdaql.query.SortPolicyTest" region="annotation"}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Sortable {

    /**
     * The outside name (an alias). Empty means the path itself is the name.
     *
     * @return the outside name, or empty
     */
    String value() default "";

    /**
     * For an association field: the property of the associated entity, for example {@code name} for
     * {@code customer.name}. Empty for a value field.
     *
     * @return the property path inside the association, or empty
     */
    String path() default "";
}
