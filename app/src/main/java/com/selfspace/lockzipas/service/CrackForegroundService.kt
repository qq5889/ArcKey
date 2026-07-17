package com.selfspace.lockzipas.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.selfspace.lockzipas.R
import com.selfspace.lockzipas.archive.SevenZipArchiveEngine
import com.selfspace.lockzipas.core.BruteforceGenerator
import com.selfspace.lockzipas.core.CrackEstimator
import com.selfspace.lockzipas.core.PasswordBookLines
import com.selfspace.lockzipas.model.CrackCheckpoint
import com.selfspace.lockzipas.model.CrackConfig
import com.selfspace.lockzipas.model.CrackPhase
import com.selfspace.lockzipas.model.CrackSession
import com.selfspace.lockzipas.model.CrackState
import com.selfspace.lockzipas.storage.ArchiveCache
import com.selfspace.lockzipas.storage.DocumentTreeArchiveWriter
import com.selfspace.lockzipas.storage.PasswordBookStore
import java.io.File
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrackForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var requestStore: CrackRequestStore
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var crackJob: Job? = null
    @Volatile private var paused = false
    @Volatile private var canceled = false

    override fun onCreate() {
        super.onCreate()
        requestStore = CrackRequestStore(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.toCrackConfig()
                requestStore.clearCheckpoint()
                requestStore.saveConfig(config)
                startRun(config)
            }
            ACTION_PAUSE -> {
                paused = true
                releaseWakeLock()
                updateSession(currentSession().copy(state = CrackState.Paused, message = "已暂停"))
            }
            ACTION_RESUME -> {
                paused = false
                acquireWakeLock()
                updateSession(currentSession().copy(state = CrackState.Running, message = "继续尝试密码"))
            }
            ACTION_CANCEL -> {
                canceled = true
                crackJob?.cancel(CancellationException("Canceled by user"))
                requestStore.clearCheckpoint()
                releaseWakeLock()
                updateSession(currentSession().copy(state = CrackState.Canceled, message = "已取消"))
                stopSelf()
            }
            else -> {
                requestStore.loadConfig()?.let(::startRun)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        crackJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startRun(config: CrackConfig) {
        crackJob?.cancel()
        releaseWakeLock()
        acquireWakeLock()
        paused = false
        canceled = false

        val initialSession = CrackSession(
            state = CrackState.Running,
            archiveName = config.archiveDisplayName,
            phaseLabel = "准备",
            message = "正在复制压缩包到本机缓存"
        )
        startForegroundCompat(initialSession)
        updateSession(initialSession)

        crackJob = scope.launch {
            runCatching {
                runCrack(config.copy(resumeCheckpoint = requestStore.loadCheckpoint()))
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                requestStore.clearCheckpoint()
                updateSession(
                    currentSession().copy(
                        state = CrackState.Failed,
                        message = error.message ?: "任务失败"
                    )
                )
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private suspend fun runCrack(config: CrackConfig) = withContext(Dispatchers.IO) {
        val archiveUri = Uri.parse(config.archiveUri)
        val outputUri = Uri.parse(config.outputTreeUri)
        val passwordBookUri = config.passwordBookUri?.let(Uri::parse)
        val passwordBook = PasswordBookStore(this@CrackForegroundService)
        val bookPasswords = passwordBookUri?.let(passwordBook::readPasswords).orEmpty()
        val dictionary = PasswordBookLines.mergeManualAndBook(config.manualPasswords, bookPasswords)
        val normalizedCharset = BruteforceGenerator.normalizeCharset(config.charset)
        val bruteTotal = BruteforceGenerator.totalCount(
            normalizedCharset.length,
            config.minLength,
            config.maxLength
        )
        val totalCandidates = BigInteger.valueOf(dictionary.size.toLong()) + bruteTotal

        val archiveFile = ArchiveCache(this@CrackForegroundService)
            .copyToCache(archiveUri, config.archiveDisplayName)
        val checkpoint = config.resumeCheckpoint
        val workerCount = config.workerCount.coerceIn(1, MAX_WORKERS)

        val attempts = AtomicLong(checkpoint?.attempts ?: 0L)
        val speedMeter = SpeedMeter(attempts.get())
        updateSession(
            currentSession().copy(
                state = CrackState.Running,
                archiveName = config.archiveDisplayName,
                totalCandidates = totalCandidates,
                attempts = attempts.get(),
                phaseLabel = "密码本",
                message = "先尝试手动密码和密码本，${workerCount} 线程同步破解"
            )
        )

        val dictionaryStart = if (checkpoint?.phase == CrackPhase.Dictionary) {
            checkpoint.dictionaryIndex.coerceAtMost(dictionary.size)
        } else if (checkpoint?.phase == CrackPhase.BruteForce) {
            dictionary.size
        } else {
            0
        }

        val dictionaryPassword = crackDictionaryParallel(
            config = config,
            archiveFile = archiveFile,
            dictionary = dictionary,
            startIndex = dictionaryStart,
            attempts = attempts,
            speedMeter = speedMeter,
            totalCandidates = totalCandidates,
            workerCount = workerCount
        )
        if (dictionaryPassword != null) {
            handleSuccess(
                config,
                outputUri,
                passwordBookUri,
                passwordBook,
                dictionaryPassword,
                SevenZipArchiveEngine(archiveFile)
            )
            return@withContext
        }

        val bruteStart = if (checkpoint?.phase == CrackPhase.BruteForce) {
            checkpoint.bruteForceOffset
        } else {
            BigInteger.ZERO
        }

        val bruteForcePassword = crackBruteForceParallel(
            config = config,
            archiveFile = archiveFile,
            charset = normalizedCharset,
            startIndex = bruteStart,
            endIndex = bruteTotal,
            attempts = attempts,
            speedMeter = speedMeter,
            totalCandidates = totalCandidates,
            workerCount = workerCount
        )
        if (bruteForcePassword != null) {
            handleSuccess(
                config,
                outputUri,
                passwordBookUri,
                passwordBook,
                bruteForcePassword,
                SevenZipArchiveEngine(archiveFile)
            )
            return@withContext
        }

        requestStore.clearCheckpoint()
        releaseWakeLock()
        updateSession(
            currentSession().copy(
                state = CrackState.Failed,
                attempts = attempts.get(),
                totalCandidates = totalCandidates,
                message = "没有在当前候选范围内找到密码"
            )
        )
        stopSelf()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val currentWakeLock = wakeLock
        if (currentWakeLock?.isHeld == true) return

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:CrackWakeLock")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    private suspend fun crackDictionaryParallel(
        config: CrackConfig,
        archiveFile: File,
        dictionary: List<String>,
        startIndex: Int,
        attempts: AtomicLong,
        speedMeter: SpeedMeter,
        totalCandidates: BigInteger,
        workerCount: Int
    ): String? = coroutineScope {
        if (startIndex >= dictionary.size) return@coroutineScope null

        val nextIndex = AtomicInteger(startIndex)
        val foundPassword = AtomicReference<String?>(null)
        val workers = List(workerCount) {
            async(Dispatchers.IO) {
                val engine = SevenZipArchiveEngine(archiveFile)
                while (foundPassword.get() == null) {
                    awaitIfPausedOrCanceled()
                    val index = nextIndex.getAndIncrement()
                    if (index >= dictionary.size) break

                    val password = dictionary[index]
                    val matched = engine.tryPassword(password)
                    val currentAttempts = attempts.incrementAndGet()

                    if (matched) {
                        foundPassword.compareAndSet(null, password)
                    }

                    val safeCheckpointIndex = (nextIndex.get() - workerCount)
                        .coerceAtLeast(startIndex)
                        .coerceAtMost(dictionary.size)
                    maybeCheckpoint(
                        currentAttempts,
                        CrackCheckpoint(
                            phase = CrackPhase.Dictionary,
                            dictionaryIndex = safeCheckpointIndex,
                            attempts = currentAttempts
                        )
                    )
                    updateProgress(
                        config,
                        totalCandidates,
                        currentAttempts,
                        speedMeter,
                        phaseLabel = "密码本",
                        currentLength = 0,
                        currentIndex = BigInteger.valueOf(index.toLong()),
                        workerCount = workerCount
                    )
                }
            }
        }
        workers.awaitAll()
        foundPassword.get()
    }

    private suspend fun crackBruteForceParallel(
        config: CrackConfig,
        archiveFile: File,
        charset: String,
        startIndex: BigInteger,
        endIndex: BigInteger,
        attempts: AtomicLong,
        speedMeter: SpeedMeter,
        totalCandidates: BigInteger,
        workerCount: Int
    ): String? = coroutineScope {
        if (startIndex >= endIndex) return@coroutineScope null

        val cursor = BigIntegerCursor(startIndex, endIndex)
        val foundPassword = AtomicReference<String?>(null)
        val workers = List(workerCount) {
            async(Dispatchers.IO) {
                val engine = SevenZipArchiveEngine(archiveFile)
                while (foundPassword.get() == null) {
                    awaitIfPausedOrCanceled()
                    val index = cursor.next() ?: break
                    val password = BruteforceGenerator.passwordAt(
                        index,
                        charset,
                        config.minLength,
                        config.maxLength
                    )
                    val matched = engine.tryPassword(password)
                    val currentAttempts = attempts.incrementAndGet()
                    val currentLength = BruteforceGenerator.lengthForIndex(
                        index,
                        charset.length,
                        config.minLength,
                        config.maxLength
                    )

                    if (matched) {
                        foundPassword.compareAndSet(null, password)
                    }

                    maybeCheckpoint(
                        currentAttempts,
                        CrackCheckpoint(
                            phase = CrackPhase.BruteForce,
                            bruteForceOffset = cursor.safeCheckpoint(workerCount),
                            attempts = currentAttempts,
                            currentLength = currentLength
                        )
                    )
                    updateProgress(
                        config,
                        totalCandidates,
                        currentAttempts,
                        speedMeter,
                        phaseLabel = "暴力破解",
                        currentLength = currentLength,
                        currentIndex = index,
                        workerCount = workerCount
                    )
                }
            }
        }
        workers.awaitAll()
        foundPassword.get()
    }

    private suspend fun handleSuccess(
        config: CrackConfig,
        outputUri: Uri,
        passwordBookUri: Uri?,
        passwordBook: PasswordBookStore,
        password: String,
        engine: SevenZipArchiveEngine
    ) {
        val savedReference = passwordBookUri?.let { uri ->
            val appended = passwordBook.appendIfMissing(uri, password)
            if (appended) "已保存到密码本" else "密码本中已有该密码"
        } ?: "未选择密码本，未保存"

        updateSession(
            currentSession().copy(
                state = CrackState.Running,
                phaseLabel = "解压",
                message = "密码已验证，正在解压",
                successPassword = password,
                successPasswordReference = savedReference
            )
        )

        val writer = DocumentTreeArchiveWriter(
            context = this,
            outputTreeUri = outputUri,
            archiveDisplayName = config.archiveDisplayName
        )
        val summary = engine.extract(password, writer) { bytes ->
            if (bytes > 0L) {
                val session = currentSession()
                updateSession(
                    session.copy(
                        message = "正在写入文件",
                        successPassword = password,
                        successPasswordReference = savedReference
                    )
                )
            }
        }

        requestStore.clearCheckpoint()
        releaseWakeLock()
        updateSession(
            currentSession().copy(
                state = CrackState.Success,
                phaseLabel = "完成",
                message = "已解压 ${summary.filesWritten} 个文件到 ${summary.outputDirectoryName}",
                successPassword = password,
                successPasswordReference = savedReference
            )
        )
        stopSelf()
    }

    private suspend fun awaitIfPausedOrCanceled() {
        while (paused && !canceled) {
            updateSession(currentSession().copy(state = CrackState.Paused, message = "已暂停"))
            delay(350)
        }
        if (canceled) throw CancellationException("Canceled")
    }

    private fun maybeCheckpoint(attempts: Long, checkpoint: CrackCheckpoint) {
        if (attempts % CHECKPOINT_INTERVAL == 0L) {
            requestStore.saveCheckpoint(checkpoint)
        }
    }

    private fun updateProgress(
        config: CrackConfig,
        totalCandidates: BigInteger,
        attempts: Long,
        speedMeter: SpeedMeter,
        phaseLabel: String,
        currentLength: Int,
        currentIndex: BigInteger,
        workerCount: Int
    ) {
        if (attempts % UI_UPDATE_INTERVAL != 0L) return
        updateSession(
            currentSession().copy(
                state = CrackState.Running,
                archiveName = config.archiveDisplayName,
                phaseLabel = phaseLabel,
                attempts = attempts,
                totalCandidates = totalCandidates,
                speedPerSecond = speedMeter.update(attempts),
                currentLength = currentLength,
                currentIndex = currentIndex,
                message = if (CrackEstimator.shouldWarn(totalCandidates)) {
                    "组合数较大，${workerCount} 线程运行中，建议保持充电并按需暂停"
                } else {
                    "${workerCount} 线程正在尝试候选密码"
                }
            )
        )
    }

    private fun currentSession(): CrackSession = CrackSessionBus.session.value

    private fun updateSession(session: CrackSession) {
        CrackSessionBus.update(session)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(session))
    }

    private fun startForegroundCompat(session: CrackSession) {
        val notification = buildNotification(session)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(session: CrackSession): Notification {
        val title = when (session.state) {
            CrackState.Success -> "ArcKey 完成"
            CrackState.Failed -> "ArcKey 未找到密码"
            CrackState.Paused -> "ArcKey 已暂停"
            CrackState.Canceled -> "ArcKey 已取消"
            else -> "ArcKey 正在处理"
        }
        val progressMax = session.totalCandidates?.let { total ->
            if (total <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) total.toInt() else 0
        } ?: 0
        val progress = if (progressMax > 0) session.attempts.coerceAtMost(progressMax.toLong()).toInt() else 0
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_archive_lock)
            .setContentTitle(title)
            .setContentText(notificationLine(session))
            .setOngoing(session.state == CrackState.Running || session.state == CrackState.Paused)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progressMax > 0 && session.state == CrackState.Running) {
            builder.setProgress(progressMax, progress, false)
        } else if (session.state == CrackState.Running) {
            builder.setProgress(0, 0, true)
        }

        if (session.state == CrackState.Running) {
            builder.addAction(0, "暂停", servicePendingIntent(ACTION_PAUSE, 10))
            builder.addAction(0, "取消", servicePendingIntent(ACTION_CANCEL, 11))
        } else if (session.state == CrackState.Paused) {
            builder.addAction(0, "继续", servicePendingIntent(ACTION_RESUME, 12))
            builder.addAction(0, "取消", servicePendingIntent(ACTION_CANCEL, 13))
        }

        return builder.build()
    }

    private fun notificationLine(session: CrackSession): String {
        val speed = if (session.speedPerSecond > 0.0) {
            " • ${"%.1f".format(session.speedPerSecond)}/s"
        } else {
            ""
        }
        return listOf(session.phaseLabel, "${session.attempts} 次$speed", session.message)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CrackForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Archive cracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows archive recovery progress"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun Intent.toCrackConfig(): CrackConfig {
        return CrackConfig(
            archiveUri = requireNotNull(getStringExtra(EXTRA_ARCHIVE_URI)),
            archiveDisplayName = getStringExtra(EXTRA_ARCHIVE_NAME) ?: "archive",
            outputTreeUri = requireNotNull(getStringExtra(EXTRA_OUTPUT_URI)),
            passwordBookUri = getStringExtra(EXTRA_PASSWORD_BOOK_URI),
            manualPasswords = getStringArrayListExtra(EXTRA_MANUAL_PASSWORDS).orEmpty(),
            charset = getStringExtra(EXTRA_CHARSET) ?: "0123456789",
            minLength = getIntExtra(EXTRA_MIN_LENGTH, 1).coerceAtLeast(1),
            maxLength = getIntExtra(EXTRA_MAX_LENGTH, 6).coerceAtLeast(1),
            workerCount = getIntExtra(EXTRA_WORKER_COUNT, defaultWorkerCount()).coerceIn(1, MAX_WORKERS)
        )
    }

    private class SpeedMeter(initialAttempts: Long) {
        private var lastAttempts = initialAttempts
        private var lastTime = SystemClock.elapsedRealtime()
        private var lastSpeed = 0.0

        @Synchronized
        fun update(attempts: Long): Double {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastTime
            if (elapsed >= 1000L) {
                lastSpeed = ((attempts - lastAttempts).coerceAtLeast(0)).toDouble() * 1000.0 / elapsed
                lastAttempts = attempts
                lastTime = now
            }
            return lastSpeed
        }
    }

    companion object {
        const val ACTION_START = "com.selfspace.lockzipas.action.START"
        const val ACTION_PAUSE = "com.selfspace.lockzipas.action.PAUSE"
        const val ACTION_RESUME = "com.selfspace.lockzipas.action.RESUME"
        const val ACTION_CANCEL = "com.selfspace.lockzipas.action.CANCEL"

        const val EXTRA_ARCHIVE_URI = "archive_uri"
        const val EXTRA_ARCHIVE_NAME = "archive_name"
        const val EXTRA_OUTPUT_URI = "output_uri"
        const val EXTRA_PASSWORD_BOOK_URI = "password_book_uri"
        const val EXTRA_MANUAL_PASSWORDS = "manual_passwords"
        const val EXTRA_CHARSET = "charset"
        const val EXTRA_MIN_LENGTH = "min_length"
        const val EXTRA_MAX_LENGTH = "max_length"
        const val EXTRA_WORKER_COUNT = "worker_count"

        private const val CHANNEL_ID = "crack_progress"
        private const val NOTIFICATION_ID = 7107
        private const val MAX_WORKERS = 8
        private const val CHECKPOINT_INTERVAL = 250L
        private const val UI_UPDATE_INTERVAL = 25L

        fun defaultWorkerCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return cores.coerceAtLeast(2).coerceAtMost(4)
        }

        fun startIntent(context: Context, config: CrackConfig): Intent {
            return Intent(context, CrackForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ARCHIVE_URI, config.archiveUri)
                putExtra(EXTRA_ARCHIVE_NAME, config.archiveDisplayName)
                putExtra(EXTRA_OUTPUT_URI, config.outputTreeUri)
                putExtra(EXTRA_PASSWORD_BOOK_URI, config.passwordBookUri)
                putStringArrayListExtra(EXTRA_MANUAL_PASSWORDS, ArrayList(config.manualPasswords))
                putExtra(EXTRA_CHARSET, config.charset)
                putExtra(EXTRA_MIN_LENGTH, config.minLength)
                putExtra(EXTRA_MAX_LENGTH, config.maxLength)
                putExtra(EXTRA_WORKER_COUNT, config.workerCount)
            }
        }

        fun actionIntent(context: Context, action: String): Intent {
            return Intent(context, CrackForegroundService::class.java).setAction(action)
        }
    }
}

private class BigIntegerCursor(
    private val start: BigInteger,
    private val endExclusive: BigInteger
) {
    private var nextIndex: BigInteger = start

    @Synchronized
    fun next(): BigInteger? {
        if (nextIndex >= endExclusive) return null
        val value = nextIndex
        nextIndex += BigInteger.ONE
        return value
    }

    @Synchronized
    fun safeCheckpoint(workerCount: Int): BigInteger {
        val rewind = BigInteger.valueOf(workerCount.toLong())
        val checkpoint = nextIndex - rewind
        return checkpoint.max(start)
    }
}
