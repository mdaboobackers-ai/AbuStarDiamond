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

sealed class Screen(val route: String) {
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

@Composable
fun GoldsmithNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.PinVerify.route,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val settings by authViewModel.settings.collectAsState(com.goldsmith.billing.data.repository.AppSettings())

    NavHost(navController = navController, startDestination = startDestination) {

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
                    val nextRoute = if (settings.userPrefix.isEmpty()) Screen.PrefixSelect.route else Screen.PinSetup.route
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.PinVerify.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNewBill = { navController.navigate(Screen.NewBill.withCustomer()) },
                onAddCustomer = { navController.navigate(Screen.Customers.route) },
                onBackup = { navController.navigate(Screen.Backup.route) },
                onHistory = { navController.navigate(Screen.InvoiceHistory.route) },
                onCustomers = { navController.navigate(Screen.Customers.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onMelting = { navController.navigate(Screen.Melting.route) },
                onAnalytics = { navController.navigate(Screen.Analytics.route) },
                onHallmarkScan = { navController.navigate(Screen.HallmarkScan.route) }
            )
        }

        composable(
            route = "new_bill?customerId={customerId}",
            arguments = listOf(navArgument("customerId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull()
            BillingScreen(
                preselectedCustomerId = customerId,
                onBack = { navController.popBackStack() },
                onInvoiceCreated = { invoiceId ->
                    navController.navigate(Screen.InvoiceDetail.withId(invoiceId)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(Screen.Customers.route) {
            CustomerListScreen(
                onBack = { navController.popBackStack() },
                onCustomerDetail = { id -> navController.navigate(Screen.CustomerDetail.withId(id)) },
                onNewBillForCustomer = { id -> navController.navigate(Screen.NewBill.withCustomer(id)) }
            )
        }

        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments!!.getLong("customerId")
            CustomerDetailScreen(
                customerId = customerId,
                onBack = { navController.popBackStack() },
                onNewBill = { navController.navigate(Screen.NewBill.withCustomer(customerId)) }
            )
        }

        composable(Screen.InvoiceHistory.route) {
            InvoiceHistoryScreen(
                onBack = { navController.popBackStack() },
                onInvoiceDetail = { id -> navController.navigate(Screen.InvoiceDetail.withId(id)) },
                onNewBill = { navController.navigate(Screen.NewBill.withCustomer()) }
            )
        }

        composable(
            route = Screen.InvoiceDetail.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments!!.getLong("invoiceId")
            InvoiceDetailScreen(
                invoiceId = invoiceId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Melting.route) {
            MeltingScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onImport = { navController.navigate(Screen.DataImport.route) }
            )
        }

        composable(Screen.DataImport.route) {
            DataImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Backup.route) {
            BackupScreen(onBack = {
                if (!navController.popBackStack()) {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            })
        }

        composable(Screen.Analytics.route) {
            AnalyticsDashboardScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.HallmarkScan.route) {
            HallmarkScannerScreen(onBack = { navController.popBackStack() })
        }
    }
}
