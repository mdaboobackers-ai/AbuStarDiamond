@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.R
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.PaymentStatus
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

// ─── Dashboard UI State ────────────────────────────────────────────────────────
data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val todayInvoiceCount: Int = 0,
    val todaySalesAmount: Double = 0.0,
    val todayTotalWeight: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val totalCustomers: Int = 0
)

// ─── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao
) : ViewModel() {

    private val todayStart: Date
        get() = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    val uiState: StateFlow<DashboardUiState> = combine(
        settingsRepo.settingsFlow,
        invoiceDao.getTodayInvoiceCount(todayStart),
        invoiceDao.getTodaySalesAmount(todayStart).map { it ?: 0.0 },
        invoiceDao.getTodasTotalWeight(todayStart).map { it ?: 0.0 },
        invoiceDao.getTotalPendingAmount().map { it ?: 0.0 },
        customerDao.getCustomerCount()
    ) { arr ->
        DashboardUiState(
            settings          = arr[0] as AppSettings,
            todayInvoiceCount = arr[1] as Int,
            todaySalesAmount  = arr[2] as Double,
            todayTotalWeight  = arr[3] as Double,
            pendingAmount     = arr[4] as Double,
            totalCustomers    = arr[5] as Int
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // Live gold rate fetch
    private val _rateLoading = MutableStateFlow(false)
    val rateLoading = _rateLoading.asStateFlow()
    private val _rateError = MutableStateFlow<String?>(null)
    val rateError = _rateError.asStateFlow()

    fun updateGoldRate(rate24K: Double) = viewModelScope.launch {
        settingsRepo.updateGoldRates(rate24K)
    }

    /** FIX: Live rate fetch from GoldAPI (free tier) */
    fun fetchLiveGoldRate() = viewModelScope.launch {
        _rateLoading.value = true
        _rateError.value   = null
        try {
            // Using a free gold price endpoint — rate in USD/troy oz → INR/gram conversion
            val url = "https://api.metals.live/v1/spot/gold"
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout    = 8000
                    conn.requestMethod  = "GET"
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        // Response: [{"price":1950.5}] — USD per troy oz
                        val priceStr = text.substringAfter("\"price\":").substringBefore("}")
                        val usdPerOz = priceStr.trim().toDoubleOrNull()
                        // Convert: 1 troy oz = 31.1035g; USD→INR ~83.5 (approximate)
                        if (usdPerOz != null) {
                            // Rate per gram in INR (approx — user can also override)
                            (usdPerOz / 31.1035) * 83.5
                        } else null
                    } else null
                } catch (e: Exception) { null }
            }
            if (result != null && result > 0) {
                settingsRepo.updateGoldRates(result)
            } else {
                _rateError.value = "Could not fetch rate — update manually"
            }
        } catch (e: Exception) {
            _rateError.value = "Network error: ${e.message}"
        } finally {
            _rateLoading.value = false
        }
    }
}

// ─── Dashboard Screen ───────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    onNewBill: () -> Unit,
    onAddCustomer: () -> Unit,
    onBackup: () -> Unit,
    onHistory: () -> Unit,
    onCustomers: () -> Unit,
    onSettings: () -> Unit,
    onAnalytics: () -> Unit = {},
    onMelting: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state       by viewModel.uiState.collectAsState()
    val rateLoading by viewModel.rateLoading.collectAsState()
    val rateError   by viewModel.rateError.collectAsState()
    var showRateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        coil.compose.AsyncImage(
                            model             = R.drawable.abu_star_logo,
                            contentDescription = null,
                            modifier          = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Column {
                            Text(
                                "ABU STAR DIAMONDS",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = AuraColors.PrimaryContainer,
                                fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold
                            )
                            if (state.settings.deviceOwnerName.isNotEmpty()) {
                                Text(
                                    state.settings.deviceOwnerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, null, tint = AuraColors.OnSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f)
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Ambient gold glow
            Box(
                Modifier.size(350.dp).align(Alignment.TopEnd).offset(80.dp, (-60).dp)
                    .blur(140.dp)
                    .background(AuraColors.PrimaryContainer.copy(alpha = 0.04f))
            )

            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Market Pulse ───────────────────────────────────────────
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        SectionHeader("Market Pulse")
                        // FIX: Only refresh button — edit goes via Settings
                        OutlinedIconButton(
                            onClick  = { viewModel.fetchLiveGoldRate() },
                            enabled  = !rateLoading,
                            border   = BorderStroke(1.dp, AuraColors.GlassWhite20),
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (rateLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AuraColors.PrimaryContainer, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Rate error
                rateError?.let { err ->
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .background(AuraColors.ErrorContainer.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = AuraColors.Error, modifier = Modifier.size(14.dp))
                            Text(err, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                        }
                    }
                }

                // Gold rate cards
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldRateCard("24K", state.settings.goldRate24K, "up",   Modifier.weight(1f))
                        GoldRateCard("22K", state.settings.goldRate22K, "flat", Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldRateCard("20K", state.settings.goldRate20K, "flat", Modifier.weight(1f))
                        GoldRateCard("18K", state.settings.goldRate18K, "down", Modifier.weight(1f))
                    }
                }

                // ── Daily Summary ──────────────────────────────────────────
                item { Spacer(Modifier.height(2.dp)) }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Daily Ledger Summary",
                                style    = MaterialTheme.typography.headlineMedium,
                                color    = AuraColors.OnSurface)
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DailyStat("Total Amount",
                                    "₹${String.format("%,.0f", state.todaySalesAmount)}",
                                    isGold = true)
                                DailyStat("Net Weight",
                                    "${String.format("%.2f", state.todayTotalWeight)}g")
                                DailyStat("Invoices", "${state.todayInvoiceCount}")
                                DailyStat("Customers", "${state.totalCustomers}")
                            }
                            Spacer(Modifier.height(12.dp))
                            // Animated progress bar
                            val progress by animateFloatAsState(
                                targetValue      = if (state.todayInvoiceCount > 0) 0.75f else 0f,
                                animationSpec    = tween(800, easing = EaseOutCubic),
                                label            = "progress"
                            )
                            Box(
                                Modifier.fillMaxWidth().height(3.dp)
                                    .background(AuraColors.GlassWhite10, RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(AuraColors.PrimaryContainer, AuraColors.Primary)
                                            ),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                // ── Pending Payments alert ─────────────────────────────────
                if (state.pendingAmount > 0) {
                    item {
                        GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment    = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, null,
                                        tint     = AuraColors.Error,
                                        modifier = Modifier.size(22.dp))
                                    Column {
                                        Text("Pending Payments",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AuraColors.OnSurfaceVariant, letterSpacing = 1.sp)
                                        Text(
                                            "₹${String.format("%,.0f", state.pendingAmount)}",
                                            style    = MaterialTheme.typography.titleLarge,
                                            color    = AuraColors.Error, fontSize = 20.sp
                                        )
                                    }
                                }
                                TextButton(onClick = onHistory) {
                                    Text("View All", color = AuraColors.PrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // ── Quick Actions ──────────────────────────────────────────
                item { Spacer(Modifier.height(4.dp)) }
                item { SectionHeader("Operations") }

                // New Bill CTA
                item {
                    Button(
                        onClick  = onNewBill,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = AuraColors.PrimaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, null, tint = AuraColors.OnPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "NEW BILL",
                            style        = MaterialTheme.typography.labelSmall,
                            color        = AuraColors.OnPrimary,
                            fontWeight   = FontWeight.Bold,
                            fontSize     = 14.sp, letterSpacing = 2.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowForward, null, tint = AuraColors.OnPrimary)
                    }
                }

                // Secondary actions grid
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAction("CLIENTS",  Icons.Default.Group,         onCustomers,  Modifier.weight(1f))
                        QuickAction("HISTORY",  Icons.Default.History,        onHistory,    Modifier.weight(1f))
                        QuickAction("MELT",     Icons.Default.Whatshot,       onMelting,    Modifier.weight(1f))
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAction("ADD CLIENT", Icons.Default.PersonAdd,   onAddCustomer, Modifier.weight(1f))
                        QuickAction("BACKUP",     Icons.Default.CloudUpload, onBackup,      Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("ANALYTICS",  Icons.Default.BarChart,    onAnalytics,   Modifier.weight(1f))
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showRateDialog) {
        GoldRateEditDialog(
            s       = state.settings,
            onDismiss = { showRateDialog = false },
            onSave  = { r -> viewModel.updateGoldRate(r); showRateDialog = false }
        )
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────
@Composable
private fun DailyStat(label: String, value: String, isGold: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            style    = MaterialTheme.typography.labelSmall,
            color    = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f),
            fontSize = 8.sp, letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style      = MaterialTheme.typography.titleLarge,
            color      = if (isGold) AuraColors.PrimaryContainer else AuraColors.OnSurface,
            fontSize   = 17.sp
        )
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(58.dp)
            .background(AuraColors.GlassWhite5, RoundedCornerShape(14.dp))
            .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
            Text(
                label,
                style        = MaterialTheme.typography.labelSmall,
                color        = AuraColors.OnSurface,
                fontSize     = 9.sp, letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun GoldRateEditDialog(
    s: AppSettings,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var rateText by remember { mutableStateOf(s.goldRate24K.toString()) }
    val r = rateText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = AuraColors.SurfaceContainerHigh,
        title = { Text("Update Gold Rate", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostTextField(
                    rateText, { rateText = it }, "24K Rate (₹ per gram)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                // Live preview of other karats
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "22K (91.6%)" to r * 0.916,
                            "20K (85.0%)" to r * 0.85,
                            "18K (75.0%)" to r * 0.75
                        ).forEach { (label, calc) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                                Text("₹${String.format("%,.2f", calc)}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.PrimaryContainer)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GoldButton("Save", onClick = {
                rateText.toDoubleOrNull()?.let { onSave(it) }
            })
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AuraColors.OnSurfaceVariant)
            }
        }
    )
}
