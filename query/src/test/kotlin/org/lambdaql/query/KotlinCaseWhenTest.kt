package org.lambdaql.query

import org.lambdaql.query.By.asc

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.lambdaql.LambdaQLException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Values chosen by conditions on the entity in Kotlin lambdas become `case when ... end` (ROADMAP D62):
 * `if (c) x else y`, `if ... else if ... else`, `when { ... }` without a subject; in select, in a compared value,
 * as a sort key and as an assigned value. Each test checks the HQL and compares the rows with the same lambda in Kotlin.
 */
@SpringBootTest(classes = [KotlinCaseWhenTest.Context::class])
@ActiveProfiles("test")
class KotlinCaseWhenTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer", "Archive", "Summary")) em.createQuery("delete from $e").executeUpdate()
        for (i in 1..6) em.persist(Order().apply { status = (i % 3).toShort(); price = 40.0 * i; isActive = i % 2 == 0 })
        em.transaction.commit()
        em.close()
    }

    private fun all(): List<Order> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o", Order::class.java).resultList
        } finally {
            em.close()
        }
    }

    private fun kotlinIds(p: (Order) -> Boolean) = all().filter(p).map { it.id }.sortedBy { it }

    private fun ids(list: List<Order>) = list.map { it.id }.sortedBy { it }

    @Test
    fun ifInACondition() {
        // @start region="case-where"
        val q = qb.from(Order::class.java).where { it.status.toInt() == if (it.isActive) 1 else 2 }
        assertEquals("select e0 from Order e0 where e0.status = case when e0.active = true then 1 else 2 end", q.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.status.toInt() == if (it.isActive) 1 else 2 }, ids(q.toList()))
    }

    @Test
    fun entityValueInABranch() {
        val q = qb.from(Order::class.java).where { (if (it.isActive) it.price else 0.0) > 100.0 }
        assertEquals("select e0 from Order e0 where case when e0.active = true then e0.price else 0.0 end > 100.0", q.toHql().hql())
        assertEquals(kotlinIds { (if (it.isActive) it.price else 0.0) > 100.0 }, ids(q.toList()))
    }

    @Test
    fun whenInSelect() {
        // @start region="case-when-select"
        val q = qb.from(Order::class.java).select {
            when {
                it.price > 200.0 -> "big"
                it.price > 100.0 -> "medium"
                else -> "small"
            }
        }
        assertEquals("select case when e0.price > 200.0 then 'big' when e0.price > 100.0 then 'medium' else 'small' end from Order e0",
            q.toHql().hql())
        // @end
        assertEquals(all().map { when { it.price > 200.0 -> "big"; it.price > 100.0 -> "medium"; else -> "small" } }.sorted(),
            q.toList().sorted())
    }

    @Test
    fun asAGroupKey() {
        // @start region="case-group"
        // orders per price band: the when is the group key and the selected value
        val q = qb.from(Order::class.java)
            .groupBy { if (it.price > 100.0) "big" else "small" }
            .select { if (it.price > 100.0) "big" else "small" }
        assertEquals("select case when e0.price > 100.0 then 'big' else 'small' end from Order e0"
            + " group by case when e0.price > 100.0 then 'big' else 'small' end", q.toHql().hql())
        // @end
        assertEquals(all().map { if (it.price > 100.0) "big" else "small" }.distinct().sorted(), q.toList().sorted())
    }

    @Test
    fun elseIfInSelect() {
        val q = qb.from(Order::class.java).select { if (it.status.toInt() == 0) "none" else if (it.isActive) "active" else "idle" }
        assertEquals("select case when e0.status = 0 then 'none' when e0.active = true then 'active' else 'idle' end from Order e0",
            q.toHql().hql())
        assertEquals(all().map { if (it.status.toInt() == 0) "none" else if (it.isActive) "active" else "idle" }.sorted(),
            q.toList().sorted())
    }

    @Test
    fun toggleAndNumberElvisInSet() {
        // @start region="set-toggle"
        // a toggle and a number elvis are values too
        val toggle = qb.update(Order::class.java).set { it.isActive = !it.isActive }.all()
        assertEquals("update Order e0 set e0.active = case when e0.active = false then true else false end", toggle.toHql().hql())
        val zero = qb.update(Order::class.java).set { it.cnt = it.cnt ?: 0 }.all()
        assertEquals("update Order e0 set e0.cnt = coalesce(e0.cnt, 0)", zero.toHql().hql())
        // an if that chooses whether to assign is rejected (LQL-2009): put that condition in where
        val e = assertThrows(LambdaQLException::class.java) {
            qb.update(Order::class.java).set { if (it.isActive) it.price = 1.0 }
        }
        assertEquals("LQL-2009", e.code())
        // @end
        val before = all().associate { it.id to it.isActive }
        toggle.execute()
        val after = all().associate { it.id to it.isActive }
        assertEquals(before.mapValues { !it.value }, after)
        zero.execute()
        assertEquals(List(6) { 0 }, all().map { it.cnt })
    }

    @Test
    fun asASortKey() {
        // @start region="case-sort"
        // active orders first, then by price
        val q = qb.from(Order::class.java).orderBy { o -> asc(if (o.isActive) 0 else 1).asc(o.price) }.limit(10)
        assertEquals("select e0 from Order e0 order by case when e0.active = true then 0 else 1 end asc, e0.price asc, e0.id asc",
            q.toHql().hql())
        // @end
        assertEquals(all().sortedWith(compareBy<Order>({ if (it.isActive) 0 else 1 }, { it.price })).map { it.id },
            q.toList().map { it.id })
    }

    @Test
    fun asAnAssignedValue() {
        // @start region="case-set"
        // 10 % off the orders above 100
        val u = qb.update(Order::class.java).set { it.price = if (it.price > 100.0) it.price * 0.9 else it.price }.all()
        assertEquals("update Order e0 set e0.price = case when e0.price > 100.0 then (e0.price * 0.9) else e0.price end", u.toHql().hql())
        // @end
        val expected = all().map { if (it.price > 100.0) it.price * 0.9 else it.price }.sorted()
        u.execute()
        val actual = all().map { it.price }.sorted()
        for (i in expected.indices) assertEquals(expected[i], actual[i], 1e-9)
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
