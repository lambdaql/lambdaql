package org.lambdaql.engine.sem;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.lambdaql.engine.ir.Assignment;
import org.lambdaql.engine.load.LambdaRef;
import org.lambdaql.engine.load.SerializedLambdaExtractor;
import org.lambdaql.query.Assign;
import org.lambdaql.query.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lambdaql.Dml.value;
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
    void valueForm() {
        // @start region="read-value"
        // Dml.value(column, value) for entities without setters; constants stay literals, also when they are boxed
        // for the generic value(V, V) (D37, D49)
        assertEquals("[col0.status := 2, col0.price := 1.0]",
                setters(o -> { value(o.getStatus(), (short) 2); value(o.getPrice(), 1.0); }));
        // setters and value can be mixed in one lambda
        assertEquals("[col0.status := 2, col0.customer.id := 1]",
                setters(o -> { o.setStatus((short) 2); value(o.getCustomer().getId(), 1); }));
        // @end
        // the column follows the setter rules: only the id of an association (U6), and a getter call on the target
        LambdaQLException other = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getCustomer().getName(), "x")));
        assertEquals("LQL-3050", other.code(), other.getMessage());
        String name = "x";
        LambdaQLException notColumn = assertThrows(LambdaQLException.class, () -> setters(o -> value(name, "y")));
        assertEquals("LQL-3039", notColumn.code(), notColumn.getMessage());
    }

    @Test
    void valueTypes() {
        // @start region="value-type"
        // value(V, V) compiles with any two types, so lambdaQL checks the value type against the column (U20)
        assertEquals("[col0.status := 2, col0.price := 1]", setters(o -> { value(o.getStatus(), 2); value(o.getPrice(), 1); }));
        String text = "abc";
        LambdaQLException wrong = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), text)));
        assertEquals("LQL-3065", wrong.code(), wrong.getMessage());
        // a decimal into a whole number column would lose the fraction
        LambdaQLException fraction = assertThrows(LambdaQLException.class, () -> setters(o -> value(o.getStatus(), 1.5)));
        assertEquals("LQL-3065", fraction.code(), fraction.getMessage());
        // @end
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
