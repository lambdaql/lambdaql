package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.LambdaQLException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin forms for the manual (chapter 10 "Rules and limits", Kotlin track, ROADMAP D56): the analysis cache.
 * Each test checks the HQL and compares the rows with the rows computed in Kotlin from the seeded data.
 * The region markers are copied into the manual.
 */
@SpringBootTest(classes = [ManualKotlinRulesTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinRulesTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        for ((status, active) in listOf(1 to true, 1 to false, 2 to true, 3 to false)) {
            em.persist(Order().apply { this.status = status.toShort(); price = 10.0 * status; isActive = active })
        }
        em.transaction.commit()
        orders = em.createQuery("select o from Order o", Order::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    /** One query of the cache example: [flag] picks the lambda outside the lambdas. */
    private fun find(status: Int, flag: Boolean): List<Order> {
        // @start region="cache-choose"
        // good: one lambda, only the value changes (the lambda is analyzed once)
        val byStatus = qb.from(Order::class.java).where { it.status.toInt() == status }
        assertEquals("select e0 from Order e0 where e0.status = :p1", byStatus.toHql().hql())

        // not possible: the shape of the query changes inside the lambda
        val error = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).where { if (flag) it.status.toInt() == 1 else it.isActive }
        }
        assertEquals("LQL-2021", error.code())

        // instead: choose the lambda outside (two lambdas, each analyzed once)
        val list = if (flag) {
            qb.from(Order::class.java).where { it.status.toInt() == 1 }.toList()
        } else {
            qb.from(Order::class.java).where { it.isActive }.toList()
        }
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == status }), ids(byStatus.toList()))
        return list
    }

    @Test
    fun cache() {
        assertEquals(ids(orders.filter { it.status.toInt() == 1 }), ids(find(2, true)))
        assertEquals(ids(orders.filter { it.isActive }), ids(find(3, false)))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
