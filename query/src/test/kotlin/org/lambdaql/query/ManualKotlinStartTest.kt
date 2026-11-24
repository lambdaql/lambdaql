package org.lambdaql.query

import org.lambdaql.query.By.asc
import org.lambdaql.query.By.desc

import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.NonUniqueResultException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.stereotype.Service
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

// @start region="spring-config"
@Configuration(proxyBeanMethods = false)
class LambdaQlConfig {

    @Bean
    fun queryBuilder(entityManagerFactory: EntityManagerFactory) =
        QueryBuilder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
}
// @end

// @start region="spring-service"
// open: this build has no kotlin("plugin.spring"), and @Transactional needs a proxy
@Service
open class OrderService(private val qb: QueryBuilder) {

    @Transactional(readOnly = true)
    open fun activeOrders(): List<Order> =
        qb.from(Order::class.java)
            .where { it.isActive }
            .toList()
}
// @end

/**
 * The examples of the Kotlin manual, chapter 2 (getting started) and the Kotlin track README.
 * Each test checks the HQL and computes the expected rows in Kotlin from the seeded data.
 * The region markers are copied into the manual (Kotlin track).
 */
@SpringBootTest(classes = [ManualKotlinStartTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinStartTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    @Autowired
    private lateinit var orderService: OrderService

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer", "Tag")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null).apply { grade = 2 }
        em.persist(kim)
        em.persist(lee)
        // status, price, active, customer
        val rows = listOf(
            listOf(1, 50.0, true, kim), listOf(1, 150.0, true, lee), listOf(2, 250.0, false, null),
            listOf(1, 120.0, false, null), listOf(3, 80.0, true, kim))
        for (r in rows) {
            em.persist(Order().apply {
                status = (r[0] as Int).toShort()
                price = r[1] as Double
                isActive = r[2] as Boolean
                customer = r[3] as Customer?
            })
        }
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        em.close()
    }

    private fun ids(list: Collection<Order>) = list.map { it.id }.sorted()

    @Test
    fun readme() {
        val minPrice = 100.0
        // @start region="readme"
        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 && it.price > minPrice }
            .orderBy { o -> desc(o.price).asc(o.id) }
            .limit(20)
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > :p1"
            + " order by e0.price desc, e0.id asc", q.toHql().hql())
        val orders: List<Order> = q.toList()
        // @end
        val expected = this.orders.filter { it.status.toInt() == 1 && it.price > minPrice }
            .sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id }).map { it.id }
        assertEquals(expected, orders.map { it.id })
    }

    @Test
    fun createAndFirstQuery() {
        // @start region="create"
        val qb = QueryBuilder(emf)
        // @end
        // @start region="first-query"
        val q = qb.from(Order::class.java).where { it.status.toInt() == 1 && it.price > 100.0 }
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
        val orders: List<Order> = q.toList()
        // @end
        assertEquals(ids(this.orders.filter { it.status.toInt() == 1 && it.price > 100.0 }), ids(orders))
    }

    @Test
    fun springBeans() {
        // OrderService gets the QueryBuilder bean of LambdaQlConfig
        assertEquals(ids(orders.filter { it.isActive }), ids(orderService.activeOrders()))
    }

    @Test
    fun captured() {
        // @start region="captured"
        fun expensive(status: Short, minPrice: Double) =
            qb.from(Order::class.java).where { it.status == status && it.price >= minPrice }

        val q = expensive(1, 100.0)
        assertEquals("select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == 1 && it.price >= 100.0 }), ids(q.toList()))
    }

    @Test
    fun manyWhere() {
        // @start region="many-where"
        val q = qb.from(Order::class.java)
            .where { it.isActive }
            .where { it.status.toInt() == 1 || it.status.toInt() == 2 }
        assertEquals("select e0 from Order e0 where e0.active = true and (e0.status = 1 or e0.status = 2)", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.isActive && (it.status.toInt() == 1 || it.status.toInt() == 2) }), ids(q.toList()))
    }

    @Test
    fun stepWhere() {
        // @start region="step-where"
        val minPrice: Double? = 100.0
        var query = qb.from(Order::class.java).where { it.isActive }
        if (minPrice != null) {
            query = query.where { it.price >= minPrice }
        }
        assertEquals("select e0 from Order e0 where e0.active = true and e0.price >= :p1", query.toHql().hql())
        val orders = query.toList()
        // @end
        assertEquals(ids(this.orders.filter { it.isActive && it.price >= 100.0 }), ids(orders))
    }

    @Test
    fun joinPath() {
        // @start region="join-path"
        val name = "Kim"
        val q = qb.from(Order::class.java).where { it.customer?.name == name }
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = :p1", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.customer?.name == name }), ids(q.toList()))
    }

    @Test
    fun first() {
        // @start region="first"
        val byPrice = qb.from(Order::class.java).orderBy { o -> asc(o.price) }
        val cheapest: Order? = byPrice.first().orElse(null)
        // first() reads one row like limit(1); paging adds the id as the last sort key
        assertEquals("select e0 from Order e0 order by e0.price asc, e0.id asc", byPrice.limit(1).toHql().hql())

        // without orderBy, "first" is the row with the smallest id
        val active = qb.from(Order::class.java).where { it.isActive }
        val firstActive: Order? = active.first().orElse(null)
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", active.limit(1).toHql().hql())
        // @end
        assertEquals(orders.minBy { it.price }.id, cheapest!!.id)
        assertEquals(orders.filter { it.isActive }.minOf { it.id }, firstActive!!.id)
        // offset is kept: the second cheapest
        assertEquals(orders.sortedBy { it.price }[1].id, byPrice.offset(1).first().orElseThrow().id)
        assertNull(qb.from(Order::class.java).where { it.status.toInt() == 9 }.first().orElse(null))
    }

    @Test
    fun one() {
        val knownId = orders[0].id
        // @start region="one"
        val id: Long = knownId
        val byId = qb.from(Order::class.java).where { it.id == id }
        assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())
        val order: Order? = byId.one().orElse(null)

        // two or more rows: an exception (use first() if any row is fine)
        assertThrows<NonUniqueResultException> { qb.from(Order::class.java).where { it.isActive }.one() }
        // @end
        assertEquals(knownId, order!!.id)
    }

    @Test
    fun count() {
        // @start region="count"
        val active = qb.from(Order::class.java).where { it.isActive }
        val total: Long = active.count()                       // every matching row
        val page = active.orderBy { o -> desc(o.price) }.limit(2)
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc", page.toHql().hql())
        val rows: List<Order> = page.toList()
        // @end
        assertEquals("select count(e0) from Order e0 where e0.active = true", active.countHql().hql())
        assertEquals(orders.count { it.isActive }.toLong(), total)
        assertEquals(orders.filter { it.isActive }.sortedByDescending { it.price }.take(2).map { it.id }, rows.map { it.id })
    }

    @Test
    fun exists() {
        // @start region="exists"
        val status3 = qb.from(Order::class.java).where { it.status.toInt() == 3 }
        assertEquals("select e0 from Order e0 where e0.status = 3", status3.toHql().hql())
        val anyStatus3: Boolean = status3.exists()
        val anyStatus9: Boolean = qb.from(Order::class.java).where { it.status.toInt() == 9 }.exists()
        // @end
        assertEquals("select 1 from Order e0 where e0.status = 3", status3.existsHql().hql())
        assertEquals(orders.any { it.status.toInt() == 3 }, anyStatus3)
        assertEquals(orders.any { it.status.toInt() == 9 }, anyStatus9)
    }

    @Test
    fun toSet() {
        // @start region="to-set"
        val sorted = qb.from(Order::class.java).orderBy { o -> desc(o.price) }
        assertEquals("select e0 from Order e0 order by e0.price desc", sorted.toHql().hql())
        val byPrice: java.util.SequencedSet<Order> = sorted.toSet()
        // @end
        assertEquals(orders.sortedByDescending { it.price }.map { it.id }, byPrice.map { it.id })
    }

    @Test
    fun toSetSizeCheck() {
        val em = emf.createEntityManager()
        em.transaction.begin()
        em.persist(Tag(1L, "same"))
        em.persist(Tag(2L, "same"))
        em.transaction.commit()
        em.close()
        // @start region="to-set-check"
        // Tag.equals compares only the name: two different rows would become one element, so toSet() refuses
        assertThrows<IllegalStateException> { qb.from(Tag::class.java).toSet() }
        val tags: List<Tag> = qb.from(Tag::class.java).toList()   // use toList() instead
        assertEquals(2, tags.size)
        // @end
    }

    /** Spring context: the datasource chosen by application-test.yaml, the test entities, and the two classes above. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    @Import(LambdaQlConfig::class, OrderService::class)
    class Context
}
