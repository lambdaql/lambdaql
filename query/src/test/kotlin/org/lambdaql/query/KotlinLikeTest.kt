package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Text matches in Kotlin lambdas (ROADMAP D60): `startsWith`, `endsWith`, `contains`, also with `ignoreCase = true`.
 * Each test checks the HQL and compares the rows with the same filter in Kotlin.
 */
@SpringBootTest(classes = [KotlinLikeTest.Context::class])
@ActiveProfiles("test")
class KotlinLikeTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    private val texts = listOf("Kim's order", "50%_off sale", "50x off", "tall", "Stall", "ALL CAPS", null)

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer", "Archive", "Summary")) em.createQuery("delete from $e").executeUpdate()
        for (t in texts) em.persist(Order().apply { status = 1; price = 10.0; isActive = true; description = t })
        em.transaction.commit()
        em.close()
    }

    private fun kotlinIds(p: (String) -> Boolean): List<Long?> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o", Order::class.java).resultList
                .filter { it.description != null && p(it.description) }.map { it.id }.sortedBy { it }
        } finally {
            em.close()
        }
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sortedBy { it }

    @Test
    fun startsAndEnds() {
        val p = "50%_"
        // @start region="like"
        // % and _ in the text are not wildcards: only "50%_off sale" matches, not "50x off"
        val starts = qb.from(Order::class.java).where { it.description.startsWith(p) }
        assertEquals("select e0 from Order e0 where e0.description like :p1 escape '!'", starts.toHql().hql())
        // ignoreCase = true: both sides in lower case
        val any = qb.from(Order::class.java).where { it.description.contains("all", ignoreCase = true) }
        assertEquals("select e0 from Order e0 where lower(e0.description) like '%all%' escape '!'", any.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.contains("all", ignoreCase = true) }, ids(any.toList()))
        assertEquals(kotlinIds { it.startsWith(p) }, ids(starts.toList()))
        val ends = qb.from(Order::class.java).where { it.description.endsWith("all") }
        assertEquals("select e0 from Order e0 where e0.description like '%all' escape '!'", ends.toHql().hql())
        assertEquals(kotlinIds { it.endsWith("all") }, ids(ends.toList()))
    }

    @Test
    fun ignoreCase() {
        // ignoreCase = true: both sides in lower case (an ordinary index on the column is not used, WARN LQL-5013)
        val q = qb.from(Order::class.java).where { it.description.contains("all", ignoreCase = true) }
        assertEquals("select e0 from Order e0 where lower(e0.description) like '%all%' escape '!'", q.toHql().hql())
        assertEquals(kotlinIds { it.contains("all", ignoreCase = true) }, ids(q.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
