package org.lambdaql.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.query.SortSpec;
import org.lambdaql.spring.sample.Member;
import org.lambdaql.spring.sample.MemberRepository;
import org.lambdaql.spring.sample.SampleApplication;
import org.lambdaql.spring.sample.Team;
import org.lambdaql.spring.sample.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link LambdaQlExecutor} in a Spring Boot application with Spring Data JPA.
 * Each test also computes the answer in Java from the seeded rows.
 * The region markers are used by Javadoc snippet tags.
 */
@SpringBootTest(classes = SampleApplication.class)
@ActiveProfiles("test")
class ExecutorTest {

    @Autowired
    private MemberRepository repository;
    @Autowired
    private TeamRepository teams;
    @Autowired
    private PlatformTransactionManager txManager;

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
        repository.save(new Member("Jung", false, 4, b));
        repository.save(new Member("Han", true, null, b));
        all = repository.findAll();
    }

    private static List<Long> ids(List<Member> members) {
        return members.stream().map(Member::getId).toList();
    }

    private static final Comparator<Member> BY_ID = Comparator.comparing(Member::getId);
    private static final Comparator<Member> GRADE_DESC_NULLS_LAST =
            Comparator.comparing(Member::getGrade, Comparator.nullsFirst(Comparator.<Integer>naturalOrder())).reversed();

    @Test
    void defaultMethods() {
        assertEquals(all.stream().filter(Member::isActive).map(Member::getId).sorted().toList(),
                ids(repository.activeMembers()).stream().sorted().toList());
        assertEquals("Kim", repository.byName("Kim").orElseThrow().getName());
        assertTrue(repository.byName("Nobody").isEmpty());
        assertEquals(all.stream().filter(Member::isActive).sorted(GRADE_DESC_NULLS_LAST.thenComparing(BY_ID))
                .limit(2).map(Member::getId).toList(), ids(repository.topGraded(2)));
    }

    @Test
    void findAllSorted() {
        // @start region="find-all-sort"
        SortSpec<Member> byGrade = SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade()).asc(m.getId()));
        List<Member> active = repository.findAll(m -> m.isActive(), byGrade);
        // @end
        assertEquals(all.stream().filter(Member::isActive).sorted(GRADE_DESC_NULLS_LAST.thenComparing(BY_ID))
                .map(Member::getId).toList(), ids(active));
    }

    @Autowired
    private jakarta.persistence.EntityManagerFactory emf;

    @Test
    void sortPolicyWithSpringSort() {
        // @start region="sort-policy"
        // usually a @Bean SortPolicy; the Boot auto-configuration registers it for the EntityManagerFactory
        org.lambdaql.query.SortPolicy.register(emf, org.lambdaql.query.SortPolicy.of(org.lambdaql.query.SortMode.STRICT,
                org.lambdaql.query.SortRules.of(Member.class)
                        .allow(m -> m.getName())
                        .alias("level", m -> m.getGrade())));
        try {
            // the client sends "level"; lambdaQL sorts by grade
            List<Member> rows = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")));
            // a property that is not in the rules
            assertThrows(org.lambdaql.SortNotAllowedException.class,
                    () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
            // @end
            assertEquals(all.stream().filter(Member::isActive).sorted(GRADE_DESC_NULLS_LAST.thenComparing(Member::getName))
                    .map(Member::getId).toList(), ids(rows));
        } finally {
            org.lambdaql.query.SortPolicy.unregister(emf);
        }
    }

    @Test
    void findAllWithSpringSort() {
        // @start region="find-all-spring-sort"
        List<Member> rows = repository.findAll(m -> m.isActive(),
                Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")));
        // a property that does not exist: the same error as Spring Data's own repositories
        assertThrows(PropertyReferenceException.class,
                () -> repository.findAll(m -> m.isActive(), Sort.by("nickname")));
        // @end
        assertEquals(all.stream().filter(Member::isActive).sorted(GRADE_DESC_NULLS_LAST.thenComparing(BY_ID))
                .map(Member::getId).toList(), ids(rows));
        assertThrows(UnsupportedOperationException.class,
                () -> repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.asc("name").ignoreCase())));
        assertEquals(all.stream().filter(Member::isActive).count(),
                repository.findAll(m -> m.isActive(), Sort.unsorted()).size());
    }

    @Test
    void page() {
        // @start region="page"
        Page<Member> page = repository.findAll(m -> m.isActive(), PageRequest.of(1, 2, Sort.by("name")));
        // page 1 (the second page) of the active members by name; the total comes from a count query
        // @end
        List<Member> byName = all.stream().filter(Member::isActive)
                .sorted(Comparator.comparing(Member::getName).thenComparing(BY_ID)).toList();
        assertEquals(byName.size(), page.getTotalElements());
        assertEquals(ids(byName.subList(2, 4)), ids(page.getContent()));
        assertEquals(2, page.getTotalPages());

        Page<Member> unsortedFirst = repository.findAll(m -> m.isActive(), PageRequest.of(0, 3));
        assertEquals(all.stream().filter(Member::isActive).map(Member::getId).sorted().limit(3).toList(),
                ids(unsortedFirst.getContent()));                                // without a sort: by id
        assertEquals(byName.size(), repository.findAll(m -> m.isActive(), Pageable.unpaged()).getTotalElements());
    }

    @Test
    void findSet() {
        // @start region="find-set"
        java.util.SequencedSet<Member> active = repository.findSet(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("id")));
        java.util.SequencedSet<Member> any = repository.findSet(m -> m.isActive());
        // @end
        assertEquals(all.stream().filter(Member::isActive).sorted(GRADE_DESC_NULLS_LAST.thenComparing(BY_ID)).map(Member::getId).toList(),
                active.stream().map(Member::getId).toList());
        assertEquals(active.size(), any.size());
    }

    @Test
    void findFirstAndOne() {
        // @start region="find-first"
        Optional<Member> best = repository.findFirst(m -> m.isActive(),
                SortSpec.of(Member.class, (m, by) -> by.desc(m.getGrade())));
        // @end
        assertEquals("Lee", best.orElseThrow().getName());
        assertEquals(all.stream().filter(Member::isActive).map(Member::getId).min(Comparator.naturalOrder()).orElseThrow(),
                repository.findFirst(m -> m.isActive()).orElseThrow().getId());
        assertEquals("Lee", repository.findFirst(m -> m.isActive(), Sort.by(Sort.Order.desc("grade"))).orElseThrow().getName());

        // @start region="find-one"
        Optional<Member> kim = repository.findOne(m -> m.getName().equals("Kim"));
        // two or more rows: Spring's IncorrectResultSizeDataAccessException
        assertThrows(IncorrectResultSizeDataAccessException.class, () -> repository.findOne(m -> m.isActive()));
        // @end
        assertEquals("Kim", kim.orElseThrow().getName());
    }

    @Test
    void countAndExists() {
        // @start region="count-exists"
        long active = repository.count(m -> m.isActive());
        boolean anyInTeamB = repository.exists(m -> m.getTeam().getName().equals("B"));
        // @end
        assertEquals(all.stream().filter(Member::isActive).count(), active);
        assertTrue(anyInTeamB);
        assertFalse(repository.exists(m -> m.getGrade() != null && m.getGrade() > 10));
    }

    @Test
    void worksWithOtherFragments() {
        assertEquals(all.size(), repository.countByHand());                          // hand-written Impl class
        assertEquals(all.stream().filter(Member::isActive).count(),
                repository.findAll((root, query, cb) -> cb.isTrue(root.get("active"))).size());   // Specification
        assertEquals(all.size(), repository.count());                                // Spring Data's own count()
    }

    @Test
    void dmlInTransaction() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        long inactive = all.stream().filter(m -> !m.isActive()).count();
        long noTeam = all.stream().filter(m -> m.getTeam() == null).count();
        assertEquals(inactive, (long) tx.execute(s -> repository.gradeInactive(7)));
        assertEquals(inactive, repository.count(m -> m.getGrade() != null && m.getGrade() == 7));
        assertEquals(noTeam, (long) tx.execute(s -> repository.deleteWithoutTeam()));
        assertEquals(1, (int) tx.execute(s -> repository.addGuest("Guest")));
        assertTrue(repository.exists(m -> m.getName().equals("Guest")));
        // without a transaction the shared EntityManager cannot run a bulk statement;
        // inside a repository method Spring translates the JPA error
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> repository.gradeInactive(1));

        long teamId = teams.findAll().get(0).getId();
        // @start region="upsert"
        int changed = tx.execute(s -> teams.upsertQuery().values(t -> { t.setId(teamId); t.setName("A2"); }).execute());
        // @end
        assertTrue(changed >= 1);
        assertEquals("A2", teams.findById(teamId).orElseThrow().getName());
    }

    /** A list element for insertAll. */
    record NewMember(String name, int grade) {
    }

    @Test
    void dmlShortcuts() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        long inactive = all.stream().filter(m -> !m.isActive()).count();
        long noTeam = all.stream().filter(m -> m.getTeam() == null).count();
        tx.executeWithoutResult(status -> {
            // @start region="dml-shortcuts"
            int changed = repository.updateAll(m -> !m.isActive(), m -> m.setGrade(7));      // condition first
            int deleted = repository.deleteAll(m -> m.getTeam() == null);                     // bulk: no cascade
            int one = repository.insertOne(m -> { m.setName("Guest"); m.setActive(false); });
            int many = repository.insertAll(List.of(new NewMember("A", 1), new NewMember("B", 2)),
                    (m, r) -> { m.setName(r.name()); m.setGrade(r.grade()); m.setActive(true); });
            // @end
            assertEquals(inactive, changed);
            assertEquals(noTeam, deleted);
            assertEquals(1, one);
            assertEquals(2, many);
        });
        assertEquals(inactive, repository.count(m -> m.getGrade() != null && m.getGrade() == 7));
        assertTrue(repository.exists(m -> m.getName().equals("Guest")));
        // no transaction: rejected before any SQL (no @Transactional on purpose)
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,
                () -> repository.deleteAll(m -> m.getName().equals("Guest")));
        assertTrue(repository.exists(m -> m.getName().equals("Guest")));
    }

    @Test
    void dmlOneProtection() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        long kimId = repository.byName("Kim").orElseThrow().getId();
        tx.executeWithoutResult(status -> {
            // @start region="dml-one"
            int renamed = repository.updateOne(m -> m.getId() == kimId, m -> m.setName("Kim2"));   // exactly one row
            // two or more rows match: nothing changes
            assertThrows(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
                    () -> repository.deleteOne(m -> m.isActive()));
            // @end
            assertEquals(1, renamed);
        });
        assertEquals(all.size(), repository.count());
        assertEquals("Kim2", repository.findById(kimId).orElseThrow().getName());
        assertEquals(1, (int) tx.execute(s -> repository.deleteOne(m -> m.getName().equals("Kim2"))));
        long teamId = teams.findAll().get(0).getId();
        assertTrue(tx.execute(s -> teams.upsertOne(t -> { t.setId(teamId); t.setName("A3"); })) >= 1);
        assertEquals("A3", teams.findById(teamId).orElseThrow().getName());
    }

    @Test
    void joinsTheTransaction() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            repository.save(new Member("New", true, 9, null));                     // not flushed yet
            assertEquals(1, repository.count(m -> m.getName().equals("New")));      // seen in the same transaction
            status.setRollbackOnly();
        });
        assertEquals(0, repository.count(m -> m.getName().equals("New")));          // rolled back
    }
}
