package com.goldsmith.billing.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.adaptive.WindowSize
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.BackupCounts
import com.goldsmith.billing.util.BackupFileConfig
import com.goldsmith.billing.util.DataSyncManager
import com.goldsmith.billing.util.DriveBackupConfig
import com.goldsmith.billing.util.GoogleDriveAuth
import com.goldsmith.billing.util.GoogleDriveHelper
import com.goldsmith.billing.util.LocalBackupStore
import com.goldsmith.billing.util.AndroidSigningInfo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class BackupState {
    object Idle : BackupState()
    data class Running(val title: String, val detail: String, val progress: Float) : BackupState()
    data class Success(
        val title: String,
        val detail: String,
        val timestamp: Long = System.currentTimeMillis(),
        val counts: BackupCounts? = null
    ) : BackupState()
    data class Error(val title: String, val detail: String) : BackupState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {
    val settings = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    fun connectDriveAccount(account: GoogleSignInAccount) = viewModelScope.launch {
        val email = account.email.orEmpty()
        settingsRepo.updateSelectedBackupEmail(email)
        _backupState.value = BackupState.Running(
            "Connecting Google Drive",
            "Checking the ASD folder for $email",
            0.35f
        )
        val helper = GoogleDriveHelper(context, account)
        val latest = runCatching { helper.latestBackupModifiedTime() }.getOrNull()
        _backupState.value = BackupState.Success(
            "Google account ready",
            if (latest != null) {
                "Using $email. Found an existing ASD backup folder."
            } else {
                "Using $email. ASD folder is ready for the first backup."
            }
        )
    }

    fun saveLocalAndUploadToDrive(account: GoogleSignInAccount) = viewModelScope.launch {
        val email = account.email.orEmpty()
        settingsRepo.updateSelectedBackupEmail(email)
        settingsRepo.updateLastBackupAccountEmail(email)

        _backupState.value = BackupState.Running(
            "Creating backup",
            "Creating encrypted local backup file",
            0.25f
        )
        val manager = DataSyncManager(context, account)
        val localFile = manager.createLocalBackupFile()
        if (localFile == null) {
            _backupState.value = BackupState.Error("Backup failed", manager.lastErrorMessage ?: "Local backup failed")
            return@launch
        }

        _backupState.value = BackupState.Running(
            "Uploading backup",
            "Uploading ${localFile.name} to Google Drive > ASD",
            0.72f
        )
        val uploaded = manager.uploadLocalBackupToDrive(localFile)
        val now = System.currentTimeMillis()
        settingsRepo.updateLastBackupTime(now)

        _backupState.value = if (uploaded) {
            BackupState.Success(
                "Backup completed",
                "Saved locally and uploaded to Google Drive as ${DriveBackupConfig.REMOTE_FILE}",
                counts = manager.lastBackupCounts
            )
        } else {
            BackupState.Success(
                "Local backup completed",
                manager.lastErrorMessage ?: "Saved locally. Google Drive upload can retry later.",
                counts = manager.lastBackupCounts
            )
        }
    }

    fun restoreFromDrive(account: GoogleSignInAccount) = viewModelScope.launch {
        val email = account.email.orEmpty()
        settingsRepo.updateSelectedBackupEmail(email)
        settingsRepo.updateLastRestoreAccountEmail(email)

        _backupState.value = BackupState.Running(
            "Opening Drive backup",
            "Checking Google Drive > ASD for the latest backup",
            0.30f
        )
        val manager = DataSyncManager(context, account)

        _backupState.value = BackupState.Running(
            "Restoring data",
            "Downloading and merging the encrypted backup",
            0.70f
        )
        val success = manager.performRestore()
        _backupState.value = if (success) {
            BackupState.Success(
                "Restore completed",
                "Data restored from $email",
                counts = manager.lastBackupCounts
            )
        } else {
            BackupState.Error("Restore failed", manager.lastErrorMessage ?: "No backup found in Google Drive > ASD")
        }
    }

    fun mergeBackup(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Restoring file", "Reading selected backup file", 0.45f)
        val manager = DataSyncManager(context)
        val success = manager.mergeBackupFromUri(uri)
        _backupState.value = if (success) {
            BackupState.Success("File restore completed", "Backup merged with this device", counts = manager.lastBackupCounts)
        } else {
            BackupState.Error("File restore failed", manager.lastErrorMessage ?: "Restore failed")
        }
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(enabled)
    }

    fun testLatestAutoBackup(file: File?) = viewModelScope.launch {
        if (file == null) {
            _backupState.value = BackupState.Error("No local backup", "Create a backup first.")
            return@launch
        }
        _backupState.value = BackupState.Running("Testing backup", "Decrypting latest local backup", 0.55f)
        val manager = DataSyncManager(context)
        val valid = manager.validateBackupFile(file)
        _backupState.value = if (valid) {
            BackupState.Success("Backup test passed", "Latest local backup is readable.")
        } else {
            BackupState.Error("Backup test failed", manager.lastErrorMessage ?: "Backup file could not be read.")
        }
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }

    fun accountSelectionFailed(detail: String? = null) {
        val sha1 = AndroidSigningInfo.sha1(context)
        val configHint = "Google Cloud Android OAuth needed: package ${context.packageName}" +
            if (sha1.isNotBlank()) ", SHA-1 $sha1." else "."
        _backupState.value = BackupState.Error(
            "Google account not selected",
            listOfNotNull(
                detail ?: "The Google account picker did not return an account. Check Google Play Services and try Select account again.",
                configHint
            ).joinToString("\n")
        )
    }

    fun drivePermissionMissing() {
        _backupState.value = BackupState.Error(
            "Drive permission needed",
            "Please allow Google Drive backup access for the selected account."
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    windowSize: WindowSize = WindowSize.COMPACT,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.backupState.collectAsState()
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var latestLocal by remember { mutableStateOf(LocalBackupStore.latestBackupFile(context)) }
    var pendingAction by remember { mutableStateOf<DriveAction?>(null) }

    fun refreshLocal() {
        latestLocal = LocalBackupStore.latestBackupFile(context)
    }

    LaunchedEffect(state) {
        if (state is BackupState.Success) refreshLocal()
    }

    BackHandler { onBack() }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.mergeBackup(uri) else viewModel.resetState()
    }

    val accountClient = remember { GoogleSignIn.getClient(context, GoogleDriveAuth.accountSignInOptions()) }
    val driveClient = remember { GoogleSignIn.getClient(context, GoogleDriveAuth.driveSignInOptions()) }

    fun perform(action: DriveAction, account: GoogleSignInAccount) {
        when (action) {
            DriveAction.Connect -> viewModel.connectDriveAccount(account)
            DriveAction.Backup -> viewModel.saveLocalAndUploadToDrive(account)
            DriveAction.Restore -> viewModel.restoreFromDrive(account)
        }
    }

    val drivePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleDriveAuth.accountFromIntent(result.data)
        val action = pendingAction
        if (account == null) {
            viewModel.accountSelectionFailed(GoogleDriveAuth.failureMessageFromIntent(result.data))
        } else if (action != null && GoogleDriveAuth.hasDrivePermission(account)) {
            perform(action, account)
        } else {
            viewModel.drivePermissionMissing()
        }
        pendingAction = null
    }

    fun continueWithAccount(action: DriveAction, account: GoogleSignInAccount) {
        if (GoogleDriveAuth.hasDrivePermission(account)) {
            perform(action, account)
            pendingAction = null
        } else {
            drivePermissionLauncher.launch(driveClient.signInIntent)
        }
    }

    val accountSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleDriveAuth.accountFromIntent(result.data)
        val action = pendingAction
        if (account != null && action != null) {
            continueWithAccount(action, account)
        } else {
            viewModel.accountSelectionFailed(GoogleDriveAuth.failureMessageFromIntent(result.data))
            pendingAction = null
        }
    }

    fun runDriveAction(action: DriveAction, forcePicker: Boolean = false) {
        pendingAction = action
        val selectedEmail = DriveBackupConfig.normalizeEmail(settings.selectedBackupEmail)
        val cached = GoogleSignIn.getLastSignedInAccount(context)
        val requiresSavedAccount = action != DriveAction.Connect
        val hasSavedAccount = selectedEmail.isNotBlank()
        val canUseCached = GoogleDriveAuth.accountEmailMatches(cached, selectedEmail) &&
            (!requiresSavedAccount || hasSavedAccount) &&
            !forcePicker

        if (canUseCached) {
            continueWithAccount(action, cached!!)
            return
        }

        val silentTask = driveClient.silentSignIn()
        if (!forcePicker && silentTask.isSuccessful) {
            val account = silentTask.result
            if (GoogleDriveAuth.accountEmailMatches(account, selectedEmail) && (!requiresSavedAccount || hasSavedAccount)) {
                continueWithAccount(action, account!!)
                return
            }
        }

        silentTask.addOnCompleteListener { task ->
            val account = runCatching { task.result }.getOrNull()
            val canUseSilent = task.isSuccessful &&
                GoogleDriveAuth.accountEmailMatches(account, selectedEmail) &&
                (!requiresSavedAccount || hasSavedAccount) &&
                !forcePicker
            if (canUseSilent) {
                continueWithAccount(action, account!!)
            } else {
                if (forcePicker) {
                    accountClient.signOut().addOnCompleteListener {
                        accountSignInLauncher.launch(accountClient.signInIntent)
                    }
                } else {
                    accountSignInLauncher.launch(accountClient.signInIntent)
                }
            }
        }
    }

    val activeEmail = settings.selectedBackupEmail
    val maxWidth = if (windowSize == WindowSize.COMPACT) Modifier.fillMaxWidth() else Modifier.widthIn(max = 820.dp).fillMaxWidth()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text("BACKUP & RESTORE", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AuraColors.PrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.92f))
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = maxWidth,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { BackupStatusCard(state, settings, activeEmail, latestLocal, sdf) }
                item {
                    AccountCard(
                        email = activeEmail,
                        enabled = state !is BackupState.Running,
                        onConnect = { runDriveAction(DriveAction.Connect) },
                        onChange = { runDriveAction(DriveAction.Connect, forcePicker = true) }
                    )
                }
                item {
                    ActionCard(
                        title = "Create backup",
                        subtitle = "Creates an encrypted local backup, then uploads it to Google Drive > ASD using the selected account.",
                        primaryText = "Backup now",
                        icon = Icons.Default.CloudUpload,
                        enabled = state !is BackupState.Running,
                        onPrimary = { runDriveAction(DriveAction.Backup) }
                    )
                }
                item {
                    ActionCard(
                        title = "Restore backup",
                        subtitle = "Opens Google Drive > ASD for the selected account and restores the encrypted backup file.",
                        primaryText = "Restore from Drive",
                        icon = Icons.Default.CloudDownload,
                        enabled = state !is BackupState.Running,
                        onPrimary = { runDriveAction(DriveAction.Restore) },
                        secondaryText = "Restore from file",
                        secondaryIcon = Icons.Default.FolderOpen,
                        onSecondary = { openBackupLauncher.launch(arrayOf(BackupFileConfig.MIME_TYPE, "application/json", "*/*")) }
                    )
                }
                item {
                    ScheduleCard(
                        enabled = settings.autoBackupEnabled,
                        localPath = LocalBackupStore.backupDir(context).absolutePath,
                        latest = latestLocal,
                        onEnabledChange = viewModel::setAutoBackup,
                        onTest = { viewModel.testLatestAutoBackup(latestLocal) },
                        onRefresh = ::refreshLocal
                    )
                }
                item { Spacer(Modifier.height(36.dp)) }
            }
        }
    }
}

private enum class DriveAction { Connect, Backup, Restore }

@Composable
private fun BackupStatusCard(
    state: BackupState,
    settings: AppSettings,
    email: String,
    latestLocal: File?,
    sdf: SimpleDateFormat
) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true, goldBorder = state is BackupState.Success) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Data vault", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                    Text(
                        when (state) {
                            BackupState.Idle -> if (settings.lastBackupTime > 0) {
                                "Last backup ${sdf.format(Date(settings.lastBackupTime))}"
                            } else {
                                "No backup created yet"
                            }
                            is BackupState.Running -> state.title
                            is BackupState.Success -> state.title
                            is BackupState.Error -> state.title
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (state) {
                            is BackupState.Error -> AuraColors.Error
                            is BackupState.Success -> AuraColors.Primary
                            else -> AuraColors.OnSurfaceVariant
                        }
                    )
                }
                StatusIcon(state)
            }
            if (state is BackupState.Running) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = AuraColors.PrimaryContainer,
                    trackColor = AuraColors.GlassWhite10
                )
            }
            Text(
                when (state) {
                    BackupState.Idle -> buildString {
                        append(if (email.isBlank()) "Select Google account before backup or restore." else "Selected account: $email")
                        latestLocal?.let { append("\nLatest local: ${it.name}") }
                    }
                    is BackupState.Running -> state.detail
                    is BackupState.Success -> buildString {
                        append(state.detail)
                        state.counts?.let { append("\n${it.summary()}") }
                    }
                    is BackupState.Error -> state.detail
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AuraColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusIcon(state: BackupState) {
    Box(
        Modifier.size(52.dp)
            .background(AuraColors.GlassWhite5, RoundedCornerShape(16.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is BackupState.Running -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AuraColors.PrimaryContainer)
            is BackupState.Success -> Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(28.dp))
            is BackupState.Error -> Icon(Icons.Default.Error, null, tint = AuraColors.Error, modifier = Modifier.size(28.dp))
            BackupState.Idle -> Icon(Icons.Default.Security, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun AccountCard(email: String, enabled: Boolean, onConnect: () -> Unit, onChange: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AccountCircle, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("Google Drive account", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(if (email.isBlank()) "No account selected" else email, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton(
                    text = if (email.isBlank()) "Select account" else "Check account",
                    onClick = onConnect,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(18.dp)) }
                )
                OutlinedButton(
                    onClick = onChange,
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Change", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    primaryText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onSecondary: (() -> Unit)? = null
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                }
            }
            GoldButton(primaryText, onClick = onPrimary, enabled = enabled, modifier = Modifier.fillMaxWidth(), icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) })
            if (secondaryText != null && onSecondary != null && secondaryIcon != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer)
                ) {
                    Icon(secondaryIcon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(secondaryText.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    enabled: Boolean,
    localPath: String,
    latest: File?,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Schedule, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Automatic backup", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                        Text("Runs daily at 1:00 AM", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AuraColors.OnPrimary,
                        checkedTrackColor = AuraColors.PrimaryContainer
                    )
                )
            }
            InfoPill("Local folder", localPath)
            InfoPill(
                "Latest local backup",
                latest?.let { "${it.name} • ${formatBytes(it.length())}" } ?: "No local backup yet"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = latest != null,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer)
                ) {
                    Icon(Icons.Default.FactCheck, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val icon = if (value == "No local backup yet") Icons.Default.CloudOff else Icons.Default.FolderOpen
        Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(17.dp))
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, fontSize = 10.sp)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun android.content.Context.persistBackupUri(uri: Uri, read: Boolean = true, write: Boolean) {
    val flags = (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
        (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
}
