package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin `in` with a collection or array made in the lambda (`listOf`, `setOf`, `arrayOf`) and with a captured array
 * (ROADMAP D76). Each test checks the HQL and compares the rows with Kotlin.
 */
@SpringBootTest(classes = [KotlinInListTest.Context::class])
@ActiveProfiles("test")
class KotlinInListTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) em.createQuery("delete from $e").executeUpdate()
        for ((i, text) in listOf("a", "b", "c", null).withIndex()) {
            em.persist(Order().apply {
                description = text
                cnt = i + 1
                status = (i + 1).toShort()
            })
        }
        em.transaction.commit()
        em.close()
    }

    private fun kotlinIds(test: (Order) -> Boolean): List<Long?> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o", Order::class.java).resultList.filter(test).map { it.id }.sorted()
        } finally {
            em.close()
        }
    }

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun madeInTheLambda() {
        // @start region="in-made-in-lambda"
        // a collection made in the lambda is computed in Kotlin and bound like a captured collection
        val list = qb.from(Order::class.java).where { it.cnt in listOf(1, 2) }
        assertEquals("select e0 from Order e0 where e0.cnt in (:p1)", list.toHql().hql())

        val notIn = qb.from(Order::class.java).where { it.description !in setOf("a", "b") }
        assertEquals("select e0 from Order e0 where e0.description not in (:p1)", notIn.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.cnt in listOf(1, 2) }, ids(list.toList()))
        // SQL meaning: the row with a NULL description is not returned by NOT IN
        assertEquals(kotlinIds { it.description != null && it.description !in setOf("a", "b") }, ids(notIn.toList()))
    }

    @Test
    fun arrays() {
        val codes = arrayOf<Short>(1, 3)
        val counts = intArrayOf(2, 4)
        // @start region="in-array"
        // an array works like a collection: captured, or made in the lambda
        val captured = qb.from(Order::class.java).where { it.status in codes }
        assertEquals("select e0 from Order e0 where e0.status in (:p1)", captured.toHql().hql())

        val made = qb.from(Order::class.java).where { it.description in arrayOf("a", "c") }
        assertEquals("select e0 from Order e0 where e0.description in (:p1)", made.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.status in codes }, ids(captured.toList()))
        assertEquals(kotlinIds { it.description in arrayOf("a", "c") }, ids(made.toList()))
        val primitive = qb.from(Order::class.java).where { it.cnt!! in counts }
        assertEquals(kotlinIds { it.cnt!! in counts }, ids(primitive.toList()))
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
