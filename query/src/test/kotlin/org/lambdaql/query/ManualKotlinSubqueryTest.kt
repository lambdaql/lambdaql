package org.lambdaql.query

import org.lambdaql.query.By.desc

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
 * Kotlin forms of subqueries for the manual (Kotlin track, chapter 5): `not exists`, `!in`, captured values,
 * `count()`, top-1, nested subqueries and the paths a subquery rejects (ROADMAP M8-1, D48, D51).
 * Each test also computes the expected rows in Kotlin from the seeded data.
 * The region markers are used by the manual.
 */
@SpringBootTest(classes = [ManualKotlinSubqueryTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinSubqueryTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>
    private lateinit var customers: List<Customer>
    private lateinit var items: List<Pair<Long, Int>>   // (order id, qty)

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null).apply { grade = 2 }
        val park = Customer(3, "Park", null)                     // grade is NULL
        for (c in listOf(kim, lee, park)) em.persist(c)
        val rows = listOf(Triple(1, 50.0, kim), Triple(2, 150.0, kim), Triple(2, 250.0, lee), Triple(1, 120.0, null),
            Triple(3, 80.0, lee), Triple(5, 30.0, park))
        val saved = rows.map { (status, price, customer) ->
            Order().apply {
                this.status = status.toShort()
                this.price = price
                this.isActive = true
                this.customer = customer
            }.also { em.persist(it) }
        }
        val its = listOf(saved[0] to 5, saved[0] to 20, saved[1] to 15, saved[2] to 1, saved[2] to 12, saved[5] to 3)
        for ((o, qty) in its) em.persist(Item().apply { order = o; this.qty = qty; price = 1.0 })
        em.transaction.commit()
        items = its.map { it.first.id to it.second }
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        customers = em.createQuery("select c from Customer c", Customer::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    private fun itemsOf(o: Order) = items.filter { it.first == o.id }

    @Test
    fun notExists() {
        // @start region="not-exists"
        // orders with status 1, or orders without items
        val q = qb.from(Order::class.java)
            .where { o, sub -> o.status.toInt() == 1 || !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
        assertEquals("select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)",
            q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == 1 || itemsOf(it).isEmpty() }), ids(q.toList()))
    }

    @Test
    fun notIn() {
        // @start region="not-in"
        // the SQL meaning: NOT IN. The grades are 5, 2 and NULL, so no row matches: x NOT IN (5, 2, NULL)
        // is never true. To skip the NULL, say so in the subquery: where { c -> c.grade != null }
        val q = qb.from(Order::class.java)
            .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).select { c -> c.grade } }
        assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)", q.toHql().hql())
        val withoutNull = qb.from(Order::class.java)
            .where { o, sub -> o.status.toInt() !in sub.from(Customer::class.java).where { c -> c.grade != null }.select { c -> c.grade } }
        assertEquals("select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1 where s1.grade is not null)",
            withoutNull.toHql().hql())
        // @end
        assertEquals(listOf<Long>(), ids(q.toList()))
        val grades = customers.mapNotNull { it.grade }
        assertEquals(ids(orders.filter { it.status.toInt() !in grades }), ids(withoutNull.toList()))
    }

    @Test
    fun capturedValue() {
        // @start region="correlated-param"
        // o comes from the outer query; minQty is a captured value: a bind parameter
        val minQty = 10
        val q = qb.from(Order::class.java)
            .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > minQty }.exists() }
        assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > :p1)",
            q.toHql().hql())
        assertEquals(mapOf("p1" to 10), q.toHql().parameters())
        // @end
        assertEquals(ids(orders.filter { o -> itemsOf(o).any { it.second > minQty } }), ids(q.toList()))
    }

    @Test
    fun count() {
        // @start region="count"
        // orders with two or more items
        val q = qb.from(Order::class.java)
            .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.count() >= 2 }
        assertEquals("select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { itemsOf(it).size >= 2 }), ids(q.toList()))
    }

    @Test
    fun topOne() {
        // @start region="top-one"
        // the most expensive order of each customer: a correlated scalar subquery with orderBy and limit(1)
        val q = qb.from(Order::class.java)
            .where { o, sub ->
                o.id == sub.from(Order::class.java)
                    .where { x -> x.customer?.id == o.customer?.id }
                    .orderBy { x -> desc(x.price) }
                    .limit(1)
                    .select { x -> x.id }
                    .first()
            }
        assertEquals("select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id"
            + " order by s1.price desc, s1.id asc limit 1)", q.toHql().hql())
        // first() without orderBy and limit(1) is rejected
        val e = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).where { o, sub -> o.id == sub.from(Order::class.java).select { x -> x.id }.first() }
        }
        assertEquals("LQL-3017", e.code())
        // @end
        val expected = orders.filter { o ->
            o.customer != null && orders.filter { it.customer?.id == o.customer.id }.maxBy { it.price }.id == o.id
        }
        assertEquals(ids(expected), ids(q.toList()))
    }

    @Test
    fun nested() {
        // @start region="nested"
        // customers that have an order with an item of quantity > 10: a subquery inside a subquery
        val q = qb.from(Customer::class.java)
            .where { c, sub ->
                sub.from(Order::class.java)
                    .where { o, sub2 ->
                        o.customer?.id == c.id &&
                            sub2.from(Item::class.java).where { i -> i.order?.id == o.id && i.qty > 10 }.exists()
                    }
                    .exists()
            }
        assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null"
            + " and s1.customer.id = e0.id and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))", q.toHql().hql())
        // @end
        val expected = customers.filter { c -> orders.any { o -> o.customer?.id == c.id && itemsOf(o).any { it.second > 10 } } }
        assertEquals(expected.map { it.id }.sorted(), q.toList().map { it.id }.sorted())
    }

    @Test
    fun rejectedPaths() {
        // @start region="rejected-paths"
        // a collection path is rejected: ask about the collection with a subquery
        val collection = assertThrows(LambdaQLException::class.java) {
            qb.from(Customer::class.java).where { it.orders.size > 0 }.toHql()
        }
        assertEquals("LQL-3010", collection.code())
        val withOrders = qb.from(Customer::class.java)
            .where { c, sub -> sub.from(Order::class.java).where { o -> o.customer?.id == c.id }.exists() }
        assertEquals("select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer is not null and s1.customer.id = e0.id)",
            withOrders.toHql().hql())
        // a path of the outer entity that needs a join is rejected: compare the foreign key id instead
        val outerJoin = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java)
                .where { o, sub -> sub.from(Customer::class.java).where { c -> c.name == o.customer?.name }.exists() }.toHql()
        }
        assertEquals("LQL-3047", outerJoin.code())
        // @end
        assertEquals(customers.filter { c -> orders.any { it.customer?.id == c.id } }.map { it.id }.sorted(),
            withOrders.toList().map { it.id }.sorted())
    }

    @Test
    fun joinInSubqueryAggregate() {
        // @start region="sub-join-sum"
        // an aggregate over the joined rows: customers whose items have a total quantity over 30
        val q = qb.from(Customer::class.java).where { c, sub ->
            sub.from(Order::class.java)
                .join(Item::class.java) { o, i -> i.order?.id == o.id }
                .where { o, _ -> o.customer?.id == c.id }
                .sum { _, i -> i.qty } > 30
        }
        assertEquals("select e0 from Customer e0 where (select sum(s2.qty) from Order s1 join Item s2 on s2.order.id = s1.id"
            + " where s1.customer is not null and s1.customer.id = e0.id) > 30", q.toHql().hql())
        // @end
        val qty = customers.associate { c -> c.id to orders.filter { it.customer?.id == c.id }.sumOf { o -> itemsOf(o).sumOf { it.second } } }
        assertEquals(customers.filter { qty[it.id]!! > 30 }.map { it.id }.sorted(), q.toList().map { it.id }.sorted())
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
