package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.Agg
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/** A row of one order (Kotlin data class). */
data class OrderRow(val id: Long?, val customerName: String?, val price: Double)

/** A row of one customer group. Aggregates can be NULL, so the types are nullable (J4). */
data class CustomerStat(val customerId: Int?, val count: Long, val total: Double?)

/**
 * Kotlin forms of the query API, in the build (ROADMAP D17): sorting, subqueries, projection and aggregates,
 * update, insert and delete. Each test also computes the expected result in Kotlin from the seeded data.
 * The region markers are used by the manual (Kotlin track).
 */
@SpringBootTest(classes = [KotlinQueryTest.Context::class])
@ActiveProfiles("test")
class KotlinQueryTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>
    private lateinit var items: List<Pair<Long, Int>>   // (order id, qty)

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null).apply { grade = 2 }
        em.persist(kim)
        em.persist(lee)
        val rows = listOf(Triple(1, 50.0, kim), Triple(1, 150.0, kim), Triple(2, 250.0, lee), Triple(1, 120.0, null), Triple(3, 80.0, lee))
        val saved = rows.map { (status, price, customer) ->
            Order().apply {
                this.status = status.toShort()
                this.price = price
                this.isActive = true
                this.customer = customer
            }.also { em.persist(it) }
        }
        val its = listOf(saved[0] to 5, saved[0] to 20, saved[1] to 15, saved[2] to 1)
        for ((o, qty) in its) em.persist(Item().apply { order = o; this.qty = qty; price = 1.0 })
        em.transaction.commit()
        items = its.map { it.first.id to it.second }
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    private fun reload(): List<Order> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order::class.java).resultList
        } finally {
            em.close()
        }
    }

    // ------------------------------------------------------------------ sorting

    @Test
    fun sort() {
        // @start region="sort"
        val q = qb.from(Order::class.java)
            .where { it.isActive }
            .orderBy { o, by -> by.desc(o.price).asc(o.customer?.name) }
            .limit(3)
        assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.active = true"
            + " order by e0.price desc, j1.name asc nulls first, e0.id asc", q.toHql().hql())
        // @end
        val expected = orders.sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id }).take(3).map { it.id }
        assertEquals(expected, q.toList().map { it.id })
    }

    // ------------------------------------------------------------------ subqueries

    @Test
    fun exists() {
        // @start region="exists"
        val q = qb.from(Order::class.java)
            .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists() }
        assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)",
            q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { o -> items.any { it.first == o.id && it.second > 10 } }), ids(q.toList()))
    }

    @Test
    fun inSubquery() {
        // @start region="in"
        val q = qb.from(Order::class.java)
            .where { o, sub -> o.customer?.id in sub.from(Customer::class.java).where { c -> c.grade!! > 3 }.select { c -> c.id } }
        assertEquals("select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade > 3)",
            q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { (it.customer?.grade ?: 0) > 3 }), ids(q.toList()))
    }

    @Test
    fun scalar() {
        // @start region="scalar"
        val q = qb.from(Order::class.java).where { o, sub -> o.price > sub.from(Order::class.java).avg { x -> x.price } }
        assertEquals("select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)", q.toHql().hql())
        // @end
        val avg = orders.map { it.price }.average()
        assertEquals(ids(orders.filter { it.price > avg }), ids(q.toList()))
    }

    // ------------------------------------------------------------------ projection and aggregates

    @Test
    fun dataClass() {
        // @start region="data-class"
        val q = qb.from(Order::class.java).select { OrderRow(it.id, it.customer?.name, it.price) }
        assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", q.toHql().hql())
        // @end
        assertEquals(orders.map { OrderRow(it.id, it.customer?.name, it.price) }.sortedBy { it.id }, q.toList().sortedBy { it.id })
    }

    @Test
    fun pair() {
        // @start region="pair"
        val q = qb.from(Order::class.java).where { it.status.toInt() == 1 }.select { it.customer?.name to it.price }
        assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1 where e0.status = 1", q.toHql().hql())
        // @end
        val expected = orders.filter { it.status.toInt() == 1 }.map { it.customer?.name to it.price }
        assertEquals(expected.sortedBy { it.second }, q.toList().sortedBy { it.second })
    }

    @Test
    fun groupBy() {
        // @start region="group-by"
        val q = qb.from(Order::class.java)
            .groupBy { it.customer?.id }
            .having { Agg.sum(it.price) > 200 }
            .select { CustomerStat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
            + " having sum(e0.price) > 200.0", q.toHql().hql())
        // @end
        val expected = orders.groupBy { it.customer?.id }
            .map { (k, v) -> CustomerStat(k, v.size.toLong(), v.sumOf { it.price }) }
            .filter { it.total!! > 200 }
        assertEquals(expected.sortedBy { it.customerId ?: -1 }, q.toList().sortedBy { it.customerId ?: -1 })
    }

    @Test
    fun groupByMany() {
        // @start region="group-many"
        val q = qb.from(Order::class.java)
            .where { it.customer != null }
            .groupBy { listOf(it.customer?.id, it.status) }
            .select { Triple(it.customer?.id, it.status, Agg.count()) }
        assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
            + " group by e0.customer.id, e0.status", q.toHql().hql())
        // @end
        val expected = orders.filter { it.customer != null }.groupBy { it.customer!!.id to it.status }
            .map { (k, v) -> Triple(k.first, k.second, v.size.toLong()) }
        val order = compareBy<Triple<Int?, Short, Long>>({ it.first }, { it.second })
        assertEquals(expected.sortedWith(order), q.toList().sortedWith(order))
    }

    // ------------------------------------------------------------------ update, insert, delete

    @Test
    fun update() {
        val st: Short = 7
        // @start region="update"
        val u = qb.update(Order::class.java)
            .set { it.status = st; it.price = it.price * 2 }
            .where { it.customer?.name == "Kim" }
        assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0)"
            + " where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null",
            u.toHql().hql())
        // @end
        val target = orders.filter { it.customer?.name == "Kim" }
        assertEquals(target.size, u.execute())
        val after = reload().associateBy { it.id }
        for (o in target) {
            assertEquals(st, after[o.id]!!.status)
            assertEquals(o.price * 2, after[o.id]!!.price)
        }
    }

    @Test
    fun updateBy() {
        // @start region="update-by"
        // the builder form, Kotlin index assignment: by[column] = value
        val u = qb.update(Order::class.java)
            .set { o, by -> by[o.description] = "done" }
            .where { it.status.toInt() == 2 }
        assertEquals("update Order e0 set e0.description = 'done' where e0.status = 2", u.toHql().hql())
        // @end
        assertEquals(orders.count { it.status.toInt() == 2 }, u.execute())
    }

    @Test
    fun insertAndDelete() {
        // @start region="insert-delete"
        val i = qb.insert(Archive::class.java).values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
        assertEquals(1, i.execute())
        val d = qb.delete(Order::class.java).where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
        assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
        // @end
        val without = orders.filter { o -> items.none { it.first == o.id } }
        assertEquals(without.size, d.execute())
        assertEquals(ids(orders - without.toSet()), ids(reload()))
    }

    @Test
    fun embeddedCompare() {
        // @start region="embedded-compare"
        // address is @Embedded (city, zip): == with a value object compares each column (D48)
        val addr = Address("Seoul", "04524")
        val same = qb.from(Order::class.java).where { it.address == addr }
        assertEquals("select e0 from Order e0 where e0.address.city = :p1 and e0.address.zip = :p2", same.toHql().hql())
        val none = qb.from(Order::class.java).where { it.address == null }
        assertEquals("select e0 from Order e0 where e0.address.city is null and e0.address.zip is null", none.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.address?.city == null && it.address?.zip == null }), ids(none.toList()))
        assertEquals(listOf<Long>(), ids(same.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @EntityScan("org.lambdaql.query")
    class Context
}
