package org.lambdaql.query

import org.lambdaql.query.By.desc

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.Agg
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * The Kotlin examples of the shared manual pages: the manual home (README) and 1. Overview.
 * Each test also computes the expected rows in Kotlin from the seeded data.
 * The region markers are used by the manual.
 */
@SpringBootTest(classes = [ManualKotlinSharedTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinSharedTest {

    /** A row with two columns. */
    data class OrderView(val id: Long?, val price: Double)

    /** The order count and the total of one customer. */
    data class CustomerTotal(val customerId: Int?, val count: Long, val total: Double?)

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
        val kim = Customer(1, "Kim", null)
        val lee = Customer(2, "Lee", null)
        em.persist(kim)
        em.persist(lee)
        val rows = listOf(Triple(1, 50.0, kim), Triple(1, 150.0, lee), Triple(2, 250.0, null), Triple(1, 120.0, null),
            Triple(3, 980.0, kim), Triple(2, 90.0, kim))
        for ((i, row) in rows.withIndex()) {
            em.persist(Order().apply {
                this.status = row.first.toShort()
                this.price = row.second
                this.isActive = i != 3
                this.customer = row.third
                this.description = if (row.second > 100) "x" else null
            })
        }
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun overviewFirst() {
        // @start region="overview-first"
        val minPrice = 100.0

        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 || it.customer!!.name == "Kim" }
            .where { it.price > minPrice }
        assertEquals("select e0 from Order e0 left join e0.customer j1"
            + " where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1", q.toHql().hql())
        val rows = q.toList()
        // @end
        // an order without a customer stays when its status is 1, as in Kotlin (|| stops before the !!)
        assertEquals(ids(orders.filter { (it.status.toInt() == 1 || it.customer?.name == "Kim") && it.price > minPrice }), ids(rows))
    }

    @Test
    fun oneLambdaPerClause() {
        // @start region="one-lambda-per-clause"
        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 && it.isActive }               // one condition lambda
            .orderBy { o -> desc(o.price).asc(o.id) }               // one sort lambda for all keys
            .select { OrderView(it.id, it.price) }                         // one select lambda
        assertEquals("select e0.id, e0.price from Order e0 where e0.status = 1 and e0.active = true"
            + " order by e0.price desc, e0.id asc", q.toHql().hql())
        // @end
        assertEquals(orders.filter { it.status.toInt() == 1 && it.isActive }
            .sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id })
            .map { OrderView(it.id, it.price) }, q.toList())
    }

    @Test
    fun aggregate() {
        // @start region="agg"
        val q = qb.from(Order::class.java)
            .groupBy { it.customer?.id }
            .having { Agg.sum(it.price) > 1000 }
            .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
            + " having sum(e0.price) > 1000.0", q.toHql().hql())
        // @end
        val expected = orders.groupBy { it.customer?.id }
            .map { (k, v) -> CustomerTotal(k, v.size.toLong(), v.sumOf { it.price }) }
            .filter { it.total!! > 1000 }
        assertEquals(expected, q.toList())
    }

    @Test
    fun readmeFirst() {
        val minPrice = 100.0
        // @start region="readme-first"
        val orders = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 && it.price > minPrice }
            .orderBy { o -> desc(o.price).asc(o.id) }
            .limit(20)
            .toList()
        // @end
        assertEquals(this.orders.filter { it.status.toInt() == 1 && it.price > minPrice }
            .sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id }).take(20).map { it.id }, orders.map { it.id })
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1 order by e0.price desc, e0.id asc",
            qb.from(Order::class.java)
                .where { it.status.toInt() == 1 && it.price > minPrice }
                .orderBy { o -> desc(o.price).asc(o.id) }
                .limit(20).toHql().hql())
    }

    @Test
    fun kotlinForms() {
        // ?: becomes coalesce
        val elvis = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
        assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", elvis.toHql().hql())
        assertEquals(ids(orders.filter { (it.description ?: "") == "x" }), ids(elvis.toList()))
        // a range with in
        val range = qb.from(Order::class.java).where { it.price in 100.0..200.0 }
        assertEquals("select e0 from Order e0 where 100.0 <= e0.price and e0.price <= 200.0", range.toHql().hql())
        assertEquals(ids(orders.filter { it.price in 100.0..200.0 }), ids(range.toList()))
        // == on a nullable captured id: a plain =, the id is never NULL
        val id: Long? = orders[0].id
        val byId = qb.from(Order::class.java).where { it.id == id }
        assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())
        assertEquals(listOf(id), ids(byId.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
