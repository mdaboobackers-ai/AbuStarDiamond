package com.goldsmith.billing.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import com.goldsmith.billing.R
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.security.KeystoreManager
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object MobileSecurityAuth {
    val allowedAuthenticators: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val loginPromptAuthenticators: Int = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailable(canAuthenticateResult: Int): Boolean =
        canAuthenticateResult == BiometricManager.BIOMETRIC_SUCCESS

    fun shouldFallbackToPinAfterFailures(failedAttempts: Int): Boolean =
        failedAttempts >= 3
}

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    val isPinSet get() = keystoreManager.isPinSet()
    val isBiometricEnabled get() = keystoreManager.isBiometricEnabled()
    val settings = settingsRepo.settingsFlow

    fun savePin(pin: String) = keystoreManager.savePin(pin)
    fun verifyPin(pin: String) = keystoreManager.verifyPin(pin)
    fun setBiometricEnabled(enabled: Boolean) = keystoreManager.setBiometricEnabled(enabled)
    fun resetPin() {
        keystoreManager.resetPin()
        keystoreManager.setBiometricEnabled(false)
    }
    fun setPrefix(prefix: String) = viewModelScope.launch { settingsRepo.updateUserPrefix(prefix) }
}

// ─── Prefix Selection Screen ──────────────────────────────────────────────────
@Composable
fun PrefixSelectionScreen(
    onPrefixSelected: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    Box(
        Modifier.fillMaxSize().background(AuraColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(48.dp))
            Text("WELCOME", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 4.sp)
            Text("Who is using this device?", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface, textAlign = TextAlign.Center)
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("F", "Y", "B").forEach { p ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            viewModel.setPrefix(p)
                            onPrefixSelected()
                        },
                        goldBorder = false
                    ) {
                        Row(
                            Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                Modifier.size(48.dp).background(AuraColors.PrimaryContainer.copy(alpha = 0.1f), CircleShape).border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(p, style = MaterialTheme.typography.titleLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Assign Prefix '$p'", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
                                Text("All invoices will start with $p-", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── PIN Setup Screen ─────────────────────────────────────────────────────────
@Composable
fun PinSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var step by remember { mutableStateOf(0) }  // 0 = create, 1 = confirm
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val currentPin = if (step == 0) pin else confirmPin

    fun onDigit(d: String) {
        errorMsg = ""
        if (currentPin.length < 4) {
            if (step == 0) pin += d else confirmPin += d
            if ((if (step == 0) pin else confirmPin).length == 4) {
                if (step == 0) { step = 1 }
                else {
                    if (pin == confirmPin) {
                        viewModel.savePin(pin)
                        onSetupComplete()
                    } else {
                        errorMsg = "PINs don't match"
                        confirmPin = ""
                    }
                }
            }
        }
    }
    fun onBack() {
        if (step == 0) pin = pin.dropLast(1)
        else confirmPin = confirmPin.dropLast(1)
    }

    PinScaffold(
        title = if (step == 0) "Create Secure PIN" else "Confirm PIN",
        subtitle = if (step == 0) "Choose a 4-digit PIN to secure your vault"
                   else "Re-enter your PIN to confirm",
        pinLength = currentPin.length,
        errorMsg = errorMsg,
        showBiometric = false,
        onDigit = ::onDigit,
        onBack = ::onBack,
        onBiometric = {}
    )
}

// ─── PIN Verify Screen ────────────────────────────────────────────────────────
@Composable
fun PinVerifyScreen(
    onVerified: () -> Unit,
    onFirstTime: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var shakeAnim by remember { mutableStateOf(false) }
    var biometricPromptShown by remember { mutableStateOf(false) }
    var biometricFailCount by remember { mutableIntStateOf(0) }
    var showForgotPinDialog by remember { mutableStateOf(false) }

    fun markBiometricFailed(message: String = "Mobile security failed. Try again.") {
        biometricFailCount += 1
        errorMsg = if (biometricFailCount >= 3) "Use PIN to continue" else message
    }

    fun showPinFallback(message: String = "Use PIN to continue") {
        biometricFailCount = 3
        errorMsg = message
    }

    LaunchedEffect(Unit) {
        if (!viewModel.isPinSet) { onFirstTime(); return@LaunchedEffect }
    }

    fun onDigit(d: String) {
        errorMsg = ""
        if (pin.length < 4) {
            pin += d
            if (pin.length == 4) {
                if (viewModel.verifyPin(pin)) { 
                    onVerified() 
                }
                else {
                    errorMsg = "Incorrect PIN"
                    shakeAnim = true
                    pin = ""
                }
            }
        }
    }
    fun onBack() { pin = pin.dropLast(1); errorMsg = "" }

    // Check biometric availability
    val canShowBiometric = remember {
        val bm = BiometricManager.from(context)
        MobileSecurityAuth.isAvailable(bm.canAuthenticate(MobileSecurityAuth.allowedAuthenticators))
    }
    val showMobileSecurity = viewModel.isBiometricEnabled && canShowBiometric
    val allowPinEntry = !showMobileSecurity || biometricFailCount >= 3

    LaunchedEffect(viewModel.isBiometricEnabled, canShowBiometric) {
        if (viewModel.isPinSet && viewModel.isBiometricEnabled && canShowBiometric && !biometricPromptShown) {
            biometricPromptShown = true
            showBiometric(
                context = context,
                onSuccess = onVerified,
                onFailed = { markBiometricFailed() },
                onFallbackToPin = { showPinFallback(it) },
                onError = { message -> errorMsg = message }
            )
        }
    }

    PinScaffold(
        title = if (!allowPinEntry) "Mobile Security" else if (showMobileSecurity) "Enter Secure PIN" else "Mobile Security",
        subtitle = when {
            !allowPinEntry -> "Confirm with mobile security or phone lock"
            showMobileSecurity -> "Mobile security failed. Use PIN to continue"
            else -> "Enter app PIN to unlock your secure vault"
        },
        pinLength = pin.length,
        errorMsg = errorMsg,
        showBiometric = showMobileSecurity,
        showPinEntry = allowPinEntry,
        onDigit = ::onDigit,
        onBack = ::onBack,
        showForgotPin = true,
        onForgotPin = { showForgotPinDialog = true },
        onBiometric = {
            showBiometric(
                context = context,
                onSuccess = onVerified,
                onFailed = { markBiometricFailed() },
                onFallbackToPin = { showPinFallback(it) },
                onError = { message -> errorMsg = message }
            )
        }
    )

    if (showForgotPinDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPinDialog = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Reset app PIN?", color = AuraColors.OnSurface) },
            text = {
                Text(
                    "This removes the current app PIN and disables biometric login. Your billing data stays safe. You will create a new PIN next.",
                    color = AuraColors.OnSurfaceVariant
                )
            },
            confirmButton = {
                GoldButton("Reset PIN", onClick = {
                    viewModel.resetPin()
                    showForgotPinDialog = false
                    onFirstTime()
                })
            },
            dismissButton = {
                TextButton(onClick = { showForgotPinDialog = false }) {
                    Text("Cancel", color = AuraColors.OnSurfaceVariant)
                }
            }
        )
    }
}

// ─── Shared PIN Scaffold ──────────────────────────────────────────────────────
@Composable
private fun PinScaffold(
    title: String,
    subtitle: String,
    pinLength: Int,
    errorMsg: String,
    showBiometric: Boolean,
    showPinEntry: Boolean = true,
    showForgotPin: Boolean = false,
    onDigit: (String) -> Unit,
    onBack: () -> Unit,
    onForgotPin: () -> Unit = {},
    onBiometric: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "login_security_anim")
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4200, easing = LinearEasing)),
        label = "ring_rotation"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "security_pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
    ) {
        // Gold background glow
        Box(
            Modifier
                .size(400.dp)
                .align(Alignment.TopStart)
                .offset((-100).dp, (-100).dp)
                .blur(120.dp)
                .background(AuraColors.PrimaryContainer.copy(alpha = 0.08f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = if (showPinEntry) 10.dp else 24.dp)
            ) {
                Box(Modifier.size(if (showPinEntry) 76.dp else 132.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.abu_star_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Glass container
            Box(
                Modifier
                    .background(AuraColors.GlassWhite12, RoundedCornerShape(32.dp))
                    .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(32.dp))
                    .padding(horizontal = 22.dp, vertical = if (showPinEntry) 20.dp else 28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (showPinEntry) 16.dp else 22.dp)
                ) {
                    // Title
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center, letterSpacing = 2.sp)
                    }

                    if (showPinEntry && !showBiometric) {
                        SecurityAnimation(ringRotation = ringRotation, pulse = pulse, size = 88.dp, iconSize = 32.dp)
                    }

                    if (showPinEntry) {
                        PinDots(filledCount = pinLength)
                    }

                    // Error
                    AnimatedVisibility(errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }

                    if (!showPinEntry) {
                        SecurityAnimation(ringRotation = ringRotation, pulse = pulse, size = 148.dp, iconSize = 50.dp)
                        Text(
                            "PIN will appear after 3 failed mobile-security attempts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (showPinEntry) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            listOf(
                                listOf("1","2","3"),
                                listOf("4","5","6"),
                                listOf("7","8","9")
                            ).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    row.forEach { digit ->
                                        KeypadButton(digit, onClick = { onDigit(digit) }, size = 58.dp)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                if (showBiometric) {
                                    KeypadButton("", onClick = onBiometric, size = 58.dp) {
                                        Icon(Icons.Default.Security, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(26.dp))
                                    }
                                } else {
                                    Spacer(Modifier.size(58.dp))
                                }
                                KeypadButton("0", onClick = { onDigit("0") }, size = 58.dp)
                                KeypadButton("", onClick = onBack, size = 58.dp) {
                                    Icon(Icons.Default.Backspace, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    if (showBiometric) {
                        Button(
                            onClick = onBiometric,
                            modifier = Modifier.fillMaxWidth().height(if (showPinEntry) 48.dp else 54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraColors.PrimaryContainer, contentColor = AuraColors.OnPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Security, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (showPinEntry) "TRY MOBILE SECURITY" else "OPEN MOBILE SECURITY", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                    }

                    // Footer
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showForgotPin) {
                            TextButton(onClick = onForgotPin) {
                                Text("Forgot PIN?", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp)
                            }
                        }
                        Divider(color = AuraColors.GlassWhite10, modifier = Modifier.width(32.dp))
                        Text("ENCRYPTED SESSION", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), letterSpacing = 2.sp)
                    }
                }
            }

            // Bottom security label
            Spacer(Modifier.height(if (showPinEntry) 14.dp else 32.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Diamond, null, tint = AuraColors.OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                Text(
                    "SECURED BY ABU STAR DIAMONDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.OnSurface.copy(alpha = 0.3f),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // Bottom gold gradient line
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AuraColors.PrimaryContainer.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
    }
}

private fun showBiometric(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onFailed: () -> Unit = {},
    onFallbackToPin: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)
    var failedAttempts = 0
    lateinit var biometricPrompt: BiometricPrompt
    biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                failedAttempts += 1
                if (MobileSecurityAuth.shouldFallbackToPinAfterFailures(failedAttempts)) {
                    biometricPrompt.cancelAuthentication()
                    onFallbackToPin("Biometric failed 3 times. Enter app PIN.")
                } else {
                    onFailed()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> onFallbackToPin("Use PIN to continue")
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFallbackToPin("Use PIN to continue")
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> Unit
                    else -> onError(errString.toString())
                }
            }
        }
    )
    biometricPrompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Use mobile security")
            .setNegativeButtonText("Use app PIN")
            .setAllowedAuthenticators(MobileSecurityAuth.loginPromptAuthenticators)
            .build()
    )
}

@Composable
private fun SecurityAnimation(
    ringRotation: Float,
    pulse: Float,
    size: Dp,
    iconSize: Dp
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 0.78f },
            modifier = Modifier.fillMaxSize().rotate(-ringRotation),
            color = AuraColors.PrimaryContainer.copy(alpha = 0.95f),
            trackColor = AuraColors.GlassWhite10,
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round
        )
        Box(
            Modifier
                .size((size.value * 0.62f * pulse).dp)
                .background(AuraColors.PrimaryContainer.copy(alpha = 0.14f), CircleShape)
                .border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Security, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(iconSize))
        }
    }
}
