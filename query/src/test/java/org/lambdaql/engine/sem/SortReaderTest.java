package org.lambdaql.engine.sem;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.analyzer.LambdaAnalysisException;
import org.lambdaql.engine.ir.Expr;
import org.lambdaql.engine.ir.SortItem;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.query.Direction;
import org.lambdaql.query.Order;
import org.lambdaql.query.SortBy;
import org.lambdaql.query.SortKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Examples for {@link SortReader}: sort lambdas become {@link SortItem} lists.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SortReaderTest.Context.class)
@ActiveProfiles("test")
class SortReaderTest {

    @Autowired
    private EntityManagerFactory emf;

    private String items(SortBy<Order> spec) {
        EntityModel model = new JpaEntityModel(emf.getMetamodel());
        // @start region="read-sort"
        LambdaRef ref = SerializedLambdaExtractor.extract(spec);
        List<SortItem> items = SortReader.readSortBy(ref, List.of(Order.class), model);
        // @end
        return items.toString();
    }

    private String key(SortKey<Order> key) {
        EntityModel model = new JpaEntityModel(emf.getMetamodel());
        // @start region="read-key"
        LambdaRef ref = SerializedLambdaExtractor.extract(key);
        Expr column = SortReader.readSortKey(ref, Order.class, model);
        // @end
        return column.toString();
    }

    @Test
    void chainBecomesItems() {
        assertEquals("[desc(col0.price), asc(col0.id)]", items((o, by) -> by.desc(o.getPrice()).asc(o.getId())));
        // true / false come as the int constants 1 / 0
        assertEquals("[asc(col0.description, 0)]", items((o, by) -> by.asc(o.getDescription(), false)));
        assertEquals("[sort(col0.customer.name, DESC_NULLS_LAST)]",
                items((o, by) -> by.sort(o.getCustomer().getName(), Direction.DESC_NULLS_LAST)));
        Direction dir = Direction.ASC;
        assertEquals("[sort(col0.price, host(captured0))]", items((o, by) -> by.sort(o.getPrice(), dir)));
    }

    @Test
    void oneKeyAndGetterReference() {
        assertEquals("col0.price", key(o -> o.getPrice()));
        assertEquals("col0.price", key(Order::getPrice));
        assertEquals("col0.customer.name", key(o -> o.getCustomer().getName()));
    }

    @Test
    void notAChain() {
        assertThrows(LambdaAnalysisException.class, () -> items((o, by) -> by));
        assertThrows(LambdaAnalysisException.class,
                () -> items((o, by) -> by.asc(o.getPrice(), o.isActive())));   // NULL position from the entity
    }

    @Test
    void directionText() {
        assertEquals(Direction.DESC_NULLS_LAST, Direction.parse("desc-nulls-last"));
        assertEquals(Direction.ASC_NULLS_FIRST, Direction.parse(" asc nulls first "));
        assertEquals(Direction.DESC_NULLS_FIRST, Direction.ASC_NULLS_LAST.reversed());
        assertThrows(IllegalArgumentException.class, () -> Direction.parse("up"));
    }

    /** Minimal Spring context: H2 from application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
