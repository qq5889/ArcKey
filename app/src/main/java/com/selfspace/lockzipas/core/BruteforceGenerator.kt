package com.selfspace.lockzipas.core

import java.math.BigInteger

object BruteforceGenerator {
    fun normalizeCharset(input: String): String {
        val seen = LinkedHashSet<Char>()
        input.forEach { char ->
            if (char != '\n' && char != '\r' && char != '\u0000') {
                seen += char
            }
        }
        return seen.joinToString("")
    }

    fun totalCount(charsetSize: Int, minLength: Int, maxLength: Int): BigInteger {
        require(charsetSize > 0) { "charsetSize must be positive" }
        require(minLength > 0) { "minLength must be positive" }
        require(maxLength >= minLength) { "maxLength must be >= minLength" }

        val base = BigInteger.valueOf(charsetSize.toLong())
        var total = BigInteger.ZERO
        for (length in minLength..maxLength) {
            total += base.pow(length)
        }
        return total
    }

    fun passwordAt(index: BigInteger, charset: String, minLength: Int, maxLength: Int): String {
        val normalized = normalizeCharset(charset)
        require(normalized.isNotEmpty()) { "charset must not be empty" }
        require(index >= BigInteger.ZERO) { "index must be >= 0" }

        val base = BigInteger.valueOf(normalized.length.toLong())
        var remaining = index
        for (length in minLength..maxLength) {
            val countForLength = base.pow(length)
            if (remaining < countForLength) {
                return passwordForLength(remaining, normalized, length)
            }
            remaining -= countForLength
        }
        throw IndexOutOfBoundsException("index $index exceeds brute force range")
    }

    fun sequence(
        charset: String,
        minLength: Int,
        maxLength: Int,
        startIndex: BigInteger = BigInteger.ZERO
    ): Sequence<IndexedPassword> {
        val normalized = normalizeCharset(charset)
        val total = totalCount(normalized.length, minLength, maxLength)
        return sequence {
            var index = startIndex
            while (index < total) {
                yield(IndexedPassword(index, passwordAt(index, normalized, minLength, maxLength)))
                index += BigInteger.ONE
            }
        }
    }

    fun lengthForIndex(index: BigInteger, charsetSize: Int, minLength: Int, maxLength: Int): Int {
        require(index >= BigInteger.ZERO)
        val base = BigInteger.valueOf(charsetSize.toLong())
        var remaining = index
        for (length in minLength..maxLength) {
            val countForLength = base.pow(length)
            if (remaining < countForLength) return length
            remaining -= countForLength
        }
        return maxLength
    }

    private fun passwordForLength(index: BigInteger, charset: String, length: Int): String {
        val base = BigInteger.valueOf(charset.length.toLong())
        var value = index
        val output = CharArray(length)
        for (position in length - 1 downTo 0) {
            val digit = value.mod(base).toInt()
            output[position] = charset[digit]
            value = value.divide(base)
        }
        return String(output)
    }
}

data class IndexedPassword(
    val index: BigInteger,
    val password: String
)
