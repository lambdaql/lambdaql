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
import java.time.LocalDateTime

/**
 * Kotlin `isAfter`, `isBefore` and `isEqual` on java.time values become comparisons (ROADMAP D75).
 * Each test checks the HQL and compares the rows with the same test run in Kotlin.
 */
@SpringBootTest(classes = [KotlinTimeCompareTest.Context::class])
@ActiveProfiles("test")
class KotlinTimeCompareTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) em.createQuery("delete from $e").executeUpdate()
        for (month in 0..4) {
            em.persist(Order().apply {
                status = month.toShort()
                updateAt = if (month == 0) null else LocalDateTime.of(2026, month, 1, 0, 0)
            })
        }
        em.transaction.commit()
        em.close()
    }

    private fun kotlinIds(test: (LocalDateTime) -> Boolean): List<Long?> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o", Order::class.java).resultList
                .filter { it.updateAt != null && test(it.updateAt!!) }.map { it.id }.sorted()
        } finally {
            em.close()
        }
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun afterBeforeEqual() {
        val since = LocalDateTime.of(2026, 2, 1, 0, 0)
        // @start region="is-after"
        val after = qb.from(Order::class.java).where { it.updateAt!!.isAfter(since) }
        assertEquals("select e0 from Order e0 where e0.updateAt > :p1", after.toHql().hql())

        val before = qb.from(Order::class.java).where { it.updateAt!!.isBefore(since) }
        assertEquals("select e0 from Order e0 where e0.updateAt < :p1", before.toHql().hql())

        val same = qb.from(Order::class.java).where { it.updateAt!!.isEqual(since) }
        assertEquals("select e0 from Order e0 where e0.updateAt = :p1", same.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.isAfter(since) }, ids(after.toList()))
        assertEquals(kotlinIds { it.isBefore(since) }, ids(before.toList()))
        assertEquals(kotlinIds { it.isEqual(since) }, ids(same.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
