package com.selfspace.lockzipas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordBookLinesTest {
    @Test
    fun parseDeduplicatesAndKeepsWhitespaceInsidePassword() {
        assertEquals(
            listOf("alpha", " beta ", "gamma"),
            PasswordBookLines.parse("alpha\n beta \nalpha\ngamma\n")
        )
    }

    @Test
    fun appendIfMissingAddsTrailingLine() {
        val result = PasswordBookLines.appendIfMissing("alpha", "beta")

        assertTrue(result.appended)
        assertEquals("alpha\nbeta\n", result.text)
    }

    @Test
    fun appendIfMissingDoesNotDuplicate() {
        val result = PasswordBookLines.appendIfMissing("alpha\nbeta\n", "beta")

        assertFalse(result.appended)
        assertEquals("alpha\nbeta\n", result.text)
    }

    @Test
    fun mergeManualAndBookPreservesManualPriority() {
        assertEquals(
            listOf("one", "two", "three"),
            PasswordBookLines.mergeManualAndBook(listOf(" one ", "two"), listOf("two", "three"))
        )
    }
}
