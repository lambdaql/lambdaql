package org.lambdaql.query;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.LambdaQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lambdaql.query.By.*;

/**
 * The sort functions of {@link By} (ROADMAP D59): one {@code orderBy} takes a plain key and the functions, a method
 * reference is a key, a Direction argument needs a plain key, and the functions do not run outside a sort lambda.
 */
@SpringBootTest(classes = SortApiTest.Context.class)
@ActiveProfiles("test")
class SortApiTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void setUp() {
        qb = new QueryBuilder(emf);
    }

    @Test
    void plainKeyAndFunctionsAreTheSame() {
        String plain = qb.from(Order.class).orderBy(o -> o.getPrice()).toHql().hql();
        String function = qb.from(Order.class).orderBy(o -> asc(o.getPrice())).toHql().hql();
        String reference = qb.from(Order.class).orderBy(Order::getPrice).toHql().hql();
        assertEquals("select e0 from Order e0 order by e0.price asc", plain);
        assertEquals(plain, function);
        assertEquals(plain, reference);
        // a method reference is one key in SortSpec too
        assertEquals(plain, qb.from(Order.class).orderBy(SortSpec.of(Order.class, Order::getPrice)).toHql().hql());
        assertEquals("select e0 from Order e0 order by e0.description asc nulls last",
                qb.from(Order.class).orderBy(o -> asc(o.getDescription(), Nulls.LAST)).toHql().hql());
        assertEquals("select e0 from Order e0 order by e0.description desc nulls first",
                qb.from(Order.class).orderBy(o -> By.desc(o.getDescription(), Nulls.FIRST)).toHql().hql());
    }

    @Test
    void directionNeedsOneKey() {
        LambdaQLException e = assertThrows(LambdaQLException.class,
                () -> qb.from(Order.class).orderBy(o -> desc(o.getPrice()), Direction.ASC));
        assertEquals("LQL-3069", e.code());
    }

    @Test
    void functionsDoNotRunOutsideASortLambda() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> desc(1));
        assertTrue(e.getMessage().startsWith("[LQL-4046]"), e.getMessage());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = "org.lambdaql.query")
    static class Context {
    }
}
