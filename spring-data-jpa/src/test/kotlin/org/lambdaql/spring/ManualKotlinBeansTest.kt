package org.lambdaql.spring

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lambdaql.SortNotAllowedException
import org.lambdaql.query.QueryBuilder
import org.lambdaql.query.SortMode
import org.lambdaql.query.SortPolicy
import org.lambdaql.query.SortRules
import org.lambdaql.spring.sample.Member
import org.lambdaql.spring.sample.Team
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Sort
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// @start region="beans"
/** Beans of your own: a sort policy, and a QueryBuilder (for example with two EntityManagerFactory beans). */
@Configuration
open class KtLambdaQlConfig {

    @Bean
    open fun sortPolicy(): SortPolicy = SortPolicy.of(SortMode.STRICT,
        SortRules.of(Member::class.java)
            .allow { it.name }
            .alias("level") { it.grade })

    @Bean
    open fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        // the same as the auto-configuration: a shared, transaction-bound EntityManager
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
// @end

/**
 * Kotlin beans for lambdaQL in Spring Boot (manual chapter 12, Kotlin track): a SortPolicy bean, which the
 * auto-configuration registers, and a QueryBuilder bean of your own.
 * The region markers are used by the manual (Kotlin track).
 */
@SpringBootTest(classes = [ManualKotlinBeansTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinBeansTest {

    @Autowired
    private lateinit var repository: KtMemberRepository

    @Autowired
    private lateinit var teams: KtTeamRepository

    @Autowired
    private lateinit var qb: QueryBuilder

    @Autowired
    private lateinit var context: org.springframework.context.ApplicationContext

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    private lateinit var all: List<Member>

    @BeforeEach
    fun seed() {
        repository.deleteAll()
        teams.deleteAll()
        val a = teams.save(Team("A"))
        repository.save(Member("Kim", true, 3, a))
        repository.save(Member("Lee", true, 5, null))
        repository.save(Member("Han", true, null, a))
        repository.save(Member("Park", false, 4, null))
        all = repository.findAll()
    }

    @Test
    fun sortPolicyBean() {
        // @start region="sort-policy"
        // the client sends "level"; lambdaQL sorts by grade
        val rows: List<Member> = repository.findAll({ it.isActive }, Sort.by(Sort.Order.desc("level"), Sort.Order.asc("name")))
        // a property that is not in the rules
        assertThrows<SortNotAllowedException> { repository.findAll({ it.isActive }, Sort.by("grade")) }
        // @end
        val expected = all.filter { it.isActive }
            .sortedWith(compareByDescending<Member, Int?>(nullsFirst()) { it.grade }.thenBy { it.name })
        assertEquals(expected.map { it.id }, rows.map { it.id })
    }

    @Test
    fun ownQueryBuilder() {
        // the bean of KtLambdaQlConfig replaces the one of the auto-configuration (lambdaQlQueryBuilder)
        assertEquals(setOf("queryBuilder"), context.getBeansOfType(QueryBuilder::class.java).keys)
        // it joins the caller's transaction: the new member is not flushed yet
        val seen = TransactionTemplate(txManager).execute {
            repository.save(Member("New", true, 9, null))
            qb.from(Member::class.java).where { it.name == "New" }.count()
        }
        assertEquals(1L, seen)
    }

    /** The datasource chosen by application-test.yaml, the test entities, the Kotlin repositories and the beans. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.spring"])
    @Import(KtLambdaQlConfig::class)
    class Context
}
