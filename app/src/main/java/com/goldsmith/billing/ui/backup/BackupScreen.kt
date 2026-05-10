package com.goldsmith.billing.ui.backup

import android.app.Activity
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed class BackupState {
    object Idle : BackupState()
    object Running : BackupState()
    data class Success(val timestamp: Long) : BackupState()
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

    private val syncManager = com.goldsmith.billing.util.DataSyncManager(context)

    fun triggerManualSync(onNeedSignIn: () -> Unit) = viewModelScope.launch {
        _backupState.value = BackupState.Running
        try {
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                onNeedSignIn()
                _backupState.value = BackupState.Idle
                return@launch
            }

            val success = syncManager.performSync()
            if (success) {
                val now = System.currentTimeMillis()
                settingsRepo.updateLastBackupTime(now)
                _backupState.value = BackupState.Success(now)
            } else {
                _backupState.value = BackupState.Error("Sync failed - check connection")
            }
        } catch (e: Exception) {
            _backupState.value = BackupState.Error(e.message ?: "Sync failed")
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
    val signedInAccount = GoogleSignIn.getLastSignedInAccount(context)

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.triggerManualSync {}
        }
    }

    fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        signInLauncher.launch(client.signInIntent)
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
                                        is BackupState.Running -> "Backing up..."
                                        is BackupState.Success -> "Completed: ${sdf.format(Date((backupState as BackupState.Success).timestamp))}"
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
                            "Account: ${signedInAccount?.email ?: "Not selected"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(12.dp))

                        GoldButton(
                            text = if (backupState is BackupState.Running) "Syncing..." else "Sync with Cloud",
                            onClick = {
                                val account = GoogleSignIn.getLastSignedInAccount(context)
                                if (account == null) launchGoogleSignIn()
                                else viewModel.triggerManualSync { launchGoogleSignIn() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backupState !is BackupState.Running,
                            icon = { Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(18.dp)) }
                        )
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
                            "Restore merges your Google Drive backup into this device. Existing newer local records are kept.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                val account = GoogleSignIn.getLastSignedInAccount(context)
                                if (account == null) launchGoogleSignIn()
                                else viewModel.triggerManualSync { launchGoogleSignIn() }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
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
