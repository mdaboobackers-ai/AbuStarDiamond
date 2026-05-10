package com.goldsmith.billing.ui.settings

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.goldsmith.billing.BuildConfig
import com.goldsmith.billing.R
import com.goldsmith.billing.data.dao.CompanyProfileDao
import com.goldsmith.billing.data.model.CompanyProfile
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.security.KeystoreManager
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.AppIconManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val companyProfileDao: CompanyProfileDao,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    val settings = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val profile = companyProfileDao.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateTheme(dark: Boolean) = viewModelScope.launch { settingsRepo.updateTheme(dark) }
    fun updateLanguage(lang: String) = viewModelScope.launch { settingsRepo.updateLanguage(lang) }
    fun updateGstPercent(pct: Double) = viewModelScope.launch { settingsRepo.updateGstPercent(pct) }
    fun updateUserPrefix(prefix: String) = viewModelScope.launch { settingsRepo.updateUserPrefix(prefix) }
    fun updateGoldRates(r24: Double, r22: Double, r20: Double, r18: Double) = viewModelScope.launch {
        settingsRepo.updateGoldRatesManual(r24, r22, r20, r18)
    }
    fun saveProfile(profile: CompanyProfile) = viewModelScope.launch {
        companyProfileDao.upsertProfile(profile.copy(updatedAt = Date()))
    }
    fun updatePin(newPin: String) { keystoreManager.savePin(newPin) }
    fun verifyPin(pin: String) = keystoreManager.verifyPin(pin)
    fun setBiometricEnabled(enabled: Boolean) = keystoreManager.setBiometricEnabled(enabled)
    fun isBiometricEnabled() = keystoreManager.isBiometricEnabled()
    fun updateAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(enabled)
    }
    fun selectIcon(iconType: String) = viewModelScope.launch {
        settingsRepo.updateSelectedIcon(iconType)
    }
    fun setCustomIconUri(uri: String) = viewModelScope.launch {
        settingsRepo.updateCustomIconUri(uri)
        settingsRepo.updateSelectedIcon(AppIconManager.ICON_CUSTOM)
    }
    fun updateInactivityLock(secs: Int) = viewModelScope.launch {
        settingsRepo.updateInactivityLockSecs(secs)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onImport: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showGstDialog by remember { mutableStateOf(false) }
    var showPrefixDialog by remember { mutableStateOf(false) }
    var showBiometricConfirm by remember { mutableStateOf<Boolean?>(null) }
    var biometricEnabled by remember { mutableStateOf(viewModel.isBiometricEnabled()) }
    var showBackupInfo by remember { mutableStateOf(false) }

    // Gallery launcher for custom icon
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setCustomIconUri(it.toString())
            AppIconManager.switchIcon(context, AppIconManager.ICON_CUSTOM)
        }
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraColors.PrimaryContainer,
                        fontSize = 16.sp,
                        letterSpacing = 3.sp
                    )
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

            // ── App Icon Chooser ──────────────────────────────────────────
            item {
                SettingsSection("App Icon") {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Choose your app launcher icon",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Option 1: Abu Star Diamonds Logo
                            IconOption(
                                label = "Abu Star\nLogo",
                                selected = settings.selectedIcon == AppIconManager.ICON_DEFAULT,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.selectIcon(AppIconManager.ICON_DEFAULT)
                                    AppIconManager.switchIcon(context, AppIconManager.ICON_DEFAULT)
                                }
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.abu_star_logo),
                                    contentDescription = "Abu Star Logo",
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Option 2: Diamond Icon
                            IconOption(
                                label = "Diamond\nIcon",
                                selected = settings.selectedIcon == AppIconManager.ICON_DIAMOND,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.selectIcon(AppIconManager.ICON_DIAMOND)
                                    AppIconManager.switchIcon(context, AppIconManager.ICON_DIAMOND)
                                }
                            ) {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    AuraColors.PrimaryContainer.copy(alpha = 0.3f),
                                                    AuraColors.Background
                                                )
                                            ),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Diamond,
                                        null,
                                        tint = AuraColors.PrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Option 3: Custom from Gallery
                            IconOption(
                                label = "Custom\nImage",
                                selected = settings.selectedIcon == AppIconManager.ICON_CUSTOM,
                                modifier = Modifier.weight(1f),
                                onClick = { galleryLauncher.launch("image/*") }
                            ) {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AuraColors.GlassWhite5)
                                        .border(
                                            1.dp,
                                            if (settings.selectedIcon == AppIconManager.ICON_CUSTOM)
                                                AuraColors.PrimaryContainer
                                            else AuraColors.GlassWhite20,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (settings.customIconUri.isNotEmpty() &&
                                        settings.selectedIcon == AppIconManager.ICON_CUSTOM) {
                                        AsyncImage(
                                            model = settings.customIconUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AddPhotoAlternate,
                                                null,
                                                tint = AuraColors.OnSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                "Gallery",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AuraColors.OnSurfaceVariant,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        // Hint
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Icon change takes effect after relaunching the app",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // ── Company Profile ────────────────────────────────────────────
            item {
                SettingsSection(stringResource(R.string.company_profile)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Company logo display
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AuraColors.SurfaceContainerHighest)
                                .border(
                                    2.dp,
                                    AuraColors.PrimaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                profile?.logoUri?.isNotEmpty() == true -> {
                                    AsyncImage(
                                        model = profile!!.logoUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                else -> {
                                    // Default: show Abu Star logo
                                    coil.compose.AsyncImage(
                                        model = R.drawable.abu_star_logo,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                profile?.companyName?.ifEmpty { "Abu Star Diamonds" }
                                    ?: "Abu Star Diamonds",
                                style = MaterialTheme.typography.headlineSmall,
                                color = AuraColors.OnSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                profile?.gstNumber?.ifEmpty { "GST: Not set" }
                                    ?: "GST: Not set",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraColors.OnSurfaceVariant
                            )
                            Text(
                                "Trust · Purity · Elegance",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.PrimaryContainer.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer)
                        }
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.data_management)) {
                    SettingsItem(
                        icon = Icons.Default.UploadFile,
                        title = stringResource(R.string.bulk_import),
                        subtitle = stringResource(R.string.bulk_import_subtitle),
                        onClick = onImport
                    )
                }
            }

            // ── Appearance ────────────────────────────────────────────────
            item {
                SettingsSection(stringResource(R.string.appearance)) {
                    SettingsToggle(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.dark_theme),
                        subtitle = if (settings.isDarkTheme) "Aura Lumina Dark" else stringResource(R.string.light_mode),
                        checked = settings.isDarkTheme,
                        onChecked = viewModel::updateTheme
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.language),
                        subtitle = if (settings.language == "ta") "தமிழ் (Tamil)" else "English",
                        onClick = {
                            viewModel.updateLanguage(if (settings.language == "en") "ta" else "en")
                            (context as? Activity)?.recreate()
                        },
                        trailing = {
                            Box(
                                Modifier
                                    .background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp))
                                    .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    if (settings.language == "ta") "தமிழ்" else "EN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraColors.PrimaryContainer
                                )
                            }
                        }
                    )
                }
            }

            // ── Gold Rates ────────────────────────────────────────────────
            item {
                SettingsSection(stringResource(R.string.gold_rates)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.current_rates_per_gram),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AuraColors.OnSurface,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(onClick = { showRateDialog = true }) {
                                Text(stringResource(R.string.edit), color = AuraColors.PrimaryContainer,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RateChip("24K", settings.goldRate24K, Modifier.weight(1f))
                            RateChip("22K", settings.goldRate22K, Modifier.weight(1f))
                        }
                    }
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Percent,
                        title = stringResource(R.string.gst_percent),
                        subtitle = "${settings.gstPercent}%",
                        onClick = { showGstDialog = true }
                    )
                }
            }

            // ── Multi-User prefix ──────────────────────────────────────────
            item {
                SettingsSection(stringResource(R.string.identity)) {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.staff_prefix),
                        subtitle = "${stringResource(R.string.invoice_prefix)}: '${settings.userPrefix}'",
                        onClick = { showPrefixDialog = true }
                    )
                }
            }

            // ── Security ──────────────────────────────────────────────────
            item {
                SettingsSection(stringResource(R.string.security)) {
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.change_pin),
                        subtitle = stringResource(R.string.change_pin_subtitle),
                        onClick = { showPinDialog = true }
                    )
                    SettingsDivider()
                    SettingsToggle(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.biometric_auth),
                        subtitle = if (biometricEnabled) stringResource(R.string.biometric_active) else stringResource(R.string.biometric_use),
                        checked = biometricEnabled,
                        onChecked = { showBiometricConfirm = it }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.auto_lock),
                        subtitle = if (settings.inactivityLockSecs > 0) 
                            "Locks after ${settings.inactivityLockSecs}s of inactivity"
                            else "Auto-lock disabled",
                        onClick = { showLockDialog = true }
                    )
                }
            }

            // ── Backup ────────────────────────────────────────────────────
            item {
                SettingsSection(stringResource(R.string.backup_sync)) {
                    SettingsToggle(
                        icon = Icons.Default.CloudUpload,
                        title = stringResource(R.string.auto_daily_backup),
                        subtitle = stringResource(R.string.auto_daily_backup_subtitle),
                        checked = settings.autoBackupEnabled,
                        onChecked = viewModel::updateAutoBackup
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Backup,
                        title = stringResource(R.string.last_backup),
                        subtitle = if (settings.lastBackupTime > 0)
                            java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(settings.lastBackupTime))
                        else stringResource(R.string.never_backed_up),
                        onClick = { showBackupInfo = true }
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────
            item {
                SettingsSection("About") {
                    Column(Modifier.padding(16.dp)) {
                        // Abu Star logo banner
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AuraColors.SurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profile?.logoUri?.isNotBlank() == true) {
                                AsyncImage(
                                    model = profile!!.logoUri,
                                    contentDescription = profile?.companyName ?: stringResource(R.string.app_name),
                                    modifier = Modifier
                                        .fillMaxHeight(0.85f)
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.abu_star_logo),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier
                                        .fillMaxHeight(0.85f)
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            profile?.companyName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = AuraColors.PrimaryContainer,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurfaceVariant,
                            letterSpacing = 2.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} · Goldsmith Billing Suite",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showProfileDialog) {
        CompanyProfileDialog(
            current = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { viewModel.saveProfile(it); showProfileDialog = false }
        )
    }

    if (showPinDialog) {
        ChangePinDialog(
            onDismiss = { showPinDialog = false },
            onSave = { viewModel.updatePin(it); showPinDialog = false }
        )
    }

    if (showRateDialog) {
        EditRatesDialog(
            r24 = settings.goldRate24K,
            r22 = settings.goldRate22K,
            r20 = settings.goldRate20K,
            r18 = settings.goldRate18K,
            onDismiss = { showRateDialog = false },
            onSave = { r24, r22, r20, r18 ->
                viewModel.updateGoldRates(r24, r22, r20, r18)
                showRateDialog = false
            }
        )
    }

    if (showLockDialog) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Auto-Lock Duration", color = AuraColors.OnSurface) },
            text = {
                val durations = listOf(0 to "Never", 30 to "30 Seconds", 60 to "1 Minute", 300 to "5 Minutes")
                Column {
                    durations.forEach { (secs, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.updateInactivityLock(secs); showLockDialog = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.inactivityLockSecs == secs, onClick = { viewModel.updateInactivityLock(secs); showLockDialog = false })
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = AuraColors.OnSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLockDialog = false }) { Text("Close", color = AuraColors.PrimaryContainer) } }
        )
    }

    if (showGstDialog) {
        var gstText by remember { mutableStateOf(settings.gstPercent.toString()) }
        AlertDialog(
            onDismissRequest = { showGstDialog = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("GST Percentage", color = AuraColors.OnSurface) },
            text = {
                GhostTextField(gstText, { gstText = it }, "GST %", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                GoldButton("Save", onClick = {
                    gstText.toDoubleOrNull()?.let { viewModel.updateGstPercent(it) }
                    showGstDialog = false
                })
            }
        )
    }

    if (showPrefixDialog) {
        var prefixText by remember { mutableStateOf(settings.userPrefix) }
        AlertDialog(
            onDismissRequest = { showPrefixDialog = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Invoice Prefix", color = AuraColors.OnSurface) },
            text = {
                GhostTextField(prefixText, { prefixText = it }, "User Prefix (F/Y/B)", placeholder = "e.g. F")
            },
            confirmButton = {
                GoldButton("Save", onClick = {
                    viewModel.updateUserPrefix(prefixText)
                    showPrefixDialog = false
                })
            }
        )
    }

    showBiometricConfirm?.let { requested ->
        ConfirmPinDialog(
            title = if (requested) "Enable biometric?" else "Disable biometric?",
            message = "Confirm your PIN before changing biometric authentication.",
            onDismiss = { showBiometricConfirm = null },
            onConfirm = { pin, setError ->
                if (viewModel.verifyPin(pin)) {
                    viewModel.setBiometricEnabled(requested)
                    biometricEnabled = requested
                    showBiometricConfirm = null
                } else {
                    setError("Incorrect PIN")
                }
            }
        )
    }

    if (showBackupInfo) {
        val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        AlertDialog(
            onDismissRequest = { showBackupInfo = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Backup Status", color = AuraColors.OnSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (settings.lastBackupTime > 0)
                            "Last backup: ${java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(settings.lastBackupTime))}"
                        else "No successful backup recorded yet.",
                        color = AuraColors.OnSurfaceVariant
                    )
                    Text("Google account: ${account?.email ?: "Not selected"}", color = AuraColors.OnSurfaceVariant)
                    Text("Use Backup & Sync from the dashboard to run backup or restore.", color = AuraColors.OnSurfaceVariant)
                }
            },
            confirmButton = { GoldButton("Close", onClick = { showBackupInfo = false }) }
        )
    }
}

// ─── Icon Option Card ─────────────────────────────────────────────────────────
@Composable
private fun IconOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) AuraColors.PrimaryContainer.copy(alpha = 0.1f)
                else AuraColors.GlassWhite5
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AuraColors.PrimaryContainer else AuraColors.GlassWhite10,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            content()
            if (selected) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(AuraColors.PrimaryContainer, CircleShape)
                        .border(1.dp, AuraColors.Background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = AuraColors.OnPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AuraColors.PrimaryContainer else AuraColors.OnSurfaceVariant,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ─── Settings Components ──────────────────────────────────────────────────────
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        GlassCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsDivider() {
    Divider(color = AuraColors.GlassWhite5, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp)
                .background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp))
                .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        }
        if (trailing != null) trailing()
        else Icon(Icons.Default.ChevronRight, null,
            tint = AuraColors.OnSurface.copy(alpha = 0.2f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp)
                .background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp))
                .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AuraColors.OnPrimary,
                checkedTrackColor = AuraColors.PrimaryContainer,
                uncheckedThumbColor = AuraColors.OnSurfaceVariant,
                uncheckedTrackColor = AuraColors.SurfaceContainerHighest
            )
        )
    }
}

@Composable
private fun RateChip(karat: String, rate: Double, modifier: Modifier = Modifier) {
    Column(
        modifier.background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(karat, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text("₹${String.format("%,.0f", rate)}", style = MaterialTheme.typography.bodyLarge,
            color = AuraColors.OnSurface, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────
@Composable
private fun CompanyProfileDialog(
    current: CompanyProfile?, onDismiss: () -> Unit, onSave: (CompanyProfile) -> Unit
) {
    var name by remember { mutableStateOf(current?.companyName ?: "Abu Star Diamonds") }
    var owner by remember { mutableStateOf(current?.ownerName ?: "") }
    var mobile by remember { mutableStateOf(current?.mobileNumber ?: "") }
    var addr1 by remember { mutableStateOf(current?.address1 ?: "") }
    var addr2 by remember { mutableStateOf(current?.address2 ?: "") }
    var city by remember { mutableStateOf(current?.city ?: "") }
    var state by remember { mutableStateOf(current?.state ?: "") }
    var pin by remember { mutableStateOf(current?.pincode ?: "") }
    var gst by remember { mutableStateOf(current?.gstNumber ?: "") }
    var logoUri by remember { mutableStateOf(current?.logoUri ?: "") }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { logoUri = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text(stringResource(R.string.company_profile), color = AuraColors.OnSurface) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AuraColors.SurfaceContainerHighest)
                            .border(1.dp, AuraColors.GlassWhite20, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoUri.isNotBlank()) {
                            AsyncImage(
                                model = logoUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.abu_star_logo),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    OutlinedButton(onClick = { logoPicker.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.change_logo))
                    }
                }
                GhostTextField(name, { name = it }, stringResource(R.string.company_name))
                GhostTextField(owner, { owner = it }, stringResource(R.string.owner_name))
                GhostTextField(mobile, { mobile = it }, stringResource(R.string.mobile),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone))
                GhostTextField(addr1, { addr1 = it }, stringResource(R.string.address_line_1))
                GhostTextField(addr2, { addr2 = it }, stringResource(R.string.address_line_2))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostTextField(city, { city = it }, stringResource(R.string.city), modifier = Modifier.weight(1f))
                    GhostTextField(state, { state = it }, stringResource(R.string.state), modifier = Modifier.weight(1f))
                }
                GhostTextField(pin, { pin = it }, stringResource(R.string.pincode),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                GhostTextField(gst, { gst = it }, stringResource(R.string.gst_number))
            }
        },
        confirmButton = {
            GoldButton(stringResource(R.string.save), onClick = {
                onSave(CompanyProfile(
                    companyName = name, ownerName = owner, mobileNumber = mobile,
                    address1 = addr1, address2 = addr2, city = city,
                    state = state, pincode = pin, gstNumber = gst,
                    logoUri = logoUri
                ))
            })
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = AuraColors.OnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Change PIN", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newPin, onValueChange = { if (it.length <= 4) newPin = it },
                    label = { Text("New 4-digit PIN") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer,
                        unfocusedBorderColor = AuraColors.GlassWhite20,
                        focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { if (it.length <= 4) confirm = it },
                    label = { Text("Confirm PIN") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer,
                        unfocusedBorderColor = AuraColors.GlassWhite20,
                        focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                if (error.isNotEmpty())
                    Text(error, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            GoldButton("Update PIN", onClick = {
                when {
                    newPin.length != 4 -> error = "PIN must be 4 digits"
                    newPin != confirm -> error = "PINs don't match"
                    else -> onSave(newPin)
                }
            })
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) }
        }
    )
}

@Composable
private fun ConfirmPinDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (String, (String) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text(title, color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, color = AuraColors.OnSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) { pin = it; error = "" } },
                    label = { Text("PIN") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = error.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer,
                        unfocusedBorderColor = AuraColors.GlassWhite20,
                        focusedTextColor = AuraColors.OnSurface,
                        unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5,
                        unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                if (error.isNotEmpty()) Text(error, color = AuraColors.Error)
            }
        },
        confirmButton = { GoldButton("Confirm", onClick = { onConfirm(pin) { error = it } }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}

@Composable
private fun EditRatesDialog(
    r24: Double, r22: Double, r20: Double, r18: Double,
    onDismiss: () -> Unit, onSave: (Double, Double, Double, Double) -> Unit
) {
    var t24 by remember { mutableStateOf(r24.toString()) }
    var t22 by remember { mutableStateOf(r22.toString()) }
    var autoCalc by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Update Gold Rates", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostTextField(t24, {
                    t24 = it
                    if (autoCalc) {
                        val v = it.toDoubleOrNull() ?: 0.0
                        t22 = String.format("%.2f", v * 0.916)
                    }
                }, "24K Rate (per gram ₹)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoCalc, onCheckedChange = { autoCalc = it },
                        colors = CheckboxDefaults.colors(checkedColor = AuraColors.PrimaryContainer))
                    Text("Auto-calculate 22K (91.6%)", color = AuraColors.OnSurface,
                        style = MaterialTheme.typography.bodyMedium)
                }
                GhostTextField(t22, { t22 = it }, "22K Rate", enabled = !autoCalc,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            GoldButton("Save Rates", onClick = {
                val rate24 = t24.toDoubleOrNull() ?: r24
                val rate22 = t22.toDoubleOrNull() ?: r22
                // Keep r20 and r18 as is or update based on new 24K if autoCalc
                val rate20 = if (autoCalc) rate24 * 0.85 else r20
                val rate18 = if (autoCalc) rate24 * 0.75 else r18
                onSave(rate24, rate22, rate20, rate18)
            })
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) }
        }
    )
}
