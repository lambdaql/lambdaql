package org.lambdaql.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Constants compared with converted columns ({@code @Convert}) go through the converter (ROADMAP D48, B1).
 * lambdaQL writes a constant from the lambda as a literal (D37); Hibernate (6.6 and 7.4) converts a literal that is compared
 * with a converted attribute, so the result is right on H2, MariaDB and Oracle (measured).
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = ConvertTest.Context.class)
@ActiveProfiles("test")
class ConvertTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void seed() {
        qb = new QueryBuilder(emf);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createQuery("delete from Coded").executeUpdate();
        em.persist(new Coded("abc", true));
        em.persist(new Coded("xyz", false));
        em.getTransaction().commit();
        em.close();
    }

    private static List<String> codes(List<Coded> rows) {
        return rows.stream().map(Coded::getCode).sorted().toList();
    }

    @Test
    void literalAndBound() {
        String wanted = "abc";
        // @start region="convert"
        // code is stored reversed ("abc" as "cba"); vip is stored as 'Y'/'N' (YesNoConverter)
        SelectQuery<Coded> literal = qb.from(Coded.class).where(c -> c.getCode().equals("abc"));
        assertEquals("select e0 from Coded e0 where e0.code = 'abc'", literal.toHql().hql());
        assertEquals(List.of("abc"), codes(literal.toList()));       // Hibernate converts the literal
        // @end
        assertEquals(List.of("abc"), codes(qb.from(Coded.class).where(c -> c.getCode().equals(wanted)).toList()));
        assertEquals(List.of("abc"), codes(qb.from(Coded.class).where(c -> c.getVip()).toList()));
        assertEquals(List.of("abc"), codes(qb.from(Coded.class).where(c -> c.getVip() == Boolean.TRUE).toList()));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
