package org.lambdaql.spring;

import org.junit.jupiter.api.Test;
import org.lambdaql.engine.load.BytecodeLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The class file reader of the lambdaql core jar in this module's tests (ROADMAP D52): ASM on JDK 21 ({@code test}),
 * {@code java.lang.classfile} on JDK 24 and later ({@code testClassFile}). The Gradle task gives the expected name.
 */
class ReaderSelectionTest {

    @Test
    void readerOfThisJvm() {
        String expected = System.getProperty("lql.reader.expected");
        if (expected != null) {
            assertEquals(expected, BytecodeLoader.readerName(), Runtime.version() + " "
                    + BytecodeLoader.class.getProtectionDomain().getCodeSource().getLocation());
        }
    }
}
