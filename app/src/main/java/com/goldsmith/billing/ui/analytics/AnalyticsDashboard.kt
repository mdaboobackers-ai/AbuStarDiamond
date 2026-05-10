package com.goldsmith.billing.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.model.PaymentStatus
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AnalyticsUiState(
    val invoiceCount: Int = 0,
    val customerCount: Int = 0,
    val revenue: Double = 0.0,
    val pureGold: Double = 0.0,
    val pending: Double = 0.0,
    val avgInvoice: Double = 0.0,
    val paidCount: Int = 0,
    val partialCount: Int = 0,
    val pendingCount: Int = 0
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    invoiceDao: InvoiceDao,
    customerDao: CustomerDao
) : ViewModel() {
    val state = combine(invoiceDao.getAllInvoices(), customerDao.getCustomerCount()) { invoices, customerCount ->
        val revenue = invoices.sumOf { it.totalAmount }
        AnalyticsUiState(
            invoiceCount = invoices.size,
            customerCount = customerCount,
            revenue = revenue,
            pureGold = invoices.sumOf { it.totalFineGoldGrams },
            pending = invoices.filter { it.paymentStatus != PaymentStatus.PAID }.sumOf { it.remainingBalance },
            avgInvoice = if (invoices.isNotEmpty()) revenue / invoices.size else 0.0,
            paidCount = invoices.count { it.paymentStatus == PaymentStatus.PAID },
            partialCount = invoices.count { it.paymentStatus == PaymentStatus.PARTIAL },
            pendingCount = invoices.count { it.paymentStatus == PaymentStatus.PENDING }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(onBack: () -> Unit, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("ANALYTICS", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalyticsCard("Revenue", "₹${String.format("%,.0f", state.revenue)}", Icons.Default.Payments, Modifier.weight(1f))
                    AnalyticsCard("Pure Gold", "${String.format("%.3f", state.pureGold)}g", Icons.Default.Diamond, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalyticsCard("Invoices", state.invoiceCount.toString(), Icons.Default.ReceiptLong, Modifier.weight(1f))
                    AnalyticsCard("Customers", state.customerCount.toString(), Icons.Default.Assessment, Modifier.weight(1f))
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Business Pulse", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                        Spacer(Modifier.height(14.dp))
                        MetricLine("Average Invoice", "₹${String.format("%,.0f", state.avgInvoice)}")
                        MetricLine("Pending Amount", "₹${String.format("%,.0f", state.pending)}")
                        MetricLine("Paid / Partial / Pending", "${state.paidCount} / ${state.partialCount} / ${state.pendingCount}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer)
            Spacer(Modifier.height(10.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, fontSize = 9.sp)
            Text(value, style = MaterialTheme.typography.titleLarge, color = AuraColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
    }
}
