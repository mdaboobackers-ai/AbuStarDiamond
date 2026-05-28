package com.goldsmith.billing.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goldsmith.billing.ui.adaptive.AdaptiveScaffold
import com.goldsmith.billing.ui.adaptive.WindowSize
import com.goldsmith.billing.ui.auth.PinSetupScreen
import com.goldsmith.billing.ui.auth.PinVerifyScreen
import com.goldsmith.billing.ui.auth.PrefixSelectionScreen
import com.goldsmith.billing.ui.analytics.AnalyticsDashboardScreen
import com.goldsmith.billing.ui.backup.BackupScreen
import com.goldsmith.billing.ui.billing.BillingScreen
import com.goldsmith.billing.ui.customer.CustomerListScreen
import com.goldsmith.billing.ui.customer.CustomerDetailScreen
import com.goldsmith.billing.ui.dashboard.DashboardScreen
import com.goldsmith.billing.ui.history.InvoiceHistoryScreen
import com.goldsmith.billing.ui.history.InvoiceDetailScreen
import com.goldsmith.billing.ui.melting.MeltingScreen
import com.goldsmith.billing.ui.ocr.HallmarkScannerScreen
import com.goldsmith.billing.ui.settings.DataImportScreen
import com.goldsmith.billing.ui.settings.SettingsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldsmith.billing.ui.auth.AuthViewModel
import com.goldsmith.billing.ui.onboarding.OnboardingRestoreScreen

sealed class Screen(val route: String) {
    object Onboarding    : Screen("onboarding")
    object PrefixSelect  : Screen("prefix_select")
    object PinSetup      : Screen("pin_setup")
    object PinVerify     : Screen("pin_verify")
    object Dashboard     : Screen("dashboard")
    object NewBill       : Screen("new_bill?customerId={customerId}") {
        fun withCustomer(customerId: Long? = null) =
            if (customerId != null) "new_bill?customerId=$customerId" else "new_bill"
    }
    object Customers     : Screen("customers")
    object CustomerDetail: Screen("customer/{customerId}") {
        fun withId(id: Long) = "customer/$id"
    }
    object InvoiceHistory: Screen("invoice_history")
    object InvoiceDetail : Screen("invoice/{invoiceId}") {
        fun withId(id: Long) = "invoice/$id"
    }
    object Melting       : Screen("melting")
    object Settings      : Screen("settings")
    object DataImport    : Screen("data_import")
    object Backup        : Screen("backup")
    object Analytics     : Screen("analytics")
    object HallmarkScan  : Screen("hallmark_scan")
}

// Screens that should NOT show the bottom/rail navigation bar
private val noNavRoutes = setOf(
    Screen.Onboarding.route,
    Screen.PrefixSelect.route,
    Screen.PinSetup.route,
    Screen.PinVerify.route,
    Screen.NewBill.route.substringBefore("?"),
    "new_bill"
)

@Composable
fun GoldsmithNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.PinVerify.route,
    windowSize: WindowSize = WindowSize.COMPACT,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val settings by authViewModel.settings.collectAsState(com.goldsmith.billing.data.repository.AppSettings())

    NavHost(navController = navController, startDestination = startDestination) {

        // ── Auth / Onboarding ─────────────────────────────────────────────

        composable(Screen.Onboarding.route) {
            OnboardingRestoreScreen(
                onRestoreDone = {
                    navController.navigate(Screen.PinSetup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.PrefixSelect.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PrefixSelect.route) {
            PrefixSelectionScreen(onPrefixSelected = {
                navController.navigate(Screen.PinSetup.route) {
                    popUpTo(Screen.PrefixSelect.route) { inclusive = true }
                }
            })
        }

        composable(Screen.PinSetup.route) {
            PinSetupScreen(onSetupComplete = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.PinSetup.route) { inclusive = true }
                }
            })
        }

        composable(Screen.PinVerify.route) {
            PinVerifyScreen(
                onVerified = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.PinVerify.route) { inclusive = true }
                    }
                },
                onFirstTime = {
                    // NEW: first install → show onboarding/restore screen first
                    val next = if (settings.userPrefix.isEmpty()) Screen.Onboarding.route else Screen.PinSetup.route
                    navController.navigate(next) {
                        popUpTo(Screen.PinVerify.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Main screens (with adaptive nav) ──────────────────────────────

        composable(Screen.Dashboard.route) {
            AdaptiveScaffold(navController = navController, windowSize = windowSize) { padding ->
                DashboardScreen(
                    contentPadding = padding,
                    onNewBill      = { navController.navigate(Screen.NewBill.withCustomer()) },
                    onAddCustomer  = { navController.navigate(Screen.Customers.route) },
                    onBackup       = { navController.navigate(Screen.Backup.route) },
                    onHistory      = { navController.navigate(Screen.InvoiceHistory.route) },
                    onCustomers    = { navController.navigate(Screen.Customers.route) },
                    onSettings     = { navController.navigate(Screen.Settings.route) },
                    onMelting      = { navController.navigate(Screen.Melting.route) },
                    onAnalytics    = { navController.navigate(Screen.Analytics.route) },
                    onHallmarkScan = { navController.navigate(Screen.HallmarkScan.route) },
                    windowSize     = windowSize
                )
            }
        }

        composable(Screen.Customers.route) {
            AdaptiveScaffold(navController = navController, windowSize = windowSize) { padding ->
                CustomerListScreen(
                    contentPadding = padding,
                    windowSize     = windowSize,
                    onCustomer     = { id -> navController.navigate(Screen.CustomerDetail.withId(id)) },
                    onNewBill      = { id -> navController.navigate(Screen.NewBill.withCustomer(id)) },
                    onBack         = { navController.popBackStack() }
                )
            }
        }

        composable(
            route     = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) { back ->
            val customerId = back.arguments?.getLong("customerId") ?: return@composable
            CustomerDetailScreen(
                customerId = customerId,
                windowSize = windowSize,
                onBack     = { navController.popBackStack() },
                onNewBill  = { navController.navigate(Screen.NewBill.withCustomer(customerId)) }
            )
        }

        composable(Screen.InvoiceHistory.route) {
            AdaptiveScaffold(navController = navController, windowSize = windowSize) { padding ->
                InvoiceHistoryScreen(
                    contentPadding = padding,
                    windowSize     = windowSize,
                    onBack         = { navController.popBackStack() },
                    onInvoice      = { id -> navController.navigate(Screen.InvoiceDetail.withId(id)) }
                )
            }
        }

        composable(
            route     = Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
        ) { back ->
            val invoiceId = back.arguments?.getLong("invoiceId") ?: return@composable
            InvoiceDetailScreen(
                invoiceId  = invoiceId,
                windowSize = windowSize,
                onBack     = { navController.popBackStack() }
            )
        }

        composable(Screen.Melting.route) {
            AdaptiveScaffold(navController = navController, windowSize = windowSize) { padding ->
                MeltingScreen(contentPadding = padding, windowSize = windowSize,
                    onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.Backup.route) {
            AdaptiveScaffold(navController = navController, windowSize = windowSize) { padding ->
                BackupScreen(onBack = { navController.popBackStack() }, windowSize = windowSize)
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack       = { navController.popBackStack() },
                onDataImport = { navController.navigate(Screen.DataImport.route) },
                windowSize   = windowSize
            )
        }

        composable(Screen.DataImport.route) {
            DataImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Analytics.route) {
            AnalyticsDashboardScreen(onBack = { navController.popBackStack() }, windowSize = windowSize)
        }

        composable(Screen.HallmarkScan.route) {
            HallmarkScannerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route     = "new_bill?customerId={customerId}",
            arguments = listOf(navArgument("customerId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { back ->
            val customerId = back.arguments?.getString("customerId")?.toLongOrNull()
            BillingScreen(
                preselectedCustomerId = customerId,
                windowSize            = windowSize,
                onBack                = { navController.popBackStack() },
                onInvoiceSaved        = { id ->
                    navController.navigate(Screen.InvoiceDetail.withId(id)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }
    }
}
