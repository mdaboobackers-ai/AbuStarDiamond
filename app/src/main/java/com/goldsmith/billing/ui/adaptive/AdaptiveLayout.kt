package com.goldsmith.billing.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.goldsmith.billing.navigation.Screen

// ─── Window size buckets (no Adaptive library dependency needed) ──────────────

enum class WindowSize { COMPACT, MEDIUM, EXPANDED }

/**
 * COMPACT  → phone portrait       (< 600 dp wide)
 * MEDIUM   → phone landscape / small tablet (600–839 dp)
 * EXPANDED → large tablet / desktop (≥ 840 dp)
 */
@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < 600  -> WindowSize.COMPACT
        configuration.screenWidthDp < 840  -> WindowSize.MEDIUM
        else                                -> WindowSize.EXPANDED
    }
}

/** True when a NavigationRail / NavigationDrawer should be used instead of a BottomNavBar */
val WindowSize.useRail: Boolean get() = this != WindowSize.COMPACT

/** Number of grid columns for list screens */
val WindowSize.gridColumns: Int get() = when (this) {
    WindowSize.COMPACT  -> 1
    WindowSize.MEDIUM   -> 2
    WindowSize.EXPANDED -> 3
}

/** Card content padding adapts to available width */
val WindowSize.cardPadding: Dp get() = when (this) {
    WindowSize.COMPACT  -> 16.dp
    WindowSize.MEDIUM   -> 20.dp
    WindowSize.EXPANDED -> 28.dp
}

/** Max content width on large screens — keeps content readable */
val WindowSize.maxContentWidth: Dp get() = when (this) {
    WindowSize.COMPACT  -> Dp.Infinity
    WindowSize.MEDIUM   -> 680.dp
    WindowSize.EXPANDED -> 900.dp
}

// ─── Navigation destinations ──────────────────────────────────────────────────

data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

val appNavDestinations = listOf(
    NavDestination(Screen.Dashboard.route,      "Home",     Icons.Filled.Home),
    NavDestination(Screen.Customers.route,      "Clients",  Icons.Filled.People),
    NavDestination(Screen.InvoiceHistory.route, "Bills",    Icons.Filled.Receipt),
    NavDestination(Screen.Melting.route,        "Melting",  Icons.Filled.Whatshot),
    NavDestination(Screen.Backup.route,         "Backup",   Icons.Filled.CloudUpload),
)

// ─── Adaptive scaffold ────────────────────────────────────────────────────────

/**
 * Drop-in replacement for Scaffold that adds:
 *  - Full-screen content on phones, with Dashboard as the navigation hub
 *  - NavigationRail       on medium screens / landscape tablets
 *  - NavigationDrawer     on large tablets / EXPANDED
 *
 * Usage: wrap your existing screen bodies in AdaptiveScaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveScaffold(
    navController: NavController,
    windowSize: WindowSize,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val currentRoute by navController.currentBackStackEntryAsState()
    val currentDest = currentRoute?.destination?.route

    when {
        // ── EXPANDED: permanent NavigationDrawer ───────────────────────────
        windowSize == WindowSize.EXPANDED -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(220.dp)) {
                        Spacer(Modifier.height(16.dp))
                        appNavDestinations.forEach { dest ->
                            NavigationDrawerItem(
                                icon  = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                selected = currentDest == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            ) {
                Scaffold(topBar = topBar, floatingActionButton = floatingActionButton, content = content)
            }
        }

        // ── MEDIUM: NavigationRail ─────────────────────────────────────────
        windowSize == WindowSize.MEDIUM -> {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(8.dp))
                    appNavDestinations.forEach { dest ->
                        NavigationRailItem(
                            icon     = { Icon(dest.icon, contentDescription = dest.label) },
                            label    = { Text(dest.label) },
                            selected = currentDest == dest.route,
                            onClick  = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
                Scaffold(
                    modifier             = Modifier.weight(1f),
                    topBar               = topBar,
                    floatingActionButton = floatingActionButton,
                    content              = content
                )
            }
        }

        // ── COMPACT: content only; Dashboard carries the modern phone navigation hub.
        else -> {
            Scaffold(
                topBar               = topBar,
                floatingActionButton = floatingActionButton,
                content = content
            )
        }
    }
}

// ─── Adaptive content wrapper ─────────────────────────────────────────────────

/**
 * Centers content and caps its width on tablets so it stays readable.
 * On phones it fills the full width.
 */
@Composable
fun AdaptiveContentWidth(
    windowSize: WindowSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().then(modifier)) {
        val maxWidth = windowSize.maxContentWidth
        Box(
            modifier = if (maxWidth == Dp.Infinity) Modifier.fillMaxWidth()
                       else Modifier.widthIn(max = maxWidth).align(androidx.compose.ui.Alignment.TopCenter),
            content = content
        )
    }
}

// ─── Two-pane layout for tablets ─────────────────────────────────────────────

/**
 * On EXPANDED screens: shows a list pane on the left and a detail pane on the right.
 * On COMPACT / MEDIUM: shows only the list pane (detail navigates separately).
 */
@Composable
fun TwoPaneLayout(
    windowSize: WindowSize,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    showDetail: Boolean
) {
    if (windowSize == WindowSize.EXPANDED) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.38f).fillMaxHeight()) { listPane() }
            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
            Box(Modifier.weight(0.62f).fillMaxHeight()) {
                if (showDetail) detailPane()
                else Box(Modifier.fillMaxSize()) // empty right pane placeholder
            }
        }
    } else {
        if (showDetail) detailPane() else listPane()
    }
}
