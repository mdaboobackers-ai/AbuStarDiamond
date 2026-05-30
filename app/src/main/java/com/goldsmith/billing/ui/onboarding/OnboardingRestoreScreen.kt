package com.goldsmith.billing.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.R
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.util.DataSyncManager
import com.goldsmith.billing.util.GoogleDriveAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingState {
    object Idle      : OnboardingState()
    object Searching : OnboardingState()
    data class Found(val email: String, val summary: String) : OnboardingState()
    data class Restored(val summary: String)                 : OnboardingState()
    data class NotFound(val email: String)                   : OnboardingState()
    data class Error(val message: String)                    : OnboardingState()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state = _state.asStateFlow()

    fun checkAndRestore(account: GoogleSignInAccount) = viewModelScope.launch {
        _state.value = OnboardingState.Searching
        val manager = DataSyncManager(context, account)
        val email   = account.email.orEmpty()
        settingsRepo.updateSelectedBackupEmail(email)
        settingsRepo.updateLastRestoreAccountEmail(email)

        // 1. Try to restore from Drive
        val restored = manager.performRestore()
        if (restored) {
            settingsRepo.updateLastBackupTime(System.currentTimeMillis())
            val counts = manager.lastBackupCounts
            _state.value = OnboardingState.Restored(
                "Restored from Google Drive (${account.email})\n${counts?.summary() ?: ""}"
            )
        } else {
            _state.value = OnboardingState.NotFound(email)
        }
    }

    fun reset() { _state.value = OnboardingState.Idle }

    fun accountSelectionFailed(message: String? = null) {
        _state.value = OnboardingState.Error(
            message ?: "Google account was not selected. Check Google Play Services and try again."
        )
    }

    fun drivePermissionMissing() {
        _state.value = OnboardingState.Error(
            "Google Drive permission was not allowed. Please sign in again and allow Drive backup access."
        )
    }
}

@Composable
fun OnboardingRestoreScreen(
    onRestoreDone: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state   by viewModel.state.collectAsState()

    val accountClient = remember { GoogleSignIn.getClient(context, GoogleDriveAuth.accountSignInOptions()) }
    val driveClient = remember { GoogleSignIn.getClient(context, GoogleDriveAuth.driveSignInOptions()) }

    val drivePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleDriveAuth.accountFromIntent(result.data)
        when {
            account == null -> viewModel.accountSelectionFailed(GoogleDriveAuth.failureMessageFromIntent(result.data))
            GoogleDriveAuth.hasDrivePermission(account) -> viewModel.checkAndRestore(account)
            else -> viewModel.drivePermissionMissing()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleDriveAuth.accountFromIntent(result.data)
        if (account == null) {
            viewModel.accountSelectionFailed(GoogleDriveAuth.failureMessageFromIntent(result.data))
        } else if (GoogleDriveAuth.hasDrivePermission(account)) {
            viewModel.checkAndRestore(account)
        } else {
            drivePermissionLauncher.launch(driveClient.signInIntent)
        }
    }

    fun launchSignIn() {
        accountClient.signOut().addOnCompleteListener {
            signInLauncher.launch(accountClient.signInIntent)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuraColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                Modifier
                    .size(92.dp)
                    .background(AuraColors.GlassWhite5, RoundedCornerShape(24.dp))
                    .border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.abu_star_logo),
                    contentDescription = "Abu Star Diamonds logo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                "Abu Star Diamonds",
                style = MaterialTheme.typography.headlineMedium,
                color = AuraColors.OnSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose the Google account for backup and restore.\nAllow Drive access when Google asks.",
                style = MaterialTheme.typography.bodyMedium,
                color = AuraColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // State card
            AnimatedVisibility(state != OnboardingState.Idle) {
                GlassCard(Modifier.fillMaxWidth(), goldBorder = state is OnboardingState.Restored) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val s = state) {
                            is OnboardingState.Searching -> {
                                CircularProgressIndicator(color = AuraColors.PrimaryContainer)
                                Text("Looking for your backup in Google Drive (ASD folder)…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraColors.OnSurfaceVariant,
                                    textAlign = TextAlign.Center)
                            }
                            is OnboardingState.Restored -> {
                                Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(40.dp))
                                Text("Data Restored!", style = MaterialTheme.typography.titleMedium,
                                    color = AuraColors.Primary, fontWeight = FontWeight.Bold)
                                Text(s.summary, style = MaterialTheme.typography.bodyMedium,
                                    color = AuraColors.OnSurfaceVariant, textAlign = TextAlign.Center)
                                GoldButton("Continue Setup", onClick = onRestoreDone, modifier = Modifier.fillMaxWidth())
                            }
                            is OnboardingState.NotFound -> {
                                Icon(Icons.Default.CloudOff, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(36.dp))
                                Text("No backup found for ${s.email}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraColors.OnSurfaceVariant, textAlign = TextAlign.Center)
                                Text("This may be a new account. Continue to set up fresh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center)
                                OutlinedButton(
                                    onClick = onSkip,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Set Up Fresh", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp) }
                            }
                            is OnboardingState.Error -> {
                                Icon(Icons.Default.Error, null, tint = AuraColors.Error, modifier = Modifier.size(36.dp))
                                Text(s.message, style = MaterialTheme.typography.bodyMedium,
                                    color = AuraColors.Error, textAlign = TextAlign.Center)
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Sign-in button (shown when idle or error/not-found)
            if (state == OnboardingState.Idle || state is OnboardingState.NotFound || state is OnboardingState.Error) {
                GoldButton(
                    text   = "Sign in with Google to Restore",
                    onClick = ::launchSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    icon   = { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Skip button
            if (state == OnboardingState.Idle || state is OnboardingState.Error) {
                TextButton(onClick = onSkip) {
                    Text("Skip — Start Fresh", color = AuraColors.OnSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
