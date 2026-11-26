package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin forms of select queries (ROADMAP D17, D23): `?.`, `!!`, `?:`, `in`.
 * Each test also computes the expected rows in Kotlin from the seeded data.
 * The region markers are used by the manual (Kotlin track).
 */
@SpringBootTest(classes = [KotlinSelectTest.Context::class])
@ActiveProfiles("test")
class KotlinSelectTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null)
        em.persist(kim)
        em.persist(lee)
        for ((status, price, customer) in listOf(Triple(1, 50.0, kim), Triple(1, 150.0, lee), Triple(2, 250.0, null), Triple(3, 80.0, kim))) {
            em.persist(Order().apply {
                this.status = status.toShort()
                this.price = price
                this.isActive = true
                this.customer = customer
            })
        }
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun safeCall() {
        // @start region="safe-call"
        // it.customer?.name reads as the path customer.name; no order without a customer can match, so an inner join
        val q = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.customer?.name == "Kim" }), ids(q.toList()))
    }

    @Test
    fun capturedAndRange() {
        // @start region="captured"
        val min = 100.0
        val q = qb.from(Order::class.java).where { it.price > min && it.status.toInt() == 1 }
        assertEquals("select e0 from Order e0 where e0.price > :p1 and e0.status = 1", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.price > min && it.status.toInt() == 1 }), ids(q.toList()))
    }

    @Test
    fun inList() {
        val statuses = listOf<Short>(1, 3)
        // @start region="in"
        val q = qb.from(Order::class.java).where { it.status in statuses }
        assertEquals("select e0 from Order e0 where e0.status in (:p1)", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status in statuses }), ids(q.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
