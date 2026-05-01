package com.goldsmith.billing.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.R
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

// ─── AuthViewModel ────────────────────────────────────────────────────────────
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    val isPinSet     get() = keystoreManager.isPinSet()
    val isBiometricEnabled get() = keystoreManager.isBiometricEnabled()

    fun savePin(pin: String) = keystoreManager.savePin(pin)
    fun verifyPin(pin: String) = keystoreManager.verifyPin(pin)
    fun setBiometricEnabled(e: Boolean) = keystoreManager.setBiometricEnabled(e)

    fun saveDeviceSetup(prefix: String, ownerName: String) = viewModelScope.launch {
        settingsRepo.updateDevicePrefix(prefix, ownerName)
        settingsRepo.setFirstLaunchDone()
    }
}

// ─── Device Setup Screen (first launch — who uses this phone?) ────────────────
@Composable
fun DeviceSetupScreen(
    onComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var ownerName   by remember { mutableStateOf("") }
    var prefix      by remember { mutableStateOf("") }
    var error       by remember { mutableStateOf("") }

    val suggestions = listOf(
        "F" to "Father",
        "Y" to "You (Owner)",
        "B" to "Brother / Partner",
        "S" to "Staff",
        "A" to "Abu Star (Main)"
    )

    Box(
        Modifier.fillMaxSize().background(AuraColors.Background),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow
        Box(
            Modifier.size(400.dp).align(Alignment.TopCenter).offset(y = (-80).dp)
                .blur(150.dp)
                .background(AuraColors.PrimaryContainer.copy(alpha = 0.07f))
        )

        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            coil.compose.AsyncImage(
                model = R.drawable.abu_star_logo,
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )

            Text(
                "WHO IS USING\nTHIS DEVICE?",
                style = MaterialTheme.typography.headlineMedium,
                color = AuraColors.OnSurface,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            Text(
                "Invoice numbers will be tagged with your prefix\nso records don't conflict across family devices",
                style = MaterialTheme.typography.bodyMedium,
                color = AuraColors.OnSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            // Quick select chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { (p, label) ->
                    val selected = prefix == p
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) AuraColors.PrimaryContainer.copy(alpha = 0.15f)
                                else AuraColors.GlassWhite5,
                                RoundedCornerShape(14.dp)
                            )
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) AuraColors.PrimaryContainer else AuraColors.GlassWhite10,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { prefix = p; ownerName = label }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(36.dp)
                                .background(
                                    if (selected) AuraColors.PrimaryContainer else AuraColors.GlassWhite10,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                p,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) AuraColors.OnPrimary else AuraColors.OnSurface,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
                            Text("Bills: INV-$p-XXXX", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f))
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Custom name
            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Your Name (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraColors.PrimaryContainer,
                    unfocusedBorderColor = AuraColors.GlassWhite20,
                    focusedTextColor = AuraColors.OnSurface,
                    unfocusedTextColor = AuraColors.OnSurface,
                    focusedContainerColor = AuraColors.GlassWhite5,
                    unfocusedContainerColor = AuraColors.GlassWhite5
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (error.isNotEmpty()) Text(error, color = AuraColors.Error)

            GoldButton(
                text = "Continue →",
                onClick = {
                    if (prefix.isEmpty()) {
                        error = "Please select who is using this device"
                    } else {
                        viewModel.saveDeviceSetup(prefix, ownerName.ifEmpty { prefix })
                        onComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── PIN Setup Screen ─────────────────────────────────────────────────────────
@Composable
fun PinSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var step       by remember { mutableStateOf(0) }
    var pin        by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg   by remember { mutableStateOf("") }

    val currentPin = if (step == 0) pin else confirmPin

    fun onDigit(d: String) {
        errorMsg = ""
        if (currentPin.length < 4) {
            if (step == 0) pin += d else confirmPin += d
            val cur = if (step == 0) pin else confirmPin
            if (cur.length == 4) {
                if (step == 0) {
                    step = 1
                } else {
                    if (pin == confirmPin) { viewModel.savePin(pin); onSetupComplete() }
                    else { errorMsg = "PINs don't match. Try again."; confirmPin = "" }
                }
            }
        }
    }
    fun onBack() {
        if (step == 0) pin = pin.dropLast(1)
        else confirmPin = confirmPin.dropLast(1)
    }

    PinScaffold(
        title    = if (step == 0) "Create Secure PIN" else "Confirm PIN",
        subtitle = if (step == 0) "Choose a 4-digit PIN to secure your vault"
                   else "Re-enter your PIN to confirm",
        pinLength     = currentPin.length,
        errorMsg      = errorMsg,
        showBiometric = false,
        onDigit       = ::onDigit,
        onBack        = ::onBack,
        onBiometric   = {}
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
    var pin      by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }

    // FIX: proper biometric prompt — auto shows on launch if enabled
    LaunchedEffect(Unit) {
        if (!viewModel.isPinSet) { onFirstTime(); return@LaunchedEffect }
        if (viewModel.isBiometricEnabled) {
            delay(300) // small delay for smooth animation
            showBiometric(context, onSuccess = onVerified, onFallback = {})
        }
    }

    fun onDigit(d: String) {
        errorMsg = ""
        if (pin.length < 4) {
            pin += d
            if (pin.length == 4) {
                if (viewModel.verifyPin(pin)) {
                    onVerified()
                } else {
                    attempts++
                    errorMsg = if (attempts >= 3) "Too many attempts. Try again in 30s."
                               else "Incorrect PIN (${attempts}/5)"
                    pin = ""
                }
            }
        }
    }
    fun onBack() { pin = pin.dropLast(1); errorMsg = "" }

    PinScaffold(
        title         = "Enter Secure PIN",
        subtitle      = "Identity verification required",
        pinLength     = pin.length,
        errorMsg      = errorMsg,
        showBiometric = viewModel.isBiometricEnabled,
        onDigit       = ::onDigit,
        onBack        = ::onBack,
        onBiometric   = { showBiometric(context, onSuccess = onVerified, onFallback = {}) }
    )
}

// ─── Shared PIN Scaffold ──────────────────────────────────────────────────────
@Composable
internal fun PinScaffold(
    title: String, subtitle: String,
    pinLength: Int, errorMsg: String,
    showBiometric: Boolean,
    onDigit: (String) -> Unit,
    onBack: () -> Unit,
    onBiometric: () -> Unit
) {
    // Shake animation on error
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(errorMsg) {
        if (errorMsg.isNotEmpty()) {
            repeat(4) {
                shakeAnim.animateTo(if (it % 2 == 0) 10f else -10f,
                    animationSpec = tween(60))
            }
            shakeAnim.animateTo(0f, animationSpec = tween(60))
        }
    }

    Box(
        Modifier.fillMaxSize().background(AuraColors.Background)
    ) {
        // Ambient gold glow
        Box(
            Modifier.size(400.dp).align(Alignment.TopStart)
                .offset((-100).dp, (-100).dp)
                .blur(120.dp)
                .background(AuraColors.PrimaryContainer.copy(alpha = 0.08f))
        )

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                coil.compose.AsyncImage(
                    model = R.drawable.abu_star_logo,
                    contentDescription = "Abu Star Diamonds",
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "ABU STAR DIAMONDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.PrimaryContainer,
                    fontSize = 15.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "TRUST · PURITY · ELEGANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f),
                    fontSize = 9.sp, letterSpacing = 2.sp
                )
            }

            // Glass PIN card
            Box(
                Modifier
                    .background(AuraColors.GlassWhite12, RoundedCornerShape(32.dp))
                    .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(32.dp))
                    .padding(28.dp)
                    .graphicsLayer { translationX = shakeAnim.value }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = MaterialTheme.typography.headlineMedium,
                            color = AuraColors.OnSurface, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center, letterSpacing = 2.sp)
                    }

                    PinDots(filledCount = pinLength)

                    AnimatedVisibility(visible = errorMsg.isNotEmpty(),
                        enter = fadeIn() + slideInVertically(),
                        exit  = fadeOut()
                    ) {
                        Text(errorMsg, color = AuraColors.Error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center)
                    }

                    // Keypad
                    Column(
                        verticalArrangement   = Arrangement.spacedBy(16.dp),
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        listOf(
                            listOf("1","2","3"),
                            listOf("4","5","6"),
                            listOf("7","8","9")
                        ).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                row.forEach { d ->
                                    KeypadButton(d, onClick = { onDigit(d) })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            if (showBiometric) {
                                KeypadButton("", onClick = onBiometric) {
                                    Icon(Icons.Default.Fingerprint, null,
                                        tint = AuraColors.OnSurfaceVariant,
                                        modifier = Modifier.size(28.dp))
                                }
                            } else {
                                Spacer(Modifier.size(68.dp))
                            }
                            KeypadButton("0", onClick = { onDigit("0") })
                            KeypadButton("", onClick = onBack) {
                                Icon(Icons.Default.Backspace, null,
                                    tint = AuraColors.OnSurfaceVariant,
                                    modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(onClick = {}) {
                            Text("Forgot PIN?", style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.PrimaryContainer, letterSpacing = 2.sp)
                        }
                        HorizontalDivider(color = AuraColors.GlassWhite10,
                            modifier = Modifier.width(32.dp))
                        Text("ENCRYPTED SESSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.35f),
                            letterSpacing = 2.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, null,
                    tint = AuraColors.OnSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(12.dp))
                Text("SECURED BY ABU STAR DIAMONDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuraColors.OnSurface.copy(alpha = 0.2f),
                    fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }

        // Bottom gold line
        Box(
            Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(listOf(
                        Color.Transparent,
                        AuraColors.PrimaryContainer.copy(alpha = 0.4f),
                        Color.Transparent
                    ))
                )
        )
    }
}

// ─── Biometric helper ─────────────────────────────────────────────────────────
private fun showBiometric(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onFallback: () -> Unit
) {
    val activity = context as? FragmentActivity ?: return
    val biometricManager = BiometricManager.from(context)

    // FIX: check if biometric is actually available before showing prompt
    val canAuth = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onFallback()
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // User cancelled or error — fall back to PIN (do nothing, PIN is already showing)
        }
        override fun onAuthenticationFailed() {
            // Finger not recognised — let user try again via PIN
        }
    })

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Use biometric to unlock Abu Star Diamonds")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    )
}
