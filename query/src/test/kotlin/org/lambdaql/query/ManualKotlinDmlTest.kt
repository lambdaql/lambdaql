package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.NonUniqueResultException
import jakarta.persistence.OptimisticLockException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.Agg
import org.lambdaql.Dml.value
import org.lambdaql.LambdaQLException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.Locale
import java.util.Objects

/**
 * Kotlin forms of UPDATE, DELETE, INSERT and upsert for the manual (chapter 7, Kotlin track, ROADMAP D56).
 * Each test seeds its own rows, runs the statement (QueryBuilder(emf) opens its own transaction),
 * reads the rows back and compares them with the rows computed in Kotlin.
 * The region markers are copied into the manual.
 */
@SpringBootTest(classes = [ManualKotlinDmlTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinDmlTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var before: List<Order>

    /** A list element for values(list) { ... }. */
    data class Line(val id: Long, val price: Double)

    /** A list element that names an order, for values(list) { a, r, sub -> ... }. */
    data class Ref(val id: Long, val orderId: Long?)

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) {
            em.createQuery("delete from $e").executeUpdate()
        }
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val noName = Customer(2, null, null).apply { grade = 2 }
        em.persist(kim)
        em.persist(noName)
        // price, status, customer, cnt, total
        data class Row(val price: Double, val status: Int, val customer: Customer?, val cnt: Int?, val total: Double?)
        val rows = listOf(
            Row(50.0, 1, kim, 1, null),
            Row(150.0, 1, noName, 2, 10.0),
            Row(250.0, 2, null, null, null),
            Row(120.0, 3, kim, 0, 5.0),
        )
        for (r in rows) {
            em.persist(Order().apply {
                price = r.price
                status = r.status.toShort()
                isActive = true
                customer = r.customer
                cnt = r.cnt
                total = r.total
            })
        }
        em.persist(VOrder().apply { id = 1L; status = 1 })
        em.transaction.commit()
        em.close()
        before = orders()
    }

    /** Adds three items: two for the first order (10.0, 20.0), one for the third order (5.0). */
    private fun items() {
        val em = emf.createEntityManager()
        em.transaction.begin()
        val all = em.createQuery("select o from Order o order by o.id", Order::class.java).resultList
        for ((index, price) in listOf(0 to 10.0, 0 to 20.0, 2 to 5.0)) {
            em.persist(Item().apply { order = all[index]; this.price = price })
        }
        em.transaction.commit()
        em.close()
    }

    private fun orders(): List<Order> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order::class.java).resultList
        } finally {
            em.close()
        }
    }

    private fun <X> find(type: Class<X>, id: Any): X? {
        val em = emf.createEntityManager()
        try {
            return em.find(type, id)
        } finally {
            em.close()
        }
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    /** MariaDB and MySQL count a row changed by ON DUPLICATE KEY UPDATE as 2. */
    private fun mysqlFamily(): Boolean {
        val db = System.getProperty("lql.db", "h2").lowercase(Locale.ROOT)
        return db == "mariadb" || db == "mysql"
    }

    // ------------------------------------------------------------------ update

    @Test
    fun update() {
        // @start region="update"
        val st: Short = 5
        val u = qb.update(Order::class.java)
            .set { it.status = st; it.price = it.price * 2.0 }
            .where { it.status.toInt() == 1 }
        // the price on the right is the value before the change
        assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1", u.toHql().hql())
        val changed = u.execute()                      // number of updated rows
        // @end
        val target = before.filter { it.status.toInt() == 1 }
        assertEquals(target.size, changed)
        val after = orders().associateBy { it.id }
        for (o in before) {
            val a = after[o.id]!!
            if (o in target) {
                assertEquals(st, a.status)
                assertEquals(o.price * 2.0, a.price)
            } else {
                assertEquals(o.status, a.status)
                assertEquals(o.price, a.price)
            }
        }
    }

    @Test
    fun updateValues() {
        val id = before[0].id
        val now = LocalDateTime.of(2024, 5, 1, 10, 30)
        // @start region="update-values"
        // null, a captured date, a boolean and an enum constant
        val u = qb.update(Order::class.java)
            .set { it.description = null; it.updateAt = now; it.isActive = false; it.kind = Order.Kind.RUSH }
            .where { it.id == id }
        assertEquals("update Order e0 set e0.description = null, e0.updateAt = :p1, e0.active = false,"
            + " e0.kind = org.lambdaql.query.Order\$Kind.RUSH where e0.id = :p2", u.toHql().hql())
        // @end
        assertEquals(1, u.execute())
        val a = find(Order::class.java, id!!)!!
        assertEquals(null, a.description)
        assertEquals(now, a.updateAt)
        assertEquals(false, a.isActive)
        assertEquals(Order.Kind.RUSH, a.kind)
        assertEquals(3, orders().count { it.isActive })
    }

    @Test
    fun updateAdd() {
        // @start region="update-add"
        // += on a property: the old value plus 1
        val add = qb.update(Order::class.java).set { it.cnt += 1 }.where { it.status.toInt() == 1 }
        assertEquals("update Order e0 set e0.cnt = (e0.cnt + 1) where e0.status = 1", add.toHql().hql())
        // ?: gives a default for NULL: coalesce
        val name = qb.update(Order::class.java).set { it.custName = it.description ?: "none" }.all()
        assertEquals("update Order e0 set e0.custName = coalesce(e0.description, 'none')", name.toHql().hql())
        // @end
        assertEquals(before.count { it.status.toInt() == 1 }, add.execute())
        assertEquals(before.size, name.execute())
        val after = orders().associateBy { it.id }
        for (o in before) {
            val a = after[o.id]!!
            val cnt: Int? = o.cnt
            val expected: Any? = if (o.status.toInt() == 1) cnt?.plus(1) else cnt
            val actual: Any? = a.cnt
            assertEquals(expected, actual)
            assertEquals(o.description ?: "none", a.custName)
        }
    }

    @Test
    fun updateEmbedded() {
        val id = before[0].id
        val city = "Busan"
        val addr = Address("Seoul", "04524")
        // @start region="update-embedded"
        // one column of the embedded value
        val one = qb.update(Order::class.java).set { it.address.city = city }.where { it.id == id }
        assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", one.toHql().hql())
        // the whole embedded value: every column (city, zip)
        val all = qb.update(Order::class.java).set { it.address = addr }.where { it.status.toInt() == 1 }
        assertEquals("update Order e0 set e0.address = :p1 where e0.status = 1", all.toHql().hql())
        // @end
        assertEquals(1, one.execute())
        assertEquals("Busan", find(Order::class.java, id!!)!!.address.city)
        assertEquals(before.count { it.status.toInt() == 1 }, all.execute())
        for (o in orders()) {
            if (o.status.toInt() == 1) {
                assertEquals("Seoul", o.address.city)
                assertEquals("04524", o.address.zip)
            } else {
                assertEquals(null, o.address)
            }
        }
    }

    @Test
    fun updateForeignKey() {
        val first = before[0].id
        val third = before[2].id
        val cid = 2
        val kim = find(Customer::class.java, 1)!!
        // @start region="update-fk"
        // the FK column: the ?. assignment always assigns
        val fk = qb.update(Order::class.java).set { it.customer?.id = cid }.where { it.id == first }
        assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
        // no customer
        val none = qb.update(Order::class.java).set { it.customer = null }.where { it.status.toInt() == 3 }
        assertEquals("update Order e0 set e0.customer = null where e0.status = 3", none.toHql().hql())
        // a captured entity: its @Id is bound (U11)
        val entity = qb.update(Order::class.java).set { it.customer = kim }.where { it.id == third }
        assertEquals("update Order e0 set e0.customer = :p1 where e0.id = :p2", entity.toHql().hql())
        // @end
        assertEquals(1, fk.execute())
        assertEquals(1, none.execute())
        assertEquals(1, entity.execute())
        val after = orders().associateBy { it.id }
        assertEquals(2, after[first]!!.customer!!.id)
        assertEquals(1, after[third]!!.customer!!.id)
        for (o in before.filter { it.status.toInt() == 3 }) assertEquals(null, after[o.id]!!.customer)
    }

    @Test
    fun updatePath() {
        // @start region="update-path"
        // a path in the condition: a subquery, plus "customer exists" (the same rows as the select, D36, U9)
        val u = qb.update(Order::class.java)
            .set { it.custName = "K" }
            .where { it.customer?.name == "Kim" }
        assertEquals("update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'"
            + " and e0.customer.id is not null", u.toHql().hql())
        // a path in a value: a subquery; NULL when there is no customer
        val copy = qb.update(Order::class.java).set { it.custName = it.customer?.name }.all()
        assertEquals("update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))",
            copy.toHql().hql())
        // @end
        val selected = ids(qb.from(Order::class.java).where { it.customer?.name == "Kim" }.toList())
        assertEquals(ids(before.filter { it.customer?.name == "Kim" }), selected)
        assertEquals(selected.size, u.execute())
        assertEquals(selected, orders().filter { it.custName == "K" }.map { it.id })
        assertEquals(before.size, copy.execute())
        for (o in orders()) assertEquals(o.customer?.name, o.custName)
    }

    @Test
    fun updateWhere() {
        items()
        // @start region="update-where"
        // a path: a subquery, as above
        val gold = qb.update(Order::class.java).set { it.status = 0 }.where { it.customer?.grade!! > 3 }
        assertEquals("update Order e0 set e0.status = 0 where (select s1.grade from Customer s1 where s1.id = fk(e0.customer)) > 3"
            + " and e0.customer.id is not null", gold.toHql().hql())
        // a subquery with sub, as in chapter 5
        val noItems = qb.update(Order::class.java).set { it.status = 9 }
            .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
        assertEquals("update Order e0 set e0.status = 9 where not exists (select 1 from Item s1 where s1.order.id = e0.id)",
            noItems.toHql().hql())
        // @end
        val withItems = setOf(before[0].id, before[2].id)
        val goldIds = before.filter { (it.customer?.grade ?: 0) > 3 }.map { it.id }.toSet()
        assertEquals(goldIds.size, gold.execute())
        assertEquals(before.count { it.id !in withItems }, noItems.execute())
        for (o in orders()) {
            val expected = when {
                o.id !in withItems -> 9
                o.id in goldIds -> 0
                else -> before.first { it.id == o.id }.status.toInt()
            }
            assertEquals(expected, o.status.toInt())
        }
    }

    @Test
    fun updateSubqueryCoalesce() {
        items()
        // @start region="update-sub-coalesce"
        // sum gives NULL when there is no row; Objects.requireNonNullElse becomes coalesce and keeps the NOT NULL price valid
        val u = qb.update(Order::class.java)
            .set { o, sub ->
                o.price = Objects.requireNonNullElse(sub.from(Item::class.java).where { i -> i.order?.id == o.id }.sum { it.price }, 0.0)
            }
            .all()
        assertEquals("update Order e0 set e0.price = coalesce((select sum(s1.price) from Item s1 where s1.order.id = e0.id), 0.0)",
            u.toHql().hql())
        // @end
        assertEquals(before.size, u.execute())
        assertEquals(listOf(30.0, 0.0, 5.0, 0.0), orders().map { it.price })
    }

    @Test
    fun versioned() {
        val start = find(VOrder::class.java, 1L)!!.version
        // @start region="update-versioned"
        // an entity with a version property (@Version): the version goes up too
        val u = qb.update(VOrder::class.java).set { it.status = 2 }.where { it.id == 1L }
        assertEquals("update versioned VOrder e0 set e0.status = 2 where e0.id = 1L", u.toHql().hql())
        // @end
        assertEquals(1, u.execute())
        assertEquals(start + 1, find(VOrder::class.java, 1L)!!.version)

        val id = 1L
        val v = find(VOrder::class.java, 1L)!!.version
        fun change(): Int {
            // @start region="update-version-check"
            val n = qb.update(VOrder::class.java)
                .set { it.status = 8 }
                .where { it.id == id && it.version == v }
                .execute()
            if (n == 0) throw OptimisticLockException()   // checking the result is your job
            // @end
            return n
        }
        assertEquals(1, change())
        assertEquals(8, find(VOrder::class.java, 1L)!!.status)
        assertThrows(OptimisticLockException::class.java) { change() }   // v is old now
    }

    @Test
    fun patch() {
        val id = before[0].id
        // @start region="update-patch"
        // PATCH: assign only the properties that have a value; every set() adds assignments
        val memo = "checked"
        val newStatus: Short? = null
        var u = qb.update(Order::class.java).set { it.description = memo }
        if (newStatus != null) u = u.set { it.status = newStatus }
        val changed = u.where { it.id == id }.execute()

        // reading a column that an earlier assignment wrote is an error (U3)
        val read = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { it.price = 1.0; it.total = it.price }
        }
        assertEquals("LQL-3042", read.code())
        // the same column twice is an error (U4), also across set() calls
        val twice = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { it.status = 1 }.set { it.status = 2 }
        }
        assertEquals("LQL-3041", twice.code())
        // @end
        assertEquals(1, changed)
        assertEquals("checked", find(Order::class.java, id!!)!!.description)
        assertEquals(before[0].status, find(Order::class.java, id)!!.status)
    }

    @Test
    fun updateAll() {
        // @start region="update-all"
        // no where: all() is required, without it there is no execute() (U5)
        val u = qb.update(Order::class.java).set { it.status = 0 }.all()
        assertEquals("update Order e0 set e0.status = 0", u.toHql().hql())
        val changed = u.execute()
        // @end
        assertEquals(before.size, changed)
        assertEquals(listOf(0), orders().map { it.status.toInt() }.distinct())
    }

    @Test
    fun updateLimit() {
        // @start region="update-limit"
        // a path in the condition: the nested select joins, so the rows are the same as the select
        val u = qb.update(Order::class.java)
            .set { it.description = "top" }
            .where { it.customer?.name == "Kim" }
            .orderBy { o, by -> by.desc(o.price) }
            .limit(1)
        assertEquals("update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0"
            + " join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)", u.toHql().hql())
        // @end
        assertEquals(1, u.execute())
        val top = before.filter { it.customer?.name == "Kim" }.sortedWith(compareByDescending<Order> { it.price }.thenBy { it.id }).first()
        assertEquals(listOf(top.id), orders().filter { it.description == "top" }.map { it.id })
    }

    @Test
    fun updateOne() {
        val id = before[1].id
        // @start region="update-one"
        // one row matches: it is changed
        val one = qb.update(Order::class.java)
            .set { it.description = "checked" }
            .where { it.id == id }
            .executeOne()                             // 0 or 1
        // two or more rows match: nothing is changed
        assertThrows(NonUniqueResultException::class.java) {
            qb.update(Order::class.java).set { it.description = "x" }.where { it.isActive }.executeOne()
        }
        // @end
        assertEquals(1, one)
        assertEquals(listOf(id), orders().filter { it.description == "checked" }.map { it.id })
        assertEquals(0, orders().count { it.description == "x" })
    }

    // ------------------------------------------------------------------ assignment forms and rules (7.3, 7.7, 7.8)

    @Test
    fun valueForms() {
        val st: Short = 7
        val id = before[0].id
        val city = "Busan"
        val cid = 2
        // @start region="value-forms"
        // Dml.value(column, value) for properties without a setter (val); one value call per statement
        val u = qb.update(Order::class.java)
            .set { o -> value(o.status, st); value(o.price, o.price * 1.1) }
            .where { it.status.toInt() == 1 }
        assertEquals("update Order e0 set e0.status = :p1, e0.price = (e0.price * 1.1) where e0.status = 1", u.toHql().hql())
        val embedded = qb.update(Order::class.java).set { o -> value(o.address.city, city) }.where { it.id == id }
        assertEquals("update Order e0 set e0.address.city = :p1 where e0.id = :p2", embedded.toHql().hql())
        val fk = qb.update(Order::class.java).set { o -> value(o.customer?.id, cid) }.where { it.id == id }
        assertEquals("update Order e0 set e0.customer.id = :p1 where e0.id = :p2", fk.toHql().hql())
        // @end
        val target = before.filter { it.status.toInt() == 1 }
        assertEquals(target.size, u.execute())
        assertEquals(1, embedded.execute())
        assertEquals(1, fk.execute())
        val after = orders().associateBy { it.id }
        for (o in target) {
            assertEquals(st, after[o.id]!!.status)
            assertEquals(o.price * 1.1, after[o.id]!!.price, 1e-9)
        }
        assertEquals("Busan", after[id]!!.address.city)
        assertEquals(2, after[id]!!.customer!!.id)
    }

    @Test
    fun oldValue() {
        // @start region="old-value"
        // the right side reads the value before the change
        val self = qb.update(Order::class.java).set { it.price = it.price * 1.1 }.all()             // reads itself: allowed
        val readFirst = qb.update(Order::class.java).set { it.total = it.price; it.price = 0.0 }.all() // read, then assign: allowed
        assertEquals("update Order e0 set e0.total = e0.price, e0.price = 0.0", readFirst.toHql().hql())
        // assign, then read again: an error at set() (U3), because MariaDB would read the new value
        val error = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { it.price = it.price * 2; it.total = it.price }
        }
        assertEquals("LQL-3042", error.code())
        // @end
        assertEquals(before.size, self.execute())
        assertEquals(before.size, readFirst.execute())
        val after = orders().associateBy { it.id }
        for (o in before) {
            assertEquals(o.price * 1.1, after[o.id]!!.total!!, 1e-9)
            assertEquals(0.0, after[o.id]!!.price)
        }
    }

    @Test
    fun assignRules() {
        // @start region="assign-rules"
        // forms that the assignment rules reject when the lambda is read (U1, U6)
        val increment = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { it.cnt++ }                       // ++: write += 1
        }
        val scope = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { it.apply { status = 1 } }        // apply: assign directly
        }
        val other = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { it.customer?.name = "x" }        // a property of another table
        }
        val noNull = assertThrows(LambdaQLException::class.java) {
            qb.update(Archive::class.java).set { it.id = null }.all()            // null into the @Id
        }
        // @end
        assertEquals("LQL-2003", increment.code(), increment.message)
        assertEquals("LQL-2003", scope.code(), scope.message)
        assertEquals("LQL-3050", other.code(), other.message)
        assertEquals("LQL-3044", noNull.code(), noNull.message)
    }

    // ------------------------------------------------------------------ delete

    @Test
    fun delete() {
        // @start region="delete"
        val d = qb.delete(Order::class.java).where { it.status.toInt() == 3 }
        assertEquals("delete from Order e0 where e0.status = 3", d.toHql().hql())
        val deleted = d.execute()

        // a path: the same rows as qb.from(Order::class.java).where { same lambda } - with !! orders without a customer stay
        val byName = qb.delete(Order::class.java).where { it.customer!!.name == null }
        assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null"
            + " and e0.customer.id is not null", byName.toHql().hql())
        // @end
        assertEquals(before.count { it.status.toInt() == 3 }, deleted)
        val selected = ids(qb.from(Order::class.java).where { it.customer!!.name == null }.toList())
        assertEquals(ids(before.filter { it.status.toInt() != 3 && it.customer != null && it.customer.name == null }), selected)
        val left = ids(orders())
        assertEquals(selected.size, byName.execute())
        assertEquals(left - selected.toSet(), ids(orders()))
    }

    @Test
    fun deleteSafeCall() {
        // @start region="delete-safe-call"
        // ?. : no "customer exists" check, so orders without a customer are deleted too, as the select returns them (D54)
        val d = qb.delete(Order::class.java).where { it.customer?.name == null }
        assertEquals("delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null",
            d.toHql().hql())
        // @end
        val selected = ids(qb.from(Order::class.java).where { it.customer?.name == null }.toList())
        val expected = ids(before.filter { it.customer?.name == null })
        assertEquals(expected, selected)
        assertEquals(true, before.any { it.customer == null && it.id in expected })
        assertEquals(expected.size, d.execute())
        assertEquals(ids(before) - expected.toSet(), ids(orders()))
    }

    @Test
    fun deleteIdsAndAll() {
        items()
        val ids = qb.from(Item::class.java).toList().map { it.id }
        // @start region="delete-all"
        // ids in a captured collection (D29)
        val byIds = qb.delete(Item::class.java).where { it.id in ids }
        assertEquals("delete from Item e0 where e0.id in (:p1)", byIds.toHql().hql())
        byIds.execute()
        // every row: all() is required (U5)
        val all = qb.delete(Order::class.java).all()
        assertEquals("delete from Order e0", all.toHql().hql())
        all.execute()
        // @end
        assertEquals(0, qb.from(Item::class.java).toList().size)
        assertEquals(0, orders().size)
    }

    @Test
    fun deleteNotExists() {
        items()
        // @start region="delete-not-exists"
        // orders without items: the subquery starts with the second lambda parameter sub
        val d = qb.delete(Order::class.java)
            .where { o, sub -> !sub.from(Item::class.java).where { i -> i.order?.id == o.id }.exists() }
        assertEquals("delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)", d.toHql().hql())
        val deleted = d.execute()
        // @end
        val withItems = listOf(before[0].id, before[2].id)
        assertEquals(before.size - withItems.size, deleted)
        assertEquals(withItems.sortedBy { it }, ids(orders()))
    }

    @Test
    fun deleteLimit() {
        // @start region="delete-limit"
        // the first rows of qb.from(Order::class.java).where { same }.orderBy { same }; the id is the last sort key
        val d = qb.delete(Order::class.java)
            .where { it.status.toInt() == 1 }
            .orderBy { o, by -> by.asc(o.price) }
            .limit(1)
        assertEquals("delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0"
            + " where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)", d.toHql().hql())
        val deleted = d.execute()
        // @end
        assertEquals(1, deleted)
        val cheapest = before.filter { it.status.toInt() == 1 }.minBy { it.price }
        assertEquals(ids(before - cheapest), ids(orders()))
    }

    @Test
    fun executeOne() {
        val id = before[0].id
        // @start region="execute-one"
        // one row matches: it is deleted
        val one = qb.delete(Order::class.java).where { it.id == id }.executeOne()
        // two or more rows match: nothing is deleted
        assertThrows(NonUniqueResultException::class.java) {
            qb.delete(Order::class.java).where { it.price > 100.0 }.executeOne()
        }
        // @end
        assertEquals(1, one)
        assertEquals(ids(before.drop(1)), ids(orders()))
    }

    // ------------------------------------------------------------------ insert

    @Test
    fun insert() {
        // @start region="insert"
        val i = qb.insert(Archive::class.java)
            .values { it.id = 100L; it.price = 9.5; it.customerName = "Kim" }
        assertEquals("insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')", i.toHql()[0].hql())
        val inserted = i.execute()
        // a generated id (IDENTITY) can be left out
        val generated = qb.insert(IdOrder::class.java).values { it.price = 1.0; it.status = 1 }.execute()
        // the values cannot read the new row
        val error = assertThrows(LambdaQLException::class.java) {
            qb.insert(Archive::class.java).values { it.id = 1L; it.price = it.price }
        }
        // @end
        assertEquals(1, inserted)
        assertEquals(1, generated)
        val a = find(Archive::class.java, 100L)!!
        assertEquals(9.5, a.price)
        assertEquals("Kim", a.customerName)
        assertEquals(listOf(1), qb.from(IdOrder::class.java).toList().map { it.status })
        assertEquals(true, error.code().startsWith("LQL-"))
    }

    @Test
    fun insertValue() {
        val id = 7L
        // @start region="insert-value"
        // properties without a setter (val): Dml.value
        val i = qb.insert(Archive::class.java).values { a -> value(a.id, id); value(a.price, 1.5) }
        assertEquals("insert into Archive (id, price) values (:p1, 1.5)", i.toHql()[0].hql())
        // @end
        assertEquals(1, i.execute())
        assertEquals(1.5, find(Archive::class.java, 7L)!!.price)
    }

    @Test
    fun insertList() {
        // @start region="insert-list"
        val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))      // data class Line(val id: Long, val price: Double)
        val i = qb.insert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }
        assertEquals("insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)", i.toHql()[0].hql())
        // @end
        assertEquals(2, i.execute())
        for (l in lines) assertEquals(l.price, find(Archive::class.java, l.id)!!.price)
        assertEquals(0, qb.insert(Archive::class.java).values(listOf<Line>()) { a, l -> a.id = l.id }.execute())
    }

    @Test
    fun insertListWithSubquery() {
        items()
        val refs = listOf(Ref(301L, before[0].id!!), Ref(302L, before[2].id!!))
        // @start region="insert-list-sub"
        // one row per element; the subquery reads the element's value (D49)
        val i = qb.insert(Archive::class.java).values(refs) { a, r, sub ->
            a.id = r.id
            a.price = sub.from(Item::class.java).where { x -> x.order?.id == r.orderId }.sum { x -> x.price }
        }
        assertEquals("insert into Archive (id, price) values (:p1, (select sum(s1.price) from Item s1 where s1.order.id = :p2)),"
            + " (:p3, (select sum(s1.price) from Item s1 where s1.order.id = :p4))", i.toHql()[0].hql())
        // @end
        assertEquals(2, i.execute())
        assertEquals(30.0, find(Archive::class.java, 301L)!!.price)
        assertEquals(5.0, find(Archive::class.java, 302L)!!.price)
    }

    @Test
    fun insertSelect() {
        // @start region="insert-select"
        val i = qb.insert(Archive::class.java)
            .from(Order::class.java)
            .where { it.status.toInt() == 1 }
            .select { a, o -> a.id = o.id; a.price = o.price; a.customerName = o.customer?.name }
        // a path in the select list: left join, so orders without a customer stay (U10)
        assertEquals("insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name"
            + " from Order e0 left join e0.customer j1 where e0.status = 1", i.toHql()[0].hql())
        // @end
        val source = before.filter { it.status.toInt() == 1 }
        assertEquals(source.size, i.execute())
        for (o in source) {
            val a = find(Archive::class.java, o.id!!)!!
            assertEquals(o.price, a.price)
            assertEquals(o.customer?.name, a.customerName)
        }
    }

    @Test
    fun insertSelectValue() {
        // @start region="insert-select-value"
        // Dml.value in insert ... select: the first argument is a property of the target a
        val i = qb.insert(Archive::class.java).from(Order::class.java).where { it.status.toInt() == 3 }
            .select { a, o -> value(a.id, o.id); value(a.customerName, o.customer?.name) }
        assertEquals("insert into Archive (id, customerName) select e0.id, j1.name from Order e0 left join e0.customer j1"
            + " where e0.status = 3", i.toHql()[0].hql())
        // @end
        val source = before.filter { it.status.toInt() == 3 }
        assertEquals(source.size, i.execute())
        for (o in source) assertEquals(o.customer?.name, find(Archive::class.java, o.id!!)!!.customerName)
    }

    @Test
    fun insertSelectWithSubquery() {
        items()
        // @start region="insert-select-sub"
        // where { o, sub -> ... } on the source, and select { a, o, sub -> ... }: the third parameter starts a subquery
        val i = qb.insert(Archive::class.java).from(Order::class.java)
            .where { o, sub -> sub.from(Item::class.java).where { x -> x.order?.id == o.id }.exists() }
            .select { a, o, sub -> a.id = o.id; a.price = sub.from(Item::class.java).where { x -> x.order?.id == o.id }.sum { x -> x.price } }
        assertEquals("insert into Archive (id, price) select e0.id, (select sum(s2.price) from Item s2 where s2.order.id = e0.id)"
            + " from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id)", i.toHql()[0].hql())
        // @end
        assertEquals(2, i.execute())
        assertEquals(30.0, find(Archive::class.java, before[0].id!!)!!.price)
        assertEquals(5.0, find(Archive::class.java, before[2].id!!)!!.price)
    }

    @Test
    fun insertGroup() {
        // @start region="insert-group"
        // INSERT ... SELECT with groupBy: one summary row per customer
        val i = qb.insert(Summary::class.java)
            .from(Order::class.java)
            .where { it.customer != null }
            .groupBy { it.customer?.id }
            .having { Agg.count() >= 2 }
            .select { s, o -> s.customerId = o.customer?.id; s.total = Agg.sum(o.price); s.cnt = Agg.count() }
        assertEquals("insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0"
            + " where e0.customer is not null group by e0.customer.id having count(*) >= 2L", i.toHql()[0].hql())
        // @end
        val expected = before.filter { it.customer != null }.groupBy { it.customer!!.id }.filter { it.value.size >= 2 }
        assertEquals(expected.size, i.execute())
        for ((cid, os) in expected) {
            val s = find(Summary::class.java, cid)!!
            assertEquals(os.sumOf { it.price }, s.total!!, 1e-9)
            assertEquals(os.size.toLong(), s.cnt)
        }
    }

    // ------------------------------------------------------------------ upsert

    @Test
    fun upsert() {
        // @start region="upsert"
        // the key is the @Id; on conflict, the other assigned columns get the new values
        val inserted = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 1.0 }.execute()
        val again = qb.upsert(Archive::class.java).values { it.id = 100L; it.price = 2.0 }
        assertEquals("insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price",
            again.toHql()[0].hql())
        val updated = again.execute()                 // MariaDB and MySQL count an updated row as 2, the others as 1
        // @end
        assertEquals(1, inserted)
        assertEquals(if (mysqlFamily()) 2 else 1, updated)
        assertEquals(2.0, find(Archive::class.java, 100L)!!.price)
    }

    @Test
    fun onConflictUpdate() {
        // @start region="on-conflict"
        // on conflict, change the existing row: a property read here reads the existing row
        val add = qb.insert(Archive::class.java)
            .values { it.id = 100L; it.price = 5.0 }
            .onConflict { it.id }
            .update { it.price = it.price + 5.0 }
        assertEquals("insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)",
            add.toHql()[0].hql())
        add.execute()                                 // inserted: 5.0
        add.execute()                                 // conflict: 5.0 + 5.0
        // @end
        assertEquals(10.0, find(Archive::class.java, 100L)!!.price)
    }

    @Test
    fun upsertWithSubquery() {
        items()
        // @start region="upsert-sub"
        // a subquery in the inserted value and in the update on conflict (D49)
        val up = qb.insert(Archive::class.java)
            .values { a, sub -> a.id = 100L; a.price = sub.from(Item::class.java).sum { x -> x.price } }
            .onConflict { it.id }
            .update { a, sub -> a.price = sub.from(Order::class.java).max { o -> o.price } }
        assertEquals("insert into Archive e0 (id, price) values (100L, (select sum(s1.price) from Item s1))"
            + " on conflict(id) do update set price = (select max(s1.price) from Order s1)", up.toHql()[0].hql())
        // @end
        up.execute()                                  // inserted: the sum of the item prices
        assertEquals(35.0, find(Archive::class.java, 100L)!!.price)
        up.execute()                                  // conflict: the largest order price
        assertEquals(before.maxOf { it.price }, find(Archive::class.java, 100L)!!.price)
    }

    @Test
    fun insertIgnore() {
        // @start region="insert-ignore"
        val once = qb.insert(Archive::class.java)
            .values { it.id = 300L; it.price = 1.0 }
            .onConflict { it.id }
            .ignore()
        assertEquals("insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)",
            once.toHql()[0].hql())
        val first = once.execute()                    // 1: inserted
        val second = qb.insert(Archive::class.java).values { it.id = 300L; it.price = 2.0 }
            .onConflict { it.id }.ignore().execute()  // 0: the row exists, nothing changes
        // @end
        assertEquals(1, first)
        assertEquals(0, second)
        assertEquals(1.0, find(Archive::class.java, 300L)!!.price)
    }

    @Test
    fun upsertList() {
        // @start region="upsert-list"
        // one statement per element; execute() returns the sum
        val lines = listOf(Line(201L, 1.0), Line(202L, 2.0))
        val first = qb.upsert(Archive::class.java).values(lines) { a, l -> a.id = l.id; a.price = l.price }.execute()
        val changed = listOf(Line(202L, 20.0), Line(203L, 3.0))
        val second = qb.upsert(Archive::class.java).values(changed) { a, l -> a.id = l.id; a.price = l.price }.execute()
        // @end
        assertEquals(2, first)
        assertEquals(if (mysqlFamily()) 3 else 2, second)          // 202 updated (2 on MariaDB and MySQL), 203 inserted
        assertEquals(1.0, find(Archive::class.java, 201L)!!.price)
        assertEquals(20.0, find(Archive::class.java, 202L)!!.price)
        assertEquals(3.0, find(Archive::class.java, 203L)!!.price)
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
