package com.selfspace.lockzipas

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    private val incomingArchiveUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingArchiveUri.value = archiveUriFromIntent(intent)
        setContent {
            ArcKeyTheme {
                ArcKeyScreen(incomingArchiveUri = incomingArchiveUri.value)
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
private fun ArcKeyScreen(incomingArchiveUri: Uri?) {
    val context = LocalContext.current
    val session by CrackSessionBus.session.collectAsState()
    val requestStore = remember { CrackRequestStore(context) }
    val appPreferences = remember { AppPreferences(context) }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(session)

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
                        android.content.Intent(context, CrackForegroundService::class.java)
                    )
                }
            )
        }
    }

    val config = warningConfig
    if (config != null) {
        AlertDialog(
            onDismissRequest = { warningConfig = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
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
private fun Header(session: CrackSession) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ArcKey",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "本机解压与授权密码恢复",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(session.state)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    Section(title = "文件") {
        ActionRow(
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            label = "压缩包",
            value = archiveName.ifBlank { "未选择" },
            buttonText = "选择",
            onClick = onPickArchive
        )
        ActionRow(
            icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
            label = "输出目录",
            value = outputLabel.ifBlank { "未选择" },
            buttonText = "选择",
            onClick = onPickOutput
        )
        if (outputHint.isNotBlank()) {
            Text(
                text = outputHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("密码本", style = MaterialTheme.typography.labelLarge)
                Text(
                    passwordBookLabel.ifBlank { "未选择" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onPickBook) {
                Text("选择")
            }
            OutlinedButton(onClick = onCreateBook) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建")
            }
        }
        AssistChip(
            onClick = {},
            label = { Text("明文 UTF-8，一行一个密码") },
            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) }
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
    Section(title = "密码") {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = minLength,
                onValueChange = onMinLengthChange,
                label = { Text("最小长度") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = maxLength,
                onValueChange = onMaxLengthChange,
                label = { Text("最大长度") },
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Section(title = "会话") {
        SessionProgress(session)
        if (formMessage.isNotBlank()) {
            Text(
                text = formMessage,
                color = MaterialTheme.colorScheme.secondary,
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
                onClick = onStart
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始")
            }
            AnimatedVisibility(visible = session.state == CrackState.Running) {
                OutlinedButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("暂停")
                }
            }
            AnimatedVisibility(visible = session.state == CrackState.Paused) {
                OutlinedButton(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("继续")
                }
            }
            OutlinedButton(
                enabled = session.state == CrackState.Running || session.state == CrackState.Paused,
                onClick = onCancel
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("取消")
            }
        }
        AnimatedVisibility(visible = canResume && session.state != CrackState.Running) {
            OutlinedButton(onClick = onResumeCheckpoint) {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress == null && session.state == CrackState.Running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            val animated by animateFloatAsState(
                targetValue = (progress ?: 0f).coerceIn(0f, 1f),
                label = "progress"
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(session.phaseLabel.ifBlank { "空闲" }, fontWeight = FontWeight.SemiBold)
            Text("${session.attempts} 次")
        }
        if (session.message.isNotBlank()) {
            Text(
                text = session.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (session.successPassword.isNotBlank()) {
            Text(
                text = "解压密码：${session.successPassword}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (session.successPasswordReference.isNotBlank()) {
            Text(
                text = session.successPasswordReference,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatusBadge(state: CrackState) {
    val (text, color, icon) = when (state) {
        CrackState.Success -> Triple("完成", Color(0xFF2D6A4F), Icons.Default.CheckCircle)
        CrackState.Failed -> Triple("失败", Color(0xFF9D3B2E), Icons.Default.ErrorOutline)
        CrackState.Canceled -> Triple("取消", Color(0xFF6B6B6B), Icons.Default.Stop)
        CrackState.Paused -> Triple("暂停", Color(0xFF876900), Icons.Default.Pause)
        CrackState.Running -> Triple("运行", Color(0xFF1C5D99), Icons.Default.Refresh)
        CrackState.Idle -> Triple("待机", Color(0xFF52605D), Icons.Default.VpnKey)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onClick) {
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
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun ArcKeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF24302F),
            onPrimary = Color.White,
            secondary = Color(0xFFD9844A),
            background = Color(0xFFFAFAF8),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF202826),
            onSurfaceVariant = Color(0xFF5C6764),
            outlineVariant = Color(0xFFD9DEDA)
        ),
        content = content
    )
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
