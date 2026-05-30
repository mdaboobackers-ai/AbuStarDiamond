package com.goldsmith.billing

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.navigation.GoldsmithNavGraph
import com.goldsmith.billing.navigation.Screen
import com.goldsmith.billing.security.KeystoreManager
import com.goldsmith.billing.ui.adaptive.rememberWindowSize
import com.goldsmith.billing.ui.splash.AnimatedSplashScreen
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
    val settings = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly,
            com.goldsmith.billing.data.repository.AppSettings())

    private var inactivityJob: Job? = null
    private val _lockApp = MutableStateFlow(false)
    val lockApp = _lockApp.asStateFlow()

    // FIX: Track whether app is in foreground.
    // Timer must NOT run while app is paused/backgrounded — that was the regression.
    private var isForegrounded = false

    fun onForeground() {
        isForegrounded = true
        resetInactivityTimer()
    }

    fun onBackground() {
        // FIX: Cancel timer when app goes background — user is not active.
        // Restart it fresh when they come back (onForeground).
        isForegrounded = false
        inactivityJob?.cancel()
    }

    fun resetInactivityTimer() {
        inactivityJob?.cancel()
        if (!isForegrounded) return                   // never run while backgrounded
        val lockSecs = settings.value.inactivityLockSecs.toLong()
        if (lockSecs <= 0) return                     // 0 = disabled
        inactivityJob = viewModelScope.launch {
            delay(lockSecs * 1000L)
            _lockApp.value = true
        }
    }

    fun unlock() {
        _lockApp.value = false
        resetInactivityTimer()
    }
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val prefs  = newBase.getSharedPreferences("goldsmith_settings", Context.MODE_PRIVATE)
        val lang   = prefs.getString("language", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val settings   by viewModel.settings.collectAsState()
            val locked     by viewModel.lockApp.collectAsState()
            var showSplash by remember { mutableStateOf(true) }
            val windowSize = rememberWindowSize()

            GoldsmithBillingTheme(darkTheme = settings.isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val startDest = when {
                        viewModel.isPinSet -> Screen.PinVerify.route
                        settings.userPrefix.isEmpty() -> Screen.Onboarding.route
                        else -> Screen.PinSetup.route
                    }

                    if (locked) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Screen.PinVerify.route) { popUpTo(0) { inclusive = true } }
                            viewModel.unlock()
                        }
                    }

                    if (showSplash) {
                        AnimatedSplashScreen { showSplash = false }
                    } else {
                        GoldsmithNavGraph(
                            navController    = navController,
                            startDestination = startDest,
                            windowSize       = windowSize
                        )
                    }
                }
            }
        }
    }

    // FIX: Only reset timer on actual user interactions while foregrounded
    override fun onResume()  { super.onResume();  viewModel.onForeground() }
    // FIX: Cancel timer when app goes to background — not restart it
    override fun onPause()   { super.onPause();   viewModel.onBackground() }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.resetInactivityTimer()
    }
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        viewModel.resetInactivityTimer()
        return super.dispatchTouchEvent(ev)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        viewModel.resetInactivityTimer()
        return super.dispatchKeyEvent(event)
    }
}
