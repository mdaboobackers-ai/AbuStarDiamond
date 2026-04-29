package com.goldsmith.billing.ui.history

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.goldsmith.billing.data.dao.BillItemDao
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.dao.CompanyProfileDao
import com.goldsmith.billing.data.dao.InvoicePaymentDao
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.customer.StatusBadge
import com.goldsmith.billing.ui.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val billItemDao: BillItemDao,
    private val companyProfileDao: CompanyProfileDao,
    private val invoicePaymentDao: InvoicePaymentDao,
    val settingsRepo: SettingsRepository
) : ViewModel() {

    val settings = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.goldsmith.billing.data.repository.AppSettings())

    val profile = companyProfileDao.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _dateFilter = MutableStateFlow(0) // 0=All, 1=Today, 2=This Week, 3=This Month
    val dateFilter = _dateFilter.asStateFlow()

    val allInvoices: StateFlow<List<Invoice>> = invoiceDao.getAllInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    val filteredInvoices: StateFlow<List<Invoice>> = combine(_query, _dateFilter, allInvoices) { q, df, all ->
        var result = all
        if (q.isNotEmpty()) result = result.filter {
            it.invoiceNumber.contains(q, true)
        }
        val cal = Calendar.getInstance()
        result = when (df) {
            1 -> { // Today
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                val start = cal.time
                result.filter { it.date >= start }
            }
            2 -> { // This week
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                result.filter { it.date >= cal.time }
            }
            3 -> { // This month
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                result.filter { it.date >= cal.time }
            }
            else -> result
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val customerCache = mutableMapOf<Long, Customer?>()

    fun setQuery(q: String) { _query.value = q }
    fun setDateFilter(f: Int) { _dateFilter.value = f }

    suspend fun getCustomer(id: Long): Customer? {
        return customerCache.getOrPut(id) { customerDao.getCustomerById(id) }
    }

    fun getInvoiceById(id: Long): Flow<Invoice?> = flow {
        emit(invoiceDao.getInvoiceById(id))
    }

    fun getBillItems(invoiceId: Long): Flow<List<BillItem>> =
        billItemDao.getBillItemsForInvoice(invoiceId)

    fun getPayments(invoiceId: Long): Flow<List<InvoicePayment>> =
        invoicePaymentDao.getPaymentsForInvoice(invoiceId)

    fun printInvoice(context: android.content.Context, invoice: Invoice, items: List<BillItem>) {
        // This requires paired Bluetooth device. For simplicity, we search for first bonded printer
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        val printer = adapter?.bondedDevices?.firstOrNull { it.name.lowercase().contains("printer") }
        if (printer != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                com.goldsmith.billing.util.BluetoothPrinter.printInvoice(context, printer, invoice, items, profile.value)
            }
        }
    }

    fun addPayment(invoice: Invoice, amount: Double, gold: Double, karat: Int, mode: String) = viewModelScope.launch {
        val payment = InvoicePayment(
            invoiceId = invoice.id,
            amount = amount,
            goldGrams = gold,
            goldKarat = karat,
            paymentMode = mode
        )
        invoicePaymentDao.insertPayment(payment)

        val rate = settings.value.goldRate24K
        val goldValue = gold * rate * (karat / 24.0)
        val totalPaidThisTime = amount + goldValue
        val newRemaining = invoice.remainingBalance - totalPaidThisTime
        
        val updatedInvoice = invoice.copy(
            remainingBalance = newRemaining,
            cashPaid = invoice.cashPaid + amount,
            goldPaidGrams = invoice.goldPaidGrams + gold,
            paymentStatus = if (newRemaining <= 0) PaymentStatus.PAID else PaymentStatus.PARTIAL
        )
        invoiceDao.updateInvoice(updatedInvoice)

        customerDao.getCustomerById(invoice.customerId)?.let { customer ->
            customerDao.updateCustomer(customer.copy(
                cashBalance = newRemaining, 
                goldBalanceGrams = customer.goldBalanceGrams - (gold * (karat / 24.0)),
                updatedAt = Date()
            ))
        }
    }
}

// ─── Invoice History Screen ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHistoryScreen(
    onBack: () -> Unit,
    onInvoiceDetail: (Long) -> Unit,
    onNewBill: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val invoices by viewModel.filteredInvoices.collectAsState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "INVOICE HISTORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraColors.PrimaryContainer,
                        fontSize = 16.sp,
                        letterSpacing = 3.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewBill,
                containerColor = AuraColors.PrimaryContainer,
                contentColor = AuraColors.OnPrimary,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search bar
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Search by customer name or invoice #",
                            color = AuraColors.OnSurface.copy(alpha = 0.3f)
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f))
                    },
                    label = {
                        Text(
                            "SEARCH RECORDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer,
                        unfocusedBorderColor = AuraColors.GlassWhite20,
                        focusedTextColor = AuraColors.OnSurface,
                        unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5,
                        unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Date filter tabs
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Today", "This Week", "This Month").mapIndexed { i, s -> i to s }) { (idx, label) ->
                        val selected = dateFilter == idx
                        Box(
                            Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) Color.Transparent else AuraColors.GlassWhite5)
                                .border(
                                    1.dp,
                                    if (selected) AuraColors.PrimaryContainer else AuraColors.GlassWhite10,
                                    RoundedCornerShape(18.dp)
                                )
                                .then(
                                    if (selected) Modifier.background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(
                                                AuraColors.PrimaryContainer.copy(alpha = 0.15f),
                                                AuraColors.Primary.copy(alpha = 0.1f)
                                            )
                                        ),
                                        RoundedCornerShape(18.dp)
                                    ) else Modifier
                                )
                                .clickable { viewModel.setDateFilter(idx) }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) AuraColors.PrimaryContainer else AuraColors.OnSurface.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Invoice cards
            if (invoices.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                null,
                                tint = AuraColors.OnSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No invoices found",
                                color = AuraColors.OnSurface.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else {
                items(invoices, key = { it.id }) { invoice ->
                    InvoiceCard(invoice = invoice, onClick = { onInvoiceDetail(invoice.id) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun InvoiceCard(invoice: Invoice, onClick: () -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val karatLabel = when {
        invoice.totalFineGoldGrams > 0 -> "Gold Items"
        else -> "—"
    }

    GlassCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(36.dp).background(
                                when (invoice.paymentStatus) {
                                    PaymentStatus.PAID -> AuraColors.Primary.copy(alpha = 0.15f)
                                    PaymentStatus.PARTIAL -> AuraColors.PrimaryContainer.copy(alpha = 0.15f)
                                    PaymentStatus.PENDING -> AuraColors.SurfaceContainerHighest
                                },
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Description, null,
                            tint = when (invoice.paymentStatus) {
                                PaymentStatus.PAID -> AuraColors.Primary
                                PaymentStatus.PARTIAL -> AuraColors.PrimaryContainer
                                PaymentStatus.PENDING -> AuraColors.OnSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text("#${invoice.invoiceNumber}", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Schedule, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            Text(sdf.format(invoice.date), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 10.sp)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(invoice.paymentStatus.name)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                        Text(karatLabel, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = AuraColors.GlassWhite5)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("${String.format("%.3f", invoice.totalWeightGrams)}g", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                }
                Text("₹${String.format("%,.0f", invoice.totalAmount)}", style = MaterialTheme.typography.titleLarge, color = AuraColors.OnSurface, fontSize = 22.sp)
            }
        }
    }
}

// ─── Invoice Detail Screen ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    invoiceId: Long,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invoice by remember { mutableStateOf<Invoice?>(null) }
    val billItems by viewModel.getBillItems(invoiceId).collectAsState(emptyList())
    val payments by viewModel.getPayments(invoiceId).collectAsState(emptyList())
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val profile by viewModel.profile.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(invoiceId) {
        viewModel.getInvoiceById(invoiceId).collect { invoice = it }
    }

    fun handleShare() {
        val inv = invoice ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val customer = viewModel.getCustomer(inv.customerId) ?: return@launch
            val uri = com.goldsmith.billing.util.PdfGenerator.generateInvoicePdf(context, inv, customer, billItems, profile)
            if (uri != null) {
                com.goldsmith.billing.util.PdfGenerator.shareViaWhatsApp(context, uri, customer.phone)
            }
        }
    }

    fun handlePdf() {
        val inv = invoice ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val customer = viewModel.getCustomer(inv.customerId) ?: return@launch
            val uri = com.goldsmith.billing.util.PdfGenerator.generateInvoicePdf(context, inv, customer, billItems, profile)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(invoice?.invoiceNumber ?: "Invoice", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = {
                    IconButton(onClick = ::handleShare) { Icon(Icons.Default.Share, null, tint = AuraColors.PrimaryContainer) }
                    IconButton(onClick = ::handlePdf) { Icon(Icons.Default.PictureAsPdf, null, tint = AuraColors.PrimaryContainer) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        },
        bottomBar = {
            if (invoice != null && invoice!!.remainingBalance > 0) {
                Surface(color = AuraColors.SurfaceContainerLowest, tonalElevation = 8.dp) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        GoldButton("Record New Payment", onClick = { showPaymentDialog = true }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    ) { padding ->
        invoice?.let { inv ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                        Column(Modifier.padding(20.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("#${inv.invoiceNumber}", style = MaterialTheme.typography.headlineMedium, color = AuraColors.PrimaryContainer)
                                    Text(sdf.format(inv.date), style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                                }
                                StatusBadge(inv.paymentStatus.name)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InvoiceMetric("Gold Rate (24K)", "₹${String.format("%.2f", inv.goldRate24K)}/g")
                                InvoiceMetric("Net Weight", "${String.format("%.3f", inv.totalWeightGrams)}g")
                                InvoiceMetric("Fine Gold", "${String.format("%.3f", inv.totalFineGoldGrams)}g")
                            }
                        }
                    }
                }

                item { Text("ITEMS", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }

                items(billItems) { item ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.description.ifEmpty { "Jewellery Item" }, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InvoiceMetric("Gross Wt", "${String.format("%.3f", item.grossWeightGrams)}g")
                                InvoiceMetric("Less Wt", "${String.format("%.3f", item.lessWeightGrams)}g")
                                InvoiceMetric("Net Wt", "${String.format("%.3f", item.netWeightGrams)}g")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InvoiceMetric("Purity", item.karatLabel)
                                InvoiceMetric("Making/g", "₹${String.format("%.2f", item.makingChargePerGram)}")
                                InvoiceMetric("Amount", "₹${String.format("%,.2f", item.itemAmount)}")
                            }
                        }
                    }
                }

                if (payments.isNotEmpty()) {
                    item { Text("PAYMENT HISTORY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(payments) { p ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(if (p.paymentMode == "CASH") "Cash Payment" else "Gold Payment (${p.goldKarat}K)", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface)
                                    Text(sdf.format(p.date), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant)
                                }
                                Text(if (p.paymentMode == "CASH") "₹${String.format("%,.0f", p.amount)}" else "${String.format("%.3f", p.goldGrams)}g", 
                                    style = MaterialTheme.typography.titleMedium, color = AuraColors.Primary)
                            }
                        }
                    }
                }

                item { Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PaymentRow("Sub Total", "₹${String.format("%,.2f", inv.subtotal)}")
                            PaymentRow("GST (${inv.gstPercent}%)", "₹${String.format("%,.2f", inv.gstAmount)}")
                            Divider(color = AuraColors.GlassWhite10)
                            PaymentRow("Total", "₹${String.format("%,.2f", inv.totalAmount)}", bold = true, color = AuraColors.OnSurface)
                            Divider(color = AuraColors.GlassWhite5)
                            PaymentRow("Paid till date", "₹${String.format("%,.2f", inv.totalAmount - inv.remainingBalance)}", color = AuraColors.Primary)
                            PaymentRow("Balance Due", "₹${String.format("%,.2f", inv.remainingBalance)}", bold = true, color = if (inv.remainingBalance > 0) AuraColors.Error else AuraColors.Primary)
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    if (showPaymentDialog && invoice != null) {
        AddPaymentDialog(
            invoice = invoice!!,
            onDismiss = { showPaymentDialog = false },
            onSave = { amt, gold, karat, mode ->
                viewModel.addPayment(invoice!!, amt, gold, karat, mode)
                showPaymentDialog = false
            }
        )
    }
}

@Composable
fun AddPaymentDialog(invoice: Invoice, onDismiss: () -> Unit, onSave: (Double, Double, Int, String) -> Unit) {
    var mode by remember { mutableStateOf("CASH") }
    var amount by remember { mutableStateOf("") }
    var gold by remember { mutableStateOf("") }
    var karat by remember { mutableIntStateOf(22) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Record Payment", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "CASH", onClick = { mode = "CASH" }, label = { Text("Cash") })
                    FilterChip(selected = mode == "GOLD", onClick = { mode = "GOLD" }, label = { Text("Gold") })
                }
                if (mode == "CASH") {
                    GhostTextField(amount, { amount = it }, "Amount (₹)", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                } else {
                    GhostTextField(gold, { gold = it }, "Gold Weight (grams)", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Karat:", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        listOf(24, 22, 18).forEach { k ->
                            FilterChip(selected = karat == k, onClick = { karat = k }, label = { Text("${k}K") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            GoldButton("Save Payment", onClick = {
                onSave(amount.toDoubleOrNull() ?: 0.0, gold.toDoubleOrNull() ?: 0.0, karat, mode)
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}

@Composable
private fun InvoiceMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PaymentRow(label: String, value: String, bold: Boolean = false, color: Color = AuraColors.OnSurfaceVariant) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
