package org.lambdaql.query;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The analysis cache is shared by every {@link QueryBuilder} on one persistence unit: a new builder does not analyze
 * a lambda again.
 */
@SpringBootTest(classes = AnalysisCacheTest.Context.class)
@ActiveProfiles("test")
class AnalysisCacheTest {

    @Autowired
    private EntityManagerFactory emf;

    private static String hql(QueryBuilder qb, int min) {
        // the same lambda class every time this method runs
        return qb.from(Order.class).where(o -> o.getStatus() > min).orderBy((o, by) -> by.desc(o.getPrice())).toHql().hql();
    }

    @Test
    void sharedAcrossBuilders() {
        String first = hql(new QueryBuilder(emf), 1);
        long before = QueryContext.ANALYSES.get();
        // a new builder per call (for example per request): the where and sort lambdas are not analyzed again
        for (int i = 0; i < 5; i++) assertEquals(first, hql(new QueryBuilder(emf), 1));
        assertEquals(before, QueryContext.ANALYSES.get());
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
