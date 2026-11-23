package org.lambdaql.spring;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lambdaql.SortNotAllowedException;
import org.lambdaql.query.SortMode;
import org.lambdaql.query.SortPolicy;
import org.lambdaql.query.SortRules;
import org.lambdaql.spring.sample.Member;
import org.lambdaql.spring.sample.MemberRepository;
import org.lambdaql.spring.sample.Team;
import org.lambdaql.spring.sample.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The examples of the Java manual, chapter 12 (Spring Data JPA).
 * The context scans this package and its sub-packages, so it has the sample repositories and {@link ManualMemberRepository}.
 */
@SpringBootTest(classes = ManualJavaSpringTest.App.class)
@ActiveProfiles("test")
class ManualJavaSpringTest {

    /** A Spring Boot application for this test: entities and repositories of org.lambdaql.spring and below. */
    @Configuration
    @EnableAutoConfiguration
    static class App {
    }

    // @start region="sort-policy-bean"
    @Configuration
    static class SortConfig {

        @Bean
        public SortPolicy sortPolicy() {
            return SortPolicy.of(SortMode.STRICT,
                    SortRules.of(Member.class)
                            .allow(m -> m.getName())
                            .alias("level", m -> m.getGrade()));
        }
    }
    // @end

    @Autowired
    private ManualMemberRepository members;
    @Autowired
    private MemberRepository repository;
    @Autowired
    private TeamRepository teams;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private EntityManager entityManager;

    private List<Member> all;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        teams.deleteAll();
        Team a = teams.save(new Team("A"));
        Team b = teams.save(new Team("B"));
        repository.save(new Member("Kim", true, 3, a));
        repository.save(new Member("Lee", true, 5, b));
        repository.save(new Member("Park", false, null, a));
        repository.save(new Member("Choi", true, 1, null));
        all = repository.findAll();
    }

    @Test
    void repositoryDefaultMethods() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertEquals(all.stream().filter(Member::isActive).map(Member::getId).sorted().toList(),
                members.activeMembers().stream().map(Member::getId).sorted().toList());
        assertEquals("Kim", members.byName("Kim").orElseThrow().getName());
        assertEquals(List.of("Lee", "Kim"), members.topGraded(2).stream().map(Member::getName).toList());
        assertEquals(1, (int) tx.execute(s -> members.gradeInactive(7)));        // Park
        assertEquals(1, (int) tx.execute(s -> members.deleteWithoutTeam()));     // Choi
        assertEquals(1, (int) tx.execute(s -> members.addGuest("Guest")));
        assertEquals(4, members.count());
    }

    @Test
    void otherFragments() {
        // @start region="fragments"
        long byHand = repository.countByHand();                                               // hand-written fragment
        List<Member> bySpec = repository.findAll((root, query, cb) -> cb.isTrue(root.get("active")));   // Specification: three arguments
        List<Member> byLambda = repository.findAll(m -> m.isActive());                        // lambdaQL Where: one argument
        long total = repository.count();                                                      // Spring Data's own count()
        // @end
        long active = all.stream().filter(Member::isActive).count();
        assertEquals(all.size(), byHand);
        assertEquals(active, bySpec.size());
        assertEquals(active, byLambda.size());
        assertEquals(all.size(), total);
    }

    @Test
    void readOutsideTransaction() {
        // @start region="detached"
        // outside a transaction each call uses a new EntityManager: the returned entity is detached
        Member kim = repository.findOne(m -> m.getName().equals("Kim")).orElseThrow();
        boolean managed = new TransactionTemplate(txManager).execute(s -> entityManager.contains(kim));
        assertFalse(managed);
        // @end
        Boolean inTx = new TransactionTemplate(txManager).execute(s ->
                entityManager.contains(repository.findOne(m -> m.getName().equals("Kim")).orElseThrow()));
        assertTrue(inTx);
    }

    /** The same members, in a context that also has the SortPolicy bean of {@link SortConfig}. */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(classes = {App.class, SortConfig.class})
    @ActiveProfiles("test")
    class WithSortPolicyBean {

        @Autowired
        private MemberRepository sorted;

        @Test
        void beanIsRegistered() {
            sorted.deleteAll();
            sorted.save(new Member("Kim", true, 3, null));
            sorted.save(new Member("Lee", true, 5, null));
            sorted.save(new Member("Han", true, null, null));
            // the client sends "level"; the policy bean sorts by grade
            List<Member> byLevel = sorted.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
            assertEquals(List.of("Lee", "Kim", "Han"), byLevel.stream().map(Member::getName).toList());
            assertThrows(SortNotAllowedException.class, () -> sorted.findAll(m -> m.isActive(), Sort.by("grade")));
            List<Member> byName = sorted.findAll(m -> m.isActive(), Sort.by("name"));
            assertEquals(byName.stream().map(Member::getName).sorted(Comparator.naturalOrder()).toList(),
                    byName.stream().map(Member::getName).toList());
        }
    }
}
