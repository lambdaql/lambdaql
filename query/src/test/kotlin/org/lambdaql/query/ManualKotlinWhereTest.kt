package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lambdaql.LambdaQLException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/** A search form: a captured object whose property is read when the query runs. */
class PriceFilter(val minPrice: Double?)

/**
 * The examples of the Kotlin manual, chapter 3 (conditions). The queries use the Java test entities.
 * Each test checks the HQL and computes the expected rows in Kotlin from the seeded data.
 * The region markers are copied into the manual (Kotlin track).
 */
@SpringBootTest(classes = [ManualKotlinWhereTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinWhereTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>
    private lateinit var codes: List<Coded>

    /** A property of this class, read by a lambda as a captured value. */
    private val defaultStatus: Short = 1

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer", "Coded")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null).apply { grade = 2 }
        em.persist(kim)
        em.persist(lee)
        fun at(month: Int) = LocalDateTime.of(if (month == 12) 2025 else 2026, month, 1, 0, 0)
        // status, price, active, description, cnt, custName, updateAt, customer
        val rows = listOf(
            listOf(1, 50.0, true, "a", 1, "a", at(12), kim),
            listOf(1, 150.0, true, "b", 3, "x", at(2), lee),
            listOf(2, 250.0, false, null, null, null, at(3), null),
            listOf(1, 120.0, false, "m", 2, "m", null, null),
            listOf(3, 80.0, true, "z", null, "z", at(5), kim))
        for (r in rows) {
            em.persist(Order().apply {
                status = (r[0] as Int).toShort()
                price = r[1] as Double
                isActive = r[2] as Boolean
                description = r[3] as String?
                cnt = r[4] as Int?
                custName = r[5] as String?
                updateAt = r[6] as LocalDateTime?
                customer = r[7] as Customer?
            })
        }
        em.persist(Coded("abc", true))
        em.persist(Coded("xyz", false))
        em.persist(Coded("nul", null))
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        codes = em.createQuery("select c from Coded c", Coded::class.java).resultList
        em.close()
    }

    private fun ids(list: Collection<Order>) = list.map { it.id }.sorted()

    private fun codeIds(list: Collection<Coded>) = list.map { it.id }.sorted()

    // ------------------------------------------------------------------ 3.3 basic form, 3.5 AND / OR / NOT

    @Test
    fun manyWhere() {
        // @start region="many-where"
        val q = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 }
            .where { it.isActive }
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == 1 && it.isActive }), ids(q.toList()))
    }

    @Test
    fun andOrNot() {
        val minPrice = 100.0
        // @start region="and-or-not"
        val both = qb.from(Order::class.java).where { (it.status.toInt() == 1 || it.isActive) && it.price > minPrice }
        assertEquals("select e0 from Order e0 where (e0.status = 1 or e0.active = true) and e0.price > :p1", both.toHql().hql())

        // the compiler moves the ! inside (De Morgan): the rows are the same
        val not = qb.from(Order::class.java).where { !(it.status.toInt() == 1 && it.price > minPrice) }
        assertEquals("select e0 from Order e0 where e0.status <> 1 or e0.price <= :p1", not.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { (it.status.toInt() == 1 || it.isActive) && it.price > minPrice }), ids(both.toList()))
        assertEquals(ids(orders.filter { !(it.status.toInt() == 1 && it.price > minPrice) }), ids(not.toList()))
    }

    @Test
    fun ifAndWhen() {
        // @start region="if-when"
        val ifAnd = qb.from(Order::class.java).where { if (it.isActive) it.price > 100.0 else false }
        assertEquals("select e0 from Order e0 where e0.active = true and e0.price > 100.0", ifAnd.toHql().hql())

        val ifOr = qb.from(Order::class.java).where { if (it.isActive) true else it.price > 100.0 }
        assertEquals("select e0 from Order e0 where e0.active = true or e0.price > 100.0", ifOr.toHql().hql())

        val whenOr = qb.from(Order::class.java).where { when { it.status.toInt() == 3 -> true; it.price > 200.0 -> true; else -> false } }
        assertEquals("select e0 from Order e0 where e0.status = 3 or e0.price > 200.0", whenOr.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.isActive && it.price > 100.0 }), ids(ifAnd.toList()))
        assertEquals(ids(orders.filter { it.isActive || it.price > 100.0 }), ids(ifOr.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() == 3 || it.price > 200.0 }), ids(whenOr.toList()))
    }

    @Test
    fun valueBeforeLambda() {
        val flag = true
        // @start region="value-before"
        // an if of captured values and constants: computed in Kotlin, bound as :p1
        val q = qb.from(Order::class.java).where { it.status.toInt() == (if (flag) 1 else 2) }
        assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql())

        // an if that reads the entity becomes case when
        val c = qb.from(Order::class.java).where { it.status.toInt() == (if (it.isActive) 1 else 2) }
        assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", c.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.status.toInt() == (if (flag) 1 else 2) }), ids(q.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() == (if (it.isActive) 1 else 2) }), ids(c.toList()))
    }

    @Test
    fun bang() {
        // @start region="bang"
        // !! is only for the compiler: lambdaQL ignores it, and rows with a NULL cnt are not returned
        val q = qb.from(Order::class.java).where { it.cnt!! > 1 }
        assertEquals("select e0 from Order e0 where e0.cnt > 1", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.cnt != null && it.cnt > 1 }), ids(q.toList()))
    }

    // ------------------------------------------------------------------ 3.4 comparisons

    @Test
    fun wholeNumber() {
        // @start region="whole-number"
        // cnt is an integer column: a decimal value is compared as it is, not cut to 2
        val literal = qb.from(Order::class.java).where { it.cnt >= 2.5 }
        assertEquals("select e0 from Order e0 where e0.cnt >= 2.5", literal.toHql().hql())

        val v = 2.5
        val captured = qb.from(Order::class.java).where { it.cnt >= v }
        assertEquals("select e0 from Order e0 where e0.cnt >= 2.5BD", captured.toHql().hql())
        // @end
        val expected = ids(orders.filter { it.cnt != null && it.cnt >= 2.5 })
        assertEquals(expected, ids(literal.toList()))
        assertEquals(expected, ids(captured.toList()))
    }

    // ------------------------------------------------------------------ 3.6 null rules

    @Test
    fun capturedNullable() {
        // @start region="captured-eq"
        val n: Int? = 3
        val q = qb.from(Order::class.java).where { it.cnt == n }
        assertEquals("select e0 from Order e0 where e0.cnt = :p1", q.toHql().hql())

        // a captured null matches no row: "= NULL" is never true (WARN LQL-4045)
        val none: Int? = null
        assertEquals(0, qb.from(Order::class.java).where { it.cnt == none }.toList().size)
        // @end
        assertEquals(ids(orders.filter { it.cnt != null && it.cnt == n }), ids(q.toList()))
    }

    @Test
    fun stringEquals() {
        val text = "b"
        // @start region="string-eq"
        val eq = qb.from(Order::class.java).where { it.description == text }
        assertEquals("select e0 from Order e0 where e0.description = :p1", eq.toHql().hql())

        // a NULL description matches neither = nor <> (SQL meaning)
        val ne = qb.from(Order::class.java).where { it.description != text }
        assertEquals("select e0 from Order e0 where e0.description <> :p1", ne.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.description == text }), ids(eq.toList()))
        assertEquals(ids(orders.filter { it.description != null && it.description != text }), ids(ne.toList()))
    }

    @Test
    fun neverNull() {
        val id: Long = orders[0].id
        // @start region="never-null"
        // the id can never be NULL: the null check that Kotlin adds is dropped
        val byId = qb.from(Order::class.java).where { it.id == id }
        assertEquals("select e0 from Order e0 where e0.id = :p1", byId.toHql().hql())

        val noId = qb.from(Order::class.java).where { it.id == null }
        assertEquals("select e0 from Order e0 where 1=0", noId.toHql().hql())

        // a path through an association can be NULL: the check stays
        val noCustomerId = qb.from(Order::class.java).where { it.customer?.id == null }
        assertEquals("select e0 from Order e0 where e0.customer.id is null", noCustomerId.toHql().hql())
        // @end
        assertEquals(listOf(id), ids(byId.toList()))
        assertEquals(0, noId.toList().size)
        assertEquals(ids(orders.filter { it.customer == null }), ids(noCustomerId.toList()))
    }

    @Test
    fun writeNullChecks() {
        // @start region="null-check"
        val c: Int? = 3
        // rows with a NULL cnt are left out (SQL meaning); Kotlin would keep them
        val ne = qb.from(Order::class.java).where { it.cnt != c }
        assertEquals("select e0 from Order e0 where e0.cnt <> :p1", ne.toHql().hql())

        // to keep the NULL rows, write the null check
        val keep = qb.from(Order::class.java).where { it.cnt == null || it.cnt != c }
        assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> :p1", keep.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.cnt != null && it.cnt != c }), ids(ne.toList()))
        assertEquals(ids(orders.filter { it.cnt != c }), ids(keep.toList()))
    }

    @Test
    fun sqlMeaning() {
        val text = "b"
        // @start region="sql-meaning"
        // two entity values: = as in SQL; a row with a NULL on either side does not match
        val same = qb.from(Order::class.java).where { it.description == it.custName }
        assertEquals("select e0 from Order e0 where e0.description = e0.custName", same.toHql().hql())

        // to keep the NULL rows, say so in the lambda
        val keep = qb.from(Order::class.java).where { it.description == null || it.description != text }
        assertEquals("select e0 from Order e0 where e0.description is null or e0.description <> :p1", keep.toHql().hql())

        // a NULL Boolean is not false
        val notVip = qb.from(Coded::class.java).where { it.vip == false }
        assertEquals("select e0 from Coded e0 where e0.vip = false", notVip.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.description != null && it.description == it.custName }), ids(same.toList()))
        assertEquals(ids(orders.filter { it.description != text }), ids(keep.toList()))
        assertEquals(codeIds(codes.filter { it.vip == false }), codeIds(notVip.toList()))
    }

    @Test
    fun capturedNullGuard() {
        // @start region="captured-null-guard"
        fun byMinPrice(min: Double?) = qb.from(Order::class.java).where { min == null || it.price > min }

        val all = byMinPrice(null)
        assertEquals("select e0 from Order e0", all.toHql().hql())               // no filter
        val some = byMinPrice(100.0)
        assertEquals("select e0 from Order e0 where e0.price > :p1", some.toHql().hql())
        // @end
        assertEquals(ids(orders), ids(all.toList()))
        assertEquals(ids(orders.filter { it.price > 100.0 }), ids(some.toList()))
    }

    @Test
    fun convert() {
        // @start region="convert"
        // code is stored reversed ("abc" as "cba"): Hibernate converts the literal
        val q = qb.from(Coded::class.java).where { it.code == "abc" }
        assertEquals("select e0 from Coded e0 where e0.code = 'abc'", q.toHql().hql())
        // @end
        assertEquals(codeIds(codes.filter { it.code == "abc" }), codeIds(q.toList()))
    }

    // ------------------------------------------------------------------ 3.7 boolean columns

    @Test
    fun nullableBoolean() {
        // @start region="nullable-boolean"
        // vip is a Boolean column that can be NULL
        val yes = qb.from(Coded::class.java).where { it.vip == true }
        assertEquals("select e0 from Coded e0 where e0.vip = true", yes.toHql().hql())

        val notYes = qb.from(Coded::class.java).where { it.vip != true }
        assertEquals("select e0 from Coded e0 where e0.vip <> true", notYes.toHql().hql())

        val elvis = qb.from(Coded::class.java).where { it.vip ?: false }
        assertEquals("select e0 from Coded e0 where e0.vip is not null and e0.vip = true", elvis.toHql().hql())

        val notElvis = qb.from(Coded::class.java).where { !(it.vip ?: false) }
        assertEquals("select e0 from Coded e0 where e0.vip is null or e0.vip = false", notElvis.toHql().hql())
        // @end
        assertEquals(codeIds(codes.filter { it.vip == true }), codeIds(yes.toList()))
        assertEquals(codeIds(codes.filter { it.vip != null && it.vip != true }), codeIds(notYes.toList()))
        assertEquals(codeIds(codes.filter { it.vip ?: false }), codeIds(elvis.toList()))
        assertEquals(codeIds(codes.filter { !(it.vip ?: false) }), codeIds(notElvis.toList()))
    }

    // ------------------------------------------------------------------ 3.8 strings, 3.9 IN

    @Test
    fun stringRejected() {
        // @start region="string-rejected"
        // a string function that has no SQL function of the same meaning is not translated
        val f = assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { it.description!!.padStart(5) == "a" }.toHql()
        }
        assertTrue(f.message!!.startsWith("[LQL-3008]"))
        // @end
    }

    @Test
    fun inIds() {
        val seeded = orders.map { it.id }.sorted().take(2)
        // @start region="in-ids"
        val ids = seeded.toList()        // two order ids, built outside the lambda

        val inList = qb.from(Order::class.java).where { it.id in ids }
        assertEquals("select e0 from Order e0 where e0.id in (:p1)", inList.toHql().hql())

        val notInList = qb.from(Order::class.java).where { it.id !in ids }
        assertEquals("select e0 from Order e0 where e0.id not in (:p1)", notInList.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.id in ids }), ids(inList.toList()))
        assertEquals(ids(orders.filter { it.id !in ids }), ids(notInList.toList()))
    }

    // ------------------------------------------------------------------ 3.10 association paths

    @Test
    fun nullGuard() {
        val name = "Kim"
        // @start region="null-guard"
        // the null guard keeps orders without a customer: a left join (?. or !! give the same)
        val safe = qb.from(Order::class.java).where { it.customer == null || it.customer?.name == name }
        assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
            safe.toHql().hql())
        val sure = qb.from(Order::class.java).where { it.customer == null || it.customer!!.name == name }
        assertEquals("select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1",
            sure.toHql().hql())

        // no order without a customer can match: an inner join
        val and = qb.from(Order::class.java).where { it.customer != null && it.customer?.name == name }
        assertEquals("select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1",
            and.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.customer == null || it.customer.name == name }), ids(safe.toList()))
        assertEquals(ids(orders.filter { it.customer == null || it.customer.name == name }), ids(sure.toList()))
        assertEquals(ids(orders.filter { it.customer != null && it.customer.name == name }), ids(and.toList()))
    }

    @Test
    fun noCustomer() {
        // @start region="no-customer"
        val q = qb.from(Order::class.java).where { it.customer == null }
        assertEquals("select e0 from Order e0 where e0.customer is null", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.customer == null }), ids(q.toList()))
    }

    // ------------------------------------------------------------------ 3.11 captured values, 3.12 order of evaluation

    @Test
    fun capturedKinds() {
        val filter = PriceFilter(100.0)
        // @start region="captured-kinds"
        val since = LocalDateTime.of(2026, 1, 1, 0, 0)
        val local = qb.from(Order::class.java).where { it.updateAt > since }                     // a local val
        assertEquals("select e0 from Order e0 where e0.updateAt > :p1", local.toHql().hql())

        val property = qb.from(Order::class.java).where { it.status == defaultStatus }           // a property of this class
        assertEquals("select e0 from Order e0 where e0.status = :p1", property.toHql().hql())

        val objectProperty = qb.from(Order::class.java).where { it.price > filter.minPrice!! }   // a property of a captured object
        assertEquals("select e0 from Order e0 where e0.price > :p1", objectProperty.toHql().hql())

        val call = qb.from(Order::class.java).where { it.updateAt > LocalDateTime.of(2026, 1, 1, 0, 0) }   // a call without the entity
        assertEquals("select e0 from Order e0 where e0.updateAt > :p1", call.toHql().hql())
        // @end
        val afterSince = ids(orders.filter { it.updateAt != null && it.updateAt > since })
        assertEquals(afterSince, ids(local.toList()))
        assertEquals(afterSince, ids(call.toList()))
        assertEquals(ids(orders.filter { it.status == defaultStatus }), ids(property.toList()))
        assertEquals(ids(orders.filter { it.price > filter.minPrice!! }), ids(objectProperty.toList()))
    }

    @Test
    fun divisionGuard() {
        // @start region="division-guard"
        // lambdaQL sends the guard as written; SQL does not promise to check qty <> 0 first
        val q = qb.from(Item::class.java).where { it.qty != 0 && it.price!! / it.qty > 2 }
        assertEquals("select e0 from Item e0 where e0.qty <> 0 and (e0.price / e0.qty) > 2.0", q.toHql().hql())

        // the same rows when qty is positive, without a division
        val safe = qb.from(Item::class.java).where { it.qty > 0 && it.price!! > 2.0 * it.qty }
        assertEquals("select e0 from Item e0 where e0.qty > 0 and e0.price > (2.0 * e0.qty)", safe.toHql().hql())
        // @end
        assertEquals(0, safe.toList().size)      // no items are seeded here
    }

    // ------------------------------------------------------------------ 3.14 ranges, 3.15 when

    @Test
    fun ranges() {
        val lo = 2
        val hi = 3
        // @start region="ranges"
        val closed = qb.from(Order::class.java).where { it.status.toInt() in 1..2 }
        assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", closed.toHql().hql())

        val outside = qb.from(Order::class.java).where { it.status.toInt() !in 1..2 }
        assertEquals("select e0 from Order e0 where 1 > e0.status or e0.status >= 3", outside.toHql().hql())

        val until = qb.from(Order::class.java).where { it.status.toInt() in 1 until 3 }
        assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", until.toHql().hql())

        val open = qb.from(Order::class.java).where { it.status.toInt() in 1..<3 }
        assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", open.toHql().hql())

        val captured = qb.from(Order::class.java).where { it.cnt in lo..hi }                  // lo, hi: captured values
        assertEquals("select e0 from Order e0 where :p1 <= e0.cnt and e0.cnt <= :p2", captured.toHql().hql())

        val prices = qb.from(Order::class.java).where { it.price in 100.0..200.0 }
        assertEquals("select e0 from Order e0 where 100.0 <= e0.price and e0.price <= 200.0", prices.toHql().hql())

        val texts = qb.from(Order::class.java).where { it.description in "a".."m" }
        assertEquals("select e0 from Order e0 where e0.description >= 'a' and e0.description <= 'm'", texts.toHql().hql())
        // @end
        val oneTwo = ids(orders.filter { it.status.toInt() in 1..2 })
        assertEquals(oneTwo, ids(closed.toList()))
        assertEquals(oneTwo, ids(until.toList()))
        assertEquals(oneTwo, ids(open.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() !in 1..2 }), ids(outside.toList()))
        assertEquals(ids(orders.filter { it.cnt != null && it.cnt in lo..hi }), ids(captured.toList()))
        assertEquals(ids(orders.filter { it.price in 100.0..200.0 }), ids(prices.toList()))
        assertEquals(ids(orders.filter { it.description != null && it.description in "a".."m" }), ids(texts.toList()))
    }

    @Test
    fun rangeRejected() {
        val from = LocalDateTime.of(2026, 1, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 1, 0, 0)
        // @start region="range-rejected"
        // a range object is not translated: a nullable subject, or a range of dates
        val nullable = assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { it.customer?.grade in 1..5 }.toHql()
        }
        assertTrue(nullable.message!!.startsWith("[LQL-3008]"))

        val dates = assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { it.updateAt in from..to }.toHql()
        }
        assertTrue(dates.message!!.startsWith("[LQL-3008]"))

        // write the two comparisons instead
        val q = qb.from(Order::class.java).where { it.updateAt >= from && it.updateAt <= to }
        assertEquals("select e0 from Order e0 where e0.updateAt >= :p1 and e0.updateAt <= :p2", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.updateAt != null && it.updateAt >= from && it.updateAt <= to }), ids(q.toList()))
    }

    @Test
    fun whenForms() {
        // @start region="when"
        val twoTexts = qb.from(Order::class.java).where { when (it.description) { "a", "b" -> true; else -> false } }
        assertEquals("select e0 from Order e0 where e0.description = 'a' or e0.description = 'b'", twoTexts.toHql().hql())

        val oneValue = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> true; else -> false } }
        assertEquals("select e0 from Order e0 where e0.status = 1", oneValue.toHql().hql())

        val range = qb.from(Order::class.java).where { when (it.status.toInt()) { in 1..2 -> true; else -> false } }
        assertEquals("select e0 from Order e0 where 1 <= e0.status and e0.status < 3", range.toHql().hql())

        val condition = qb.from(Order::class.java).where { when (it.status.toInt()) { 1 -> it.isActive; else -> false } }
        assertEquals("select e0 from Order e0 where e0.status = 1 and e0.active = true", condition.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.description == "a" || it.description == "b" }), ids(twoTexts.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() == 1 }), ids(oneValue.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() in 1..2 }), ids(range.toList()))
        assertEquals(ids(orders.filter { it.status.toInt() == 1 && it.isActive }), ids(condition.toList()))
    }

    @Test
    fun whenRejected() {
        // @start region="when-rejected"
        // when compiled to a switch is not supported yet (LQL-2010): two or more Int values, three or more strings
        val ints = assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { when (it.status.toInt()) { 1, 2 -> true; else -> false } }.toHql()
        }
        assertTrue(ints.message!!.startsWith("[LQL-2010]"))

        val texts = assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { when (it.description) { "a", "b", "c" -> true; else -> false } }.toHql()
        }
        assertTrue(texts.message!!.startsWith("[LQL-2010]"))

        // an enum subject is not supported either
        assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { when (it.kind) { Order.Kind.RUSH -> true; else -> false } }.toHql()
        }
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
