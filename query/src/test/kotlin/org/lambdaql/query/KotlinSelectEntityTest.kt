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
 * Entities as select values in Kotlin lambdas (ROADMAP D70): `select { it }`, a joined entity `select { o, c -> c }`,
 * and several entities in a data class or a `Pair`. Each test checks the HQL and compares the rows with Kotlin.
 */
@SpringBootTest(classes = [KotlinSelectEntityTest.Context::class])
@ActiveProfiles("test")
class KotlinSelectEntityTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    /** An order and its customer, both entities. */
    data class OrderWithCustomer(val order: Order, val customer: Customer?)

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer().apply { id = 1; name = "Kim" }
        val lee = Customer().apply { id = 2; name = "Lee" }
        em.persist(kim)
        em.persist(lee)
        for ((i, owner) in listOf(kim, kim, lee, null).withIndex()) {
            em.persist(Order().apply { price = 100.0 * (i + 1); customer = owner })
        }
        em.transaction.commit()
        em.close()
    }

    private fun all(): List<Order> {
        val em = emf.createEntityManager()
        try {
            return em.createQuery("select o from Order o left join fetch o.customer order by o.id", Order::class.java).resultList
        } finally {
            em.close()
        }
    }

    @Test
    fun joinedEntity() {
        // @start region="select-joined"
        // the customers that have an order, through an explicit join; Hibernate returns each entity once
        val q = qb.from(Order::class.java)
            .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .select { _, c -> c }
        // @end
        assertEquals("select e1 from Order e0 join Customer e1 on e0.customer is not null and e0.customer.id = e1.id", q.toHql().hql())
        assertEquals(all().mapNotNull { it.customer?.name }.distinct().sorted(), q.toList().map { it.name }.sorted())
    }

    @Test
    fun severalEntities() {
        // @start region="select-entities"
        // an order and its customer together; with leftJoin an order without a customer has null
        val q = qb.from(Order::class.java)
            .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .select { o, c -> OrderWithCustomer(o, c) }
        // @end
        assertEquals("select e0, e1 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id", q.toHql().hql())
        val rows = q.toList().sortedBy { it.order.id }
        assertEquals(all().map { it.id to it.customer?.id }, rows.map { it.order.id to it.customer?.id })
        // a Pair of entities works too
        val pairs = qb.from(Order::class.java)
            .leftJoin(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .select { o, c -> o to c }
        assertEquals("select e0, e1 from Order e0 left join Customer e1 on e0.customer is not null and e0.customer.id = e1.id", pairs.toHql().hql())
        assertEquals(all().map { it.id to it.customer?.id }, pairs.toList().sortedBy { it.first.id }.map { it.first.id to it.second?.id })
    }

    @Test
    fun tenEntities() {
        // @start region="ten"
        // up to 10 entities: each lambda gets all of them in join order
        val q = qb.from(Order::class.java)
            .join(Customer::class.java) { o, c -> o.customer?.id == c.id }
            .join(Order::class.java) { o, _, a -> a.id == o.id }
            .join(Order::class.java) { _, _, a, b -> b.id == a.id }
            .join(Order::class.java) { _, _, _, b, d -> d.id == b.id }
            .join(Order::class.java) { _, _, _, _, d, e -> e.id == d.id }
            .join(Order::class.java) { _, _, _, _, _, e, f -> f.id == e.id }
            .join(Order::class.java) { _, _, _, _, _, _, f, g -> g.id == f.id }
            .join(Order::class.java) { _, _, _, _, _, _, _, g, h -> h.id == g.id }
            .leftJoin(Order::class.java) { _, _, _, _, _, _, _, _, h, k -> k.id == h.id }
            .where { _, c, _, _, _, _, _, _, _, k -> c.name == "Kim" && k.price > 0.0 }
            .select { _, c, _, _, _, _, _, _, _, k -> c.name to k.id }
        // @end
        assertEquals(all().filter { it.customer?.name == "Kim" }.map { "Kim" to it.id }, q.toList().sortedBy { it.second })
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
