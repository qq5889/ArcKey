package com.selfspace.lockzipas.core

data class PasswordBookAppendResult(
    val text: String,
    val appended: Boolean
)

object PasswordBookLines {
    fun parse(text: String): List<String> {
        val seen = LinkedHashSet<String>()
        text.replace("\r\n", "\n")
            .split('\n')
            .map { it.removeSuffix("\r") }
            .filter { it.isNotEmpty() }
            .forEach { seen += it }
        return seen.toList()
    }

    fun mergeManualAndBook(manualPasswords: List<String>, bookPasswords: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        manualPasswords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { seen += it }
        bookPasswords
            .filter { it.isNotEmpty() }
            .forEach { seen += it }
        return seen.toList()
    }

    fun appendIfMissing(existingText: String, password: String): PasswordBookAppendResult {
        require(password.isNotEmpty()) { "password must not be empty" }
        require(!password.contains('\n') && !password.contains('\r')) {
            "password must be a single line"
        }
        if (parse(existingText).contains(password)) {
            return PasswordBookAppendResult(existingText, appended = false)
        }

        val separator = if (
            existingText.isEmpty() ||
            existingText.endsWith('\n') ||
            existingText.endsWith('\r')
        ) {
            ""
        } else {
            "\n"
        }
        return PasswordBookAppendResult(existingText + separator + password + "\n", appended = true)
    }
}
