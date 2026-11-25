package org.lambdaql.query

import org.lambdaql.query.By.asc
import org.lambdaql.query.By.desc
import org.lambdaql.query.By.sort

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.LambdaQLException
import org.lambdaql.SortNotAllowedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * Kotlin forms of sorting and paging for the manual (Kotlin track, chapter 4): sort lambdas, NULL positions,
 * paging, `SortSpec` and the outside sort policy (ROADMAP D16, D35, D43).
 * Each test also computes the expected rows in Kotlin from the seeded data.
 * The region markers are used by the manual.
 */
@SpringBootTest(classes = [ManualKotlinSortTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinSortTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder
    private lateinit var orders: List<Order>

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("OrderLine", "Summary", "Archive", "Item", "Order", "Customer")) em.createQuery("delete from $e").executeUpdate()
        val kim = Customer(1, "Kim", null).apply { grade = 5 }
        val lee = Customer(2, "Lee", null)
        em.persist(kim)
        em.persist(lee)
        val day = LocalDateTime.of(2026, 1, 1, 0, 0)
        // (status, price, active, customer, description, updateAt)
        val rows = listOf(
            listOf(1, 50.0, true, kim, "b", day),
            listOf(1, 150.0, true, lee, null, day.plusDays(1)),
            listOf(2, 250.0, false, null, "a", null),
            listOf(1, 120.0, true, null, "c", day.plusDays(2)),
            listOf(3, 80.0, true, kim, null, null),
            listOf(1, 30.0, true, lee, "a", day.plusDays(3)),
        )
        val saved = rows.map { r ->
            Order().apply {
                status = (r[0] as Int).toShort()
                price = r[1] as Double
                isActive = r[2] as Boolean
                customer = r[3] as Customer?
                description = r[4] as String?
                updateAt = r[5] as LocalDateTime?
            }.also { em.persist(it) }
        }
        for (o in saved.take(3)) em.persist(OrderLine(o, "memo " + o.price))
        em.transaction.commit()
        orders = em.createQuery("select o from Order o left join fetch o.customer", Order::class.java).resultList
        em.close()
    }

    @AfterEach
    fun cleanUp() {
        SortPolicy.unregister(emf)            // other tests share this persistence unit
        val em = emf.createEntityManager()
        em.transaction.begin()
        em.createQuery("delete from OrderLine").executeUpdate()   // other test classes delete orders without it
        em.transaction.commit()
        em.close()
    }

    private fun ids(list: List<Order>) = list.map { it.id }

    private val byId = compareBy<Order> { it.id }

    @Test
    fun shortForm() {
        // @start region="short-form"
        // one key: the key alone, ascending unless a Direction is given
        val byPrice = qb.from(Order::class.java).orderBy { it.price }
        assertEquals("select e0 from Order e0 order by e0.price asc", byPrice.toHql().hql())
        val newest = qb.from(Order::class.java).orderBy({ it.updateAt }, Direction.DESC)
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
        // @end
        assertEquals(orders.map { it.price }.sorted(), byPrice.toList().map { it.price })
        assertEquals(orders.map { it.updateAt }.sortedWith(nullsFirst(naturalOrder<LocalDateTime>())).reversed(),
            newest.toList().map { it.updateAt })
    }

    @Test
    fun nullsAndDirection() {
        val newestFirst = true      // for example from a request parameter
        // @start region="nulls-and-direction"
        // the second argument: true = NULL first, false = NULL last
        val nullsLast = qb.from(Order::class.java).orderBy { o -> asc(o.description, Nulls.LAST).asc(o.id) }
        assertEquals("select e0 from Order e0 order by e0.description asc nulls last, e0.id asc", nullsLast.toHql().hql())
        // a direction from a variable: choose it outside the lambda
        val dir = if (newestFirst) Direction.DESC else Direction.ASC
        val byDate = qb.from(Order::class.java).orderBy { o -> sort(o.updateAt, dir).asc(o.id) }
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", byDate.toHql().hql())
        // the short form with a NULL position
        val byName = qb.from(Order::class.java).orderBy({ it.customer?.name }, Direction.DESC_NULLS_LAST)
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last", byName.toHql().hql())
        // @end
        assertEquals(ids(orders.sortedWith(compareBy<Order, String?>(nullsLast()) { it.description }.then(byId))), ids(nullsLast.toList()))
        assertEquals(ids(orders.sortedWith(compareByDescending<Order, LocalDateTime?>(nullsFirst()) { it.updateAt }.then(byId))),
            ids(byDate.toList()))
        assertEquals(orders.map { it.customer?.name }.sortedWith(nullsFirst(naturalOrder<String>())).reversed(),
            byName.select { it.customer?.name }.toList())
    }

    /** The same sort lambda with a direction given at run time. */
    private fun byDate(dir: Direction) = qb.from(Order::class.java).orderBy { o -> sort(o.updateAt, dir).asc(o.id) }

    @Test
    fun directionReadOnEveryRun() {
        // one lambda, analysed once; the captured direction is read on every run (S10)
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last, e0.id asc", byDate(Direction.DESC).toHql().hql())
        assertEquals("select e0 from Order e0 order by e0.updateAt asc nulls last, e0.id asc",
            byDate(Direction.ASC_NULLS_LAST).toHql().hql())
        assertEquals(ids(orders.sortedWith(compareBy<Order, LocalDateTime?>(nullsLast()) { it.updateAt }.then(byId))),
            ids(byDate(Direction.ASC_NULLS_LAST).toList()))
        // the helpers of Direction
        assertEquals(Direction.DESC_NULLS_LAST, Direction.parse("desc-nulls-last"))
        assertEquals(Direction.ASC_NULLS_FIRST, Direction.parse("Asc Nulls_First"))
        assertThrows(IllegalArgumentException::class.java) { Direction.parse("upward") }
        assertEquals(Direction.DESC, Direction.ASC.reversed())
        assertEquals(Direction.DESC_NULLS_FIRST, Direction.ASC_NULLS_LAST.reversed())
    }

    @Test
    fun sortPath() {
        // @start region="sort-path"
        // a path used only for sorting is a left join: the orders without a customer stay
        val q = qb.from(Order::class.java).orderBy { o -> asc(o.customer?.name).asc(o.id) }
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc", q.toHql().hql())
        // @end
        assertEquals(ids(orders.sortedWith(compareBy<Order, String?>(nullsFirst()) { it.customer?.name }.then(byId))), ids(q.toList()))
    }

    @Test
    fun sortKeys() {
        // @start region="sort-keys"
        // arithmetic, a widening conversion and the Elvis operator of a value
        assertEquals("select e0 from Order e0 order by (e0.price * 2.0) asc",
            qb.from(Order::class.java).orderBy { o -> asc(o.price * 2) }.toHql().hql())
        assertEquals("select e0 from Order e0 order by e0.status asc",
            qb.from(Order::class.java).orderBy { o -> asc(o.status.toLong()) }.toHql().hql())
        val elvis = qb.from(Order::class.java).orderBy { o -> asc(o.description ?: "").asc(o.id) }
        assertEquals("select e0 from Order e0 order by coalesce(e0.description, '') asc nulls first, e0.id asc", elvis.toHql().hql())
        // @end
        assertEquals(ids(orders.sortedWith(compareBy<Order> { it.description ?: "" }.then(byId))), ids(elvis.toList()))
    }

    private fun <T> inTransaction(work: (QueryBuilder) -> T): T {
        val em = emf.createEntityManager()
        em.transaction.begin()
        try {
            return work(QueryBuilder(em))   // OrderLine has a @Lob column: PostgreSQL reads it only in a transaction
        } finally {
            em.transaction.rollback()
            em.close()
        }
    }

    @Test
    fun requiredAssociation() {
        // @start region="required-association"
        // order is optional = false: a sort or select path through it cannot lose rows, so it is an inner join
        val sorted = qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }
        assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc", sorted.toHql().hql())
        val prices = qb.from(OrderLine::class.java).select { it.order.price }
        assertEquals("select j1.price from OrderLine e0 join e0.order j1", prices.toHql().hql())
        // @end
        val expected = orders.sortedBy { it.id }.take(3).map { it.price }.sorted()
        assertEquals(expected, sorted.select { it.order.price }.toList())
        assertEquals(expected, prices.toList().sorted())
        assertEquals(3, inTransaction { it.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }.toList().size })
    }

    @Test
    fun lobAndEnum() {
        val log = LoggerFactory.getLogger(LobCheck::class.java) as Logger
        val old = log.level
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        log.addAppender(appender)
        log.level = Level.DEBUG
        // the messages are logged once per column and use in a JVM: forget what other tests logged before
        @Suppress("UNCHECKED_CAST")
        val logged = LobCheck::class.java.getDeclaredField("LOGGED").apply { isAccessible = true }.get(null) as MutableSet<String>
        logged.removeIf { it.startsWith("memo|") || it == "kind|enum" }
        try {
            // @start region="lob"
            // memo is @Lob: H2 and MariaDB sort it, group it and make it distinct, Oracle rejects it (ORA-22848, ORA-22849).
            // lambdaQL keeps the query and logs LQL-5010 at DEBUG
            qb.from(OrderLine::class.java).orderBy { l -> asc(l.memo) }.toHql()
            qb.from(OrderLine::class.java).groupBy { it.memo }.select { it.memo }.toHql()
            qb.from(OrderLine::class.java).select { it.memo }.distinct().toHql()
            // @end
            assertEquals(3, appender.list.count { it.formattedMessage.startsWith("[LQL-5010]") }, appender.list.toString())
            // @start region="enum-string-sort"
            // kind is @Enumerated(EnumType.STRING): the database sorts by name, Kotlin by declaration order (LQL-5011, DEBUG)
            val q = qb.from(Order::class.java).orderBy { o -> asc(o.kind) }
            assertEquals("select e0 from Order e0 order by e0.kind asc nulls first", q.toHql().hql())
            // @end
            assertEquals(1, appender.list.count { it.formattedMessage.startsWith("[LQL-5011]") }, appender.list.toString())
        } finally {
            logged.removeIf { it.startsWith("memo|") || it == "kind|enum" }   // MappingRulesTest counts them too
            log.level = old
            log.detachAppender(appender)
        }
    }

    @Test
    fun lambdaRules() {
        val flag = true
        // @start region="lambda-rules"
        // allowed: a straight chain of by calls
        qb.from(Order::class.java).orderBy { o -> desc(o.price).asc(o.id) }
        // rejected when orderBy is called: sort functions chosen by a condition (a condition inside one key is allowed)
        val branch = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).orderBy { o -> if (flag) desc(o.price) else asc(o.id) }
        }
        assertEquals("LQL-2023", branch.code())
        // rejected: a key that does not use the entity
        val constant = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).orderBy { _ -> asc(1) }
        }
        assertEquals("LQL-3031", constant.code())
        // @end
    }

    @Test
    fun paging() {
        // @start region="page"
        val page = qb.from(Order::class.java)
            .where { it.status.toInt() == 1 }
            .orderBy { o -> desc(o.price) }
            .limit(2)      // page size
            .offset(2)     // skip 2 rows (page 2)
        // limit and offset go to Hibernate; the id is added as the last key
        assertEquals("select e0 from Order e0 where e0.status = 1 order by e0.price desc, e0.id asc", page.toHql().hql())
        // @end
        val expected = orders.filter { it.status.toInt() == 1 }.sortedWith(compareByDescending<Order> { it.price }.then(byId))
        assertEquals(ids(expected.drop(2).take(2)), ids(page.toList()))
        assertThrows(IllegalArgumentException::class.java) { qb.from(Order::class.java).limit(-1) }
        assertThrows(IllegalArgumentException::class.java) { qb.from(Order::class.java).orderBy { it.price }.offset(-1) }
        // the id is not added twice
        assertEquals("select e0 from Order e0 order by e0.id desc",
            qb.from(Order::class.java).orderBy { o -> desc(o.id) }.limit(2).toHql().hql())
    }

    @Test
    fun pageOnly() {
        // @start region="page-only"
        // paging without orderBy: the rows are sorted by id, so the pages are stable
        val q = qb.from(Order::class.java).where { it.isActive }.limit(2)
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.id asc", q.toHql().hql())
        // @end
        assertEquals(orders.filter { it.isActive }.map { it.id }.sortedBy { it }.take(2), ids(q.toList()))
    }

    @Test
    fun firstAndCount() {
        // @start region="first-and-count"
        // keep the where step in a value: count() gives the number of all rows, orderBy ... limit gives one page
        val active = qb.from(Order::class.java).where { it.isActive }
        val total = active.count()
        val page = active.orderBy { o -> desc(o.price) }.limit(2).toList()
        // first() reads one row; offset(1).first() is the second row
        val second = active.orderBy { o -> desc(o.price) }.offset(1).first()
        // @end
        val expected = orders.filter { it.isActive }.sortedWith(compareByDescending<Order> { it.price }.then(byId))
        assertEquals(expected.size.toLong(), total)
        assertEquals(ids(expected.take(2)), ids(page))
        assertEquals(expected[1].id, second.orElseThrow().id)
    }

    @Test
    fun uniqueKey() {
        // @start region="unique-key"
        // code is @Column(unique = true, nullable = false): it orders every row, so paging adds no id tie-breaker
        assertEquals("select e0 from OrderLine e0 order by e0.code asc",
            qb.from(OrderLine::class.java).orderBy { l -> asc(l.code) }.limit(2).toHql().hql())
        // a key that is not unique still gets the id tie-breaker
        assertEquals("select e0 from OrderLine e0 join e0.order j1 order by j1.price asc, e0.id asc",
            qb.from(OrderLine::class.java).orderBy { l -> asc(l.order.price) }.limit(2).toHql().hql())
        // @end
        val codes = inTransaction { it.from(OrderLine::class.java).orderBy { l -> asc(l.code) }.limit(2).toList().map { l -> l.code } }
        assertEquals(orders.sortedBy { it.id }.take(3).map { "memo-" + it.price }.sorted().take(2), codes)
    }

    @Test
    fun sortSpec() {
        // @start region="sort-spec"
        // keep it in a value (for example at the top level or in a companion object) and use it in many queries
        val byDescription = SortSpec.of(Order::class.java) { o -> desc(o.description).asc(o.id) }
        val q = qb.from(Order::class.java).where { it.isActive }.orderBy(byDescription)
        assertEquals("select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc",
            q.toHql().hql())
        // @end
        assertEquals(ids(orders.filter { it.isActive }
            .sortedWith(compareByDescending<Order, String?>(nullsFirst()) { it.description }.then(byId))), ids(q.toList()))
        assertEquals(ids(q.toList()).take(2), ids(qb.from(Order::class.java).where { it.isActive }.orderBy(byDescription).limit(2).toList()))
    }

    @Test
    fun sortSpecThen() {
        // @start region="sort-spec-then"
        val byStatus = SortSpec.of(Order::class.java) { o -> asc(o.status) }
        val byPrice = SortSpec.of(Order::class.java) { o -> desc(o.price) }
        val q = qb.from(Order::class.java).orderBy(byStatus.then(byPrice))
        assertEquals("select e0 from Order e0 order by e0.status asc, e0.price desc", q.toHql().hql())
        // then() also takes a sort lambda
        assertEquals("select e0 from Order e0 order by e0.status asc, e0.id desc",
            qb.from(Order::class.java).orderBy(byStatus.then { o -> desc(o.id) }).toHql().hql())
        // @end
        assertEquals(ids(orders.sortedWith(compareBy<Order> { it.status }.thenByDescending { it.price })), ids(q.toList()))
    }

    @Test
    fun sortSpecReversed() {
        // @start region="sort-spec-reversed"
        val spec = SortSpec.of(Order::class.java) { o -> asc(o.description).asc(o.id) }
        // every key is reversed, the NULL position too: asc nulls first -> desc nulls last
        val q = qb.from(Order::class.java).orderBy(spec.reversed())
        assertEquals("select e0 from Order e0 order by e0.description desc nulls last, e0.id desc", q.toHql().hql())
        // @end
        assertEquals(ids(qb.from(Order::class.java).orderBy(spec).toList()).reversed(), ids(q.toList()))
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first, e0.id asc",
            qb.from(Order::class.java).orderBy(spec.reversed().reversed()).toHql().hql())
    }

    @Test
    fun sortSpecPath() {
        // @start region="sort-spec-path"
        // for sorts chosen outside the code (the Spring module uses it); only real properties are accepted
        val byName = SortSpec.ofPath(Order::class.java, "customer.name", Direction.DESC)
            .then(SortSpec.ofPath(Order::class.java, "id", Direction.ASC))
        val q = qb.from(Order::class.java).orderBy(byName)
        assertEquals("select e0 from Order e0 left join e0.customer j1 order by j1.name desc nulls last, e0.id asc", q.toHql().hql())
        assertThrows(IllegalArgumentException::class.java) {
            qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "price; drop table x", Direction.ASC))
        }
        // @end
        assertEquals(ids(orders.sortedWith(compareByDescending<Order, String?>(nullsFirst()) { it.customer?.name }.then(byId))),
            ids(q.toList()))
    }

    @Test
    fun policy() {
        // @start region="policy"
        val orderRules = SortRules.of(Order::class.java)
            .allow { it.price }                      // the name "price"
            .allow { it.customer?.name }             // an association path: allowed only when listed
            .alias("newest") { it.updateAt }         // the outside name "newest" sorts by updateAt
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, orderRules))

        val newest = qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC))
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last", newest.toHql().hql())
        // not in the rules, and the path behind an alias: SortNotAllowedException when orderBy is called
        for (name in listOf("description", "updateAt")) {
            val e = assertThrows(SortNotAllowedException::class.java) {
                qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, name, Direction.ASC))
            }
            assertEquals("LQL-4040", e.code())
        }
        // a sort lambda is your code: never checked
        assertEquals("select e0 from Order e0 order by e0.description asc nulls first",
            qb.from(Order::class.java).orderBy { o -> asc(o.description) }.toHql().hql())
        // @end
        assertEquals(orders.map { it.updateAt }.sortedWith(nullsFirst(naturalOrder<LocalDateTime>())).reversed(),
            newest.toList().map { it.updateAt })
        assertEquals(SortMode.STRICT, SortPolicy.effective(emf).mode())
    }

    @Test
    fun annotation() {
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, SortRules.of(Order::class.java).allow { it.price }))
        // @start region="annotation"
        // Customer has no rules in the policy, so its @Sortable fields are the rules:
        //   @Sortable private String name;   @Sortable("level") private Integer grade;
        val byLevel = qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "level", Direction.DESC))
        assertEquals("select e0 from Customer e0 order by e0.grade desc nulls last", byLevel.toHql().hql())
        assertThrows(SortNotAllowedException::class.java) {
            qb.from(Customer::class.java).orderBy(SortSpec.ofPath(Customer::class.java, "id", Direction.ASC))
        }
        // @end
        assertEquals(listOf("Kim", "Lee"), byLevel.toList().map { it.name })
    }

    @Test
    fun stringPaths() {
        // @start region="string-paths"
        // the string forms used by configuration files (lambdaql.sort.allow / lambdaql.sort.alias)
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT,
            SortRules.of(Order::class.java).allowPath("price", "customer.name").aliasPath("newest", "updateAt")))
        assertEquals("select e0 from Order e0 order by e0.updateAt desc nulls last",
            qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "newest", Direction.DESC)).toHql().hql())
        // in STRICT mode a name that is not in the rules is rejected, even when it is not a property
        assertThrows(SortNotAllowedException::class.java) {
            qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "noSuchName", Direction.ASC))
        }
        // @end
    }

    @Test
    fun ruleThatIsNotAColumn() {
        // a rule must be a column of the entity: other keys are rejected when the rules are first used (LQL-4041)
        SortPolicy.register(emf, SortPolicy.of(SortMode.STRICT, SortRules.of(Order::class.java).allow { it.price * 2 }))
        val e = assertThrows(LambdaQLException::class.java) {
            qb.from(Order::class.java).orderBy(SortSpec.ofPath(Order::class.java, "price", Direction.ASC)).toHql()
        }
        assertEquals("LQL-4041", e.code())
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
