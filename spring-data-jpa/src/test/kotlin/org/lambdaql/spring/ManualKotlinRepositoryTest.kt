package org.lambdaql.spring

import org.lambdaql.query.By.desc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lambdaql.query.SortSpec
import org.lambdaql.spring.sample.Member
import org.lambdaql.spring.sample.Team
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.SequencedSet

/**
 * Kotlin repositories with LambdaQlExecutor in a Spring Boot application (manual chapter 12, Kotlin track).
 * Each test also computes the answer in Kotlin from the seeded rows.
 * The region markers are used by the manual (Kotlin track).
 */
@SpringBootTest(classes = [ManualKotlinRepositoryTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinRepositoryTest {

    @Autowired
    private lateinit var repository: KtMemberRepository

    @Autowired
    private lateinit var shortcuts: KtMemberShortcutRepository

    @Autowired
    private lateinit var specs: KtMemberSpecRepository

    @Autowired
    private lateinit var teams: KtTeamRepository

    @Autowired
    private lateinit var service: KtMemberService

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    private lateinit var all: List<Member>

    @BeforeEach
    fun seed() {
        repository.deleteAll()
        teams.deleteAll()
        val a = teams.save(Team("A"))
        val b = teams.save(Team("B"))
        repository.save(Member("Kim", true, 3, a))
        repository.save(Member("Lee", true, 5, b))
        repository.save(Member("Park", false, null, a))
        repository.save(Member("Choi", true, 1, null))
        repository.save(Member("Jung", false, 4, b))
        repository.save(Member("Han", true, null, b))
        all = repository.findAll()
    }

    private fun ids(members: Collection<Member>) = members.map { it.id }

    /** Grade high to low, NULL last, then id. */
    private val byGradeDesc = compareByDescending<Member, Int?>(nullsFirst()) { it.grade }.thenBy { it.id }

    @Test
    fun defaultMethods() {
        assertEquals(ids(all.filter { it.isActive }).sortedBy { it }, ids(repository.activeMembers()).sortedBy { it })
        assertEquals("Kim", repository.byName("Kim").orElseThrow().name)
        assertTrue(repository.byName("Nobody").isEmpty)
        assertEquals(ids(all.filter { it.isActive }.sortedWith(byGradeDesc).take(2)), ids(repository.topGraded(2)))
    }

    @Test
    fun builders() {
        val tx = TransactionTemplate(txManager)
        val inactive = all.count { !it.isActive }
        val noTeam = all.count { it.team == null }
        assertEquals(inactive, tx.execute { repository.gradeInactive(7) })
        assertEquals(inactive.toLong(), repository.count { it.grade == 7 })
        assertEquals(noTeam, tx.execute { repository.deleteWithoutTeam() })
        assertEquals(1, tx.execute { repository.addGuest("Guest") })
        assertTrue(repository.exists { it.name == "Guest" })
        // without a transaction: Spring translates the JPA error, nothing changes
        assertThrows<InvalidDataAccessApiUsageException> { repository.gradeInactive(1) }
        assertEquals(0L, repository.count { it.grade == 1 && !it.isActive })
    }

    @Test
    fun executorMethods() {
        // @start region="executor"
        val byGrade = SortSpec.of(Member::class.java) { m -> desc(m.grade).asc(m.id) }
        val active: List<Member> = repository.findAll({ it.isActive }, byGrade)

        // the active member with the highest grade
        val best: Optional<Member> = repository.findFirst({ it.isActive },
            SortSpec.of(Member::class.java) { m -> desc(m.grade) })

        val kim: Optional<Member> = repository.findOne { it.name == "Kim" }
        // two or more rows: Spring's IncorrectResultSizeDataAccessException
        assertThrows<IncorrectResultSizeDataAccessException> { repository.findOne { it.isActive } }

        val activeCount: Long = repository.count { it.isActive }
        val anyInTeamB: Boolean = repository.exists { it.team?.name == "B" }

        val byName: SequencedSet<Member> = repository.findSet({ it.isActive }, Sort.by("name"))
        val anyOrder: SequencedSet<Member> = repository.findSet { it.isActive }   // no fixed order, like findAll { }
        // @end
        val activeRows = all.filter { it.isActive }
        assertEquals(ids(activeRows.sortedWith(byGradeDesc)), ids(active))
        assertEquals("Lee", best.orElseThrow().name)
        assertEquals("Kim", kim.orElseThrow().name)
        assertEquals(activeRows.size.toLong(), activeCount)
        assertTrue(anyInTeamB)
        assertEquals(ids(activeRows.sortedWith(compareBy<Member> { it.name }.thenBy { it.id })), ids(byName))
        assertEquals(activeRows.size, anyOrder.size)
        assertFalse(repository.exists { it.grade != null && it.grade > 10 })
    }

    @Test
    fun shortcuts() {
        val tx = TransactionTemplate(txManager)
        val inactive = all.count { !it.isActive }
        val noTeam = all.count { it.team == null }
        val kimId = all.first { it.name == "Kim" }.id
        tx.executeWithoutResult {
            assertEquals(inactive, shortcuts.gradeInactive(7))
            assertEquals(1, shortcuts.rename(kimId, "Kim2"))
            assertEquals(noTeam, shortcuts.purgeWithoutTeam())
            assertEquals(1, shortcuts.addGuest("Guest"))
            assertEquals(2, shortcuts.addMembers(listOf(NewMember("A", 1), NewMember("B", 2))))
            assertEquals(1, shortcuts.deleteByName("A"))
        }
        assertEquals(inactive.toLong(), repository.count { it.grade == 7 })
        assertEquals("Kim2", repository.findById(kimId).orElseThrow().name)
        assertTrue(repository.exists { it.name == "Guest" })
        assertEquals(listOf("B"), repository.findAll { it.grade == 2 }.map { it.name })
        assertEquals(all.size - noTeam + 2, repository.count().toInt())
        // no transaction: rejected before any SQL
        assertThrows<InvalidDataAccessApiUsageException> { shortcuts.purgeWithoutTeam() }
    }

    @Test
    fun oneRowProtection() {
        val tx = TransactionTemplate(txManager)
        val kimId = repository.byName("Kim").orElseThrow().id
        tx.executeWithoutResult {
            // @start region="dml-one"
            val renamed = repository.updateOne({ it.id == kimId }) { it.name = "Kim2" }   // exactly one row
            // two or more rows match: nothing changes
            assertThrows<IncorrectResultSizeDataAccessException> { repository.deleteOne { it.isActive } }
            // @end
            assertEquals(1, renamed)
        }
        assertEquals(all.size.toLong(), repository.count())
        assertEquals("Kim2", repository.findById(kimId).orElseThrow().name)
    }

    @Test
    fun upsert() {
        val tx = TransactionTemplate(txManager)
        val teamId = teams.findAll().first().id
        // @start region="upsert"
        // teams is a KtTeamRepository, tx a Spring TransactionTemplate
        val changed = tx.execute { teams.upsertQuery().values { it.id = teamId; it.name = "A2" }.execute() }
        // @end
        assertTrue(changed!! >= 1)
        assertEquals("A2", teams.findById(teamId).orElseThrow().name)
        assertTrue(tx.execute { teams.upsertOne { it.id = teamId; it.name = "A3" } }!! >= 1)
        assertEquals("A3", teams.findById(teamId).orElseThrow().name)
    }

    @Test
    fun springSortAndPage() {
        // @start region="spring-sort"
        val rows: List<Member> = repository.findAll({ it.isActive },
            Sort.by(Sort.Order.desc("grade"), Sort.Order.asc("team.name"), Sort.Order.asc("id")))
        // a property that does not exist: the same error as Spring Data's own repositories
        val unknown = assertThrows<RuntimeException> { repository.findAll({ it.isActive }, Sort.by("nickname")) }
        assertEquals("PropertyReferenceException", unknown.javaClass.simpleName)

        // page 1 (the second page) of the active members by name; the total comes from a count query
        val page: Page<Member> = repository.findAll({ it.isActive }, PageRequest.of(1, 2, Sort.by("name")))
        // @end
        val activeRows = all.filter { it.isActive }
        assertEquals(ids(activeRows.sortedWith(byGradeDesc)), ids(rows))
        val byName = activeRows.sortedWith(compareBy<Member> { it.name }.thenBy { it.id })
        assertEquals(byName.size.toLong(), page.totalElements)
        assertEquals(ids(byName.subList(2, 4)), ids(page.content))
    }

    @Test
    fun otherFragments() {
        // the lambda has one parameter, a Specification three: Kotlin picks the lambdaQL findAll
        assertEquals(all.count { it.isActive }, specs.findAll { it.isActive }.size)
        assertEquals(all.count { it.isActive },
            specs.findAll(Specification<Member> { root, _, cb -> cb.isTrue(root.get("active")) }).size)
        assertEquals(all.size.toLong(), specs.countByHand())                         // hand-written fragment
        assertEquals(all.size.toLong(), specs.count())                               // Spring Data's own count()
    }

    @Test
    fun service() {
        val tx = TransactionTemplate(txManager)
        // the service's QueryBuilder joins the caller's transaction: the new member is not flushed yet
        val seen = tx.execute {
            repository.save(Member("New", true, 9, null))
            service.countByName("New")
        }
        assertEquals(1L, seen)
        assertEquals(1L, service.countByName("Kim"))
    }

    /** The datasource chosen by application-test.yaml, the test entities, and the Kotlin repositories. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.spring"])
    @Import(KtMemberService::class)
    class Context
}
