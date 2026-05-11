package com.goldsmith.billing.ui.backup

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.DriveBackupConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed class BackupState {
    object Idle : BackupState()
    data class Running(val operation: String) : BackupState()
    data class Success(val timestamp: Long, val operation: String) : BackupState()
    data class Error(val message: String) : BackupState()
}

private enum class BackupAction(val operation: String) {
    Backup("Backup"),
    Restore("Restore"),
    Sync("Sync")
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

    fun rememberBackupAccount(email: String?) = viewModelScope.launch {
        val cleanEmail = DriveBackupConfig.normalizeEmail(email)
        if (cleanEmail.isNotBlank()) settingsRepo.updateSelectedBackupEmail(cleanEmail)
    }

    fun showAccountError(message: String) {
        _backupState.value = BackupState.Error(message)
    }

    fun clearSelectedAccount() = viewModelScope.launch {
        settingsRepo.updateSelectedBackupEmail("")
    }

    fun triggerManualSync(activeAccount: GoogleSignInAccount?, onNeedSignIn: () -> Unit) =
        triggerCloudOperation(BackupAction.Sync, activeAccount, onNeedSignIn) { manager ->
            manager.performSync()
        }

    fun triggerManualSync(onNeedSignIn: () -> Unit) = triggerManualSync(null, onNeedSignIn)

    fun triggerBackup(activeAccount: GoogleSignInAccount?, onNeedSignIn: () -> Unit) =
        triggerCloudOperation(BackupAction.Backup, activeAccount, onNeedSignIn) { manager ->
            manager.performBackup()
        }

    fun triggerBackup(onNeedSignIn: () -> Unit) = triggerBackup(null, onNeedSignIn)

    fun triggerRestore(activeAccount: GoogleSignInAccount?, onNeedSignIn: () -> Unit) =
        triggerCloudOperation(BackupAction.Restore, activeAccount, onNeedSignIn) { manager ->
            manager.performRestore()
        }

    fun triggerRestore(onNeedSignIn: () -> Unit) = triggerRestore(null, onNeedSignIn)

    private fun triggerCloudOperation(
        action: BackupAction,
        activeAccount: GoogleSignInAccount?,
        onNeedSignIn: () -> Unit,
        task: suspend (com.goldsmith.billing.util.DataSyncManager) -> Boolean
    ) = viewModelScope.launch {
        _backupState.value = BackupState.Running(action.operation)
        try {
            val account = activeAccount ?: GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                onNeedSignIn()
                _backupState.value = BackupState.Idle
                return@launch
            }
            if (!GoogleSignIn.hasPermissions(account, Scope(DriveBackupConfig.SCOPE))) {
                onNeedSignIn()
                _backupState.value = BackupState.Error("Google Drive permission is required. Please choose the account and allow backup access.")
                return@launch
            }
            val accountEmail = DriveBackupConfig.resolveActiveAccountEmail(account.email, null)
            if (accountEmail.isNotBlank()) settingsRepo.updateSelectedBackupEmail(accountEmail)

            val syncManager = com.goldsmith.billing.util.DataSyncManager(context, account)
            val success = task(syncManager)
            if (success) {
                val now = System.currentTimeMillis()
                if (action != BackupAction.Restore) {
                    settingsRepo.updateLastBackupTime(now)
                    settingsRepo.updateLastBackupAccountEmail(accountEmail)
                } else {
                    settingsRepo.updateLastRestoreAccountEmail(accountEmail)
                }
                _backupState.value = BackupState.Success(now, action.operation)
            } else {
                _backupState.value = BackupState.Error(syncManager.lastErrorMessage ?: "${action.operation} failed - check Google Drive access")
            }
        } catch (e: Exception) {
            _backupState.value = BackupState.Error(e.message ?: "${action.operation} failed")
        }
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(enabled)
    }

    fun resetState() { _backupState.value = BackupState.Idle }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var activeAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    val visibleAccount = DriveBackupConfig.displayEmail(activeAccount?.email, settings.selectedBackupEmail)
    var pendingCloudAction by remember { mutableStateOf<BackupAction?>(null) }

    BackHandler { onBack() }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            pendingCloudAction = null
            activeAccount = GoogleSignIn.getLastSignedInAccount(context)
            viewModel.showAccountError("Google account selection was cancelled. Please select an account to run backup.")
            return@rememberLauncherForActivityResult
        }
        var signInError: ApiException? = null
        val pickerAccount = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
        } catch (e: ApiException) {
            signInError = e
            null
        }
        val fallbackAccount = GoogleSignIn.getLastSignedInAccount(context)
        val resolvedAccount = pickerAccount ?: fallbackAccount
        val resolvedEmail = DriveBackupConfig.resolveActiveAccountEmail(
            pickerEmail = pickerAccount?.email,
            lastSignedInEmail = fallbackAccount?.email
        )
        if (resolvedAccount != null && resolvedEmail.isNotBlank()) {
            activeAccount = resolvedAccount
            viewModel.rememberBackupAccount(resolvedEmail)
            when (pendingCloudAction) {
                BackupAction.Restore -> viewModel.triggerRestore(resolvedAccount) {}
                BackupAction.Sync -> viewModel.triggerManualSync(resolvedAccount) {}
                BackupAction.Backup -> viewModel.triggerBackup(resolvedAccount) {}
                null -> viewModel.resetState()
            }
            pendingCloudAction = null
        } else {
            pendingCloudAction = null
            val statusCode = signInError?.statusCode
            viewModel.showAccountError(
                if (statusCode != null) "Google sign-in failed. Status code: $statusCode. Please check Google Play Services and app Drive access."
                else "Google account was not selected. Please choose the account again."
            )
        }
    }

    fun launchGoogleSignIn(forceChooseAccount: Boolean = false) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveBackupConfig.SCOPE))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        if (forceChooseAccount) {
            client.signOut().addOnCompleteListener { signInLauncher.launch(client.signInIntent) }
        } else {
            signInLauncher.launch(client.signInIntent)
        }
    }

    fun runCloudAction(action: BackupAction) {
        val account = activeAccount ?: GoogleSignIn.getLastSignedInAccount(context)
        activeAccount = account
        pendingCloudAction = action
        if (account == null || !GoogleSignIn.hasPermissions(account, Scope(DriveBackupConfig.SCOPE))) {
            launchGoogleSignIn(forceChooseAccount = account != null)
            return
        }
        when (action) {
            BackupAction.Backup -> viewModel.triggerBackup(account) { launchGoogleSignIn() }
            BackupAction.Restore -> viewModel.triggerRestore(account) { launchGoogleSignIn() }
            BackupAction.Sync -> viewModel.triggerManualSync(account) { launchGoogleSignIn() }
        }
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            null,
                            tint = AuraColors.PrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "BACKUP & RESTORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer,
                            fontSize = 14.sp,
                            letterSpacing = 3.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            item {
                GlassCard(Modifier.fillMaxWidth(), goldBorder = backupState is BackupState.Success) {
                    Column(Modifier.padding(24.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Backup Status",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = AuraColors.OnSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    when (backupState) {
                                        is BackupState.Idle -> if (settings.lastBackupTime > 0)
                                            "Last: ${sdf.format(Date(settings.lastBackupTime))}"
                                        else "Never backed up"
                                        is BackupState.Running -> "${(backupState as BackupState.Running).operation} running..."
                                        is BackupState.Success -> "${(backupState as BackupState.Success).operation} completed: ${sdf.format(Date((backupState as BackupState.Success).timestamp))}"
                                        is BackupState.Error -> "Error: ${(backupState as BackupState.Error).message}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (backupState) {
                                        is BackupState.Success -> AuraColors.Primary
                                        is BackupState.Error -> AuraColors.Error
                                        else -> AuraColors.OnSurfaceVariant
                                    }
                                )
                            }

                            Box(
                                Modifier
                                    .size(52.dp)
                                    .background(
                                        when (backupState) {
                                            is BackupState.Success -> AuraColors.Primary.copy(alpha = 0.15f)
                                            is BackupState.Error -> AuraColors.Error.copy(alpha = 0.15f)
                                            else -> AuraColors.GlassWhite5
                                        },
                                        RoundedCornerShape(16.dp)
                                    )
                                    .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                when (backupState) {
                                    is BackupState.Running -> CircularProgressIndicator(
                                        color = AuraColors.PrimaryContainer,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    is BackupState.Success -> Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = AuraColors.Primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    is BackupState.Error -> Icon(
                                        Icons.Default.Error,
                                        null,
                                        tint = AuraColors.Error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    else -> Icon(
                                        Icons.Default.CloudUpload,
                                        null,
                                        tint = AuraColors.PrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            "Account: ${visibleAccount.ifBlank { "Select account to backup" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.75f)
                        )
                        Text(
                            "Storage: encrypted hidden Google Drive app backup folder",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.45f),
                            fontSize = 10.sp
                        )
                        Text(
                            "Format: Version 3 portable encrypted backup",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        Text(
                            "App ID: ${context.packageName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.35f),
                            fontSize = 10.sp
                        )
                        if (settings.lastBackupAccountEmail.isNotBlank()) {
                            Text(
                                "Last backup account: ${settings.lastBackupAccountEmail}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        GoldButton(
                            text = if (backupState is BackupState.Running) "Backing up..." else "BACKUP NOW",
                            onClick = {
                                runCloudAction(BackupAction.Backup)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backupState !is BackupState.Running,
                            icon = { Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp)) }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                runCloudAction(BackupAction.Sync)
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            enabled = backupState !is BackupState.Running,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface),
                            border = BorderStroke(1.dp, AuraColors.GlassWhite20),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("FULL MERGE SYNC", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                pendingCloudAction = null
                                launchGoogleSignIn(forceChooseAccount = true)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            enabled = backupState !is BackupState.Running
                        ) {
                            Icon(Icons.Default.ManageAccounts, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Change Google account", style = MaterialTheme.typography.labelSmall)
                        }
                        if (visibleAccount.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    pendingCloudAction = null
                                    activeAccount = null
                                    viewModel.clearSelectedAccount()
                                    viewModel.resetState()
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                enabled = backupState !is BackupState.Running
                            ) {
                                Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Forget backup account", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Auto backup toggle
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp))
                                .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                null,
                                tint = AuraColors.PrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Auto Daily Backup",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AuraColors.OnSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Runs automatically every day via WorkManager",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = viewModel::setAutoBackup,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AuraColors.OnPrimary,
                                checkedTrackColor = AuraColors.PrimaryContainer,
                                uncheckedThumbColor = AuraColors.OnSurfaceVariant,
                                uncheckedTrackColor = AuraColors.SurfaceContainerHighest
                            )
                        )
                    }
                }
            }

            // Restore
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                null,
                                tint = AuraColors.PrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Restore from Google Drive",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AuraColors.OnSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Restore reads the latest backup from the selected Google account. Existing newer local records are kept.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                runCloudAction(BackupAction.Restore)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = backupState !is BackupState.Running,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface),
                            border = BorderStroke(1.dp, AuraColors.GlassWhite20),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "RESTORE FROM DRIVE",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Security note
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = AuraColors.Primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Column {
                            Text(
                                "End-to-End Encrypted",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AuraColors.OnSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "All backups are encrypted using AES-256-GCM before upload. Your data is decryptable only on this device using your Android Keystore key.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
