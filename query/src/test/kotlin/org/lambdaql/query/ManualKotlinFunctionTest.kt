package org.lambdaql.query

import org.lambdaql.query.By.desc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.lambdaql.DbOnlyException
import org.lambdaql.LambdaQLException
import org.lambdaql.NonSargableException
import org.lambdaql.SqlFunction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * A database function as a top-level Kotlin function. It works, but it is not the advised form:
 * without the import, a standard library function with the same name could be called instead.
 */
@SqlFunction("SQ_FN_DISCOUNT")
fun manualKotlinTopLevelDiscount(price: Double?): Double? = throw DbOnlyException()

/**
 * Kotlin forms of database functions in queries (manual chapter 8, Kotlin track): `@SqlFunction` functions in the
 * `companion object` of an interface, with `@JvmStatic`. The functions `SQ_FN_DISCOUNT` and `SQ_FN_IS_GOLD` are made
 * for the test on each database and dropped at the end.
 * Each test also computes the expected rows in Kotlin from the seeded data.
 * The region markers are used by the manual (Kotlin track).
 */
@SpringBootTest(classes = [ManualKotlinFunctionTest.Context::class])
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManualKotlinFunctionTest {

    // @start region="declare"
    /** Database functions: functions in the companion object of your own interface. The bodies only throw. */
    interface OrderFns {
        companion object {
            /** `SQ_FN_DISCOUNT(price)`: 90% of the price. */
            @JvmStatic
            @SqlFunction("SQ_FN_DISCOUNT")
            fun discount(price: Double?): Double? = throw DbOnlyException()

            /** `SQ_FN_IS_GOLD(grade)`: a grade of 5 or more. */
            @JvmStatic
            @SqlFunction("SQ_FN_IS_GOLD")
            fun isGold(grade: Int?): Boolean? = throw DbOnlyException()
        }
    }
    // @end

    /** The same function in a Kotlin object. */
    object PriceFns {
        @JvmStatic
        @SqlFunction("SQ_FN_DISCOUNT")
        fun discount(price: Double?): Double? = throw DbOnlyException()
    }

    /** A function used by the SARGable test: on MariaDB, MySQL and PostgreSQL it is made not deterministic. */
    interface CheckFns {
        companion object {
            @JvmStatic
            @SqlFunction("SQ_FN_NONDET")
            fun nondet(value: Double?): Double? = throw DbOnlyException()
        }
    }

    // @start region="bad-declare"
    /** Functions that break the rules. */
    interface BadFns {
        companion object {
            @JvmStatic
            @SqlFunction("SQ_FN_DISCOUNT")
            fun computes(price: Double?): Double? = price                  // a body that computes a value

            @JvmStatic
            @SqlFunction("SQ_FN_DISCOUNT")
            fun withDefault(price: Double?, rate: Double = 0.9): Double? = throw DbOnlyException()   // a default argument

            @JvmStatic
            @SqlFunction("SQ_FN_DISCOUNT")
            fun many(vararg prices: Double?): Double? = throw DbOnlyException()                      // vararg

            @JvmStatic
            @SqlFunction(value = "SQ_TAX_KT", sql = "SQ_FN_DISCOUNT")
            fun unregistered(price: Double?): Double? = throw DbOnlyException()   // its own SQL name, not registered
        }
    }
    // @end

    /** A row with a computed value. */
    data class Priced(val id: Long?, val discounted: Double?)

    private val db = System.getProperty("lql.db", "h2")

    private val create = mapOf(
        "h2" to listOf(
            "CREATE ALIAS IF NOT EXISTS SQ_FN_DISCOUNT FOR \"org.lambdaql.query.H2Functions.discount\"",
            "CREATE ALIAS IF NOT EXISTS SQ_FN_IS_GOLD FOR \"org.lambdaql.query.H2Functions.isGold\""),
        "mariadb" to listOf(
            "CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p DOUBLE) RETURNS DOUBLE DETERMINISTIC RETURN p * 0.9",
            "CREATE OR REPLACE FUNCTION SQ_FN_IS_GOLD(g INT) RETURNS BOOLEAN DETERMINISTIC RETURN g >= 5"),
        "oracle" to listOf(
            "CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p IN NUMBER) RETURN NUMBER DETERMINISTIC IS BEGIN RETURN p * 0.9; END;",
            "CREATE OR REPLACE FUNCTION SQ_FN_IS_GOLD(g IN NUMBER) RETURN BOOLEAN DETERMINISTIC IS BEGIN RETURN g >= 5; END;"),
        // MySQL has no CREATE OR REPLACE FUNCTION
        "mysql" to listOf(
            "DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT", "DROP FUNCTION IF EXISTS SQ_FN_IS_GOLD",
            "CREATE FUNCTION SQ_FN_DISCOUNT(p DOUBLE) RETURNS DOUBLE DETERMINISTIC NO SQL RETURN p * 0.9",
            "CREATE FUNCTION SQ_FN_IS_GOLD(g INT) RETURNS BOOLEAN DETERMINISTIC NO SQL RETURN g >= 5"),
        // plpgsql, not LANGUAGE sql: PostgreSQL inlines a simple SQL function, so it would not be a function call
        "postgresql" to listOf(
            "CREATE OR REPLACE FUNCTION SQ_FN_DISCOUNT(p DOUBLE PRECISION) RETURNS DOUBLE PRECISION LANGUAGE plpgsql IMMUTABLE"
                + " AS 'BEGIN RETURN p * 0.9; END'",
            "CREATE OR REPLACE FUNCTION SQ_FN_IS_GOLD(g INTEGER) RETURNS BOOLEAN LANGUAGE plpgsql IMMUTABLE"
                + " AS 'BEGIN RETURN g >= 5; END'"))

    private val drop = mapOf(
        "h2" to listOf("DROP ALIAS IF EXISTS SQ_FN_DISCOUNT", "DROP ALIAS IF EXISTS SQ_FN_IS_GOLD"),
        "mariadb" to listOf("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT", "DROP FUNCTION IF EXISTS SQ_FN_IS_GOLD"),
        "oracle" to listOf("DROP FUNCTION SQ_FN_DISCOUNT", "DROP FUNCTION SQ_FN_IS_GOLD", "PURGE RECYCLEBIN"),
        "mysql" to listOf("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT", "DROP FUNCTION IF EXISTS SQ_FN_IS_GOLD"),
        "postgresql" to listOf("DROP FUNCTION IF EXISTS SQ_FN_DISCOUNT(DOUBLE PRECISION)", "DROP FUNCTION IF EXISTS SQ_FN_IS_GOLD(INTEGER)"))

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (sql in create.getValue(db)) em.createNativeQuery(sql).executeUpdate()
        for (e in listOf("Summary", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null).apply { grade = 2 }
        em.persist(kim)
        em.persist(lee)
        for ((price, customer) in listOf(50.0 to kim, 150.0 to kim, 250.0 to lee, 120.0 to null)) {
            em.persist(Order().apply {
                this.status = 1
                this.price = price
                this.isActive = true
                this.customer = customer
            })
        }
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        em.close()
    }

    @AfterAll
    fun dropFunctions() {
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (sql in drop.getValue(db)) em.createNativeQuery(sql).executeUpdate()
        em.transaction.commit()
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun where() {
        // @start region="where"
        // the function call is written as function('<name>' as <type>, ...); the column is its argument
        val q = qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }
        assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.price * 0.9 > 100 }), ids(q.toList()))
    }

    @Test
    fun capturedArgument() {
        val base = 150.0
        // @start region="captured"
        // a function of captured values only is still computed by the database (the value is bound)
        val q = qb.from(Order::class.java).where { it.price > OrderFns.discount(base)!! }
        assertEquals("select e0 from Order e0 where e0.price > function('SQ_FN_DISCOUNT' as Double, :p1)", q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.price > base * 0.9 }), ids(q.toList()))
    }

    @Test
    fun booleanFunction() {
        // @start region="boolean"
        val q = qb.from(Order::class.java).where { OrderFns.isGold(it.customer!!.grade)!! }
        assertEquals("select e0 from Order e0 join e0.customer j1 where function('SQ_FN_IS_GOLD' as Boolean, j1.grade) = true",
            q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.customer != null && it.customer.grade >= 5 }), ids(q.toList()))
    }

    @Test
    fun selectAndSort() {
        // @start region="select"
        val q = qb.from(Order::class.java)
            .orderBy { o -> desc(OrderFns.discount(o.price)) }
            .select { Priced(it.id, OrderFns.discount(it.price)) }
        assertEquals("select e0.id, function('SQ_FN_DISCOUNT' as Double, e0.price) from Order e0"
            + " order by function('SQ_FN_DISCOUNT' as Double, e0.price) desc nulls last", q.toHql().hql())
        // @end
        val rows = q.toList()
        assertEquals(orders.sortedByDescending { it.price }.map { it.id }, rows.map { it.id })
        for (row in rows) {
            assertEquals(orders.first { it.id == row.id }.price * 0.9, row.discounted!!, 1e-9)
        }
    }

    @Test
    fun update() {
        // @start region="update"
        val u = qb.update(Order::class.java)
            .set { it.price = OrderFns.discount(it.price)!! }
            .where { it.price > 100 }
        assertEquals("update Order e0 set e0.price = function('SQ_FN_DISCOUNT' as Double, e0.price) where e0.price > 100.0",
            u.toHql().hql())
        // @end
        assertEquals(orders.count { it.price > 100 }, u.execute())
    }

    @Test
    fun callAlone() {
        val price = 150.0
        // @start region="call"
        // a function on its own: a lambda without parameters
        val discounted: Double? = qb.call { OrderFns.discount(price) }.execute()
        // @end
        assertEquals(price * 0.9, discounted!!, 1e-9)
    }

    @Test
    fun otherDeclarations() {
        // @start region="other-forms"
        // a Kotlin object with @JvmStatic, and a top-level function: the same HQL
        val inObject = qb.from(Order::class.java).where { PriceFns.discount(it.price)!! > 100 }
        val topLevel = qb.from(Order::class.java).where { manualKotlinTopLevelDiscount(it.price)!! > 100 }
        assertEquals(inObject.toHql().hql(), topLevel.toHql().hql())
        // @end
        assertEquals("select e0 from Order e0 where function('SQ_FN_DISCOUNT' as Double, e0.price) > 100.0", inObject.toHql().hql())
        val expected = ids(orders.filter { it.price * 0.9 > 100 })
        assertEquals(expected, ids(inObject.toList()))
        assertEquals(expected, ids(topLevel.toList()))
        // a Java interface with static methods, called from Kotlin
        val fromJava = qb.from(Order::class.java).where { FunctionTest.OrderFns.discount(it.price) > 100 }
        assertEquals(inObject.toHql().hql(), fromJava.toHql().hql())
        assertEquals(expected, ids(fromJava.toList()))
        // a value of a Kotlin function type is not a database function: an unsupported call
        val fn: (Double?) -> Double? = { it }
        assertEquals("LQL-3008", assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { fn(it.price)!! > 100 }
        }.code())
    }

    @Test
    fun rules() {
        // @start region="rules"
        // the body never runs in Kotlin
        assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
        // a body that computes a value is rejected (Kotlin and the database could give different values)
        assertEquals("LQL-3052", assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { BadFns.computes(it.price)!! > 1 }
        }.code())
        // a call that uses a Kotlin default argument, and a vararg function
        assertEquals("LQL-3056", assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { BadFns.withDefault(it.price)!! > 1 }
        }.code())
        assertEquals("LQL-3057", assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { BadFns.many(it.price)!! > 1 }
        }.code())
        // a function with its own SQL name must be registered (lambdaql.functions.classes)
        assertEquals("LQL-3058", assertThrows<LambdaQLException> {
            qb.from(Order::class.java).where { BadFns.unregistered(it.price)!! > 1 }
        }.code())
        // @end
    }

    @Test
    fun sargableWarnings() {
        val log = LoggerFactory.getLogger(SargableCheck::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        log.addAppender(appender)
        val em = emf.createEntityManager()
        em.transaction.begin()
        // a function-based (expression) index: Oracle and PostgreSQL; MariaDB and MySQL cannot index a stored function
        if (db == "oracle" || db == "postgresql") {
            em.createNativeQuery("CREATE INDEX SQ_IX_DISCOUNT ON SQ_orders (SQ_FN_DISCOUNT(price))").executeUpdate()
        }
        if (db == "mariadb") {
            em.createNativeQuery("CREATE OR REPLACE FUNCTION SQ_FN_NONDET(p DOUBLE) RETURNS DOUBLE NOT DETERMINISTIC RETURN p").executeUpdate()
        }
        if (db == "mysql") {
            em.createNativeQuery("DROP FUNCTION IF EXISTS SQ_FN_NONDET").executeUpdate()
            em.createNativeQuery("CREATE FUNCTION SQ_FN_NONDET(p DOUBLE) RETURNS DOUBLE NOT DETERMINISTIC NO SQL RETURN p").executeUpdate()
        }
        // PostgreSQL: VOLATILE (the default) is the NOT DETERMINISTIC of MariaDB and MySQL
        if (db == "postgresql") {
            em.createNativeQuery("CREATE OR REPLACE FUNCTION SQ_FN_NONDET(p DOUBLE PRECISION) RETURNS DOUBLE PRECISION"
                + " LANGUAGE plpgsql VOLATILE AS 'BEGIN RETURN p; END'").executeUpdate()
        }
        em.transaction.commit()
        try {
            val base = 100.0
            // @start region="sargable"
            // the function wraps the column: an index on price cannot be used (LQL-5001 at WARN),
            // unless Oracle or PostgreSQL has a function-based index on SQ_FN_DISCOUNT(price)
            qb.from(Order::class.java).where { OrderFns.discount(it.price)!! > 100 }.toHql()
            // the function is on the value side: fine, unless the database says it is not deterministic (LQL-5007 at WARN)
            qb.from(Order::class.java).where { it.price > CheckFns.nondet(base)!! }.toHql()
            // @end
            val codes = appender.list.map { it.formattedMessage.substring(1, 9) }
            val expected = when (db) {
                "oracle" -> listOf()                                    // function-based index
                "postgresql" -> listOf("LQL-5007")                      // expression index; SQ_FN_NONDET is VOLATILE
                "mariadb", "mysql" -> listOf("LQL-5001", "LQL-5007")
                else -> listOf("LQL-5001")
            }
            assertEquals(expected, codes, appender.list.toString())
        } finally {
            log.detachAppender(appender)
            em.transaction.begin()
            if (db == "oracle" || db == "postgresql") em.createNativeQuery("DROP INDEX SQ_IX_DISCOUNT").executeUpdate()
            if (db == "mariadb" || db == "mysql") em.createNativeQuery("DROP FUNCTION IF EXISTS SQ_FN_NONDET").executeUpdate()
            if (db == "postgresql") em.createNativeQuery("DROP FUNCTION IF EXISTS SQ_FN_NONDET(DOUBLE PRECISION)").executeUpdate()
            em.transaction.commit()
            em.close()
        }
    }

    @Test
    fun sargableStrict() {
        val n = 5
        try {
            // @start region="sargable-strict"
            // STRICT: what WARN would log fails the analysis (NonSargableException, LQL-3066)
            SargablePolicy.register(emf, SargableMode.STRICT)
            val fn = assertThrows<NonSargableException> {
                qb.from(Order::class.java).where { OrderFns.discount(it.total)!! > 100 }.toHql()
            }
            assertEquals("LQL-3066", fn.code())
            assertThrows<NonSargableException> { qb.from(Order::class.java).where { it.cnt!! * 2 > n }.toHql() }
            // the same meaning on the value side uses the index; divide by a decimal (n / 2 is integer division)
            val moved = qb.from(Order::class.java).where { it.cnt!! > n / 2.0 }.toHql()
            assertEquals("select e0 from Order e0 where e0.cnt > 2.5BD", moved.hql())
            // arithmetic between columns cannot move, so STRICT allows it
            qb.from(Order::class.java).where { it.cnt!! * it.status > n }.toHql()
            // @end
        } finally {
            SargablePolicy.unregister(emf)
        }
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
