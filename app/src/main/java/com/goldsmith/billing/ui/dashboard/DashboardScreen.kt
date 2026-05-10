@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.dashboard

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

// ─── ViewModel ────────────────────────────────────────────────────────────────
data class DashboardUiState(
    val settings: AppSettings = AppSettings(),
    val todayInvoiceCount: Int = 0,
    val todaySalesAmount: Double = 0.0,
    val monthlySalesAmount: Double = 0.0,
    val todayTotalWeight: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val totalCustomers: Int = 0,
    val upcomingEvents: List<CustomerEvent> = emptyList()
)

data class CustomerEvent(val customer: com.goldsmith.billing.data.model.Customer, val type: String, val date: Date)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
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

    val uiState: StateFlow<DashboardUiState> = combine(
        settingsRepo.settingsFlow,
        invoiceDao.getTodayInvoiceCount(todayStart),
        invoiceDao.getTodaySalesAmount(todayStart).map { it ?: 0.0 },
        invoiceDao.getMonthlySalesAmount(monthStart).map { it ?: 0.0 },
        invoiceDao.getTodasTotalWeight(todayStart).map { it ?: 0.0 },
        invoiceDao.getTotalPendingAmount().map { it ?: 0.0 },
        customerDao.getCustomerCount(),
        customerDao.getAllCustomers() 
    ) { values ->
        val allCustomers = values[7] as List<com.goldsmith.billing.data.model.Customer>
        DashboardUiState(
            settings = values[0] as AppSettings,
            todayInvoiceCount = (values[1] as Int),
            todaySalesAmount = (values[2] as Double),
            monthlySalesAmount = (values[3] as Double),
            todayTotalWeight = (values[4] as Double),
            pendingAmount = (values[5] as Double),
            totalCustomers = (values[6] as Int),
            upcomingEvents = calculateUpcomingEvents(allCustomers)
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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        coil.compose.AsyncImage(
                            model = R.drawable.abu_star_logo,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text("ABU STAR DIAMONDS", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
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

            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Market Pulse ──────────────────────────────────────────────
                item {
                    SectionHeader(
                        title = "Market Pulse"
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoldRateCard("Pure Gold 24K", state.settings.goldRate24K, "up", Modifier.weight(1f))
                        GoldRateCard("Standard 22K", state.settings.goldRate22K, "flat", Modifier.weight(1f))
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
                                        GoldButton("Send Wish", onClick = { /* Share wish */ }, modifier = Modifier.height(32.dp))
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
                            Text("Daily Ledger Summary", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                            Spacer(Modifier.height(20.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SummaryMetric("Total Amount", "₹${String.format("%,.0f", state.todaySalesAmount)}", isGold = true)
                                SummaryMetric("Net Weight", "${String.format("%.2f", state.todayTotalWeight)}g", isGold = false)
                                SummaryMetric("Invoices", "${state.todayInvoiceCount}", isGold = false)
                            }
                        }
                    }
                }

                // ... Quick actions continue ...
                item {
                    if (state.pendingAmount > 0) {
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
                                        Text("Pending Payments", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, letterSpacing = 1.sp)
                                        Text("₹${String.format("%,.0f", state.pendingAmount)}", style = MaterialTheme.typography.titleLarge, color = AuraColors.Error, fontSize = 22.sp)
                                    }
                                }
                                TextButton(onClick = onHistory) {
                                    Text("View", color = AuraColors.PrimaryContainer, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                item { SectionHeader("Operations") }
                item {
                    Button(
                        onClick = onNewBill,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuraColors.PrimaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, null, tint = AuraColors.OnPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("NEW BILL", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowForward, null, tint = AuraColors.OnPrimary)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard("ADD CUSTOMER", Icons.Default.PersonAdd, onClick = onAddCustomer, modifier = Modifier.weight(1f))
                        QuickActionCard("BACKUP", Icons.Default.CloudUpload, onClick = onBackup, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard("HISTORY", Icons.Default.History, onClick = onHistory, modifier = Modifier.weight(1f))
                        QuickActionCard("MELTING", Icons.Default.Whatshot, onClick = onMelting, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    QuickActionCard("ANALYTICS", Icons.Default.Analytics, onClick = onAnalytics, modifier = Modifier.fillMaxWidth())
                }
                item {
                    QuickActionCard("HALLMARK OCR", Icons.Default.DocumentScanner, onClick = onHallmarkScan, modifier = Modifier.fillMaxWidth())
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

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
