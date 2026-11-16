package org.lambdaql.spring;

import org.junit.jupiter.api.Test;
import org.lambdaql.query.QueryBuilder;
import org.lambdaql.spring.sample.Member;
import org.lambdaql.spring.sample.MemberRepository;
import org.lambdaql.spring.sample.SampleApplication;
import org.lambdaql.spring.sample.Team;
import org.lambdaql.spring.sample.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the Spring Boot auto-configuration: the {@link QueryBuilder} bean,
 * and one shared executor used by two repositories with different entities.
 */
@SpringBootTest(classes = SampleApplication.class)
@ActiveProfiles("test")
class AutoConfigurationTest {

    @Autowired
    private QueryBuilder qb;
    @Autowired
    private MemberRepository members;
    @Autowired
    private TeamRepository teams;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void queryBuilderBean() {
        members.deleteAll();
        teams.deleteAll();
        // @start region="query-builder-bean"
        // inject it into a service: it joins the current transaction
        TransactionTemplate tx = new TransactionTemplate(txManager);
        long seen = tx.execute(status -> {
            members.save(new Member("Kim", true, 3, null));                       // not flushed yet
            return qb.from(Member.class).where(m -> m.getName().equals("Kim")).count();
        });
        // @end
        assertEquals(1, seen);
    }

    @Test
    void oneExecutorManyRepositories() {
        members.deleteAll();
        teams.deleteAll();
        teams.save(new Team("A"));
        members.save(new Member("Kim", true, 3, null));
        members.save(new Member("Lee", true, 3, null));
        assertEquals(1, teams.count(t -> t.getName().equals("A")));             // Team, not Member
        assertEquals(2, members.count(m -> m.isActive()));
        assertEquals(1, teams.query().where(t -> t.getName() != null).count());
    }
}
