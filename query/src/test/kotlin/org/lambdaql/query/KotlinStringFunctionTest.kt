package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lambdaql.query.By.asc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Kotlin string functions on an entity value become standard HQL functions (ROADMAP D74): `lowercase()`,
 * `uppercase()`, `trim()`, `length`, `substring`, `indexOf`, `replace`, `+` and string templates; `isEmpty()`,
 * `isNullOrEmpty()`, `isBlank()` and `equals(s, ignoreCase = true)` become conditions.
 * Each test checks the HQL and compares the rows with the same test run in Kotlin.
 */
@SpringBootTest(classes = [KotlinStringFunctionTest.Context::class])
@ActiveProfiles("test")
class KotlinStringFunctionTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    private lateinit var qb: QueryBuilder

    /** Oracle stores '' as NULL, and trim('   ') gives NULL there: no text is empty or blank. */
    private val oracle = System.getProperty("lql.db", "h2") == "oracle"

    @BeforeEach
    fun seed() {
        qb = QueryBuilder(emf)
        val em = emf.createEntityManager()
        em.transaction.begin()
        for (e in listOf("Item", "Order", "Customer", "Archive", "VOrder", "IdOrder")) em.createQuery("delete from $e").executeUpdate()
        for ((i, text) in listOf(" Kim ", "kimi", "PARK", "", "   ", null, "lee-01").withIndex()) {
            em.persist(Order().apply {
                description = text
                status = i.toShort()
                price = 10.0 * (i + 1)
            })
        }
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

    /** The ids of the orders that pass the test in Kotlin; SQL gives NULL for a null text, so a null text never passes. */
    private fun kotlinIds(test: (String) -> Boolean) = all().filter { it.description != null && test(it.description!!) }.map { it.id }.sorted()

    private fun ids(list: List<Order>) = list.map { it.id }.sorted()

    @Test
    fun lowercaseAndUppercase() {
        // @start region="lowercase"
        val q = qb.from(Order::class.java).where { it.description?.lowercase() == "park" }
        assertEquals("select e0 from Order e0 where lower(e0.description) = 'park'", q.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.lowercase() == "park" }, ids(q.toList()))
        val upper = qb.from(Order::class.java).where { it.description!!.uppercase() == "KIMI" }
        assertEquals("select e0 from Order e0 where upper(e0.description) = 'KIMI'", upper.toHql().hql())
        assertEquals(kotlinIds { it.uppercase() == "KIMI" }, ids(upper.toList()))
    }

    @Test
    fun trimAndLength() {
        // @start region="trim-length"
        val trimmed = qb.from(Order::class.java).where { it.description?.trim() == "Kim" }
        assertEquals("select e0 from Order e0 where trim(e0.description) = 'Kim'", trimmed.toHql().hql())

        val longer = qb.from(Order::class.java).where { it.description!!.length > 3 }
        assertEquals("select e0 from Order e0 where length(e0.description) > 3", longer.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.trim() == "Kim" }, ids(trimmed.toList()))
        assertEquals(kotlinIds { it.length > 3 }, ids(longer.toList()))
    }

    @Test
    fun emptyAndBlank() {
        // @start region="empty-blank"
        val empty = qb.from(Order::class.java).where { it.description!!.isEmpty() }
        assertEquals("select e0 from Order e0 where length(e0.description) = 0", empty.toHql().hql())

        // isNullOrEmpty() is inline: a null check and a length check
        val nullOrEmpty = qb.from(Order::class.java).where { it.description.isNullOrEmpty() }
        assertEquals("select e0 from Order e0 where e0.description is null or length(e0.description) = 0", nullOrEmpty.toHql().hql())

        val blank = qb.from(Order::class.java).where { it.description!!.isBlank() }
        assertEquals("select e0 from Order e0 where length(trim(e0.description)) = 0", blank.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.isEmpty() }, ids(empty.toList()))
        assertEquals(all().filter { it.description.isNullOrEmpty() }.map { it.id }.sorted(), ids(nullOrEmpty.toList()))
        assertEquals(if (oracle) emptyList() else kotlinIds { it.isBlank() }, ids(blank.toList()))
        // isNullOrBlank() is inline: a null check and isBlank()
        val nullOrBlank = qb.from(Order::class.java).where { it.description.isNullOrBlank() }
        assertEquals("select e0 from Order e0 where e0.description is null or length(trim(e0.description)) = 0", nullOrBlank.toHql().hql())
        assertEquals(all().filter { if (oracle) it.description == null else it.description.isNullOrBlank() }.map { it.id }.sorted(), ids(nullOrBlank.toList()))
    }

    @Test
    fun equalsIgnoreCase() {
        // @start region="ignore-case"
        val name = "Park"
        val q = qb.from(Order::class.java).where { it.description.equals(name, ignoreCase = true) }
        assertEquals("select e0 from Order e0 where lower(e0.description) = lower(:p1)", q.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.equals(name, ignoreCase = true) }, ids(q.toList()))
    }

    @Test
    fun substringIndexOfReplace() {
        // @start region="substring-index-replace"
        val head = qb.from(Order::class.java).where { it.description!!.substring(0, 3) == "lee" }
        assertEquals("select e0 from Order e0 where substring(e0.description, 1, 3) = 'lee'", head.toHql().hql())

        val dash = qb.from(Order::class.java).where { it.description!!.indexOf("-") == 3 }
        assertEquals("select e0 from Order e0 where (locate('-', e0.description) - 1) = 3", dash.toHql().hql())

        val replaced = qb.from(Order::class.java).where { it.description!!.replace("-", "_") == "lee_01" }
        assertEquals("select e0 from Order e0 where replace(e0.description, '-', '_') = 'lee_01'", replaced.toHql().hql())
        // @end
        assertEquals(kotlinIds { it.length >= 3 && it.substring(0, 3) == "lee" }, ids(head.toList()))
        assertEquals(kotlinIds { it.indexOf("-") == 3 }, ids(dash.toList()))
        assertEquals(kotlinIds { it.replace("-", "_") == "lee_01" }, ids(replaced.toList()))
    }

    @Test
    fun templatesAndPlus() {
        // @start region="template"
        val prefix = "A-"
        val q = qb.from(Order::class.java).where { "$prefix${it.description}!" == "A-PARK!" }
        assertEquals("select e0 from Order e0 where concat(:p1, e0.description, '!') = 'A-PARK!'", q.toHql().hql())

        val labels = qb.from(Order::class.java).where { it.status.toInt() == 2 }.select { it.description + "#" + it.status }
        assertEquals("select concat(e0.description, '#', str(e0.status)) from Order e0 where e0.status = 2", labels.toHql().hql())
        // @end
        assertEquals(kotlinIds { "$prefix$it!" == "A-PARK!" }, ids(q.toList()))
        assertEquals(listOf("PARK#2"), labels.toList())
    }

    @Test
    fun templateOfCapturedValues() {
        val a = "lee"
        val b = "01"
        // @start region="template-captured"
        // only captured values: the text is made in Kotlin and bound
        val q = qb.from(Order::class.java).where { "$a-$b" == it.description }
        assertEquals("select e0 from Order e0 where e0.description = :p1", q.toHql().hql())
        // @end
        assertEquals(kotlinIds { it == "$a-$b" }, ids(q.toList()))
    }

    @Test
    fun inSelectSortAndSet() {
        // @start region="select-sort-set"
        val upper = qb.from(Order::class.java).where { it.status.toInt() == 2 }.select { it.description?.uppercase() }
        assertEquals("select upper(e0.description) from Order e0 where e0.status = 2", upper.toHql().hql())

        val sorted = qb.from(Order::class.java).orderBy { asc(it.description?.length).asc(it.id) }
        assertEquals("select e0 from Order e0 order by length(e0.description) asc nulls first, e0.id asc", sorted.toHql().hql())

        val trim = qb.update(Order::class.java).set { it.description = it.description?.trim() }.all()
        assertEquals("update Order e0 set e0.description = trim(e0.description)", trim.toHql().hql())
        // @end
        assertEquals(listOf("PARK"), upper.toList())
        assertEquals(7, sorted.toList().size)
        trim.execute()
        val trimmed = if (oracle) listOf("Kim", "PARK", "kimi", "lee-01") else listOf("", "", "Kim", "PARK", "kimi", "lee-01")
        assertEquals(trimmed, all().mapNotNull { it.description }.sorted())
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
