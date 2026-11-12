package org.lambdaql.engine.sem;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.lambdaql.query.Customer;
import org.lambdaql.query.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examples for {@link JpaEntityModel} with the test entities {@link Order} and {@link Customer}.
 * The region markers are used by Javadoc {@code {@snippet}} tags.
 */
@SpringBootTest(classes = JpaEntityModelTest.Context.class)
@ActiveProfiles("test")
class JpaEntityModelTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void properties() {
        // @start region="entity-model"
        EntityModel model = new JpaEntityModel(entityManagerFactory.getMetamodel());

        assertTrue(model.isEntity(Order.class));
        assertFalse(model.isEntity(String.class));
        assertEquals("Order", model.entityName(Order.class));

        EntityModel.Property price = model.property(Order.class, "price").orElseThrow();
        assertEquals(EntityModel.Kind.BASIC, price.kind());
        assertEquals(Double.class, price.javaType());   // double -> Double
        assertTrue(price.nonNull());                    // primitive, so never NULL

        EntityModel.Property customer = model.property(Order.class, "customer").orElseThrow();
        assertEquals(EntityModel.Kind.ASSOCIATION, customer.kind());
        assertEquals(Customer.class, customer.javaType());

        assertTrue(model.property(Order.class, "noSuchProperty").isEmpty());
        // @end
    }

    @Test
    void idProperty() {
        EntityModel model = new JpaEntityModel(entityManagerFactory.getMetamodel());
        // @start region="entity-model-id"
        EntityModel.Property id = model.property(Order.class, "id").orElseThrow();
        assertTrue(id.id());
        assertTrue(id.nonNull());                       // an id is never NULL
        assertEquals(Optional.of("id"), model.idName(Order.class));
        // @end
    }

    /** Minimal Spring context: H2 from application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
