package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.Agg
import org.lambdaql.LambdaQLException
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

// @start region="inline-fun"
/** Your own inline function: the compiler copies the body into the lambda, so lambdaQL reads a plain condition. */
private inline fun Order.isOpenInline() = status.toInt() == 1
// @end

/**
 * Kotlin forms for the manual (chapter 9 "Kotlin notes" and chapter 10, Kotlin track, ROADMAP D56):
 * equality, null operators, captured values, property references, scope functions and group keys.
 * Each test checks the HQL and compares the rows with the rows computed in Kotlin from the seeded data
 * (read back from the database, so Oracle's '' = NULL is part of the expected rows).
 * The region markers are copied into the manual.
 */
@SpringBootTest(classes = [ManualKotlinNotesTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinNotesTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>
    private lateinit var codes: List<Coded>
    private lateinit var items: List<Pair<Long?, Int>>   // (order id, qty)

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer", "Coded")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null).apply { grade = 2 }
        val noName = Customer(3, null, null)
        for (c in listOf(kim, lee, noName)) em.persist(c)
        // status, price, active, customer, description, total, cnt
        data class Row(val status: Int, val price: Double, val active: Boolean, val customer: Customer?, val description: String?, val total: Double?, val cnt: Int?)
        val rows = listOf(
            Row(1, 50.0, true, kim, "x", null, 1),
            Row(1, 150.0, false, lee, "", 2.0, 2),
            Row(2, 250.0, true, null, null, 5.0, null),
            Row(3, 80.0, true, noName, "abcd", null, 1),
            Row(1, 120.0, false, kim, "abc", 4.0, null),
        )
        val saved = rows.map { r ->
            Order().apply {
                status = r.status.toShort()
                price = r.price
                isActive = r.active
                customer = r.customer
                description = r.description
                total = r.total
                cnt = r.cnt
                custName = if (r.status == 2 || r.description == "x") r.description else "other"   // NULL = NULL in the third row
            }.also { em.persist(it) }
        }
        val its = listOf(saved[0] to 5, saved[0] to 20, saved[2] to 15)
        for ((o, qty) in its) em.persist(Item().apply { order = o; this.qty = qty; price = 1.0 })
        for ((code, vip) in listOf("A" to true, "B" to false, "C" to null)) em.persist(Coded(code, vip))
        em.transaction.commit()
        items = its.map { it.first.id to it.second }
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        codes = em.createQuery("select c from Coded c", Coded::class.java).resultList
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    // ------------------------------------------------------------------ 9.2 lambda forms

    @Test
    fun whereChain() {
        // @start region="where-chain"
        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 }
            .where { it.price > 100.0 }        // more where() calls are joined with AND
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.price > 100.0", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == 1 && it.price > 100.0 }), ids(q.toList()))
    }

    @Test
    fun namedParameters() {
        // @start region="named-params"
        // lambdas with two parameters use names
        val q = qb.from(Order::class.java)
            .where { o, sub -> sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
            .orderBy { o, by -> by.desc(o.price).asc(o.id) }
        assertEquals("select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)"
            + " order by e0.price desc, e0.id asc", q.toHql().hql())
        // @end
        val expected = orders.filter { o -> items.any { it.first == o.id } }.sortedByDescending { it.price }.map { it.id }
        assertEquals(expected, q.toList().map { it.id })
    }

    // ------------------------------------------------------------------ 9.4 equality

    @Test
    fun equality() {
        val text = "abc"
        // @start region="equality"
        // plain = and <> (D48): a row whose description is NULL matches neither
        val same = qb.from(Order::class.java).where { it.description == text }
        assertEquals("select e0 from Order e0 where e0.description = :p1", same.toHql().hql())
        val other = qb.from(Order::class.java).where { it.description != "x" }
        assertEquals("select e0 from Order e0 where e0.description <> 'x'", other.toHql().hql())
        // NULL rows too: write the null check yourself
        val orNull = qb.from(Order::class.java).where { it.description == null || it.description != "x" }
        assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> 'x'", orNull.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.description == text }), ids(same.toList()))
        assertEquals(ids(orders.filter { it.description != null && it.description != "x" }), ids(other.toList()))
        assertEquals(ids(orders.filter { it.description == null || it.description != "x" }), ids(orNull.toList()))
    }

    @Test
    fun booleans() {
        // @start region="booleans"
        val active = qb.from(Order::class.java).where { it.isActive }
        assertEquals("select e0 from Order e0 where e0.active = true", active.toHql().hql())
        val inactive = qb.from(Order::class.java).where { !it.isActive }
        assertEquals("select e0 from Order e0 where e0.active = false", inactive.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.isActive }), ids(active.toList()))
        assertEquals(ids(orders.filter { !it.isActive }), ids(inactive.toList()))
    }

    // ------------------------------------------------------------------ 9.5 null operators

    @Test
    fun pathForms() {
        // @start region="path-forms"
        // ?. and !! on a path: the same column; in this condition both are an inner join
        val safe = qb.from(Order::class.java).where { it.customer?.name == "Kim" }
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", safe.toHql().hql())
        val sure = qb.from(Order::class.java).where { it.customer!!.name == "Kim" }
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'", sure.toHql().hql())
        // a path that ends at the @Id of the target: the FK column, no join
        val fk = qb.from(Order::class.java).where { it.customer?.id == 1 }
        assertEquals("select e0 from Order e0 where e0.customer is not null and e0.customer.id = 1", fk.toHql().hql())
        // @end
        val kim = ids(orders.filter { it.customer?.name == "Kim" })
        assertEquals(kim, ids(safe.toList()))
        assertEquals(kim, ids(sure.toList()))
        assertEquals(ids(orders.filter { it.customer?.id == 1 }), ids(fk.toList()))
    }

    @Test
    fun nullPaths() {
        // @start region="null-paths"
        // orders without a customer: the FK column, no join
        val none = qb.from(Order::class.java).where { it.customer == null }
        assertEquals("select e0 from Order e0 where e0.customer is null", none.toHql().hql())
        // orders without a customer, and orders of a customer without a name: a left join (D54)
        val safe = qb.from(Order::class.java).where { it.customer?.name == null }
        assertEquals("select e0 from Order e0 left join e0.customer j1 where j1.name is null", safe.toHql().hql())
        // only orders of a customer without a name: the inner join leaves out orders without a customer
        val sure = qb.from(Order::class.java).where { it.customer!!.name == null }
        assertEquals("select e0 from Order e0 join e0.customer j1 where j1.name is null", sure.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.customer == null }), ids(none.toList()))
        assertEquals(ids(orders.filter { it.customer?.name == null }), ids(safe.toList()))
        assertEquals(ids(orders.filter { it.customer != null && it.customer.name == null }), ids(sure.toList()))
    }

    @Test
    fun elvisValue() {
        // @start region="elvis-value"
        // ?: in a select value: coalesce
        val text = qb.from(Order::class.java).select { it.description ?: "" }
        assertEquals("select coalesce(e0.description, '') from Order e0", text.toHql().hql())
        // ?: after ?. in a select value is rejected (J1)
        val error = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).select { it.customer?.name ?: "none" }
        }
        assertEquals("LQL-2022", error.code())
        // ?: with a number in a condition is rejected
        val number = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
        }
        assertEquals("LQL-2021", number.code())
        // in a string condition, ?: is coalesce too
        val x = qb.from(Order::class.java).where { (it.description ?: "") == "x" }
        assertEquals("select e0 from Order e0 where coalesce(e0.description, '') = 'x'", x.toHql().hql())
        // @end
        // Oracle stores '' as NULL, so coalesce(NULL, '') is NULL there: compare NULL and '' as the same text
        assertEquals(orders.map { it.description ?: "" }.sorted(), text.toList().map { it ?: "" }.sorted())
        assertEquals(ids(orders.filter { (it.description ?: "") == "x" }), ids(x.toList()))
    }

    @Test
    fun booleanElvis() {
        // @start region="boolean-elvis"
        // Coded.vip is a nullable Boolean: in a condition, ?: false treats NULL as false
        val vip = qb.from(Coded::class.java).where { it.vip ?: false }
        assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", vip.toHql().hql())
        val notVip = qb.from(Coded::class.java).where { !(it.vip ?: false) }
        assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notVip.toHql().hql())
        // @end
        assertEquals(codes.filter { it.vip ?: false }.map { it.id }.sorted(), vip.toList().map { it.id }.sorted())
        assertEquals(codes.filter { !(it.vip ?: false) }.map { it.id }.sorted(), notVip.toList().map { it.id }.sorted())
    }

    @Test
    fun hiddenNullCheck() {
        val q: Int? = 2
        // @start region="hidden-null-check"
        // cnt is a nullable Integer: Kotlin checks for null before the unboxing, and the check stays in the HQL
        val one = qb.from(Order::class.java).where { it.cnt == 1 }
        assertEquals("select e0 from Order e0 where e0.cnt is not null and e0.cnt = 1", one.toHql().hql())
        val notOne = qb.from(Order::class.java).where { it.cnt != 1 }          // NULL rows are returned, as in Kotlin
        assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", notOne.toHql().hql())
        // both sides nullable: Intrinsics.areEqual, a value comparison
        val same = qb.from(Order::class.java).where { it.cnt == q }
        assertEquals("select e0 from Order e0 where e0.cnt = :p1", same.toHql().hql())
        // two entity values: NULL on either side does not match
        val columns = qb.from(Order::class.java).where { it.description == it.custName }
        assertEquals("select e0 from Order e0 where e0.description = e0.custName", columns.toHql().hql())
        // @end
        val cnt: Map<Long?, Int?> = orders.associate { it.id to it.cnt }
        assertEquals(ids(orders.filter { cnt[it.id] == 1 }), ids(one.toList()))
        assertEquals(ids(orders.filter { cnt[it.id] != 1 }), ids(notOne.toList()))
        assertEquals(ids(orders.filter { cnt[it.id] == q }), ids(same.toList()))
        assertEquals(ids(orders.filter { it.description != null && it.description == it.custName }), ids(columns.toList()))
    }

    @Test
    fun ranges() {
        val lo = 1
        val hi = 2
        val ids = listOf(orders[0].id, orders[1].id)
        val counts = listOf(2)
        // @start region="ranges"
        val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
        assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())
        val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
        assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())
        val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
        assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())
        val captured = qb.from(Order::class.java).where { it.status.toInt() in lo..hi }
        assertEquals("select e0 from Order e0 where :p1 <= e0.status and e0.status <= :p2", captured.toHql().hql())
        // a captured collection (D29)
        val inIds = qb.from(Order::class.java).where { it.id in ids }
        assertEquals("select e0 from Order e0 where e0.id in (:p1)", inIds.toHql().hql())
        // a nullable target gets no null guard: NULL rows are returned by neither in nor !in (SQL)
        val notIn = qb.from(Order::class.java).where { it.cnt !in counts }
        assertEquals("select e0 from Order e0 where e0.cnt not in (:p1)", notIn.toHql().hql())
        // when without a subject, with true / false branches: OR
        val anyOf = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
        assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", anyOf.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() in 1..2 }), ids(closed.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() !in 1..2 }), ids(outside.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() in 1 until 3 }), ids(until.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() in lo..hi }), ids(captured.toList()))
        assertEquals(ids(orders.filter { it.id in ids }), ids(inIds.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() == 3 || it.price > 200.0 }), ids(anyOf.toList()))
        assertEquals(ids(orders.filter { it.cnt != null && it.cnt !in counts }), ids(notIn.toList()))
    }

    // ------------------------------------------------------------------ 9.8 captured values

    @Test
    fun capturedNull() {
        // @start region="captured-null"
        // a captured null in == is bound as NULL: no row matches
        val text: String? = null
        val none = qb.from(Order::class.java).where { it.description == text }
        assertEquals("select e0 from Order e0 where e0.description = :p1", none.toHql().hql())
        assertEquals(0, none.toList().size)
        // a method call on a captured null fails when the value is computed
        val error = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).where { it.description == text!!.trim() }.toList()
        }
        assertEquals("LQL-3027", error.code())
        // @end
    }

    // @start region="order-repo"
    class OrderRepo(private val qb: QueryBuilder) {
        private val minStatus = 2

        fun open(lo: Int?) = qb.from(Order::class.java)
            .where { it.status >= minStatus }                // a private property: read each time the query runs
            .where { lo == null || it.status > lo }          // lo == null: no filter
    }
    // @end

    @Test
    fun capturedInClass() {
        val repo = OrderRepo(qb)
        // @start region="order-repo-use"
        val all = repo.open(null)
        val above = repo.open(2)
        assertEquals("select e0 from Order e0 where e0.status >= :p1", all.toHql().hql())
        assertEquals("select e0 from Order e0 where e0.status >= :p1 and e0.status > :p2", above.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status >= 2 }), ids(all.toList()))
        assertEquals(ids(orders.filter { it.status > 2 }), ids(above.toList()))
    }

    // ------------------------------------------------------------------ 9.9 property references and scope functions

    @Test
    fun scopeFunctions() {
        // @start region="scope-functions"
        val inline = qb.from(Order::class.java).where { it.isOpenInline() }     // inline fun Order.isOpenInline()
        assertEquals("select e0 from Order e0 where e0.status = 1", inline.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == 1 }), ids(inline.toList()))
    }

    // ------------------------------------------------------------------ 9.11 results

    @Test
    fun groupByPair() {
        // @start region="group-pair"
        // a Pair as the group key: two keys
        val q = qb.from(Order::class.java)
            .where { it.customer != null }
            .groupBy { it.customer?.id to it.status }
            .select { Triple(it.customer?.id, it.status, Agg.count()) }
        assertEquals("select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null"
            + " group by e0.customer.id, e0.status", q.toHql().hql())
        // @end
        val expected = orders.filter { it.customer != null }.groupBy { it.customer!!.id to it.status }
            .map { (k, v) -> Triple(k.first, k.second, v.size.toLong()) }
        val order = compareBy<Triple<Int?, Short, Long>>({ it.first }, { it.second })
        assertEquals(expected.sortedWith(order), q.toList().sortedWith(order))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
