package com.selfspace.lockzipas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchivePathValidatorTest {
    @Test
    fun acceptsNestedRelativePath() {
        assertEquals(
            listOf("docs", "report.txt"),
            ArchivePathValidator.segmentsFor("docs/report.txt")
        )
    }

    @Test
    fun rejectsParentTraversal() {
        assertThrows(PathValidationException::class.java) {
            ArchivePathValidator.segmentsFor("../secret.txt")
        }
    }

    @Test
    fun rejectsAbsolutePath() {
        assertThrows(PathValidationException::class.java) {
            ArchivePathValidator.segmentsFor("/tmp/secret.txt")
        }
    }

    @Test
    fun rejectsWindowsDrivePath() {
        assertThrows(PathValidationException::class.java) {
            ArchivePathValidator.segmentsFor("C:\\Users\\secret.txt")
        }
    }

    @Test
    fun sanitizesArchiveNameForOutputDirectory() {
        assertEquals("my_archive", ArchivePathValidator.sanitizeDirectoryName("my:archive.zip"))
    }
}
