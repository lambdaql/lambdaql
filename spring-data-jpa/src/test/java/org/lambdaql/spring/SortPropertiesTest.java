package org.lambdaql.spring;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lambdaql.SortNotAllowedException;
import org.lambdaql.query.SortMode;
import org.lambdaql.query.SortPolicy;
import org.lambdaql.spring.sample.Member;
import org.lambdaql.spring.sample.MemberRepository;
import org.lambdaql.spring.sample.SampleApplication;
import org.lambdaql.spring.sample.Team;
import org.lambdaql.spring.sample.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The outside sort policy from Spring Boot properties (ROADMAP D43): the auto-configuration reads
 * {@code lambdaql.sort.*} and registers the policy for the entity manager factory.
 * The region markers are used by Javadoc snippet tags.
 */
// @start region="properties"
@SpringBootTest(classes = SampleApplication.class, properties = {
        "lambdaql.sort.mode=STRICT",
        "lambdaql.sort.allow.Member=name,team.name",
        "lambdaql.sort.alias.Member.level=grade"})
// @end
@ActiveProfiles("test")
class SortPropertiesTest {

    @Autowired
    private MemberRepository repository;
    @Autowired
    private TeamRepository teams;
    @Autowired
    private EntityManagerFactory emf;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        teams.deleteAll();
        Team a = teams.save(new Team("A"));
        repository.save(new Member("Kim", true, 3, a));
        repository.save(new Member("Lee", true, 5, null));
        repository.save(new Member("Choi", true, 1, a));
    }

    @Test
    void properties() {
        assertEquals(SortMode.STRICT, SortPolicy.find(emf).orElseThrow().mode());
        // @start region="properties-use"
        List<Member> byLevel = repository.findAll(m -> m.isActive(), Sort.by(Sort.Order.desc("level")));
        List<Member> byTeam = repository.findAll(m -> m.isActive(), Sort.by("team.name", "name"));
        assertThrows(SortNotAllowedException.class, () -> repository.findAll(m -> m.isActive(), Sort.by("grade")));
        // @end
        assertEquals(List.of("Lee", "Kim", "Choi"), byLevel.stream().map(Member::getName).toList());
        assertEquals(byTeam.stream().sorted(Comparator.comparing((Member m) -> m.getTeam() == null ? null : m.getTeam().getName(),
                Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Member::getName)).toList(), byTeam);
    }
}
