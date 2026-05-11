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
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.BackupFileConfig
import com.goldsmith.billing.util.DataSyncManager
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
import javax.inject.Inject

sealed class BackupState {
    object Idle : BackupState()
    data class Running(val label: String) : BackupState()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : BackupState()
    data class Error(val message: String) : BackupState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val settings = settingsRepo.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.goldsmith.billing.data.repository.AppSettings()
        )

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState = _backupState.asStateFlow()

    fun saveBackup(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Saving backup")
        val manager = DataSyncManager(context)
        val success = manager.exportBackupToUri(uri)
        if (success) {
            context.persistBackupUri(uri, write = true)
            val now = System.currentTimeMillis()
            settingsRepo.updateBackupDocumentUri(uri.toString())
            settingsRepo.updateLastBackupTime(now)
            _backupState.value = BackupState.Success("Backup saved safely")
        } else {
            _backupState.value = BackupState.Error(manager.lastErrorMessage ?: "Backup failed")
        }
    }

    fun quickSaveBackup(onMissingLocation: () -> Unit) {
        val saved = settings.value.backupDocumentUri
        if (saved.isBlank()) {
            onMissingLocation()
            return
        }
        saveBackup(Uri.parse(saved))
    }

    fun mergeBackup(uri: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Running("Merging backup")
        val manager = DataSyncManager(context)
        val success = manager.mergeBackupFromUri(uri)
        if (success) {
            context.persistBackupUri(uri, write = false)
            settingsRepo.updateLastRestoreAccountEmail("File restore")
            _backupState.value = BackupState.Success("Backup merged with this device")
        } else {
            _backupState.value = BackupState.Error(manager.lastErrorMessage ?: "Restore failed")
        }
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(enabled)
    }

    fun resetState() {
        _backupState.value = BackupState.Idle
    }
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

    BackHandler { onBack() }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupFileConfig.MIME_TYPE)
    ) { uri ->
        if (uri != null) viewModel.saveBackup(uri)
        else viewModel.resetState()
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.mergeBackup(uri)
        else viewModel.resetState()
    }

    fun chooseBackupLocation() {
        createBackupLauncher.launch(BackupFileConfig.defaultFileName())
    }

    val autoBackupPath = remember { LocalBackupStore.backupDir(context).absolutePath }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                        Text(
                            "BACKUP VAULT",
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
            item {
                BackupHeroCard(
                    state = backupState,
                    lastBackup = settings.lastBackupTime,
                    backupUri = settings.backupDocumentUri,
                    sdf = sdf
                )
            }

            item {
                GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Save,
                            title = "Save Backup",
                            subtitle = "Creates one encrypted backup file. Choose Google Drive from the file picker if you want cloud copy."
                        )
                        GoldButton(
                            text = if (backupState is BackupState.Running) "Working..." else "Quick Save",
                            onClick = { viewModel.quickSaveBackup(::chooseBackupLocation) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backupState !is BackupState.Running,
                            icon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) }
                        )
                        OutlinedButton(
                            onClick = ::chooseBackupLocation,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = backupState !is BackupState.Running,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface),
                            border = BorderStroke(1.dp, AuraColors.GlassWhite20),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SAVE AS NEW BACKUP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Merge,
                            title = "Restore / Merge",
                            subtitle = "Select an Abu Star backup file. Existing newer records are kept and offline family invoices are merged."
                        )
                        OutlinedButton(
                            onClick = {
                                openBackupLauncher.launch(arrayOf(BackupFileConfig.MIME_TYPE, "application/json", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = backupState !is BackupState.Running,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                            border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("CHOOSE BACKUP FILE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SectionHeader(
                                icon = Icons.Default.Schedule,
                                title = "Auto Backup",
                                subtitle = "Daily backup runs around 1:00 AM and creates a new encrypted file automatically."
                            )
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
                        BackupLocationPill("Auto folder: $autoBackupPath")
                        AnimatedVisibility(settings.backupDocumentUri.isNotBlank()) {
                            BackupLocationPill(settings.backupDocumentUri)
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            icon = Icons.Default.Groups,
                            title = "Family Offline Flow",
                            subtitle = "Use unique prefixes on each family device. Save/merge the same backup file whenever devices come online."
                        )
                        FlowStep("1", "Each device bills offline with its own prefix.")
                        FlowStep("2", "When online, save backup from one device to the shared file.")
                        FlowStep("3", "Other devices choose the same file and merge.")
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun BackupHeroCard(
    state: BackupState,
    lastBackup: Long,
    backupUri: String,
    sdf: SimpleDateFormat
) {
    GlassCard(Modifier.fillMaxWidth(), elevated = true, goldBorder = state is BackupState.Success) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Data Safety", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    Text(
                        when (state) {
                            is BackupState.Idle -> if (lastBackup > 0) "Last backup ${sdf.format(Date(lastBackup))}" else "No backup saved yet"
                            is BackupState.Running -> "${state.label}..."
                            is BackupState.Success -> "${state.message} at ${sdf.format(Date(state.timestamp))}"
                            is BackupState.Error -> state.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (state) {
                            is BackupState.Success -> AuraColors.Primary
                            is BackupState.Error -> AuraColors.Error
                            else -> AuraColors.OnSurfaceVariant
                        }
                    )
                }
                Box(
                    Modifier
                        .size(54.dp)
                        .background(AuraColors.GlassWhite5, RoundedCornerShape(18.dp))
                        .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is BackupState.Running -> CircularProgressIndicator(color = AuraColors.PrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        is BackupState.Success -> Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(28.dp))
                        is BackupState.Error -> Icon(Icons.Default.Error, null, tint = AuraColors.Error, modifier = Modifier.size(28.dp))
                        else -> Icon(Icons.Default.Lock, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
            }
            BackupLocationPill(backupUri.ifBlank { "No backup file selected" })
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
                .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
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
        Modifier
            .fillMaxWidth()
            .background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.InsertDriveFile, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(16.dp))
        Text(
            value.substringAfterLast('/').takeLast(64),
            style = MaterialTheme.typography.labelSmall,
            color = AuraColors.OnSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FlowStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .background(AuraColors.PrimaryContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 10.sp)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
    }
}

private fun android.content.Context.persistBackupUri(uri: Uri, read: Boolean = true, write: Boolean) {
    val flags = (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
        (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    runCatching {
        contentResolver.takePersistableUriPermission(uri, flags)
    }
}
