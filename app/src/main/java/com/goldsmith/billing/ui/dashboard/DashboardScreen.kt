@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.R
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.CompanyProfile
import com.goldsmith.billing.data.remote.MarketRateSnapshot
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.adaptive.WindowSize
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.CelebrationWishUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val todayInvoiceCount: Int = 0,
    val todaySalesAmount: Double = 0.0,
    val monthlySalesAmount: Double = 0.0,
    val todayTotalWeight: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val totalCustomers: Int = 0,
    val upcomingEvents: List<CustomerEvent> = emptyList(),
    val companyProfile: CompanyProfile? = null,
    val marketRate: MarketRateSnapshot = MarketRateSnapshot(rate24K = 7245.0)
)

data class CustomerEvent(val customer: com.goldsmith.billing.data.model.Customer, val type: String, val date: Date)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val companyProfileDao: CompanyProfileDao,
    private val goldRateService: com.goldsmith.billing.data.remote.GoldRateService
) : ViewModel() {

    private val todayStart: Date get() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.time
    }

    private val monthStart: Date get() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.time
    }

    private val marketRate = MutableStateFlow(MarketRateSnapshot(rate24K = 7245.0))

    val uiState: StateFlow<DashboardUiState> = combine(
        settingsRepo.settingsFlow,
        invoiceDao.getTodayInvoiceCount(todayStart),
        invoiceDao.getTodaySalesAmount(todayStart).map { it ?: 0.0 },
        invoiceDao.getMonthlySalesAmount(monthStart).map { it ?: 0.0 },
        invoiceDao.getTodasTotalWeight(todayStart).map { it ?: 0.0 },
        invoiceDao.getTotalPendingAmount().map { it ?: 0.0 },
        customerDao.getCustomerCount(),
        customerDao.getAllCustomers(),
        companyProfileDao.getProfile(),
        marketRate
    ) { values ->
        val allCustomers = values[7] as List<com.goldsmith.billing.data.model.Customer>
        val settings = values[0] as AppSettings
        DashboardUiState(
            settings = settings,
            todayInvoiceCount = (values[1] as Int),
            todaySalesAmount = (values[2] as Double),
            monthlySalesAmount = (values[3] as Double),
            todayTotalWeight = (values[4] as Double),
            pendingAmount = (values[5] as Double),
            totalCustomers = (values[6] as Int),
            upcomingEvents = calculateUpcomingEvents(allCustomers),
            companyProfile = values[8] as CompanyProfile?,
            marketRate = values[9] as MarketRateSnapshot
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun calculateUpcomingEvents(customers: List<com.goldsmith.billing.data.model.Customer>): List<CustomerEvent> {
        val today = Calendar.getInstance()
        val events = mutableListOf<CustomerEvent>()
        customers.forEach { c ->
            c.dob?.let { date ->
                if (isSameDayMonth(today, date)) events.add(CustomerEvent(c, "Birthday", date))
            }
            c.anniversary?.let { date ->
                if (isSameDayMonth(today, date)) events.add(CustomerEvent(c, "Anniversary", date))
            }
        }
        return events
    }

    private fun isSameDayMonth(c1: Calendar, d2: Date): Boolean {
        val c2 = Calendar.getInstance().apply { time = d2 }
        return c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH) &&
               c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
    }

    fun updateGoldRate(rate24K: Double) = viewModelScope.launch {
        settingsRepo.updateGoldRates(rate24K)
    }

    fun refreshMarketRate(savedRate24K: Double) = viewModelScope.launch {
        val latestRates = goldRateService.fetchLatestGoldRates()
        if (latestRates != null && latestRates.rate24K in 10_000.0..25_000.0) {
            settingsRepo.updateGoldRatesManual(
                rate24K = latestRates.rate24K,
                rate22K = latestRates.rate22K ?: latestRates.rate24K * 0.916,
                rate20K = latestRates.rate20K ?: latestRates.rate24K * (20.0 / 24.0),
                rate18K = latestRates.rate18K ?: latestRates.rate24K * 0.75
            )
            marketRate.value = MarketRateSnapshot(
                rate24K = latestRates.rate24K,
                sourceLabel = latestRates.sourceLabel
            )
        } else {
            marketRate.value = goldRateService.fetchCurrentMarketSnapshot(savedRate24K)
        }
    }
}

// ─── Dashboard Screen ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNewBill: () -> Unit,
    onAddCustomer: () -> Unit,
    onBackup: () -> Unit,
    onHistory: () -> Unit,
    onCustomers: () -> Unit,
    onSettings: () -> Unit,
    onMelting: () -> Unit,
    onAnalytics: () -> Unit,
    onHallmarkScan: () -> Unit,
    windowSize: WindowSize = WindowSize.COMPACT,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshMarketRate(state.settings.goldRate24K)
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        coil.compose.AsyncImage(
                            model = state.companyProfile?.logoUri?.takeIf { it.isNotBlank() } ?: R.drawable.abu_star_logo,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            state.companyProfile?.companyName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, null, tint = AuraColors.OnSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Gold ambient glow top-right
            Box(
                Modifier.size(300.dp).offset(100.dp, (-50).dp)
                    .blur(120.dp)
                    .background(AuraColors.PrimaryContainer.copy(alpha = 0.05f))
            )

            val dashboardModifier = if (windowSize == WindowSize.COMPACT) {
                Modifier.fillMaxSize().padding(padding)
            } else {
                Modifier.fillMaxHeight()
                    .widthIn(max = if (windowSize == WindowSize.MEDIUM) 760.dp else 980.dp)
                    .align(Alignment.TopCenter)
                    .padding(padding)
            }

            LazyColumn(
                dashboardModifier,
                contentPadding = PaddingValues(
                    horizontal = if (windowSize == WindowSize.COMPACT) 16.dp else 28.dp,
                    vertical = 18.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Market Pulse ──────────────────────────────────────────────
                item {
                    SectionHeader(
                        title = stringResource(R.string.market_pulse)
                    )
                }
                item {
                    val location = listOf(state.marketRate.city, state.marketRate.state, state.marketRate.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (location.isNotBlank()) location else "Tamil Nadu, India",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraColors.OnSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${state.marketRate.sourceLabel} - manual rates stay saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AuraColors.OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoldRateCard(stringResource(R.string.pure_gold_24k), state.settings.goldRate24K, "up", Modifier.weight(1f))
                        GoldRateCard(stringResource(R.string.standard_22k), state.settings.goldRate22K, "flat", Modifier.weight(1f))
                    }
                }

                // ── Customer Alerts ───────────────────────────────────────────
                if (state.upcomingEvents.isNotEmpty()) {
                    item { SectionHeader("Client Celebrations") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.upcomingEvents) { event ->
                                GlassCard(Modifier.width(200.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Icon(
                                            if (event.type == "Birthday") Icons.Default.Cake else Icons.Default.Celebration,
                                            null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(event.customer.name, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.Bold)
                                        Text(event.type, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant)
                                        Spacer(Modifier.height(12.dp))
                                        GoldButton(
                                            "Send Wish",
                                            onClick = {
                                                sendCelebrationWish(
                                                    context = context,
                                                    event = event,
                                                    senderName = state.companyProfile?.companyName
                                                )
                                            },
                                            modifier = Modifier.height(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Daily Summary ─────────────────────────────────────────────
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Monthly Performance", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 1.sp)
                            Text("₹${String.format("%,.0f", state.monthlySalesAmount)} Sales", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                            Spacer(Modifier.height(16.dp))
                            Divider(color = AuraColors.GlassWhite10)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.daily_summary), style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                            Spacer(Modifier.height(20.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SummaryMetric(stringResource(R.string.total_amount), "₹${String.format("%,.0f", state.todaySalesAmount)}", isGold = true)
                                SummaryMetric(stringResource(R.string.net_weight), "${String.format("%.2f", state.todayTotalWeight)}g", isGold = false)
                                SummaryMetric(stringResource(R.string.invoices_issued), "${state.todayInvoiceCount}", isGold = false)
                            }
                        }
                    }
                }

                // ... Quick actions continue ...
                item {
                    if (state.pendingAmount > 0.005) {
                        GlassCard(
                            Modifier.fillMaxWidth(),
                            goldBorder = true
                        ) {
                            Row(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, null, tint = AuraColors.Error, modifier = Modifier.size(24.dp))
                                    Column {
                                        Text(stringResource(R.string.pending_payments), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, letterSpacing = 1.sp)
                                        Text("₹${String.format("%,.0f", state.pendingAmount)}", style = MaterialTheme.typography.titleLarge, color = AuraColors.Error, fontSize = 22.sp)
                                    }
                                }
                                TextButton(onClick = onHistory) {
                                    Text(stringResource(R.string.view), color = AuraColors.PrimaryContainer, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.operations)) }
                item {
                    Button(
                        onClick = onNewBill,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuraColors.PrimaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, null, tint = AuraColors.OnPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.new_bill).uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowForward, null, tint = AuraColors.OnPrimary)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard(stringResource(R.string.add_customer).uppercase(), Icons.Default.PersonAdd, onClick = onAddCustomer, modifier = Modifier.weight(1f))
                        QuickActionCard(stringResource(R.string.backup_to_drive).uppercase(), Icons.Default.CloudUpload, onClick = onBackup, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard(stringResource(R.string.nav_history).uppercase(), Icons.Default.History, onClick = onHistory, modifier = Modifier.weight(1f))
                        QuickActionCard(stringResource(R.string.melting_module).uppercase(), Icons.Default.Whatshot, onClick = onMelting, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    QuickActionCard(stringResource(R.string.analytics_dashboard).uppercase(), Icons.Default.Analytics, onClick = onAnalytics, modifier = Modifier.fillMaxWidth())
                }
                item {
                    QuickActionCard(stringResource(R.string.hallmark_ocr).uppercase(), Icons.Default.DocumentScanner, onClick = onHallmarkScan, modifier = Modifier.fillMaxWidth())
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

}

private fun sendCelebrationWish(
    context: Context,
    event: CustomerEvent,
    senderName: String?
) {
    val message = CelebrationWishUtil.buildWishMessage(
        customerName = event.customer.name,
        eventType = event.type,
        senderName = senderName
    )
    val phone = CelebrationWishUtil.whatsappPhoneNumber(event.customer.phone)

    if (phone != null && launchWhatsAppWish(context, phone, message, "com.whatsapp")) return
    if (phone != null && launchWhatsAppWish(context, phone, message, "com.whatsapp.w4b")) return

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "Send Wish"))
    }.onFailure {
        Toast.makeText(context, "No app available to send wish", Toast.LENGTH_SHORT).show()
    }
}

private fun launchWhatsAppWish(
    context: Context,
    phone: String,
    message: String,
    packageName: String
): Boolean {
    val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage(packageName)
    }
    return runCatching {
        context.startActivity(intent)
    }.isSuccess
}

@Composable
private fun SummaryMetric(label: String, value: String, isGold: Boolean) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = if (isGold) AuraColors.PrimaryContainer else AuraColors.OnSurface, fontSize = 20.sp)
    }
}

@Composable
private fun QuickActionCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(64.dp)
            .background(AuraColors.GlassWhite5, RoundedCornerShape(16.dp))
            .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun GoldRateEditDialog(current24K: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var rateText by remember { mutableStateOf(current24K.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Update Gold Rate", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostTextField(rateText, { rateText = it }, "24K Rate (per gram ₹)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                val r = rateText.toDoubleOrNull() ?: 0.0
                Text("22K: ₹${String.format("%.2f", r * 0.916)}", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            GoldButton("Save", onClick = { rateText.toDoubleOrNull()?.let { onSave(it) } })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
