package com.selfspace.lockzipas.core

import com.selfspace.lockzipas.model.CrackCheckpoint
import com.selfspace.lockzipas.model.CrackPhase
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckpointCodecTest {
    @Test
    fun roundTripsCheckpoint() {
        val checkpoint = CrackCheckpoint(
            phase = CrackPhase.BruteForce,
            dictionaryIndex = 12,
            bruteForceOffset = BigInteger("123456789"),
            attempts = 400L,
            currentLength = 6
        )

        assertEquals(checkpoint, CheckpointCodec.decode(CheckpointCodec.encode(checkpoint)))
    }

    @Test
    fun returnsNullForInvalidPayload() {
        assertNull(CheckpointCodec.decode("not-a-checkpoint"))
    }
}
