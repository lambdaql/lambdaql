package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.lambdaql.DbParam
import org.lambdaql.InOut
import org.lambdaql.LambdaQLException
import org.lambdaql.SqlProcedure
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin forms of stored procedure calls (manual chapter 8, Kotlin track).
 * H2 has no OUT parameters, so the calls run on MariaDB, MySQL, PostgreSQL and Oracle only (PR15); the checks made
 * while lambdaQL reads the lambda run on H2 too. The procedures are made for the test and dropped at the end.
 * The region markers are used by the manual (Kotlin track).
 */
@SpringBootTest(classes = [ManualKotlinProcedureTest.Context::class])
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManualKotlinProcedureTest {

    // @start region="declare"
    /** Two OUT values: a data class, in the database order. */
    data class Stat(val total: Double?, val cnt: Int?)

    /** The INOUT value and the OUT value, in the database order. */
    data class Adjusted(val balance: Int?, val msg: String?)

    /** A row of a result set: columns are matched by name (customer_id or customerId). */
    data class Line(val id: Long?, val price: Double?)

    /** A stub interface: lambdaQL reads the calls and never implements it. */
    interface OrderProcs {
        @SqlProcedure("SQ_PROC_CALC")
        fun calc(customerId: Int, rate: Double): Double?                // IN, IN, OUT (non-null parameters)

        @SqlProcedure("SQ_PROC_STAT")
        fun stat(customerId: Int?): Stat                                // IN, OUT, OUT

        @SqlProcedure("SQ_PROC_ADJUST")
        fun adjust(add: Int?, @InOut balance: Int?): Adjusted           // IN, INOUT, OUT

        @SqlProcedure("SQ_PROC_RECENT")
        fun recent(customerId: Int?): List<Line>                        // a result set (Oracle, PostgreSQL: a cursor)

        @SqlProcedure("SQ_PROC_PURGE")
        fun purge(status: Int?)                                         // no result

        @SqlProcedure("SQ_PROC_CALC")
        fun calcWrong(customerId: Int?): Double?                        // does not match the database: 1 argument
    }
    // @end

    // @start region="names"
    /** kotlinc keeps argument names only with -java-parameters; @DbParam gives the database name without it. */
    interface DiffProcs {
        @SqlProcedure("SQ_PROC_DIFF")
        fun diff(@DbParam("p_to") to: Int?, @DbParam("p_from") from: Int?): Int?        // the database order: runs

        @SqlProcedure("SQ_PROC_DIFF")
        fun swapped(@DbParam("p_from") from: Int?, @DbParam("p_to") to: Int?): Int?     // the same names, other order

        @SqlProcedure("SQ_PROC_DIFF")
        fun text(to: String?, from: Int?): Int?                                         // a string for a number parameter
    }
    // @end

    // @start region="package"
    /** Oracle package procedures: TWICE has one form, HALF has two (an overload). */
    interface PkgProcs {
        @SqlProcedure(pkg = "SQ_PKG", value = "TWICE")
        fun twice(value: Double?): Double?

        @SqlProcedure(pkg = "SQ_PKG", value = "HALF")
        fun half(value: Double?): Double?
    }
    // @end

    private val db = System.getProperty("lql.db", "h2")

    private val mariadb = listOf(
        "CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE, OUT p_total DOUBLE)"
            + " BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_STAT(IN p_cust INT, OUT p_total DOUBLE, OUT p_cnt INT)"
            + " BEGIN SELECT SUM(price), COUNT(*) INTO p_total, p_cnt FROM SQ_orders WHERE customer_id = p_cust; END",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_ADJUST(IN p_add INT, INOUT p_balance INT, OUT p_msg VARCHAR(20))"
            + " BEGIN SET p_balance = p_balance + p_add; SET p_msg = 'ok'; END",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_RECENT(IN p_cust INT)"
            + " BEGIN SELECT id, price FROM SQ_orders WHERE customer_id = p_cust ORDER BY id; END",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_PURGE(IN p_status INT) BEGIN DELETE FROM SQ_orders WHERE status = p_status; END",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_DIFF(IN p_to INT, IN p_from INT, OUT p_diff INT) BEGIN SET p_diff = p_to - p_from; END")

    private val mariadbDrop = listOf("DROP PROCEDURE IF EXISTS SQ_PROC_CALC", "DROP PROCEDURE IF EXISTS SQ_PROC_STAT",
        "DROP PROCEDURE IF EXISTS SQ_PROC_ADJUST", "DROP PROCEDURE IF EXISTS SQ_PROC_RECENT",
        "DROP PROCEDURE IF EXISTS SQ_PROC_PURGE", "DROP PROCEDURE IF EXISTS SQ_PROC_DIFF")

    /** PostgreSQL 14+: procedures with OUT parameters; a result set is a refcursor OUT parameter. */
    private val postgresql = listOf(
        "CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(IN p_cust INT, IN p_rate DOUBLE PRECISION, OUT p_total DOUBLE PRECISION)"
            + " LANGUAGE plpgsql AS $$ BEGIN SELECT COALESCE(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders"
            + " WHERE customer_id = p_cust; END $$",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_STAT(IN p_cust INT, OUT p_total DOUBLE PRECISION, OUT p_cnt INT)"
            + " LANGUAGE plpgsql AS $$ BEGIN SELECT SUM(price), COUNT(*) INTO p_total, p_cnt FROM SQ_orders"
            + " WHERE customer_id = p_cust; END $$",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_ADJUST(IN p_add INT, INOUT p_balance INT, OUT p_msg VARCHAR)"
            + " LANGUAGE plpgsql AS $$ BEGIN p_balance = p_balance + p_add; p_msg = 'ok'; END $$",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_RECENT(IN p_cust INT, OUT p_cur REFCURSOR)"
            + " LANGUAGE plpgsql AS $$ BEGIN OPEN p_cur FOR SELECT id, price FROM SQ_orders WHERE customer_id = p_cust"
            + " ORDER BY id; END $$",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_PURGE(IN p_status INT) LANGUAGE plpgsql"
            + " AS $$ BEGIN DELETE FROM SQ_orders WHERE status = p_status; END $$",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_DIFF(IN p_to INT, IN p_from INT, OUT p_diff INT) LANGUAGE plpgsql"
            + " AS $$ BEGIN p_diff = p_to - p_from; END $$")

    private val oracle = listOf(
        "CREATE OR REPLACE PROCEDURE SQ_PROC_CALC(p_cust IN NUMBER, p_rate IN NUMBER, p_total OUT NUMBER) IS"
            + " BEGIN SELECT NVL(SUM(price), 0) * p_rate INTO p_total FROM SQ_orders WHERE customer_id = p_cust; END;",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_STAT(p_cust IN NUMBER, p_total OUT NUMBER, p_cnt OUT NUMBER) IS"
            + " BEGIN SELECT SUM(price), COUNT(*) INTO p_total, p_cnt FROM SQ_orders WHERE customer_id = p_cust; END;",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_ADJUST(p_add IN NUMBER, p_balance IN OUT NUMBER, p_msg OUT VARCHAR2) IS"
            + " BEGIN p_balance := p_balance + p_add; p_msg := 'ok'; END;",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_RECENT(p_cust IN NUMBER, p_cur OUT SYS_REFCURSOR) IS"
            + " BEGIN OPEN p_cur FOR SELECT id, price FROM SQ_orders WHERE customer_id = p_cust ORDER BY id; END;",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_PURGE(p_status IN NUMBER) IS BEGIN DELETE FROM SQ_orders WHERE status = p_status; END;",
        "CREATE OR REPLACE PROCEDURE SQ_PROC_DIFF(p_to IN NUMBER, p_from IN NUMBER, p_diff OUT NUMBER) IS BEGIN p_diff := p_to - p_from; END;",
        "CREATE OR REPLACE PACKAGE SQ_PKG AS PROCEDURE twice(p IN NUMBER, r OUT NUMBER);"
            + " PROCEDURE half(p IN NUMBER, r OUT NUMBER); PROCEDURE half(p IN NUMBER, q IN NUMBER, r OUT NUMBER); END;",
        "CREATE OR REPLACE PACKAGE BODY SQ_PKG AS PROCEDURE twice(p IN NUMBER, r OUT NUMBER) IS BEGIN r := p * 2; END;"
            + " PROCEDURE half(p IN NUMBER, r OUT NUMBER) IS BEGIN r := p / 2; END;"
            + " PROCEDURE half(p IN NUMBER, q IN NUMBER, r OUT NUMBER) IS BEGIN r := p / q; END; END;")

    private val create = mapOf(
        "h2" to listOf(),
        "mariadb" to mariadb,
        // MySQL has no OR REPLACE: drop first, then create
        "mysql" to mariadbDrop + mariadb.map { it.replace("CREATE OR REPLACE ", "CREATE ") },
        "postgresql" to postgresql,
        "oracle" to oracle)

    private val drop = mapOf(
        "h2" to listOf(),
        "mariadb" to mariadbDrop,
        "mysql" to mariadbDrop,
        "postgresql" to mariadbDrop,
        "oracle" to listOf("DROP PROCEDURE SQ_PROC_CALC", "DROP PROCEDURE SQ_PROC_STAT", "DROP PROCEDURE SQ_PROC_ADJUST",
            "DROP PROCEDURE SQ_PROC_RECENT", "DROP PROCEDURE SQ_PROC_PURGE", "DROP PROCEDURE SQ_PROC_DIFF",
            "DROP PACKAGE SQ_PKG", "PURGE RECYCLEBIN"))

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
        val kim = Customer(1, "Kim", null)
        em.persist(kim)
        for ((status, price, customer) in listOf(Triple(1, 50.0, kim), Triple(1, 150.0, kim), Triple(9, 70.0, kim), Triple(9, 250.0, null))) {
            em.persist(Order().apply {
                this.status = status.toShort()
                this.price = price
                this.isActive = true
                this.customer = customer
            })
        }
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order::class.java).resultList
        em.close()
    }

    @AfterAll
    fun dropProcedures() {
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (sql in drop.getValue(db)) em.createNativeQuery(sql).executeUpdate()
        em.transaction.commit()
        em.close()
    }

    private fun ofKim() = orders.filter { it.customer?.id == 1 }

    @Test
    fun call() {
        assumeFalse(db == "h2", "H2 has no OUT parameters (PR15)")
        val customerId = 1
        val rate = 2.0
        // @start region="call"
        val total: Double? = qb.call(OrderProcs::class.java) { it.calc(customerId, rate) }.execute()
        val stat: Stat = qb.call(OrderProcs::class.java) { it.stat(customerId) }.execute()
        val recent: List<Line> = qb.call(OrderProcs::class.java) { it.recent(customerId) }.execute()
        // named arguments with plain values: the compiler puts them back in the stub order
        val same: Double? = qb.call(OrderProcs::class.java) { it.calc(rate = rate, customerId = customerId) }.execute()
        // @end
        val sum = ofKim().sumOf { it.price }
        assertEquals(sum * rate, total!!, 1e-9)
        assertEquals(sum * rate, same!!, 1e-9)
        assertEquals(sum, stat.total!!, 1e-9)
        assertEquals(ofKim().size, stat.cnt)
        assertEquals(ofKim().map { Line(it.id, it.price) }, recent)
    }

    @Test
    fun inOut() {
        assumeFalse(db == "h2", "H2 has no OUT parameters (PR15)")
        // @start region="inout"
        // 10 goes in as the INOUT value; the new balance comes back in the data class
        val adjusted: Adjusted = qb.call(OrderProcs::class.java) { it.adjust(5, 10) }.execute()
        assertEquals(Adjusted(15, "ok"), adjusted)
        // @end
    }

    @Test
    fun exec() {
        assumeFalse(db == "h2", "H2 has no OUT parameters (PR15)")
        // @start region="exec"
        // no result: exec, not call
        qb.exec(OrderProcs::class.java) { it.purge(9) }.execute()
        // @end
        assertEquals(orders.filter { it.status.toInt() != 9 }.map { it.id }, qb.from(Order::class.java).toList().map { it.id }.sorted())
    }

    @Test
    fun argumentNames() {
        assumeFalse(db == "h2", "H2 has no OUT parameters (PR15)")
        // @start region="name-check"
        assertEquals(7, qb.call(DiffProcs::class.java) { it.diff(10, 3) }.execute())
        // the same names in another order: stopped before the call (it would run with the values swapped)
        assertEquals("LQL-4043", assertThrows<LambdaQLException> {
            qb.call(DiffProcs::class.java) { it.swapped(3, 10) }.execute()
        }.code())
        // the kind of each argument is compared with the database parameter: a string for a number parameter stops
        assertEquals("LQL-4036", assertThrows<LambdaQLException> {
            qb.call(DiffProcs::class.java) { it.text("10", 3) }.execute()
        }.code())
        // @end
    }

    @Test
    fun oracleOverloads() {
        assumeTrue(db == "oracle", "packages: Oracle only")
        // @start region="overload"
        assertEquals(5.0, qb.call(PkgProcs::class.java) { it.twice(2.5) }.execute()!!, 1e-9)
        // HALF has two overloads in the package: lambdaQL cannot tell which one to call
        assertEquals("LQL-4044", assertThrows<LambdaQLException> {
            qb.call(PkgProcs::class.java) { it.half(10.0) }.execute()
        }.code())
        // @end
    }

    @Test
    fun rules() {
        assumeFalse(db == "h2", "H2 has no OUT parameters (PR15)")
        // @start region="rules"
        // the stub is checked against the database parameters on the first call
        assertEquals("LQL-4036", assertThrows<LambdaQLException> {
            qb.call(OrderProcs::class.java) { it.calcWrong(1) }.execute()
        }.code())
        // exec cannot drop OUT values
        assertEquals("LQL-4037", assertThrows<LambdaQLException> {
            qb.exec(OrderProcs::class.java) { it.calc(1, 1.0) }.execute()
        }.code())
        // @end
    }

    @Test
    fun notAProcedure() {
        // checked while lambdaQL reads the lambda, before the database is asked (H2 too)
        // @start region="not-annotated"
        // only @SqlProcedure functions of the stub can be called
        assertEquals("LQL-3060", assertThrows<LambdaQLException> {
            qb.call(OrderProcs::class.java) { it.toString() }.execute()
        }.code())
        // @end
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
