package org.lambdaql.engine.sem;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.ir.Assignment;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.query.Assign;
import org.lambdaql.query.AssignBy;
import org.lambdaql.query.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Examples for {@link AssignmentReader}: assignment lambdas become column/value pairs.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = AssignmentReaderTest.Context.class)
@ActiveProfiles("test")
class AssignmentReaderTest {

    @Autowired
    private EntityManagerFactory emf;

    private String setters(Assign<Order> assign) {
        EntityModel model = new JpaEntityModel(emf.getMetamodel());
        // @start region="read-assign"
        LambdaRef ref = SerializedLambdaExtractor.extract(assign);
        List<Assignment> items = AssignmentReader.readSetters(ref, List.of(Order.class), -1, model);
        // @end
        return items.toString();
    }

    private String builder(AssignBy<Order> assign) {
        EntityModel model = new JpaEntityModel(emf.getMetamodel());
        // @start region="read-by"
        LambdaRef ref = SerializedLambdaExtractor.extract(assign);
        List<Assignment> items = AssignmentReader.readBuilder(ref, List.of(Order.class), model);
        // @end
        return items.toString();
    }

    @Test
    void setterForm() {
        String memo = "x";
        assertEquals("[col0.status := 2, col0.description := host(captured0)]",
                setters(o -> { o.setStatus((short) 2); o.setDescription(memo); }));
        assertEquals("[col0.price := (col0.price * 1.1)]", setters(o -> o.setPrice(o.getPrice() * 1.1)));
        assertEquals("[col0.customer.id := 1]", setters(o -> o.getCustomer().setId(1)));                // the foreign key
        assertEquals("[col0.cnt := coalesce(col0.cnt, 0)]",
                setters(o -> o.setCnt(java.util.Objects.requireNonNullElse(o.getCnt(), 0))));         // U2
    }

    @Test
    void builderForm() {
        // constants stay literals, also when they are boxed for the generic set(V, V) (D37)
        assertEquals("[col0.status := 2, col0.price := 1.0]",
                builder((o, by) -> by.set(o.getStatus(), (short) 2).set(o.getPrice(), 1.0)));
        // the lambda returns nothing, so statements work too (and Kotlin by[o.status] = 2)
        assertEquals("[col0.status := 2, col0.price := 1.0]",
                builder((o, by) -> { by.set(o.getStatus(), (short) 2); by.set(o.getPrice(), 1.0); }));
    }

    @Test
    void rules() {
        // @start region="rules"
        // U1: only setters; no condition, no local variable
        assertThrows(LambdaQLException.class, () -> setters(o -> { if (o.isActive()) o.setStatus((short) 1); }));
        // U6: an association's other properties belong to another table
        assertThrows(LambdaQLException.class, () -> setters(o -> o.getCustomer().setName("x")));
        // U3: a value reads a column written before
        assertThrows(LambdaQLException.class, () -> AssignmentReader.checkRules(
                AssignmentReader.readSetters(SerializedLambdaExtractor.extract((Assign<Order>) o -> {
                    o.setPrice(1);
                    o.setTex((float) o.getPrice());
                }), List.of(Order.class), -1, new JpaEntityModel(emf.getMetamodel())), "test"));
        // @end
    }

    /** Minimal Spring context: H2 from application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
