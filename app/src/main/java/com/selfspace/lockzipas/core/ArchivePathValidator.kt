package com.selfspace.lockzipas.core

class PathValidationException(message: String) : IllegalArgumentException(message)

object ArchivePathValidator {
    fun segmentsFor(rawPath: String): List<String> {
        if (rawPath.isBlank()) {
            throw PathValidationException("Archive entry has an empty path")
        }
        if (rawPath.indexOf('\u0000') >= 0) {
            throw PathValidationException("Archive entry contains a null byte")
        }

        val normalized = rawPath.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            throw PathValidationException("Absolute archive paths are not allowed")
        }

        val segments = normalized.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            throw PathValidationException("Archive entry has no writable segments")
        }

        segments.forEachIndexed { index, segment ->
            if (segment == "." || segment == "..") {
                throw PathValidationException("Archive entry attempts path traversal")
            }
            if (index == 0 && segment.length == 2 && segment[1] == ':' && segment[0].isLetter()) {
                throw PathValidationException("Drive-prefixed archive paths are not allowed")
            }
        }
        return segments
    }

    fun sanitizeDirectoryName(input: String): String {
        val baseName = input.substringAfterLast('/').substringBeforeLast('.', input)
        val cleaned = baseName
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim('.', ' ', '_')
        return if (cleaned.isBlank()) "archive" else cleaned.take(80)
    }
}
