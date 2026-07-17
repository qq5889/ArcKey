package com.selfspace.lockzipas.model

import java.math.BigInteger

enum class CrackState {
    Idle,
    Running,
    Paused,
    Success,
    Failed,
    Canceled
}

enum class CrackPhase {
    Dictionary,
    BruteForce
}

data class CrackCheckpoint(
    val phase: CrackPhase,
    val dictionaryIndex: Int = 0,
    val bruteForceOffset: BigInteger = BigInteger.ZERO,
    val attempts: Long = 0L,
    val currentLength: Int = 0
)

data class CrackConfig(
    val archiveUri: String,
    val archiveDisplayName: String,
    val outputTreeUri: String,
    val passwordBookUri: String?,
    val manualPasswords: List<String>,
    val charset: String,
    val minLength: Int,
    val maxLength: Int,
    val workerCount: Int,
    val resumeCheckpoint: CrackCheckpoint? = null
)

data class CrackSession(
    val state: CrackState = CrackState.Idle,
    val archiveName: String = "",
    val phaseLabel: String = "",
    val attempts: Long = 0L,
    val totalCandidates: BigInteger? = null,
    val speedPerSecond: Double = 0.0,
    val currentLength: Int = 0,
    val currentIndex: BigInteger = BigInteger.ZERO,
    val message: String = "",
    val successPassword: String = "",
    val successPasswordReference: String = ""
)

data class ArchiveEntry(
    val index: Int,
    val path: String,
    val size: Long,
    val isFolder: Boolean
)

data class ExtractionSummary(
    val filesWritten: Int,
    val bytesWritten: Long,
    val outputDirectoryName: String
)
