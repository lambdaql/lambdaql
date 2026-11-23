package org.lambdaql.query

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lambdaql.DbOnlyException
import org.lambdaql.LambdaQLException
import org.lambdaql.UnsupportedLambdaShapeException
import org.lambdaql.i18n.LqlMessages
import org.lambdaql.query.ManualKotlinFunctionTest.OrderFns
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.Locale

/**
 * The Kotlin examples of 11. Errors and languages: catching a lambdaQL error, checking its code in tests,
 * a database function called outside a lambda, and the message language.
 * The region markers are used by the manual.
 */
@SpringBootTest(classes = [ManualKotlinErrorsTest.Context::class])
@ActiveProfiles("test")
class ManualKotlinErrorsTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    @Test
    fun catchTheError() {
        val qb = QueryBuilder(emf)
        val flag = true
        // @start region="catch"
        val logged = try {
            qb.from(Order::class.java)
                .where { it.status.toInt() == if (flag) 1 else 2 }   // a value if: not supported
                .toList()
            null
        } catch (e: LambdaQLException) {
            // for example log.error("lambdaQL error {}: {}", e.code(), e.getMessage(Locale.ROOT))
            "lambdaQL error ${e.code()}: ${e.getMessage(Locale.ROOT)}"
        }
        // @end
        assertTrue(logged != null && logged.startsWith("lambdaQL error LQL-2021: [LQL-2021] "), logged)
    }

    @Test
    fun checkTheCode() {
        val flag = true
        val qb = QueryBuilder(emf)
        // @start region="check-code"
        // org.junit.jupiter.api.assertThrows
        val e = assertThrows<LambdaQLException> { qb.from(Order::class.java).where { it.status.toInt() == if (flag) 1 else 2 } }
        assertEquals("LQL-2021", e.code())                           // good
        assertInstanceOf(UnsupportedLambdaShapeException::class.java, e)
        // assertEquals("Could not ...", e.message)                 // do not do this
        // @end
    }

    @Test
    fun dbOnly() {
        // @start region="db-only"
        // the body never runs in Kotlin
        assertEquals("LQL-4034", assertThrows<DbOnlyException> { OrderFns.discount(1.0) }.code())
        // @end
    }

    @Test
    fun setLocale() {
        try {
            // @start region="set-locale"
            // at application start
            LqlMessages.setLocale(Locale.JAPANESE)       // Japanese messages
            val current: Locale = LqlMessages.locale()   // the locale lambdaQL uses now
            LqlMessages.setLocale(null)                  // back to the default: the system property, then the JVM default
            // @end
            assertEquals(Locale.JAPANESE, current)
        } finally {
            LqlMessages.setLocale(null)
        }
    }

    /** Minimal Spring context: the datasource chosen by application-test.yaml and the test entities. */
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["org.lambdaql.query"])
    class Context
}
