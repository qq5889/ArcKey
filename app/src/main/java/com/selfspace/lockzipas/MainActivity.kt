package com.selfspace.lockzipas

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.selfspace.lockzipas.core.BruteforceGenerator
import com.selfspace.lockzipas.core.CrackEstimator
import com.selfspace.lockzipas.model.CrackConfig
import com.selfspace.lockzipas.model.CrackSession
import com.selfspace.lockzipas.model.CrackState
import com.selfspace.lockzipas.service.CrackForegroundService
import com.selfspace.lockzipas.service.CrackRequestStore
import com.selfspace.lockzipas.service.CrackSessionBus
import com.selfspace.lockzipas.storage.AppPreferences
import com.selfspace.lockzipas.storage.ArchiveParentDirectory
import com.selfspace.lockzipas.storage.UriDisplayName
import java.math.BigInteger
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val incomingArchiveUri = mutableStateOf<Uri?>(null)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 10, 18)
        window.navigationBarColor = android.graphics.Color.rgb(7, 10, 18)
        incomingArchiveUri.value = archiveUriFromIntent(intent)
        setContent {
            ArcKeyTheme {
                SplashGate {
                    ArcKeyScreen(incomingArchiveUri = incomingArchiveUri.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingArchiveUri.value = archiveUriFromIntent(intent)
    }
}

@Composable
private fun SplashGate(content: @Composable () -> Unit) {
    var showingSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1_400)
        showingSplash = false
    }

    if (showingSplash) {
        CyberSplash()
    } else {
        content()
    }
}

@Composable
private fun ArcKeyScreen(incomingArchiveUri: Uri?) {
    val context = LocalContext.current
    val session by CrackSessionBus.session.collectAsState()
    val requestStore = remember { CrackRequestStore(context) }
    val appPreferences = remember { AppPreferences(context) }

    KeepScreenAwake(active = session.state == CrackState.Running)

    var archiveUri by remember { mutableStateOf<Uri?>(null) }
    var archiveName by remember { mutableStateOf("") }
    var outputTreeUri by remember { mutableStateOf<Uri?>(null) }
    var outputLabel by remember { mutableStateOf("") }
    var defaultOutputTreeUri by remember { mutableStateOf<Uri?>(null) }
    var outputHint by remember { mutableStateOf("") }
    var passwordBookUri by remember { mutableStateOf(appPreferences.lastPasswordBookUri()) }
    var passwordBookLabel by remember { mutableStateOf(appPreferences.lastPasswordBookLabel()) }
    var manualPasswords by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf(true) }
    var lowercase by remember { mutableStateOf(false) }
    var uppercase by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var customCharset by remember { mutableStateOf("") }
    var minLength by remember { mutableStateOf("1") }
    var maxLength by remember { mutableStateOf("6") }
    var workerCount by remember { mutableStateOf(CrackForegroundService.defaultWorkerCount().toString()) }
    var warningConfig by remember { mutableStateOf<CrackConfig?>(null) }
    var canResume by remember { mutableStateOf(requestStore.loadConfig()?.resumeCheckpoint != null) }
    var formMessage by remember { mutableStateOf("") }

    fun applyArchiveUri(uri: Uri) {
        takePersistable(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        archiveUri = uri
        archiveName = UriDisplayName.resolve(context, uri)
        val parentSuggestion = ArchiveParentDirectory.infer(context, uri)
        defaultOutputTreeUri = parentSuggestion?.treeUri
        if (outputTreeUri == null && parentSuggestion != null) {
            if (ArchiveParentDirectory.hasPersistedWritePermission(context, parentSuggestion.treeUri)) {
                outputTreeUri = parentSuggestion.treeUri
                outputLabel = parentSuggestion.label
                outputHint = ""
            } else {
                outputLabel = parentSuggestion.label
                outputHint = "首次使用同目录输出需要点“选择”授权一次"
            }
        }
    }

    val archiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            applyArchiveUri(uri)
        }
    }
    val outputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            takePersistable(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            outputTreeUri = uri
            outputLabel = uri.lastPathSegment ?: "已选择输出目录"
            outputHint = ""
        }
    }
    val bookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            takePersistable(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            passwordBookUri = uri
            passwordBookLabel = UriDisplayName.resolve(context, uri)
            appPreferences.saveLastPasswordBook(uri, passwordBookLabel)
        }
    }
    val createBookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            takePersistable(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            passwordBookUri = uri
            passwordBookLabel = UriDisplayName.resolve(context, uri)
            appPreferences.saveLastPasswordBook(uri, passwordBookLabel)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(incomingArchiveUri) {
        if (incomingArchiveUri != null) {
            applyArchiveUri(incomingArchiveUri)
            formMessage = "已从其他应用打开压缩包"
        }
    }

    val charset = remember(digits, lowercase, uppercase, symbols, customCharset) {
        buildCharset(digits, lowercase, uppercase, symbols, customCharset)
    }
    val minValue = minLength.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val maxValue = maxLength.toIntOrNull()?.coerceAtLeast(minValue) ?: minValue
    val manualCount = manualPasswords.lineSequence().count { it.trim().isNotEmpty() }
    val estimatedTotal = remember(manualCount, charset, minValue, maxValue) {
        if (charset.isBlank()) {
            BigInteger.valueOf(manualCount.toLong())
        } else {
            CrackEstimator.totalCandidates(manualCount, 0, charset, minValue, maxValue)
        }
    }

    CyberBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(session, archiveName.ifBlank { session.archiveName })

            FileSection(
                archiveName = archiveName,
                outputLabel = outputLabel,
                passwordBookLabel = passwordBookLabel,
                onPickArchive = { archiveLauncher.launch(arrayOf("*/*")) },
                onPickOutput = { outputLauncher.launch(defaultOutputTreeUri) },
                onPickBook = { bookLauncher.launch(arrayOf("text/plain", "text/*", "*/*")) },
                onCreateBook = { createBookLauncher.launch("arckey-passwords.txt") },
                outputHint = outputHint
            )

            PasswordSection(
                manualPasswords = manualPasswords,
                onManualPasswordsChange = { manualPasswords = it },
                digits = digits,
                onDigitsChange = { digits = it },
                lowercase = lowercase,
                onLowercaseChange = { lowercase = it },
                uppercase = uppercase,
                onUppercaseChange = { uppercase = it },
                symbols = symbols,
                onSymbolsChange = { symbols = it },
                customCharset = customCharset,
                onCustomCharsetChange = { customCharset = it },
                minLength = minLength,
                onMinLengthChange = { minLength = it.filter(Char::isDigit).take(2) },
                maxLength = maxLength,
                onMaxLengthChange = { maxLength = it.filter(Char::isDigit).take(2) },
                workerCount = workerCount,
                onWorkerCountChange = { workerCount = it.filter(Char::isDigit).take(1) },
                estimatedTotal = estimatedTotal
            )

            SessionSection(
                session = session,
                canResume = canResume,
                formMessage = formMessage,
                onStart = {
                    val effectiveOutputTreeUri = outputTreeUri ?: defaultOutputTreeUri?.takeIf {
                        ArchiveParentDirectory.hasPersistedWritePermission(context, it)
                    }
                    val config = buildConfigOrNull(
                        archiveUri = archiveUri,
                        archiveName = archiveName,
                        outputTreeUri = effectiveOutputTreeUri,
                        passwordBookUri = passwordBookUri,
                        manualPasswords = manualPasswords,
                        charset = charset,
                        minLength = minValue,
                        maxLength = maxValue,
                        workerCount = workerCount.toIntOrNull()?.coerceIn(1, 8)
                            ?: CrackForegroundService.defaultWorkerCount()
                    )
                    if (config != null) {
                        formMessage = ""
                        if (CrackEstimator.shouldWarn(estimatedTotal)) {
                            warningConfig = config
                        } else {
                            startCrack(context, config)
                            canResume = true
                        }
                    } else if (archiveUri == null) {
                        formMessage = "请选择压缩包"
                    } else if (effectiveOutputTreeUri == null) {
                        formMessage = if (defaultOutputTreeUri != null) {
                            "请授权压缩包所在目录作为输出目录"
                        } else {
                            "请选择输出目录"
                        }
                        outputLauncher.launch(defaultOutputTreeUri)
                    } else if (passwordBookUri == null) {
                        formMessage = "请选择或新建密码本，用于保存解压成功的密码"
                    } else {
                        formMessage = "请检查密码字符集和长度"
                    }
                },
                onPause = {
                    context.startService(
                        CrackForegroundService.actionIntent(context, CrackForegroundService.ACTION_PAUSE)
                    )
                },
                onResume = {
                    context.startService(
                        CrackForegroundService.actionIntent(context, CrackForegroundService.ACTION_RESUME)
                    )
                },
                onCancel = {
                    context.startService(
                        CrackForegroundService.actionIntent(context, CrackForegroundService.ACTION_CANCEL)
                    )
                    canResume = false
                },
                onResumeCheckpoint = {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, CrackForegroundService::class.java)
                    )
                }
            )
        }
    }

    val config = warningConfig
    if (config != null) {
        AlertDialog(
            onDismissRequest = { warningConfig = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber) },
            title = { Text("组合数较大") },
            text = {
                Text("当前范围约有 $estimatedTotal 个候选。建议充电、保持散热，并按需暂停。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        startCrack(context, config)
                        canResume = true
                        warningConfig = null
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始")
                }
            },
            dismissButton = {
                TextButton(onClick = { warningConfig = null }) {
                    Text("返回调整")
                }
            }
        )
    }
}

@Composable
private fun KeepScreenAwake(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, active) {
        val window = (context as? Activity)?.window
        if (active) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (active) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
private fun CyberSplash() {
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splashPulse"
    )
    val glow by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashGlow"
    )

    CyberBackdrop(scanOffset = pulse) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(142.dp)) {
                    val stroke = 2.dp.toPx()
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.14f * glow),
                        radius = size.minDimension / 2.2f,
                        style = Stroke(width = stroke)
                    )
                    drawCircle(
                        color = NeonPink.copy(alpha = 0.12f),
                        radius = size.minDimension / 2.8f,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = NeonCyan.copy(alpha = 0.92f),
                        startAngle = pulse * 360f - 90f,
                        sweepAngle = 118f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = NeonPink.copy(alpha = 0.72f),
                        startAngle = -pulse * 360f + 120f,
                        sweepAngle = 76f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_archive_lock),
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = 0.96f + glow * 0.06f
                            scaleY = 0.96f + glow * 0.06f
                        }
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "ArcKey",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "本机解锁引擎启动中",
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                progress = { (0.16f + pulse * 0.84f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = NeonPink,
                trackColor = CyberPanelHigh
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "正在校验恢复环境",
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CyberBackdrop(
    modifier: Modifier = Modifier,
    scanOffset: Float? = null,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val animatedScan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "backdropScan"
    )
    val scan = scanOffset ?: animatedScan

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        CyberBackground,
                        Color(0xFF0B1020),
                        Color(0xFF13091E)
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .drawBehind {
                val grid = 28.dp.toPx()
                var y = -grid + scan * grid
                while (y < size.height + grid) {
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.035f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += grid
                }
                var x = -grid + scan * grid
                while (x < size.width + grid) {
                    drawLine(
                        color = NeonPink.copy(alpha = 0.028f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += grid
                }
                val scanY = scan * (size.height + 180.dp.toPx()) - 90.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonCyan.copy(alpha = 0.055f),
                            Color.Transparent
                        ),
                        startY = scanY - 110.dp.toPx(),
                        endY = scanY + 110.dp.toPx()
                    )
                )
                drawLine(
                    color = NeonPink.copy(alpha = 0.22f),
                    start = Offset(size.width * 0.62f, 0f),
                    end = Offset(size.width, size.height * 0.24f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = NeonCyan.copy(alpha = 0.16f),
                    start = Offset(0f, size.height * 0.78f),
                    end = Offset(size.width * 0.38f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        content()
    }
}

@Composable
private fun Header(session: CrackSession, currentArchiveName: String) {
    val taskLabel = currentArchiveName.ifBlank { "等待载入压缩包" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ArcKey",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "本机解压与授权密码恢复",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText
                )
            }
            StatusBadge(session.state)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = CyberPanel.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.22f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Text(
                    text = taskLabel,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun FileSection(
    archiveName: String,
    outputLabel: String,
    passwordBookLabel: String,
    onPickArchive: () -> Unit,
    onPickOutput: () -> Unit,
    onPickBook: () -> Unit,
    onCreateBook: () -> Unit,
    outputHint: String
) {
    Section(step = "01", title = "文件路径", accent = NeonCyan) {
        ActionRow(
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            label = "压缩包",
            value = archiveName.ifBlank { "未选择" },
            buttonText = "选择",
            onClick = onPickArchive,
            accent = NeonCyan
        )
        ActionRow(
            icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
            label = "输出目录",
            value = outputLabel.ifBlank { "未选择" },
            buttonText = "选择",
            onClick = onPickOutput,
            accent = NeonPink
        )
        if (outputHint.isNotBlank()) {
            Text(
                text = outputHint,
                style = MaterialTheme.typography.bodySmall,
                color = NeonAmber
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = NeonGreen
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("密码本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    passwordBookLabel.ifBlank { "未选择" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MutedText
                )
            }
            OutlinedButton(
                onClick = onPickBook,
                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
            ) {
                Text("选择")
            }
            OutlinedButton(
                onClick = onCreateBook,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建")
            }
        }
        AssistChip(
            onClick = {},
            label = { Text("明文 UTF-8，一行一个密码") },
            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber) }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PasswordSection(
    manualPasswords: String,
    onManualPasswordsChange: (String) -> Unit,
    digits: Boolean,
    onDigitsChange: (Boolean) -> Unit,
    lowercase: Boolean,
    onLowercaseChange: (Boolean) -> Unit,
    uppercase: Boolean,
    onUppercaseChange: (Boolean) -> Unit,
    symbols: Boolean,
    onSymbolsChange: (Boolean) -> Unit,
    customCharset: String,
    onCustomCharsetChange: (String) -> Unit,
    minLength: String,
    onMinLengthChange: (String) -> Unit,
    maxLength: String,
    onMaxLengthChange: (String) -> Unit,
    workerCount: String,
    onWorkerCountChange: (String) -> Unit,
    estimatedTotal: BigInteger
) {
    Section(step = "02", title = "密码空间", accent = NeonPink) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp),
            value = manualPasswords,
            onValueChange = onManualPasswordsChange,
            label = { Text("手动候选") },
            minLines = 3,
            maxLines = 5,
            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleLine("数字", digits, onDigitsChange)
            ToggleLine("小写", lowercase, onLowercaseChange)
            ToggleLine("大写", uppercase, onUppercaseChange)
            ToggleLine("符号", symbols, onSymbolsChange)
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = customCharset,
            onValueChange = onCustomCharsetChange,
            label = { Text("自定义字符集") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = minLength,
                onValueChange = onMinLengthChange,
                label = { Text("最小") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = maxLength,
                onValueChange = onMaxLengthChange,
                label = { Text("最大") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = workerCount,
                onValueChange = onWorkerCountChange,
                label = { Text("线程") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Text(
            text = "预计候选 $estimatedTotal",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan
        )
    }
}

@Composable
private fun SessionSection(
    session: CrackSession,
    canResume: Boolean,
    formMessage: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onResumeCheckpoint: () -> Unit
) {
    Section(step = "03", title = "破解控制台", accent = NeonGreen) {
        SessionProgress(session)
        if (formMessage.isNotBlank()) {
            Text(
                text = formMessage,
                color = NeonAmber,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = session.state != CrackState.Running && session.state != CrackState.Paused,
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color(0xFF021116)
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始")
            }
            AnimatedVisibility(visible = session.state == CrackState.Running) {
                OutlinedButton(
                    onClick = onPause,
                    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("暂停")
                }
            }
            AnimatedVisibility(visible = session.state == CrackState.Paused) {
                OutlinedButton(
                    onClick = onResume,
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("继续")
                }
            }
            OutlinedButton(
                enabled = session.state == CrackState.Running || session.state == CrackState.Paused,
                onClick = onCancel,
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("取消")
            }
        }
        AnimatedVisibility(visible = canResume && session.state != CrackState.Running) {
            OutlinedButton(
                onClick = onResumeCheckpoint,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("恢复上次 checkpoint")
            }
        }
    }
}

@Composable
private fun SessionProgress(session: CrackSession) {
    val progress = session.totalCandidates?.let { total ->
        if (total > BigInteger.ZERO && total <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
            session.attempts.toFloat() / total.toFloat()
        } else {
            null
        }
    }
    val animated by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        label = "progress"
    )
    val running = session.state == CrackState.Running
    val scanTransition = rememberInfiniteTransition(label = "progressScan")
    val scan by scanTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressScanValue"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.phaseLabel.ifBlank { "空闲" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.message.ifBlank { "等待任务参数" },
                    color = MutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = if (progress != null) "${(animated * 100).toInt()}%" else "--",
                color = if (running) NeonCyan else MutedText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(CyberPanelHigh)
        ) {
            if (progress == null && running) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    color = NeonCyan,
                    trackColor = CyberPanelHigh
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonCyan, NeonPink)
                            )
                        )
                )
            }
            if (running) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.2f)
                        .graphicsLayer {
                            translationX = (scan * 6f - 1f) * 220f
                        }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricBlock("尝试", session.attempts.toString(), Modifier.weight(1f))
            MetricBlock("速度", speedLabel(session.speedPerSecond), Modifier.weight(1f))
            MetricBlock("长度", if (session.currentLength > 0) session.currentLength.toString() else "-", Modifier.weight(1f))
        }
        if (session.successPassword.isNotBlank()) {
            Text(
                text = "解压密码：${session.successPassword}",
                color = NeonGreen,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (session.successPasswordReference.isNotBlank()) {
            Text(
                text = session.successPasswordReference,
                color = NeonGreen,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MutedText,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusBadge(state: CrackState) {
    val (text, color, icon) = when (state) {
        CrackState.Success -> Triple("完成", NeonGreen, Icons.Default.CheckCircle)
        CrackState.Failed -> Triple("失败", DangerRed, Icons.Default.ErrorOutline)
        CrackState.Canceled -> Triple("取消", MutedText, Icons.Default.Stop)
        CrackState.Paused -> Triple("暂停", NeonAmber, Icons.Default.Pause)
        CrackState.Running -> Triple("运行", NeonCyan, Icons.Default.Refresh)
        CrackState.Idle -> Triple("待机", MutedText, Icons.Default.VpnKey)
    }
    val pulseTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulseValue"
    )
    Surface(
        modifier = Modifier.drawBehind {
            if (state == CrackState.Running) {
                drawCircle(
                    color = color.copy(alpha = 0.12f * pulse),
                    radius = size.maxDimension * 0.7f,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Section(
    step: String,
    title: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = accent.copy(alpha = 0.5f),
                    start = Offset(18.dp.toPx(), 0f),
                    end = Offset(size.width - 18.dp.toPx(), 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
        shape = RoundedCornerShape(14.dp),
        color = CyberPanel.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = step,
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = accent.copy(alpha = 0.24f))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    buttonText: String,
    onClick: () -> Unit,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MutedText
            )
        }
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
        ) {
            Text(buttonText)
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = NeonCyan,
                uncheckedColor = MutedText,
                checkmarkColor = Color(0xFF021116)
            )
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ArcKeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonCyan,
            onPrimary = Color(0xFF021116),
            secondary = NeonPink,
            onSecondary = Color.White,
            tertiary = NeonGreen,
            background = CyberBackground,
            onBackground = Color(0xFFEAF7FF),
            surface = CyberPanel,
            onSurface = Color(0xFFEAF7FF),
            surfaceVariant = CyberPanelHigh,
            onSurfaceVariant = MutedText,
            outline = Color(0xFF33415F),
            outlineVariant = Color(0xFF25314A),
            error = DangerRed
        ),
        content = content
    )
}

private fun speedLabel(speed: Double): String {
    return if (speed > 0.0) {
        "${"%.1f".format(speed)}/s"
    } else {
        "--/s"
    }
}

private fun buildConfigOrNull(
    archiveUri: Uri?,
    archiveName: String,
    outputTreeUri: Uri?,
    passwordBookUri: Uri?,
    manualPasswords: String,
    charset: String,
    minLength: Int,
    maxLength: Int,
    workerCount: Int
): CrackConfig? {
    if (archiveUri == null || outputTreeUri == null || passwordBookUri == null) return null
    val normalizedCharset = BruteforceGenerator.normalizeCharset(charset)
    if (normalizedCharset.isBlank()) return null
    return CrackConfig(
        archiveUri = archiveUri.toString(),
        archiveDisplayName = archiveName.ifBlank { "archive" },
        outputTreeUri = outputTreeUri.toString(),
        passwordBookUri = passwordBookUri.toString(),
        manualPasswords = manualPasswords.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList(),
        charset = normalizedCharset,
        minLength = minLength,
        maxLength = maxLength.coerceAtLeast(minLength),
        workerCount = workerCount.coerceIn(1, 8)
    )
}

private fun startCrack(context: Context, config: CrackConfig) {
    ContextCompat.startForegroundService(
        context,
        CrackForegroundService.startIntent(context, config)
    )
}

private fun buildCharset(
    digits: Boolean,
    lowercase: Boolean,
    uppercase: Boolean,
    symbols: Boolean,
    custom: String
): String {
    val builder = StringBuilder()
    if (digits) builder.append("0123456789")
    if (lowercase) builder.append("abcdefghijklmnopqrstuvwxyz")
    if (uppercase) builder.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    if (symbols) builder.append("!@#\$%^&*()-_=+[]{};:,.?/|")
    builder.append(custom)
    return BruteforceGenerator.normalizeCharset(builder.toString())
}

private fun takePersistable(context: Context, uri: Uri, flags: Int) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }
}

private fun archiveUriFromIntent(intent: Intent?): Uri? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
        }
        else -> null
    }
}

private val CyberBackground = Color(0xFF070A12)
private val CyberPanel = Color(0xFF101827)
private val CyberPanelHigh = Color(0xFF17223A)
private val NeonCyan = Color(0xFF20E3FF)
private val NeonPink = Color(0xFFFF3DCE)
private val NeonGreen = Color(0xFF5CFF8D)
private val NeonAmber = Color(0xFFFFC857)
private val DangerRed = Color(0xFFFF5C7A)
private val MutedText = Color(0xFFA8B4CA)
