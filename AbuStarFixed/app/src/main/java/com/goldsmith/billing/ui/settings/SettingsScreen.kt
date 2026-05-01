@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
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
import java.util.*
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

    // FIX: Theme now actually saves and applies
    fun updateTheme(dark: Boolean) = viewModelScope.launch { settingsRepo.updateTheme(dark) }

    // FIX: Language now persists across restarts via SharedPrefs cache in MainActivity
    fun updateLanguage(lang: String) = viewModelScope.launch { settingsRepo.updateLanguage(lang) }

    fun updateGstPercent(pct: Double) = viewModelScope.launch { settingsRepo.updateGstPercent(pct) }
    fun updateGoldRates(r24: Double, r22: Double, r20: Double, r18: Double) = viewModelScope.launch {
        settingsRepo.updateGoldRatesManual(r24, r22, r20, r18)
    }
    fun saveProfile(p: CompanyProfile) = viewModelScope.launch {
        companyProfileDao.upsertProfile(p.copy(updatedAt = Date()))
    }
    fun updatePin(newPin: String) { keystoreManager.savePin(newPin) }
    fun setBiometricEnabled(e: Boolean) = keystoreManager.setBiometricEnabled(e)
    fun isBiometricEnabled() = keystoreManager.isBiometricEnabled()

    // FIX: Auto-backup toggle persists properly
    fun updateAutoBackup(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoBackupEnabled(enabled)
    }

    // FIX: Inactivity lock duration is now editable
    fun updateInactivityLock(secs: Int) = viewModelScope.launch {
        settingsRepo.updateInactivityLock(secs)
    }

    fun selectIcon(icon: String) = viewModelScope.launch { settingsRepo.updateSelectedIcon(icon) }
    fun setCustomIconUri(uri: String) = viewModelScope.launch {
        settingsRepo.updateCustomIconUri(uri)
        settingsRepo.updateSelectedIcon(AppIconManager.ICON_CUSTOM)
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val profile  by viewModel.profile.collectAsState()

    var showProfileDialog  by remember { mutableStateOf(false) }
    var showPinDialog      by remember { mutableStateOf(false) }
    var showRateDialog     by remember { mutableStateOf(false) }
    var showLockDialog     by remember { mutableStateOf(false) }
    var showGstDialog      by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
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
                title = { Text("SETTINGS", style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── App Icon ──────────────────────────────────────────────────
            item {
                SettingsSectionTitle("App Icon")
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Choose your launcher icon", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.7f))
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconOption("Abu Star\nLogo", settings.selectedIcon == AppIconManager.ICON_DEFAULT, Modifier.weight(1f),
                                onClick = { viewModel.selectIcon(AppIconManager.ICON_DEFAULT); AppIconManager.switchIcon(context, AppIconManager.ICON_DEFAULT) }
                            ) {
                                AsyncImage(model = R.drawable.abu_star_logo, contentDescription = null,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            }
                            IconOption("Diamond\nIcon", settings.selectedIcon == AppIconManager.ICON_DIAMOND, Modifier.weight(1f),
                                onClick = { viewModel.selectIcon(AppIconManager.ICON_DIAMOND); AppIconManager.switchIcon(context, AppIconManager.ICON_DIAMOND) }
                            ) {
                                Box(Modifier.size(52.dp).background(Brush.radialGradient(listOf(AuraColors.PrimaryContainer.copy(alpha = 0.3f), AuraColors.Background)), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(28.dp))
                                }
                            }
                            IconOption("Custom\nGallery", settings.selectedIcon == AppIconManager.ICON_CUSTOM, Modifier.weight(1f),
                                onClick = { galleryLauncher.launch("image/*") }
                            ) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(AuraColors.GlassWhite5).border(1.dp, AuraColors.GlassWhite20, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center) {
                                    if (settings.customIconUri.isNotEmpty() && settings.selectedIcon == AppIconManager.ICON_CUSTOM) {
                                        AsyncImage(model = settings.customIconUri, contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.AddPhotoAlternate, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Icon changes take effect after restarting the app", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.35f), fontSize = 10.sp)
                    }
                }
            }

            // ── Company Profile ───────────────────────────────────────────
            item {
                SettingsSectionTitle("Company Profile")
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(AuraColors.SurfaceContainerHighest).border(2.dp, AuraColors.PrimaryContainer.copy(alpha = 0.35f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            if (profile?.logoUri?.isNotEmpty() == true) {
                                AsyncImage(model = profile!!.logoUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            } else {
                                AsyncImage(model = R.drawable.abu_star_logo, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(profile?.companyName?.ifEmpty { "Abu Star Diamonds" } ?: "Abu Star Diamonds", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(profile?.gstNumber?.ifEmpty { "GST: Not set" } ?: "GST: Not set", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                            Text("Trust · Purity · Elegance", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                        IconButton(onClick = { showProfileDialog = true }) { Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer) }
                    }
                }
            }

            // ── Appearance (FIX: theme and language now save correctly) ──
            item {
                SettingsSectionTitle("Appearance")
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingsToggle(Icons.Default.DarkMode, "Dark Theme",
                        if (settings.isDarkTheme) "Aura Lumina Dark (active)" else "Light Mode (active)",
                        settings.isDarkTheme) { viewModel.updateTheme(it) }
                    SettingsDivider()
                    SettingsItem(Icons.Default.Language, "Language",
                        if (settings.language == "ta") "தமிழ் (Tamil)" else "English",
                        onClick = { viewModel.updateLanguage(if (settings.language == "en") "ta" else "en") },
                        trailing = {
                            Box(Modifier.background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text(if (settings.language == "ta") "தமிழ்" else "EN", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer)
                            }
                        }
                    )
                }
            }

            // ── Gold Rates ────────────────────────────────────────────────
            item {
                SettingsSectionTitle("Gold Rates")
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Current Rates", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
                            TextButton(onClick = { showRateDialog = true }) { Text("Edit", color = AuraColors.PrimaryContainer, style = MaterialTheme.typography.labelSmall) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RateChip("24K", settings.goldRate24K)
                            RateChip("22K", settings.goldRate22K)
                            RateChip("20K", settings.goldRate20K)
                            RateChip("18K", settings.goldRate18K)
                        }
                    }
                    SettingsDivider()
                    SettingsItem(Icons.Default.Percent, "GST Percentage", "${settings.gstPercent}%", onClick = { showGstDialog = true })
                }
            }

            // ── Security ──────────────────────────────────────────────────
            item {
                SettingsSectionTitle("Security")
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingsItem(Icons.Default.Lock, "Change PIN", "Update your 4-digit secure PIN", onClick = { showPinDialog = true })
                    SettingsDivider()
                    SettingsToggle(Icons.Default.Fingerprint, "Biometric Authentication", "Use fingerprint or face to unlock",
                        viewModel.isBiometricEnabled()) { viewModel.setBiometricEnabled(it) }
                    SettingsDivider()
                    // FIX: Auto-lock is now editable
                    SettingsItem(Icons.Default.Timer, "Auto-Lock Duration",
                        when (settings.inactivityLockSecs) {
                            0    -> "Disabled"
                            30   -> "30 seconds"
                            60   -> "1 minute"
                            120  -> "2 minutes"
                            300  -> "5 minutes"
                            else -> "${settings.inactivityLockSecs}s"
                        },
                        onClick = { showLockDialog = true }
                    )
                }
            }

            // ── Backup ────────────────────────────────────────────────────
            item {
                SettingsSectionTitle("Backup & Sync")
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingsToggle(Icons.Default.CloudUpload, "Auto Daily Backup", "Backup to Google Drive every day",
                        settings.autoBackupEnabled) { viewModel.updateAutoBackup(it) }
                    SettingsDivider()
                    SettingsItem(Icons.Default.History, "Last Backup",
                        if (settings.lastBackupTime > 0)
                            java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(settings.lastBackupTime))
                        else "Never backed up",
                        onClick = {}
                    )
                    SettingsDivider()
                    SettingsItem(Icons.Default.Devices, "Device Prefix",
                        "This device: INV-${settings.devicePrefix.ifEmpty { "?" }}-XXXX  (${settings.deviceOwnerName})",
                        onClick = {}
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────
            item {
                SettingsSectionTitle("About")
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(model = R.drawable.abu_star_logo, contentDescription = null,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                        Spacer(Modifier.height(10.dp))
                        Text("Abu Star Diamonds", style = MaterialTheme.typography.headlineSmall, color = AuraColors.PrimaryContainer)
                        Text("Trust · Purity · Elegance", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Version 1.0.0 · Goldsmith Billing Suite", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // Dialogs
    if (showProfileDialog) CompanyProfileDialog(profile, { showProfileDialog = false }) { viewModel.saveProfile(it); showProfileDialog = false }
    if (showPinDialog)     ChangePinDialog({ showPinDialog = false }) { viewModel.updatePin(it); showPinDialog = false }
    if (showRateDialog)    EditRatesDialog(settings, { showRateDialog = false }) { r24, r22, r20, r18 -> viewModel.updateGoldRates(r24, r22, r20, r18); showRateDialog = false }
    if (showGstDialog)     GstDialog(settings.gstPercent, { showGstDialog = false }) { viewModel.updateGstPercent(it); showGstDialog = false }
    if (showLockDialog)    LockDurationDialog(settings.inactivityLockSecs, { showLockDialog = false }) { viewModel.updateInactivityLock(it); showLockDialog = false }
}

// ─── Sub-components ───────────────────────────────────────────────────────────
@Composable fun SettingsSectionTitle(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}
@Composable fun SettingsDivider() {
    HorizontalDivider(color = AuraColors.GlassWhite5, modifier = Modifier.padding(horizontal = 16.dp))
}
@Composable private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        }
        if (trailing != null) trailing()
        else Icon(Icons.Default.ChevronRight, null, tint = AuraColors.OnSurface.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
    }
}
@Composable private fun SettingsToggle(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = AuraColors.OnPrimary, checkedTrackColor = AuraColors.PrimaryContainer, uncheckedThumbColor = AuraColors.OnSurfaceVariant, uncheckedTrackColor = AuraColors.SurfaceContainerHighest))
    }
}
@Composable private fun RateChip(karat: String, rate: Double) {
    Column(Modifier.background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(karat, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 0.5.sp)
        Text("₹${String.format("%,.0f", rate)}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}
@Composable private fun IconOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) AuraColors.PrimaryContainer.copy(alpha = 0.1f) else AuraColors.GlassWhite5).border(if (selected) 2.dp else 1.dp, if (selected) AuraColors.PrimaryContainer else AuraColors.GlassWhite10, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            content()
            if (selected) Box(Modifier.size(14.dp).background(AuraColors.PrimaryContainer, CircleShape).border(1.dp, AuraColors.Background, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = AuraColors.OnPrimary, modifier = Modifier.size(8.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) AuraColors.PrimaryContainer else AuraColors.OnSurfaceVariant, fontSize = 8.sp, letterSpacing = 0.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────
@Composable private fun CompanyProfileDialog(current: CompanyProfile?, onDismiss: () -> Unit, onSave: (CompanyProfile) -> Unit) {
    var name by remember { mutableStateOf(current?.companyName ?: "Abu Star Diamonds") }
    var owner by remember { mutableStateOf(current?.ownerName ?: "") }
    var mobile by remember { mutableStateOf(current?.mobileNumber ?: "") }
    var addr1 by remember { mutableStateOf(current?.address1 ?: "") }
    var addr2 by remember { mutableStateOf(current?.address2 ?: "") }
    var city by remember { mutableStateOf(current?.city ?: "") }
    var state by remember { mutableStateOf(current?.state ?: "") }
    var pin by remember { mutableStateOf(current?.pincode ?: "") }
    var gst by remember { mutableStateOf(current?.gstNumber ?: "") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Company Profile", color = AuraColors.OnSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostTextField(name, { name = it }, "Company Name")
                GhostTextField(owner, { owner = it }, "Owner Name")
                GhostTextField(mobile, { mobile = it }, "Mobile", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone))
                GhostTextField(addr1, { addr1 = it }, "Address Line 1")
                GhostTextField(addr2, { addr2 = it }, "Address Line 2")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { GhostTextField(city, { city = it }, "City", modifier = Modifier.weight(1f)); GhostTextField(state, { state = it }, "State", modifier = Modifier.weight(1f)) }
                GhostTextField(pin, { pin = it }, "Pincode", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                GhostTextField(gst, { gst = it }, "GST Number")
            }
        },
        confirmButton = { GoldButton("Save", onClick = { onSave(CompanyProfile(companyName = name, ownerName = owner, mobileNumber = mobile, address1 = addr1, address2 = addr2, city = city, state = state, pincode = pin, gstNumber = gst)) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
@Composable private fun ChangePinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var newPin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Change PIN", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = newPin, onValueChange = { if (it.length <= 4) newPin = it }, label = { Text("New 4-digit PIN") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AuraColors.PrimaryContainer, unfocusedBorderColor = AuraColors.GlassWhite20, focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface, focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5), shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = confirm, onValueChange = { if (it.length <= 4) confirm = it }, label = { Text("Confirm PIN") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AuraColors.PrimaryContainer, unfocusedBorderColor = AuraColors.GlassWhite20, focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface, focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5), shape = RoundedCornerShape(10.dp))
                if (error.isNotEmpty()) Text(error, color = AuraColors.Error)
            }
        },
        confirmButton = { GoldButton("Update", onClick = { if (newPin.length != 4) error = "PIN must be 4 digits" else if (newPin != confirm) error = "PINs don't match" else onSave(newPin) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
@Composable private fun EditRatesDialog(s: AppSettings, onDismiss: () -> Unit, onSave: (Double, Double, Double, Double) -> Unit) {
    var t24 by remember { mutableStateOf(s.goldRate24K.toString()) }
    var t22 by remember { mutableStateOf(s.goldRate22K.toString()) }
    var t20 by remember { mutableStateOf(s.goldRate20K.toString()) }
    var t18 by remember { mutableStateOf(s.goldRate18K.toString()) }
    var auto by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Edit Gold Rates", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostTextField(t24, { t24 = it; if (auto) { val v = it.toDoubleOrNull() ?: 0.0; t22 = String.format("%.2f", v * 0.916); t20 = String.format("%.2f", v * 0.85); t18 = String.format("%.2f", v * 0.75) } }, "24K (₹/gram)", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = auto, onCheckedChange = { auto = it }, colors = CheckboxDefaults.colors(checkedColor = AuraColors.PrimaryContainer)); Text("Auto-calculate 22K/20K/18K", color = AuraColors.OnSurface, style = MaterialTheme.typography.bodyMedium) }
                GhostTextField(t22, { t22 = it }, "22K", enabled = !auto, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GhostTextField(t20, { t20 = it }, "20K (85%)", enabled = !auto, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GhostTextField(t18, { t18 = it }, "18K", enabled = !auto, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { GoldButton("Save", onClick = { onSave(t24.toDoubleOrNull() ?: s.goldRate24K, t22.toDoubleOrNull() ?: s.goldRate22K, t20.toDoubleOrNull() ?: s.goldRate20K, t18.toDoubleOrNull() ?: s.goldRate18K) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
@Composable private fun GstDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var gst by remember { mutableStateOf(current.toString()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("GST Percentage", color = AuraColors.OnSurface) },
        text = { GhostTextField(gst, { gst = it }, "GST %", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
        confirmButton = { GoldButton("Save", onClick = { gst.toDoubleOrNull()?.let { onSave(it) } }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
@Composable private fun LockDurationDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val options = listOf(0 to "Disabled", 30 to "30 seconds", 60 to "1 minute", 120 to "2 minutes", 300 to "5 minutes", 600 to "10 minutes")
    var selected by remember { mutableIntStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Auto-Lock Duration", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (secs, label) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected == secs) AuraColors.PrimaryContainer.copy(alpha = 0.1f) else Color.Transparent).clickable { selected = secs }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected == secs, onClick = { selected = secs }, colors = RadioButtonDefaults.colors(selectedColor = AuraColors.PrimaryContainer))
                        Text(label, color = AuraColors.OnSurface, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { GoldButton("Save", onClick = { onSave(selected) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
