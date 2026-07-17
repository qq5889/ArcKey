package com.selfspace.lockzipas.core

import java.math.BigInteger

object CrackEstimator {
    private val warningThreshold = BigInteger("10000000")

    fun totalCandidates(
        manualCount: Int,
        bookCount: Int,
        charset: String,
        minLength: Int,
        maxLength: Int
    ): BigInteger {
        val normalized = BruteforceGenerator.normalizeCharset(charset)
        if (normalized.isBlank()) return BigInteger.valueOf((manualCount + bookCount).toLong())
        return BigInteger.valueOf((manualCount + bookCount).toLong()) +
            BruteforceGenerator.totalCount(normalized.length, minLength, maxLength)
    }

    fun shouldWarn(totalCandidates: BigInteger): Boolean = totalCandidates > warningThreshold
}
