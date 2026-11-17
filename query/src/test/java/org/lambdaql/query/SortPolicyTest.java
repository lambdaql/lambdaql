package org.lambdaql.query;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.SortNotAllowedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the outside sort policy (ROADMAP D43): sorts made from strings are checked against the rules of the
 * persistence unit; sorts written as lambdas are never checked.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SortPolicyTest.Context.class)
@ActiveProfiles("test")
class SortPolicyTest {

    @Autowired
    private EntityManagerFactory emf;

    private QueryBuilder qb;

    @BeforeEach
    void setUp() {
        qb = new QueryBuilder(emf);
        // @start region="rules"
        SortRules<Order> orderRules = SortRules.of(Order.class)
                .allow(o -> o.getPrice())                        // the name "price"
                .allow(o -> o.getCustomer().getName())           // an association path: allowed only when listed
                .alias("newest", o -> o.getUpdateAt());          // the outside name "newest" sorts by updateAt
        // @end
        // @start region="strict"
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules));
        // @end
    }

    @AfterEach
    void tearDown() {
        SortPolicy.unregister(emf);           // other tests share this persistence unit
    }

    private String hql(String name, Direction direction) {
        return qb.from(Order.class).orderBy(SortSpec.ofPath(Order.class, name, direction)).toHql().hql();
    }

    @Test
    void allowedAndAlias() {
        // @start region="allowed"
        assertEquals("select e0 from Order e0 order by e0.price asc", hql("price", Direction.ASC));
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first", hql("customer.name", Direction.ASC));
        // the alias: the client sends "newest", lambdaQL sorts by updateAt
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
        // @end
    }

    @Test
    void strict() {
        // @start region="rejected"
        // not in the rules, an unlisted association path, the path behind an alias, an entity without rules
        for (String name : new String[]{"description", "customer.grade", "updateAt"}) {
            assertEquals("LQL-4040", assertThrows(SortNotAllowedException.class, () -> hql(name, Direction.ASC)).code());
        }
        assertThrows(SortNotAllowedException.class,
                () -> qb.from(Item.class).orderBy(SortSpec.ofPath(Item.class, "qty", Direction.ASC)).toHql());
        // a sort written as a lambda is code, not outside input: never checked
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
                qb.from(Order.class).orderBy((o, by) -> by.asc(o.getDescription())).toHql().hql());
        // @end
    }

    @Test
    void annotation() {
        // @start region="annotation"
        // Customer has no rules in the policy, so its @Sortable fields are the rules:
        //   @Sortable private String name;   @Sortable("level") private Integer grade;
        assertEquals("select e0 from Customer e0 order by e0.name asc nulls first",
                qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "name", Direction.ASC)).toHql().hql());
        assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last",
                qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "level", Direction.DESC)).toHql().hql());
        assertThrows(SortNotAllowedException.class,
                () -> qb.from(Customer.class).orderBy(SortSpec.ofPath(Customer.class, "id", Direction.ASC)).toHql());
        // @end
    }

    @Test
    void stringPaths() {
        // @start region="string-paths"
        // the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
                SortRules.of(Order.class).allowPath("price", "customer.name").aliasPath("newest", "updateAt")));
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", hql("newest", Direction.DESC));
        assertThrows(SortNotAllowedException.class, () -> hql("description", Direction.ASC));
        // @end
    }

    @Test
    void warnAndOff() {
        // @start region="warn"
        // WARN (the default): the sort runs and LQL-5003 is logged once per entity and name
        SortPolicy.register(emf, SortPolicy.of(SortMode.WARN));
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first", hql("description", Direction.ASC));
        // @end
        SortPolicy.register(emf, SortPolicy.of(SortMode.OFF));
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first", hql("description", Direction.ASC));
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    static class Context {
    }
}
