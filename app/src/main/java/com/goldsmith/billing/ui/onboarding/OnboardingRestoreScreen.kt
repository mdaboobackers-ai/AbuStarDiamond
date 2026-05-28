package com.goldsmith.billing.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.DataSyncManager
import com.goldsmith.billing.util.DriveBackupConfig
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
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state = _state.asStateFlow()

    fun checkAndRestore(account: GoogleSignInAccount) = viewModelScope.launch {
        _state.value = OnboardingState.Searching
        val manager = DataSyncManager(context, account)
        val email   = account.email.orEmpty()

        // 1. Try to restore from Drive
        val restored = manager.performRestore()
        if (restored) {
            val counts = manager.lastBackupCounts
            _state.value = OnboardingState.Restored(
                "Restored from Google Drive (${account.email})\n${counts?.summary() ?: ""}"
            )
        } else {
            _state.value = OnboardingState.NotFound(email)
        }
    }

    fun reset() { _state.value = OnboardingState.Idle }
}

@Composable
fun OnboardingRestoreScreen(
    onRestoreDone: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state   by viewModel.state.collectAsState()

    val gsoOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveBackupConfig.SCOPE))
        .build()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).result
        }.getOrNull()
        if (account != null) {
            viewModel.checkAndRestore(account)
        } else {
            viewModel.reset()
        }
    }

    fun launchSignIn() {
        val existing = GoogleSignIn.getLastSignedInAccount(context)
        if (existing != null && GoogleSignIn.hasPermissions(existing, Scope(DriveBackupConfig.SCOPE))) {
            viewModel.checkAndRestore(existing)
        } else {
            signInLauncher.launch(GoogleSignIn.getClient(context, gsoOptions).signInIntent)
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
            // Logo area
            Box(
                Modifier
                    .size(80.dp)
                    .background(AuraColors.PrimaryContainer.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(40.dp))
            }

            Text(
                "Abu Star Diamonds",
                style = MaterialTheme.typography.headlineMedium,
                color = AuraColors.OnSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Welcome! If you've used this app before,\nsign in to restore your data.",
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
