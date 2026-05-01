package com.goldsmith.billing

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.navigation.GoldsmithNavGraph
import com.goldsmith.billing.navigation.Screen
import com.goldsmith.billing.security.KeystoreManager
import com.goldsmith.billing.ui.theme.GoldsmithBillingTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val isPinSet get() = keystoreManager.isPinSet()

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // FIX: Inactivity lock — only fires when NO interaction for lockSecs
    private var inactivityJob: Job? = null
    private val _lockApp = MutableStateFlow(false)
    val lockApp = _lockApp.asStateFlow()

    private var _isUnlocked = false  // tracks if user is past auth screen

    fun onAppUnlocked() {
        _isUnlocked = true
        _lockApp.value = false
        resetInactivityTimer()
    }

    fun resetInactivityTimer() {
        if (!_isUnlocked) return  // FIX: don't run timer on lock screen itself
        inactivityJob?.cancel()
        val lockSecs = settings.value.inactivityLockSecs.toLong()
        if (lockSecs <= 0) return  // 0 = disabled
        inactivityJob = viewModelScope.launch {
            delay(lockSecs * 1000L)
            _lockApp.value = true
            _isUnlocked = false
        }
    }

    fun onAppLocked() {
        _isUnlocked = false
        inactivityJob?.cancel()
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // FIX: Apply locale BEFORE super.onCreate — attaches correct context
    override fun attachBaseContext(newBase: Context) {
        // Read language synchronously from shared prefs fallback
        val prefs = newBase.getSharedPreferences("goldsmith_locale_cache", Context.MODE_PRIVATE)
        val lang = prefs.getString("lang", "en") ?: "en"
        super.attachBaseContext(applyLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // FIX: FLAG_SECURE in debug too (can be toggled) — prevents screenshots
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            val settings by viewModel.settings.collectAsState()
            val locked by viewModel.lockApp.collectAsState()

            // FIX: Cache language to SharedPrefs so attachBaseContext can read it
            LaunchedEffect(settings.language) {
                val prefs = getSharedPreferences("goldsmith_locale_cache", Context.MODE_PRIVATE)
                prefs.edit().putString("lang", settings.language).apply()
            }

            GoldsmithBillingTheme(darkTheme = settings.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val startDestination = when {
                        settings.devicePrefix.isEmpty() && !settings.isFirstLaunch -> Screen.DeviceSetup.route
                        viewModel.isPinSet -> Screen.PinVerify.route
                        else -> Screen.PinSetup.route
                    }

                    // FIX: Lock screen navigation — only when user was unlocked
                    LaunchedEffect(locked) {
                        if (locked) {
                            navController.navigate(Screen.PinVerify.route) {
                                popUpTo(0) { inclusive = true }
                            }
                            viewModel.onAppLocked()
                        }
                    }

                    GoldsmithNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        onUnlocked = { viewModel.onAppUnlocked() }
                    )
                }
            }
        }
    }

    // FIX: Reset timer on ANY user touch — called by Android automatically
    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.resetInactivityTimer()
    }

    override fun onResume() {
        super.onResume()
        // Don't reset timer on resume — only reset on actual user touch
        // This prevents the timer from being reset when app comes to foreground
        // without user interaction
    }

    override fun onStop() {
        super.onStop()
        // App going to background — cancel timer, lock on next resume will be
        // handled by the inactivity timer if user was active before background
    }

    companion object {
        fun applyLocale(context: Context, lang: String): Context {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createConfigurationContext(config)
            } else {
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                context
            }
        }
    }
}
