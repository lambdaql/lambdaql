package org.lambdaql.query

import org.lambdaql.query.By.asc
import org.lambdaql.query.By.desc

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.Agg
// @start region="agg-import"
import org.lambdaql.Agg.sum
// @end
import org.lambdaql.LambdaQLException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin forms of select, aggregates, group by, explicit joins, derived tables and CTEs for the manual
 * (Kotlin track, chapter 6; ROADMAP D17, D44, D45, D46, D51).
 * Each test also computes the expected rows in Kotlin from the seeded data.
 * The region markers are used by the manual.
 */
@SpringBootTest(classes = [ManualKotlinSelectTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinSelectTest {

    // @start region="result-types"
    data class OrderView(val id: Long?, val customerName: String?, val price: Double?)
    data class Stat(val customerId: Int?, val count: Long, val total: Double?)
    data class KeyCount(val customerId: Int?, val status: Short?, val count: Long)
    data class Total(val count: Long, val average: Double?)
    // @end

    // @start region="row-type"
    data class Row(val orderId: Long?, val customerName: String?)
    // @end

    // @start region="derived-types"
    /** A row of a derived table or CTE: the properties name the columns (J5). */
    data class CustomerTotal(val customerId: Int?, val cnt: Long, val total: Double?)
    data class Priced(val id: Long?, val price: Double?)
    // @end

    /** A total that cannot be NULL (rule J4). */
    data class StrictTotal(val total: Double)

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
        val rows = listOf(Triple(1, 50.0, kim), Triple(1, 150.0, kim), Triple(2, 250.0, lee), Triple(1, 120.0, null),
            Triple(3, 80.0, lee), Triple(1, 150.0, park))
        val saved = rows.map { (status, price, customer) ->
            Order().apply {
                this.status = status.toShort()
                this.price = price
                this.isActive = true
                this.customer = customer
            }.also { em.persist(it) }
        }
        saved[0].description = "first"
        val its = listOf(saved[0] to 5, saved[0] to 20, saved[1] to 15, saved[2] to 1)
        for ((o, qty) in its) em.persist(Item().apply { order = o; this.qty = qty; price = 1.0 })
        em.transaction.commit()
        items = its.map { it.first.id to it.second }
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        customers = em.createQuery("select c from Customer c", Customer::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    private fun customerOf(o: Order) = customers.firstOrNull { it.id == o.customer?.id }

    // ------------------------------------------------------------------ values and constructors

    @Test
    fun value() {
        // @start region="value"
        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 }
            .orderBy { o -> asc(o.price) }
            .select { it.price }
        assertEquals("select e0.price from Order e0 where e0.status = 1 order by e0.price asc", q.toHql().hql())
        val prices: List<Double> = q.toList()
        // @end
        assertEquals(orders.filter { it.status.toInt() == 1 }.map { it.price }.sorted(), prices)
    }

    @Test
    fun valuePath() {
        // @start region="value-path"
        // a path in select is a left join: the order without a customer stays (its value is null)
        val names = qb.from(Order::class.java).select { it.customer?.name }
        assertEquals("select j1.name from Order e0 left join e0.customer j1", names.toHql().hql())
        // !! gives the same query
        assertEquals(names.toHql().hql(), qb.from(Order::class.java).select { it.customer!!.name }.toHql().hql())
        // a path that ends with the @Id reads the foreign key column: no join
        assertEquals("select e0.customer.id from Order e0", qb.from(Order::class.java).select { it.customer?.id }.toHql().hql())
        // @end
        assertEquals(orders.map { customerOf(it)?.name }.sortedBy { it ?: "" }, names.toList().sortedBy { it ?: "" })
    }

    @Test
    fun valueElvis() {
        // @start region="value-elvis"
        // ?: on a column that is not a boolean: coalesce
        val q = qb.from(Order::class.java).select { it.description ?: "" }
        assertEquals("select coalesce(e0.description, '') from Order e0", q.toHql().hql())
        // ?: after a ?. path is rejected (LQL-2022)
        val afterPath = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).select { it.customer?.name ?: "none" }.toHql()
        }
        assertEquals("LQL-2022", afterPath.code())
        // @end
        assertEquals(orders.map { it.description ?: "" }.sorted(), q.toList().sorted())
    }

    @Test
    fun dataClassAndPair() {
        // @start region="data-class"
        // only the columns are selected; lambdaQL calls the constructor
        val views = qb.from(Order::class.java).select { OrderView(it.id, it.customer?.name, it.price) }
        assertEquals("select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1", views.toHql().hql())
        // Pair (a to b) and Triple
        val pairs = qb.from(Order::class.java).select { it.customer?.name to it.price }
        assertEquals("select j1.name, e0.price from Order e0 left join e0.customer j1", pairs.toHql().hql())
        val triples = qb.from(Order::class.java).select { Triple(it.id, it.status, it.isActive) }
        assertEquals("select e0.id, e0.status, e0.active from Order e0", triples.toHql().hql())
        // @end
        assertEquals(orders.map { OrderView(it.id, customerOf(it)?.name, it.price) }.sortedBy { it.id }, views.toList().sortedBy { it.id })
        assertEquals(orders.map { customerOf(it)?.name to it.price }.sortedWith(compareBy({ it.first ?: "" }, { it.second })),
            pairs.toList().sortedWith(compareBy({ it.first ?: "" }, { it.second })))
        assertEquals(orders.map { Triple(it.id, it.status, it.isActive) }.sortedBy { it.first }, triples.toList().sortedBy { it.first })
    }

    // ------------------------------------------------------------------ aggregates and group by

    @Test
    fun total() {
        // @start region="total"
        // aggregates without groupBy: always one row
        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 }
            .select { Total(Agg.count(), Agg.avg(it.price)) }
        assertEquals("select count(*), avg(e0.price) from Order e0 where e0.status = 1", q.toHql().hql())
        val t: Total? = q.one().orElse(null)
        // @end
        val ones = orders.filter { it.status.toInt() == 1 }
        assertEquals(ones.size.toLong(), t!!.count)
        assertEquals(ones.map { it.price }.average(), t.average!!, 1e-9)
    }

    @Test
    fun groupOne() {
        // @start region="group-one"
        // the key ends with the @Id: no join. The orders without a customer are one group with the key NULL
        val q = qb.from(Order::class.java)
            .groupBy { it.customer?.id }
            .select { Stat(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id", q.toHql().hql())
        // @end
        val expected = orders.groupBy { it.customer?.id }.map { (k, v) -> Stat(k, v.size.toLong(), v.sumOf { it.price }) }
        assertEquals(expected.sortedBy { it.customerId ?: -1 }, q.toList().sortedBy { it.customerId ?: -1 })
    }

    @Test
    fun groupMany() {
        // @start region="group-many"
        val q = qb.from(Order::class.java)
            .where { it.customer != null }
            .groupBy { listOf(it.customer?.id, it.status) }
            .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
        assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
            + " group by e0.customer.id, e0.status", q.toHql().hql())
        // a Pair (a to b) gives the same keys; a Triple gives three
        val pair = qb.from(Order::class.java)
            .where { it.customer != null }
            .groupBy { it.customer?.id to it.status }
            .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
        assertEquals(q.toHql().hql(), pair.toHql().hql())
        val three = qb.from(Order::class.java)
            .groupBy { Triple(it.customer?.id, it.status, it.isActive) }
            .select { Triple(it.customer?.id, it.status, Agg.count()) }
        assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 group by e0.customer.id, e0.status, e0.active",
            three.toHql().hql())
        // @end
        val order = compareBy<KeyCount>({ it.customerId }, { it.status })
        val expected = orders.filter { it.customer != null }.groupBy { it.customer!!.id to it.status }
            .map { (k, v) -> KeyCount(k.first, k.second, v.size.toLong()) }
        assertEquals(expected.sortedWith(order), q.toList().sortedWith(order))
        assertEquals(expected.sortedWith(order), pair.toList().sortedWith(order))
    }

    @Test
    fun groupKeyError() {
        // @start region="group-key-error"
        // status is not a group key
        val e = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java)
                .groupBy { it.customer?.id }
                .select { KeyCount(it.customer?.id, it.status, Agg.count()) }
        }
        assertEquals("LQL-3035", e.code())
        // @end
    }

    @Test
    fun having() {
        // @start region="having"
        // sum is imported (import org.lambdaql.Agg.sum); having can be called more than once (AND)
        val q = qb.from(Order::class.java)
            .groupBy { it.customer?.id }
            .having { sum(it.price) > 200 }
            .having { Agg.count() >= 2 }
            .select { Stat(it.customer?.id, Agg.count(), sum(it.price)) }
        assertEquals("select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id"
            + " having sum(e0.price) > 200.0 and count(*) >= 2L", q.toHql().hql())
        // @end
        // the Kotlin sum() of the standard library still works next to the imported Agg.sum
        val expected = orders.groupBy { it.customer?.id }
            .map { (k, v) -> Stat(k, v.size.toLong(), v.map { it.price }.sum()) }
            .filter { it.total!! > 200 && it.count >= 2 }
        assertEquals(expected.sortedBy { it.customerId ?: -1 }, q.toList().sortedBy { it.customerId ?: -1 })
    }

    @Test
    fun rules() {
        // @start region="rules"
        // aggregates belong in having, not in where
        val where = assertThrows(LambdaQLException::class.java) { qb.from(Order::class.java).where { Agg.count() > 1 } }
        assertEquals("LQL-3005", where.code())
        // with distinct, the sort key must be selected
        val j7 = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).orderBy { o -> asc(o.id) }.select { it.price }.distinct().toHql()
        }
        assertEquals("LQL-3046", j7.code())
        // sum over no rows is NULL; the non-null parameter of StrictTotal(val total: Double) cannot take it
        assertThrows(IllegalStateException::class.java) {
            qb.from(Order::class.java).where { it.status.toInt() == 9 }.select { StrictTotal(Agg.sum(it.price)) }.toList()
        }
        // @end
    }

    // ------------------------------------------------------------------ distinct and paging

    @Test
    fun distinct() {
        // @start region="distinct"
        val q = qb.from(Order::class.java)
            .orderBy { o -> desc(o.price) }
            .select { it.price }
            .distinct()
        assertEquals("select distinct e0.price from Order e0 order by e0.price desc", q.toHql().hql())
        // toSet() always uses select distinct
        val prices = q.toSet()
        // @end
        val expected = orders.map { it.price }.distinct().sortedDescending()
        assertEquals(expected, q.toList())
        assertEquals(expected, prices.toList())
    }

    @Test
    fun valuePage() {
        // @start region="value-page"
        // paging adds the id as the last key, so the pages do not overlap
        val page = qb.from(Order::class.java)
            .orderBy { o -> desc(o.price) }
            .select { it.price }
            .limit(2).offset(1)
        assertEquals("select e0.price from Order e0 order by e0.price desc, e0.id asc", page.toHql().hql())
        // @end
        assertEquals(orders.sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id }).map { it.price }.drop(1).take(2),
            page.toList())
    }

    // ------------------------------------------------------------------ explicit joins

    @Test
    fun join() {
        // @start region="join"
        val q = qb.from(Order::class.java)
            .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .where { _, c -> c.grade != null && c.grade >= 5 }
        assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
            + " where e1.grade is not null and e1.grade >= 5", q.toHql().hql())
        // without select, the result is the main entity
        val result: List<Order> = q.toList()
        // @end
        assertEquals(ids(orders.filter { (customerOf(it)?.grade ?: 0) >= 5 }), ids(result))
    }

    @Test
    fun leftJoinNull() {
        // @start region="left-join-null"
        // c is missing for an order without a customer: c == null finds those rows
        val q = qb.from(Order::class.java)
            .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .where { _, c -> c == null || c.name == "Kim" }
        assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
            + " where e1.id is null or e1.name = 'Kim'", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { customerOf(it) == null || customerOf(it)!!.name == "Kim" }), ids(q.toList()))
    }

    @Test
    fun joinSortSelect() {
        // @start region="join-sort-select"
        val sorted = qb.from(Order::class.java)
            .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .orderBy { o, c -> asc(c.name).asc(o.id) }
        assertEquals("select e0 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
            + " order by e1.name asc nulls first, e0.id asc", sorted.toHql().hql())
        // the order without a customer has customerName = null
        val rows = qb.from(Order::class.java)
            .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .orderBy { o, _ -> asc(o.id) }
            .select { o, c -> Row(o.id, c.name) }
        assertEquals("select e0.id, e1.name from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
            + " order by e0.id asc", rows.toHql().hql())
        // @end
        assertEquals(orders.sortedWith(compareBy<Order, String?>(nullsFirst()) { customerOf(it)?.name }.thenBy { it.id }).map { it.id },
            sorted.toList().map { it.id })
        assertEquals(orders.sortedBy { it.id }.map { Row(it.id, customerOf(it)?.name) }, rows.toList())
    }

    @Test
    fun joinThree() {
        // @start region="join-three"
        // orders of a customer with an item of quantity > 10 (an order with two such items is two SQL rows)
        val q = qb.from(Order::class.java)
            .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .join(Item::class.java) { o, _, i -> i.order?.id == o.id }
            .where { _, _, i -> i.qty > 10 }
        assertEquals("select e0 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id"
            + " join Item e2 on e2.order.id = e0.id where e2.qty > 10", q.toHql().hql())
        // @end
        val expected = orders.filter { customerOf(it) != null }.flatMap { o -> items.filter { it.first == o.id && it.second > 10 }.map { o.id } }
        assertEquals(expected.sorted(), ids(q.toList()))
        assertEquals(expected.size.toLong(), q.count())
    }

    @Test
    fun crossJoin() {
        // @start region="cross-join"
        // every pair of rows; the conditions go in where
        val q = qb.from(Order::class.java)
            .crossJoin(Customer::class.java)
            .where { o, c -> c.grade != null && o.price > 100 }
        assertEquals("select e0 from Order e0 cross join Customer e1 where e1.grade is not null and e0.price > 100.0",
            q.toHql().hql())
        // @end
        assertEquals(orders.count { it.price > 100 }.toLong() * customers.count { it.grade != null }, q.count())
    }

    @Test
    fun joinPaging() {
        // @start region="join-paging"
        // with paging, the id of every entity is added to the order, so the pages do not overlap
        val pairs = qb.from(Order::class.java)
            .crossJoin(Customer::class.java)
            .orderBy { o, _ -> desc(o.price) }
            .select { o, c -> Row(o.id, c.name) }
        assertEquals("select e0.id, e1.name from Order e0 cross join Customer e1 order by e0.price desc, e0.id asc, e1.id asc",
            pairs.limit(5).toHql().hql())
        // @end
        val all = (0 until 4).flatMap { page -> pairs.limit(5).offset(page * 5).toList() }
        val expected = orders.sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id })
            .flatMap { o -> customers.sortedBy { it.id }.map { Row(o.id, it.name) } }
        assertEquals(expected, all)
    }

    private fun totals() = qb.from(Order::class.java)
        .where { it.customer != null }
        .groupBy { it.customer?.id }
        .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }

    private fun expectedTotals() = orders.filter { it.customer != null }.groupBy { it.customer!!.id }
        .map { (k, v) -> CustomerTotal(k, v.size.toLong(), v.sumOf { it.price }) }

    @Test
    fun joinDerivedAndCteReuse() {
        // @start region="join-derived"
        // a derived table without a name; a customer without orders has no row in it (left join)
        val small = qb.from(Customer::class.java)
            .leftJoin(qb.from(Order::class.java)
                .where { it.customer != null }
                .groupBy { it.customer?.id }
                .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }) { c, x -> x.customerId == c.id }
            .where { _, x -> x.customerId == null || x.total!! < 210 }
        assertEquals("select e0 from Customer e0 left join (select d0.customer.id as customerId, count(*) as cnt,"
            + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e1"
            + " on e1.customerId = e0.id where e1.customerId is null or e1.total < 210.0", small.toHql().hql())
        // @end
        val totals = expectedTotals().associateBy { it.customerId }
        assertEquals(customers.filter { (totals[it.id]?.total ?: 0.0) < 210 }.map { it.name }.sorted(),
            small.toList().map { it.name }.sorted())
        val t = qb.with(totals())
        // @start region="cte-reuse"
        // the same CTE read twice is written once
        val reused = qb.from(Customer::class.java)
            .join(t) { c, x -> x.customerId == c.id }
            .leftJoin(t) { c, _, y -> y.customerId == c.id && y.total!! > 210 }
            .select { c, _, y -> c.name to y.total }
        assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total from Order d0"
            + " where d0.customer is not null group by d0.customer.id)"
            + " select e0.name, e2.total from Customer e0 join t e1 on e1.customerId = e0.id"
            + " left join t e2 on e2.customerId = e0.id and e2.total > 210.0", reused.toHql().hql())
        // @end
        val byName = expectedTotals().associate { tot -> customers.first { it.id == tot.customerId }.name to tot.total }
        assertEquals(byName.map { (n, tot) -> n to (if (tot!! > 210) tot else null) }.sortedBy { it.first },
            reused.toList().sortedBy { it.first })
        // a derived row has no id: x == null is rejected, compare a column (LQL-3067)
        val noId = assertThrows(LambdaQLException::class.java) {
            qb.from(Customer::class.java).leftJoin(totals()) { c, x -> x.customerId == c.id }.where { _, x -> x == null }
        }
        assertEquals("LQL-3067", noId.code())
    }

    // ------------------------------------------------------------------ derived tables and CTEs

    @Test
    fun derived() {
        // @start region="derived"
        // the rows of a group query are the table of the outer query; r.total reads the column "total"
        val q = qb.from(qb.from(Order::class.java)
                .where { it.customer != null }
                .groupBy { it.customer?.id }
                .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) })
            .where { r -> r.total!! > 100 }
            .orderBy { r -> desc(r.total) }
        assertEquals("select e0.customerId, e0.cnt, e0.total from (select d0.customer.id as customerId, count(*) as cnt,"
            + " sum(d0.price) as total from Order d0 where d0.customer is not null group by d0.customer.id) e0"
            + " where e0.total > 100.0 order by e0.total desc nulls last", q.toHql().hql())
        // @end
        val expected = expectedTotals().filter { it.total!! > 100 }.sortedByDescending { it.total }
        assertEquals(expected, q.toList())
        assertEquals(expected.size.toLong(), q.count())
    }

    @Test
    fun derivedInnerLimit() {
        // @start region="derived-limit"
        // the three most expensive orders, then the cheapest of them
        val cheapestOfTop = qb.from(qb.from(Order::class.java)
                .orderBy { o -> desc(o.price) }
                .limit(3)
                .select { Priced(it.id, it.price) })
            .orderBy { r -> asc(r.price) }
            .first()
        // @end
        val top = orders.sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id }).take(3)
        val cheapest = top.minBy { it.price }
        assertEquals(Priced(cheapest.id, cheapest.price), cheapestOfTop.orElseThrow())
    }

    @Test
    fun derivedRowType() {
        // @start region="derived-row-type"
        // a single value (or a Pair, or a class that is not a data class) has no column names
        val e = assertThrows(LambdaQLException::class.java) { qb.from(qb.from(Order::class.java).select { it.price }) }
        assertEquals("LQL-3064", e.code())
        // @end
    }

    @Test
    fun cte() {
        // @start region="cte"
        val perCustomer = qb.from(Order::class.java)
            .where { it.customer != null }
            .groupBy { it.customer?.id }
            .select { CustomerTotal(it.customer?.id, Agg.count(), Agg.sum(it.price)) }
        val t: Cte<CustomerTotal> = qb.with(perCustomer)
        val q = qb.from(t).where { r -> r.cnt >= 2 }
        assertEquals("with t as (select d0.customer.id as customerId, count(*) as cnt, sum(d0.price) as total"
            + " from Order d0 where d0.customer is not null group by d0.customer.id)"
            + " select e0.customerId, e0.cnt, e0.total from t e0 where e0.cnt >= 2L", q.toHql().hql())
        // @end
        assertEquals(expectedTotals().filter { it.cnt >= 2 }.sortedBy { it.customerId }, q.toList().sortedBy { it.customerId })
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
