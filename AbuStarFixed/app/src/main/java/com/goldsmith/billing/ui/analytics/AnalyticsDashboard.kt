@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.analytics

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class MonthlyData(val month: String, val amount: Double, val weight: Double, val count: Int)
data class CustomerStat(val customer: Customer, val totalAmount: Double, val invoiceCount: Int)

data class AnalyticsState(
    val loading: Boolean = true,
    val totalRevenue: Double = 0.0,
    val totalGoldGrams: Double = 0.0,
    val totalInvoices: Int = 0,
    val pendingAmount: Double = 0.0,
    val monthlyData: List<MonthlyData> = emptyList(),
    val topCustomers: List<CustomerStat> = emptyList(),
    val paidCount: Int = 0,
    val partialCount: Int = 0,
    val pendingCount: Int = 0,
    val avgInvoiceValue: Double = 0.0
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao
) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsState())
    val state = _state.asStateFlow()

    init { loadAnalytics() }

    fun loadAnalytics() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        try {
            val cal = Calendar.getInstance()
            val now = cal.time
            cal.add(Calendar.MONTH, -11)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val start = cal.time
            val all  = invoiceDao.getInvoicesForRange(start, now)
            val customers = customerDao.getAllCustomers().first()

            val sdf = SimpleDateFormat("MMM yy", Locale.getDefault())
            val mMap = linkedMapOf<String, Triple<Double, Double, Int>>()
            all.forEach { inv ->
                val k = sdf.format(inv.date)
                val e = mMap[k] ?: Triple(0.0, 0.0, 0)
                mMap[k] = Triple(e.first + inv.totalAmount, e.second + inv.totalNetWeightGrams, e.third + 1)
            }
            val monthly = mMap.entries.map { (k, v) -> MonthlyData(k, v.first, v.second, v.third) }

            val custMap = mutableMapOf<Long, Pair<Double, Int>>()
            all.forEach { inv ->
                val e = custMap[inv.customerId] ?: Pair(0.0, 0)
                custMap[inv.customerId] = Pair(e.first + inv.totalAmount, e.second + 1)
            }
            val top = custMap.entries.sortedByDescending { it.value.first }.take(5)
                .mapNotNull { (cid, d) -> customers.find { it.id == cid }?.let { CustomerStat(it, d.first, d.second) } }

            val total   = all.sumOf { it.totalAmount }
            val pending = all.filter { it.paymentStatus != PaymentStatus.PAID }.sumOf { it.remainingCash }

            _state.value = AnalyticsState(
                loading        = false,
                totalRevenue   = total,
                totalGoldGrams = all.sumOf { it.totalNetWeightGrams },
                totalInvoices  = all.size,
                pendingAmount  = pending,
                monthlyData    = monthly,
                topCustomers   = top,
                paidCount      = all.count { it.paymentStatus == PaymentStatus.PAID },
                partialCount   = all.count { it.paymentStatus == PaymentStatus.PARTIAL },
                pendingCount   = all.count { it.paymentStatus == PaymentStatus.PENDING },
                avgInvoiceValue = if (all.isNotEmpty()) total / all.size else 0.0
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false)
        }
    }
}

@Composable
fun AnalyticsDashboardScreen(onBack: () -> Unit, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("ANALYTICS", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = { IconButton(onClick = { viewModel.loadAnalytics() }) { Icon(Icons.Default.Refresh, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AuraColors.PrimaryContainer) }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard("Total Revenue", fmtInr(state.totalRevenue), Icons.Default.Payments, AuraColors.Primary, Modifier.weight(1f))
                        KpiCard("Gold Traded",   "${String.format("%.1f", state.totalGoldGrams)}g", Icons.Default.Diamond, AuraColors.PrimaryContainer, Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard("Invoices",    "${state.totalInvoices}", Icons.Default.Receipt, AuraColors.OnSurface, Modifier.weight(1f))
                        KpiCard("Pending",     fmtInr(state.pendingAmount), Icons.Default.AccessTime, AuraColors.Error, Modifier.weight(1f))
                    }
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("AVG INVOICE VALUE", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
                                Text(fmtInr(state.avgInvoiceValue), style = MaterialTheme.typography.headlineMedium, color = AuraColors.PrimaryContainer, fontSize = 22.sp)
                            }
                            Icon(Icons.Default.TrendingUp, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                        }
                    }
                }

                item { SectionHeader("Payment Status") }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            PaymentDonut(paid = state.paidCount, partial = state.partialCount, pending = state.pendingCount, modifier = Modifier.size(100.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LegendRow("Paid",    state.paidCount,    AuraColors.Primary)
                                LegendRow("Partial", state.partialCount, AuraColors.PrimaryContainer)
                                LegendRow("Pending", state.pendingCount, AuraColors.Error)
                            }
                        }
                    }
                }

                if (state.monthlyData.isNotEmpty()) {
                    item { SectionHeader("Monthly Revenue (12 Months)") }
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                val maxAmt = state.monthlyData.maxOf { it.amount }.coerceAtLeast(1.0)
                                Row(Modifier.fillMaxWidth().height(160.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    state.monthlyData.takeLast(12).forEach { md ->
                                        AnimatedBar((md.amount / maxAmt).toFloat().coerceIn(0f, 1f), md.month, Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    item { SectionHeader("Monthly Gold (grams)") }
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                val maxG = state.monthlyData.maxOf { it.weight }.coerceAtLeast(1.0)
                                Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    state.monthlyData.takeLast(12).forEach { md ->
                                        AnimatedBar((md.weight / maxG).toFloat().coerceIn(0f, 1f), md.month, Modifier.weight(1f), isGold = true)
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.topCustomers.isNotEmpty()) {
                    item { SectionHeader("Top 5 Customers") }
                    itemsIndexed(state.topCustomers) { idx, cs ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(36.dp).background(
                                        when (idx) { 0 -> AuraColors.PrimaryContainer; 1 -> AuraColors.Primary.copy(0.7f); 2 -> AuraColors.Primary.copy(0.5f); else -> AuraColors.GlassWhite10 },
                                        RoundedCornerShape(10.dp)
                                    ), contentAlignment = Alignment.Center
                                ) {
                                    Text("#${idx+1}", style = MaterialTheme.typography.labelSmall, color = if (idx < 3) AuraColors.OnPrimary else AuraColors.OnSurface, fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(cs.customer.name, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text("${cs.invoiceCount} invoice${if (cs.invoiceCount>1) "s" else ""}  ·  ${cs.customer.phone}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(fmtInr(cs.totalAmount), style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    val frac = (cs.totalAmount / state.topCustomers.first().totalAmount).toFloat()
                                    val animFrac by animateFloatAsState(frac, tween(700), label = "prog")
                                    Box(Modifier.width(80.dp).height(4.dp).background(AuraColors.GlassWhite10, RoundedCornerShape(2.dp))) {
                                        Box(Modifier.fillMaxWidth(animFrac).fillMaxHeight().background(
                                            Brush.horizontalGradient(listOf(AuraColors.PrimaryContainer, AuraColors.Primary)), RoundedCornerShape(2.dp)))
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, vc: Color, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 8.sp, letterSpacing = 0.5.sp)
                Icon(icon, null, tint = vc.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = vc, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnimatedBar(fraction: Float, label: String, modifier: Modifier, isGold: Boolean = false) {
    val anim by animateFloatAsState(fraction, tween(800, easing = EaseOutCubic), label = "bar")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth(0.65f).fillMaxHeight(anim)
                .background(
                    Brush.verticalGradient(if (isGold) listOf(AuraColors.PrimaryContainer, AuraColors.Primary.copy(0.3f)) else listOf(AuraColors.PrimaryContainer, AuraColors.PrimaryContainer.copy(0.3f))),
                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                ))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 6.5.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun PaymentDonut(paid: Int, partial: Int, pending: Int, modifier: Modifier) {
    val total = (paid + partial + pending).coerceAtLeast(1)
    val pF by animateFloatAsState(paid.toFloat() / total, tween(900), label = "p")
    val ptF by animateFloatAsState(partial.toFloat() / total, tween(900, 150), label = "pt")
    val penF by animateFloatAsState((pending.toFloat() / total).coerceAtLeast(0f), tween(900, 300), label = "pen")
    val goldC = Color(0xFFE9C349); val goldD = Color(0xFFD4AF37); val redC = Color(0xFF8B1A1A)
    Canvas(modifier) {
        val s = Stroke(width = 20f, cap = StrokeCap.Round)
        var start = -90f
        if (pF > 0.01f) { drawArc(goldC, start, pF * 360f, false, style = s); start += pF * 360f }
        if (ptF > 0.01f) { drawArc(goldD, start, ptF * 360f, false, style = s); start += ptF * 360f }
        if (penF > 0.01f) { drawArc(redC, start, penF * 360f, false, style = s) }
    }
}

@Composable
private fun LegendRow(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
        Text("($count)", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
    }
}

private fun fmtInr(v: Double): String {
    val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
    nf.minimumFractionDigits = 0; nf.maximumFractionDigits = 0
    return "₹${nf.format(v)}"
}
