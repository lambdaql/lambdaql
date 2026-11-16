package org.lambdaql.spring;

import org.lambdaql.query.Direction;
import org.lambdaql.query.SortSpec;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyPath;

/**
 * Turns a Spring {@link Sort} into a lambdaQL {@link SortSpec}.
 * <ul>
 *   <li>Each property is checked with Spring Data's {@link PropertyPath}, so an unknown name gives
 *       {@code PropertyReferenceException}, the same as Spring Data's own repositories.
 *       Then lambdaQL checks again that the path ends at a basic value ({@link SortSpec#ofPath}).</li>
 *   <li>{@code NullHandling.NATIVE} becomes the lambdaQL default ({@link Direction#ASC} / {@link Direction#DESC},
 *       ROADMAP D16), so the order is the same on every database.</li>
 *   <li>{@code ignoreCase} is not supported yet (it needs the case-insensitive rules of ROADMAP D24).</li>
 * </ul>
 *
 * <p>Example (from the test <a href="https://github.com/codebit0/lambdaql/blob/main/spring-data-jpa/src/test/java/org/lambdaql/spring/ExecutorTest.java">ExecutorTest</a>):
 * {@snippet class="org.lambdaql.spring.ExecutorTest" region="find-all-spring-sort"}
 */
public final class SpringSorts {

    private SpringSorts() {
    }

    /**
     * Converts a sorted Spring {@link Sort}.
     *
     * @param type the entity class
     * @param sort a sort with at least one order
     * @param <T>  the entity type
     * @return the same order as a spec
     * @throws IllegalArgumentException                                     if the sort has no order
     * @throws org.springframework.data.mapping.PropertyReferenceException if a property does not exist
     * @throws UnsupportedOperationException                                if an order uses {@code ignoreCase}
     */
    public static <T> SortSpec<T> toSpec(Class<T> type, Sort sort) {
        SortSpec<T> spec = null;
        for (Sort.Order order : sort) {
            if (order.isIgnoreCase()) {
                throw new UnsupportedOperationException("ignoreCase sort is not supported yet: " + order.getProperty());
            }
            String path = PropertyPath.from(order.getProperty(), type).toDotPath();
            SortSpec<T> part = SortSpec.ofPath(type, path, direction(order));
            spec = spec == null ? part : spec.then(part);
        }
        if (spec == null) throw new IllegalArgumentException("the sort has no order");
        return spec;
    }

    /**
     * Converts the direction and NULL handling of one order.
     *
     * @param order a Spring order
     * @return the lambdaQL direction
     */
    static Direction direction(Sort.Order order) {
        boolean asc = order.isAscending();
        return switch (order.getNullHandling()) {
            case NATIVE -> asc ? Direction.ASC : Direction.DESC;
            case NULLS_FIRST -> asc ? Direction.ASC_NULLS_FIRST : Direction.DESC_NULLS_FIRST;
            case NULLS_LAST -> asc ? Direction.ASC_NULLS_LAST : Direction.DESC_NULLS_LAST;
        };
    }
}
