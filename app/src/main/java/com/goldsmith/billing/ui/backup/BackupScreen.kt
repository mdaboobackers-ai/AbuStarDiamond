package com.goldsmith.billing.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.BackupFileConfig
import com.goldsmith.billing.util.BackupCounts
import com.goldsmith.billing.util.DataSyncManager
import com.goldsmith.billing.util.DriveBackupConfig
import com.goldsmith.billing.util.LocalBackupStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import javax.inject.Inject

sealed class BackupState {
    object Idle : BackupState()
    data class Running(val label: String) : BackupState()
    data class Success(
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val counts: BackupCounts? = null
    ) : BackupState()
    data class Error(val message: String) : BackupState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val settings = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.goldsmith.billing.data.repository.AppSettings())

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    // ── Local + Drive backup (WhatsApp style) ──────────────────────────────
    fun saveLocalAndUploadToDrive(account: GoogleSignInAccount) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Saving local backup…")
        val manager = DataSyncManager(context, account)

        // Step 1: Always create local file first
        val file = manager.createLocalBackupFile()
        if (file == null) {
            _backupState.value = BackupState.Error(manager.lastErrorMessage ?: "Local backup failed")
            return@launch
        }

        // Step 2: Upload same file to Drive ASD folder
        _backupState.value = BackupState.Running("Uploading to Google Drive (ASD folder)…")
        val uploaded = manager.uploadLocalBackupToDrive(file)

        val accountDisplay = account.email.orEmpty()
        settingsRepo.updateLastBackupAccountEmail(accountDisplay)
        settingsRepo.updateLastBackupTime(System.currentTimeMillis())

        _backupState.value = if (uploaded) {
            BackupState.Success(
                "Local backup saved + uploaded to Drive\nAccount: $accountDisplay",
                counts = manager.lastBackupCounts
            )
        } else {
            // Local succeeded even if Drive failed
            BackupState.Success(
                "Local backup saved (Drive upload failed — will retry next auto-backup)\nAccount: $accountDisplay",
                counts = manager.lastBackupCounts
            )
        }
    }

    fun saveLocalBackup() = viewModelScope.launch {
        _backupState.value = BackupState.Running("Saving local backup…")
        val manager = DataSyncManager(context)
        val file = manager.createLocalBackupFile()
        if (file != null) {
            settingsRepo.updateLastBackupTime(System.currentTimeMillis())
            _backupState.value = BackupState.Success(
                "Local backup saved: ${file.name}", counts = manager.lastBackupCounts
            )
        } else {
            _backupState.value = BackupState.Error(manager.lastErrorMessage ?: "Local backup failed")
        }
    }

    fun saveBackup(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Saving backup")
        val manager = DataSyncManager(context)
        val now = System.currentTimeMillis()
        val success = manager.exportBackupToUri(uri, now)
        if (success) {
            context.persistBackupUri(uri, write = true)
            settingsRepo.updateBackupDocumentUri(uri.toString())
            settingsRepo.updateLastBackupTime(now)
            _backupState.value = BackupState.Success("Backup saved safely", counts = manager.lastBackupCounts)
        } else {
            _backupState.value = BackupState.Error(manager.lastErrorMessage ?: "Backup failed")
        }
    }

    // Restore from Drive on new install
    fun restoreFromDrive(account: GoogleSignInAccount) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Looking for backup in Google Drive (ASD folder)…")
        val manager = DataSyncManager(context, account)
        val success = manager.performRestore()
        val accountDisplay = account.email.orEmpty()
        settingsRepo.updateLastRestoreAccountEmail(accountDisplay)
        _backupState.value = if (success) {
            BackupState.Success(
                "Data restored from Drive\nAccount: $accountDisplay",
                counts = manager.lastBackupCounts
            )
        } else {
            BackupState.Error(manager.lastErrorMessage ?: "No backup found in Drive for this account")
        }
    }

    fun mergeBackup(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Merging backup")
        val manager = DataSyncManager(context)
        val success = manager.mergeBackupFromUri(uri)
        if (success) {
            context.persistBackupUri(uri, write = false)
            settingsRepo.updateLastRestoreAccountEmail("File restore")
            _backupState.value = BackupState.Success(
                "Backup merged with this device", counts = manager.lastBackupCounts
            )
        } else {
            _backupState.value = BackupState.Error(manager.lastErrorMessage ?: "Restore failed")
        }
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(enabled)
    }

    fun testLatestAutoBackup(file: File?) = viewModelScope.launch {
        if (file == null) {
            _backupState.value = BackupState.Error("No auto backup file found yet")
            return@launch
        }
        _backupState.value = BackupState.Running("Testing backup")
        val manager = DataSyncManager(context)
        val valid = manager.validateBackupFile(file)
        _backupState.value = if (valid)
            BackupState.Success("Test restore passed. Backup file is readable")
        else
            BackupState.Error(manager.lastErrorMessage ?: "Backup test failed")
    }

    fun resetState() { _backupState.value = BackupState.Idle }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context      = LocalContext.current
    val settings     by viewModel.settings.collectAsState()
    val backupState  by viewModel.backupState.collectAsState()
    val sdf          = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var latestAuto   by remember { mutableStateOf(LocalBackupStore.latestBackupFile(context)) }
    fun refreshAuto() { latestAuto = LocalBackupStore.latestBackupFile(context) }

    BackHandler { onBack() }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFileConfig.MIME_TYPE)
    ) { uri -> if (uri != null) viewModel.saveBackup(uri) else viewModel.resetState(); refreshAuto() }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.mergeBackup(uri) else viewModel.resetState() }

    fun driveSignInOptions(forRestore: Boolean) = GoogleSignInOptions
        .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveBackupConfig.SCOPE))
        .build()

    fun driveClient(forRestore: Boolean) =
        GoogleSignIn.getClient(context, driveSignInOptions(forRestore))

    var pendingAction by remember { mutableStateOf<String?>(null) } // "backup" | "restore"

    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).result
        }.getOrNull()
        if (account != null) {
            when (pendingAction) {
                "backup"  -> { viewModel.saveLocalAndUploadToDrive(account); refreshAuto() }
                "restore" -> viewModel.restoreFromDrive(account)
            }
        } else {
            viewModel.resetState()
        }
        pendingAction = null
    }

    fun launchDrive(action: String) {
        pendingAction = action
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveBackupConfig.SCOPE))) {
            when (action) {
                "backup"  -> { viewModel.saveLocalAndUploadToDrive(account); refreshAuto() }
                "restore" -> viewModel.restoreFromDrive(account)
            }
        } else {
            driveSignInLauncher.launch(driveClient(action == "restore").signInIntent)
        }
    }

    val autoBackupPath = remember { LocalBackupStore.backupDir(context).absolutePath }
    // Show the currently linked Drive account email
    val driveAccountEmail = settings.lastBackupAccountEmail.ifBlank {
        GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty()
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                        Text("BACKUP VAULT", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 3.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Hero status card ──────────────────────────────────────────
            item {
                BackupHeroCard(state = backupState, lastBackup = settings.lastBackupTime,
                    driveEmail = driveAccountEmail, sdf = sdf)
            }

            // ── Save Backup section ───────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Save,
                            title = "Save Backup",
                            subtitle = "Like WhatsApp: saves an encrypted file on your phone AND uploads to your Google Drive (ASD folder)."
                        )
                        // Primary: local + drive together
                        GoldButton(
                            text = if (backupState is BackupState.Running) "Working…" else "Backup Now (Local + Drive)",
                            onClick = { launchDrive("backup") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backupState !is BackupState.Running,
                            icon = { Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp)) }
                        )
                        // Secondary: local only
                        OutlinedButton(
                            onClick = { viewModel.saveLocalBackup(); refreshAuto() },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            enabled = backupState !is BackupState.Running,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                            border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("LOCAL ONLY", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                        // Show linked account
                        if (driveAccountEmail.isNotBlank()) {
                            DriveAccountPill(email = driveAccountEmail)
                        }
                    }
                }
            }

            // ── Restore section ───────────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Default.CloudDownload,
                            title = "Restore from Google Drive",
                            subtitle = "On a new phone: sign in with your Google account and tap Restore. All data in the ASD folder will be recovered."
                        )
                        GoldButton(
                            text = "Restore from Drive",
                            onClick = { launchDrive("restore") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backupState !is BackupState.Running,
                            icon = { Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp)) }
                        )
                        OutlinedButton(
                            onClick = { openBackupLauncher.launch(arrayOf(BackupFileConfig.MIME_TYPE, "application/json", "*/*")) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            enabled = backupState !is BackupState.Running,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                            border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("RESTORE FROM FILE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            // ── Auto backup & health ──────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            SectionHeader(
                                icon = Icons.Default.Schedule,
                                title = "Auto Backup",
                                subtitle = "Runs every day at 1:15 AM. Saves local + uploads to Drive if signed in."
                            )
                            Switch(
                                checked = settings.autoBackupEnabled,
                                onCheckedChange = viewModel::setAutoBackup,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor  = AuraColors.OnPrimary,
                                    checkedTrackColor  = AuraColors.PrimaryContainer,
                                    uncheckedThumbColor = AuraColors.OnSurfaceVariant,
                                    uncheckedTrackColor = AuraColors.SurfaceContainerHighest
                                )
                            )
                        }
                        BackupLocationPill("Local folder: $autoBackupPath")
                        BackupHealthPanel(
                            latestFile = latestAuto,
                            onTestRestore = { viewModel.testLatestAutoBackup(latestAuto) },
                            onRefresh = ::refreshAuto,
                            enabled = backupState !is BackupState.Running
                        )
                    }
                }
            }

            // ── Drive protection note ─────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Security,
                            title = "Drive Folder Protection",
                            subtitle = "Backup files are stored in a folder called ASD inside your Google Drive. " +
                                "To prevent accidental deletion, open Google Drive → ASD folder → ⋮ → " +
                                "Add to Starred. Starred folders are harder to accidentally delete."
                        )
                        FlowStep("1", "Open Google Drive on your phone or browser.")
                        FlowStep("2", "Find the folder named ASD.")
                        FlowStep("3", "Long-press or tap ⋮ → Star it to protect from accidental delete.")
                        FlowStep("4", "Backup files inside are encrypted — unreadable without the app PIN.")
                    }
                }
            }

            // ── Family sync note ──────────────────────────────────────────
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Groups,
                            title = "Family Device Sync",
                            subtitle = "Each family member uses their own Gmail. Here is the recommended sync flow."
                        )
                        FlowStep("1", "Each device bills offline with its own prefix (F, Y, B).")
                        FlowStep("2", "One member backs up to their Drive (ASD folder).")
                        FlowStep("3", "Other members download that backup file from WhatsApp/email and tap 'Restore from File'.")
                        FlowStep("4", "All invoices merge safely — no duplicates, no data loss.")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "💡 Tip: Share the backup file (.asdb) via WhatsApp to family members whenever you want to sync. They tap 'Restore from File' and their data updates instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.PrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun DriveAccountPill(email: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(AuraColors.PrimaryContainer.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.AccountCircle, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(16.dp))
        Text(
            "Drive account: $email",
            style = MaterialTheme.typography.labelSmall,
            color = AuraColors.PrimaryContainer,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun BackupHealthPanel(latestFile: File?, onTestRestore: () -> Unit, onRefresh: () -> Unit, enabled: Boolean) {
    val stale = LocalBackupStore.isStale(latestFile)
    Column(
        Modifier.fillMaxWidth()
            .background(
                if (stale) AuraColors.Error.copy(alpha = 0.08f) else AuraColors.Primary.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp,
                if (stale) AuraColors.Error.copy(alpha = 0.25f) else AuraColors.Primary.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Backup Health", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
            Icon(if (stale) Icons.Default.Warning else Icons.Default.Verified, null,
                tint = if (stale) AuraColors.Error else AuraColors.Primary, modifier = Modifier.size(20.dp))
        }
        Text(latestFile?.name ?: "No auto backup file found yet",
            style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, fontSize = 11.sp)
        Text(
            if (latestFile != null)
                "Size ${formatBytes(latestFile.length())} • ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(latestFile.lastModified()))}"
            else "Auto backup will create the first file at 1:15 AM.",
            style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.72f)
        )
        if (stale) Text("Warning: no fresh auto backup in the last 2 days.", style = MaterialTheme.typography.bodyMedium, color = AuraColors.Error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onTestRestore, enabled = enabled && latestFile != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FactCheck, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("TEST RESTORE", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
            }
            TextButton(onClick = onRefresh, enabled = enabled) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L         -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else                   -> "$bytes B"
}

@Composable
private fun BackupHeroCard(state: BackupState, lastBackup: Long, driveEmail: String, sdf: SimpleDateFormat) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true, goldBorder = state is BackupState.Success) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Data Safety", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    Text(
                        when (state) {
                            is BackupState.Idle    -> if (lastBackup > 0) "Last backup ${sdf.format(Date(lastBackup))}" else "No backup saved yet"
                            is BackupState.Running -> "${state.label}…"
                            is BackupState.Success -> buildString {
                                append("${state.message} at ${sdf.format(Date(state.timestamp))}")
                                state.counts?.let { append("\n${it.summary()}") }
                            }
                            is BackupState.Error   -> state.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (state) {
                            is BackupState.Success -> AuraColors.Primary
                            is BackupState.Error   -> AuraColors.Error
                            else                   -> AuraColors.OnSurfaceVariant
                        }
                    )
                    if (driveEmail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Drive: $driveEmail", style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer, fontSize = 10.sp)
                    }
                }
                Box(Modifier.size(54.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(18.dp))
                    .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    when (state) {
                        is BackupState.Running -> CircularProgressIndicator(color = AuraColors.PrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        is BackupState.Success -> Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(28.dp))
                        is BackupState.Error   -> Icon(Icons.Default.Error, null, tint = AuraColors.Error, modifier = Modifier.size(28.dp))
                        else                   -> Icon(Icons.Default.Lock, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun BackupLocationPill(value: String) {
    Row(
        Modifier.fillMaxWidth().background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.InsertDriveFile, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(16.dp))
        Text(value.substringAfterLast('/').takeLast(64), style = MaterialTheme.typography.labelSmall,
            color = AuraColors.OnSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun FlowStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).background(AuraColors.PrimaryContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 10.sp)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
    }
}

private fun android.content.Context.persistBackupUri(uri: Uri, read: Boolean = true, write: Boolean) {
    val flags = (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
        (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
}
