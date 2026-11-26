package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.LambdaQLException
import org.lambdaql.SqlProcedure
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin bytecode patterns found while the manual examples were turned into tests (ROADMAP D56):
 * the null check Kotlin adds before unboxing (D22), an elvis with a number, Kotlin progressions in `in`,
 * an assignment to the `@Version` column (U15) and a procedure stub call that leaves out a default argument.
 * Each query test checks the HQL and compares the rows with the rows computed in Kotlin from the seeded data.
 */
@SpringBootTest(classes = [KotlinRegressionTest.Context::class])
@ActiveProfiles("test")
class KotlinRegressionTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    /** A stub with a default argument: Kotlin calls `calc$default` when the argument is left out. */
    interface DefaultProcs {
        @SqlProcedure("SQ_PROC_CALC")
        fun calc(customerId: Int, rate: Double = 1.0): Double?
    }

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Summary", "Archive", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        // status, total, cnt
        for ((status, total, cnt) in listOf(Triple(1, null, 1), Triple(2, 2.0, 2), Triple(3, 5.0, null), Triple(4, null, 1), Triple(5, 4.0, null))) {
            em.persist(Order().apply {
                this.status = status.toShort()
                this.price = 10.0 * status
                this.isActive = true
                this.total = total
                this.cnt = cnt
            })
        }
        em.transaction.commit()
        em.close()
    }

    private fun orders(): List<Order> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o", Order::class.java).resultList
        } finally {
            em.close()
        }
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun nullableColumnAndConstant() {
        // cnt is a nullable Integer; the null check Kotlin adds before the unboxing is not part of the query
        val one = qb.from(Order::class.java).where { it.cnt == 1 }
        assertEquals("select e0 from Order e0 where e0.cnt = 1", one.toHql().hql())
        val notOne = qb.from(Order::class.java).where { it.cnt != 1 }
        assertEquals("select e0 from Order e0 where e0.cnt <> 1", notOne.toHql().hql())
        val nullOrNotOne = qb.from(Order::class.java).where { it.cnt == null || it.cnt != 1 }
        assertEquals("select e0 from Order e0 where e0.cnt is null or e0.cnt <> 1", nullOrNotOne.toHql().hql())
        val all = orders()
        assertEquals(ids(all.filter { it.cnt == 1 }), ids(one.toList()))
        assertEquals(ids(all.filter { it.cnt != null && it.cnt != 1 }), ids(notOne.toList()))
        assertEquals(ids(all.filter { it.cnt == null || it.cnt != 1 }), ids(nullOrNotOne.toList()))
    }

    @Test
    fun numberElvis() {
        val where = qb.from(Order::class.java).where { (it.total ?: 0.0) > 3.0 }
        assertEquals("select e0 from Order e0 where coalesce(e0.total, 0.0) > 3.0", where.toHql().hql())
        assertEquals(ids(orders().filter { (it.total ?: 0.0) > 3.0 }), ids(where.toList()))

        val before = orders().associate { it.id to it.total }
        val add = qb.update(Order::class.java).set { it.total = (it.total ?: 0.0) + 1 }.all()
        assertEquals("update Order e0 set e0.total = (coalesce(e0.total, 0.0) + 1.0)", add.toHql().hql())
        assertEquals(before.size, add.execute())
        for (o in orders()) assertEquals((before[o.id] ?: 0.0) + 1, o.total)

        val reset = qb.update(Order::class.java).set { it.cnt = it.cnt ?: 0 }.all()
        assertEquals("update Order e0 set e0.cnt = coalesce(e0.cnt, 0)", reset.toHql().hql())
        assertEquals(before.size, reset.execute())
        assertEquals(0, orders().count { it.cnt == null })
    }

    @Test
    fun progressions() {
        val down = qb.from(Order::class.java).where { it.status.toInt() in 4 downTo 2 }
        assertEquals("select e0 from Order e0 where e0.status in (:p1)", down.toHql().hql())
        val odd = qb.from(Order::class.java).where { it.status.toInt() in 1..5 step 2 }
        assertEquals("select e0 from Order e0 where e0.status in (:p1)", odd.toHql().hql())
        val all = orders()
        assertEquals(ids(all.filter { it.status.toInt() in 4 downTo 2 }), ids(down.toList()))
        assertEquals(ids(all.filter { it.status.toInt() in 1..5 step 2 }), ids(odd.toList()))
    }

    @Test
    fun versionAssignment() {
        val error = assertThrows(LambdaQLException::class.java) {
            qb.update(VOrder::class.java).set { it.version = 3L }.all().toHql()
        }
        assertEquals("LQL-3068", error.code())
    }

    @Test
    fun defaultArgument() {
        val error = assertThrows(LambdaQLException::class.java) {
            qb.call(DefaultProcs::class.java) { it.calc(1) }.execute()
        }
        assertEquals("LQL-3062", error.code())
    }

    @Test
    fun twoStepPath() {
        val em = emf.createEntityManager()
        em.transaction.begin()
        val kim = Customer(1, "Kim", null)
        em.persist(kim)
        val withCustomer = Order().apply { status = 7; price = 1.0; isActive = true; customer = kim }
        val withoutCustomer = Order().apply { status = 7; price = 1.0; isActive = true }
        em.persist(withCustomer)
        em.persist(withoutCustomer)
        for (o in listOf(withCustomer, withoutCustomer, null)) em.persist(Item().apply { order = o; price = 1.0; qty = 1 })
        em.transaction.commit()
        em.close()
        // the comparison goes through !! at the last step: no item without an order or a customer can match,
        // so both steps are inner joins
        val kimItems = qb.from(Item::class.java).where { it.order?.customer!!.name == "Kim" }
        assertEquals("select e0 from Item e0 join e0.order j1 join j1.customer j2 where j2.name = 'Kim'", kimItems.toHql().hql())
        // ?. at both steps and "is null": an item without an order, or with an order without a customer, matches
        val noName = qb.from(Item::class.java).where { it.order?.customer?.name == null }
        assertEquals("select e0 from Item e0 left join e0.order j1 left join j1.customer j2 where j2.name is null",
            noName.toHql().hql())
        val read = emf.createEntityManager()
        val items = read.createQuery("select i from Item i left join fetch i.order o left join fetch o.customer", Item::class.java).resultList
        read.close()
        assertEquals(items.filter { it.order?.customer?.name == "Kim" }.map { it.id }.sorted(), kimItems.toList().map { it.id }.sorted())
        assertEquals(items.filter { it.order?.customer?.name == null }.map { it.id }.sorted(), noName.toList().map { it.id }.sorted())
    }

    @Test
    fun hostValueIf() {
        for (flag in listOf(true, false)) {
            // @start region="host-if"
            // flag and both values are captured values or constants: computed in Kotlin, bound as :p1
            val q = qb.from(Order::class.java).where { it.status.toInt() == if (flag) 1 else 2 }
            assertEquals("select e0 from Order e0 where e0.status = :p1", q.toHql().hql())
            // @end
            assertEquals(ids(orders().filter { it.status.toInt() == if (flag) 1 else 2 }), ids(q.toList()))
        }
        // a condition that reads the entity is not computed in Kotlin: it becomes case when
        val q = qb.from(Order::class.java).where { it.status.toInt() == if (it.isActive) 1 else 2 }
        assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql())
        assertEquals(ids(orders().filter { it.status.toInt() == if (it.isActive) 1 else 2 }), ids(q.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
