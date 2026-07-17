package com.selfspace.lockzipas.core

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BruteforceGeneratorTest {
    @Test
    fun passwordAtEnumeratesByLengthThenLexicographicOrder() {
        val charset = "01"

        assertEquals("0", BruteforceGenerator.passwordAt(BigInteger.ZERO, charset, 1, 2))
        assertEquals("1", BruteforceGenerator.passwordAt(BigInteger.ONE, charset, 1, 2))
        assertEquals("00", BruteforceGenerator.passwordAt(BigInteger("2"), charset, 1, 2))
        assertEquals("01", BruteforceGenerator.passwordAt(BigInteger("3"), charset, 1, 2))
        assertEquals("10", BruteforceGenerator.passwordAt(BigInteger("4"), charset, 1, 2))
        assertEquals("11", BruteforceGenerator.passwordAt(BigInteger("5"), charset, 1, 2))
    }

    @Test
    fun totalCountSumsAllLengths() {
        assertEquals(BigInteger("1111110"), BruteforceGenerator.totalCount(10, 1, 6))
    }

    @Test
    fun normalizeCharsetPreservesFirstOccurrenceOnly() {
        assertEquals("abc", BruteforceGenerator.normalizeCharset("aabbccaa"))
    }

    @Test
    fun passwordAtRejectsOutOfRangeIndex() {
        assertThrows(IndexOutOfBoundsException::class.java) {
            BruteforceGenerator.passwordAt(BigInteger("6"), "01", 1, 2)
        }
    }
}
