package com.selfspace.lockzipas.core

import com.selfspace.lockzipas.model.CrackCheckpoint
import com.selfspace.lockzipas.model.CrackPhase
import java.math.BigInteger

object CheckpointCodec {
    fun encode(checkpoint: CrackCheckpoint): String {
        return listOf(
            checkpoint.phase.name,
            checkpoint.dictionaryIndex.toString(),
            checkpoint.bruteForceOffset.toString(),
            checkpoint.attempts.toString(),
            checkpoint.currentLength.toString()
        ).joinToString("|")
    }

    fun decode(value: String?): CrackCheckpoint? {
        if (value.isNullOrBlank()) return null
        val parts = value.split('|')
        if (parts.size != 5) return null
        return runCatching {
            CrackCheckpoint(
                phase = CrackPhase.valueOf(parts[0]),
                dictionaryIndex = parts[1].toInt(),
                bruteForceOffset = BigInteger(parts[2]),
                attempts = parts[3].toLong(),
                currentLength = parts[4].toInt()
            )
        }.getOrNull()
    }
}
