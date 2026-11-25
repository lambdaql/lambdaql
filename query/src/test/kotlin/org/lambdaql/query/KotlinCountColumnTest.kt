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
 * `count { column }` and `distinct().count { column }` in Kotlin lambdas: the non-NULL values of one column, or the
 * different ones. Each test checks the HQL and computes the same number in Kotlin from the seeded rows.
 */
@SpringBootTest(classes = [KotlinCountColumnTest.Context::class])
@ActiveProfiles("test")
class KotlinCountColumnTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer().apply { id = 1; name = "Kim" }
        val lee = Customer().apply { id = 2; name = "Lee" }
        em.persist(kim)
        em.persist(lee)
        for ((text, customer) in listOf("gift" to kim, null to kim, "rush" to lee, "gift" to null, null to null)) {
            em.persist(Order().apply { price = 100.0; description = text; this.customer = customer; isActive = true })
        }
        em.transaction.commit()
        em.close()
    }

    private fun all(): List<Order> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        } finally {
            em.close()
        }
    }

    @Test
    fun countColumn() {
        // @start region="count-column"
        // rows whose description is not NULL
        val orders = qb.from(Order::class.java)
        val described = orders.count { it.description }
        // @end
        assertEquals("select count(e0.description) from Order e0", orders.countHql({ it.description }, false).hql())
        assertEquals(all().count { it.description != null }.toLong(), described)
    }

    @Test
    fun countRow() {
        // @start region="count-row"
        // { it } is the whole row: count(*)
        val all = qb.from(Order::class.java).count { it }
        // @end
        assertEquals("select count(*) from Order e0", qb.from(Order::class.java).countHql({ it }, false).hql())
        assertEquals(all().size.toLong(), all)
    }

    @Test
    fun countDistinct() {
        // @start region="count-distinct"
        // the number of different customers that have an order
        val orders = qb.from(Order::class.java)
        val customers = orders.distinct().count { it.customer?.id }
        // @end
        assertEquals("select count(distinct e0.customer.id) from Order e0", orders.countHql({ it.customer?.id }, true).hql())
        assertEquals(all().mapNotNull { it.customer?.id }.distinct().size.toLong(), customers)
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
