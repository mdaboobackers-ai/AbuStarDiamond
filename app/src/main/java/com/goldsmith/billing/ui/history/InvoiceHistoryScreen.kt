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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.goldsmith.billing.data.dao.BillItemDao
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.dao.CompanyProfileDao
import com.goldsmith.billing.data.dao.InvoicePaymentDao
import com.goldsmith.billing.data.dao.MeltingDao
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.customer.StatusBadge
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.GoldCalc
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
    private val meltingDao: MeltingDao,
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

    fun getMeltingRecords(invoiceId: Long): Flow<List<MeltingRecord>> =
        meltingDao.getMeltingRecordsByInvoice(invoiceId)

    @SuppressLint("MissingPermission")
    fun bondedPrinters(context: android.content.Context): List<BluetoothDevice> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices
            .sortedWith(compareByDescending<BluetoothDevice> { device ->
                runCatching { device.name.orEmpty().contains("printer", ignoreCase = true) }.getOrDefault(false)
            }.thenBy { device -> runCatching { device.name.orEmpty() }.getOrDefault("") })
    }

    fun printInvoice(context: android.content.Context, device: BluetoothDevice, invoice: Invoice, items: List<BillItem>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val printed = com.goldsmith.billing.util.BluetoothPrinter.printInvoice(context, device, invoice, items, profile.value)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(printed)
            }
        }
    }

    fun addPayment(invoice: Invoice, amount: Double, gold: Double, karat: Int, mode: String, attachmentUris: List<String>) = viewModelScope.launch {
        val payment = InvoicePayment(
            invoiceId = invoice.id,
            amount = amount,
            goldGrams = gold,
            goldKarat = karat,
            paymentMode = mode,
            attachmentUris = attachmentUris
        )
        invoicePaymentDao.insertPayment(payment)

        val currentRate = settings.value.goldRate24K
        val invoiceRate = invoice.goldRate24K.takeIf { it > 0.0 } ?: currentRate
        val newRemaining = GoldCalc.invoiceBalanceAfterPaymentAtCurrentRate(
            invoiceRemainingBalance = invoice.remainingBalance,
            invoiceRate24K = invoiceRate,
            currentRate24K = currentRate,
            cashPaid = amount,
            goldGrams = gold,
            goldKarat = karat
        )
        val currentCashDue = GoldCalc.pendingCashAtRate(newRemaining, invoiceRate, currentRate)
        
        val updatedInvoice = invoice.copy(
            remainingBalance = newRemaining,
            cashPaid = invoice.cashPaid + amount,
            goldPaidGrams = invoice.goldPaidGrams + gold,
            paymentStatus = if (newRemaining <= 0) PaymentStatus.PAID else PaymentStatus.PARTIAL
        )
        invoiceDao.updateInvoice(updatedInvoice)

        customerDao.getCustomerById(invoice.customerId)?.let { customer ->
            customerDao.updateCustomer(customer.copy(
                cashBalance = currentCashDue,
                goldBalanceGrams = customer.goldBalanceGrams - GoldCalc.pureGoldFromKarat(gold, karat),
                updatedAt = Date()
            ))
        }
        if (mode == "GOLD" && gold > 0.0) {
            meltingDao.insertMeltingRecord(MeltingRecord(
                customerId = invoice.customerId,
                rawWeightGrams = gold,
                finalPureWeightGrams = GoldCalc.pureGoldFromKarat(gold, karat),
                purityPercent = (karat / 24.0) * 100.0,
                notes = "Auto-generated from payment for Invoice #${invoice.invoiceNumber}",
                linkedInvoiceId = invoice.id
            ))
        }
    }

    fun updatePayment(invoice: Invoice, payment: InvoicePayment, amount: Double, gold: Double, karat: Int, mode: String, attachmentUris: List<String>) = viewModelScope.launch {
        val updatedPayment = payment.copy(
            amount = if (mode == "CASH") amount else 0.0,
            goldGrams = if (mode == "GOLD") gold else 0.0,
            goldKarat = karat,
            paymentMode = mode,
            attachmentUris = attachmentUris,
            date = Date()
        )
        invoicePaymentDao.updatePayment(updatedPayment)

        val currentRate = settings.value.goldRate24K
        val invoiceRate = invoice.goldRate24K.takeIf { it > 0.0 } ?: currentRate
        val restoredRemaining = GoldCalc.invoiceBalanceAfterReversingPaymentAtCurrentRate(
            invoiceRemainingBalance = invoice.remainingBalance,
            invoiceRate24K = invoiceRate,
            currentRate24K = currentRate,
            cashPaid = payment.amount,
            goldGrams = payment.goldGrams,
            goldKarat = payment.goldKarat
        )
        val newRemaining = GoldCalc.invoiceBalanceAfterPaymentAtCurrentRate(
            invoiceRemainingBalance = restoredRemaining,
            invoiceRate24K = invoiceRate,
            currentRate24K = currentRate,
            cashPaid = updatedPayment.amount,
            goldGrams = updatedPayment.goldGrams,
            goldKarat = updatedPayment.goldKarat
        )
        val currentCashDue = GoldCalc.pendingCashAtRate(newRemaining, invoiceRate, currentRate)

        invoiceDao.updateInvoice(invoice.copy(
            remainingBalance = newRemaining,
            cashPaid = invoice.cashPaid - payment.amount + updatedPayment.amount,
            goldPaidGrams = invoice.goldPaidGrams - payment.goldGrams + updatedPayment.goldGrams,
            paymentStatus = if (newRemaining <= 0) PaymentStatus.PAID else PaymentStatus.PARTIAL,
            updatedAt = Date()
        ))

        customerDao.getCustomerById(invoice.customerId)?.let { customer ->
            val oldPure = GoldCalc.pureGoldFromKarat(payment.goldGrams, payment.goldKarat)
            val newPure = GoldCalc.pureGoldFromKarat(updatedPayment.goldGrams, updatedPayment.goldKarat)
            customerDao.updateCustomer(customer.copy(
                cashBalance = currentCashDue,
                goldBalanceGrams = customer.goldBalanceGrams + oldPure - newPure,
                updatedAt = Date()
            ))
        }
    }

    fun updatePaymentAttachments(payment: InvoicePayment, attachmentUris: List<String>) = viewModelScope.launch {
        invoicePaymentDao.updatePayment(payment.copy(attachmentUris = attachmentUris, date = Date()))
    }

    fun deletePayment(invoice: Invoice, payment: InvoicePayment) = viewModelScope.launch {
        invoicePaymentDao.deletePayment(payment)
        val currentRate = settings.value.goldRate24K
        val invoiceRate = invoice.goldRate24K.takeIf { it > 0.0 } ?: currentRate
        val newRemaining = GoldCalc.invoiceBalanceAfterReversingPaymentAtCurrentRate(
            invoiceRemainingBalance = invoice.remainingBalance,
            invoiceRate24K = invoiceRate,
            currentRate24K = currentRate,
            cashPaid = payment.amount,
            goldGrams = payment.goldGrams,
            goldKarat = payment.goldKarat
        )
        val currentCashDue = GoldCalc.pendingCashAtRate(newRemaining, invoiceRate, currentRate)
        invoiceDao.updateInvoice(invoice.copy(
            remainingBalance = newRemaining,
            cashPaid = (invoice.cashPaid - payment.amount).coerceAtLeast(0.0),
            goldPaidGrams = (invoice.goldPaidGrams - payment.goldGrams).coerceAtLeast(0.0),
            paymentStatus = if (newRemaining <= 0) PaymentStatus.PAID else if (newRemaining < invoice.totalAmount) PaymentStatus.PARTIAL else PaymentStatus.PENDING,
            updatedAt = Date()
        ))
        customerDao.getCustomerById(invoice.customerId)?.let { customer ->
            customerDao.updateCustomer(customer.copy(
                cashBalance = currentCashDue,
                goldBalanceGrams = customer.goldBalanceGrams + GoldCalc.pureGoldFromKarat(payment.goldGrams, payment.goldKarat),
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
    var customer by remember { mutableStateOf<Customer?>(null) }
    val billItems by viewModel.getBillItems(invoiceId).collectAsState(emptyList())
    val payments by viewModel.getPayments(invoiceId).collectAsState(emptyList())
    val meltingRecords by viewModel.getMeltingRecords(invoiceId).collectAsState(emptyList())
    val settings by viewModel.settings.collectAsState()
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val profile by viewModel.profile.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<InvoicePayment?>(null) }
    var deletePaymentTarget by remember { mutableStateOf<InvoicePayment?>(null) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var printMessage by remember { mutableStateOf<String?>(null) }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pairedPrinters = viewModel.bondedPrinters(context)
            showPrinterDialog = true
        } else {
            printMessage = "Bluetooth permission is required to print"
        }
    }

    LaunchedEffect(invoiceId) {
        viewModel.getInvoiceById(invoiceId).collect {
            invoice = it
            customer = it?.let { inv -> viewModel.getCustomer(inv.customerId) }
        }
    }

    fun handleShare() {
        val inv = invoice ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val customer = viewModel.getCustomer(inv.customerId) ?: return@launch
            val uri = com.goldsmith.billing.util.PdfGenerator.generateInvoicePdf(context, inv, customer, billItems, profile, meltingRecords, settings.goldRate24K)
            if (uri != null) {
                com.goldsmith.billing.util.PdfGenerator.shareViaWhatsApp(context, uri, customer.phone)
            }
        }
    }

    fun handlePdf() {
        val inv = invoice ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val customer = viewModel.getCustomer(inv.customerId) ?: return@launch
            val uri = com.goldsmith.billing.util.PdfGenerator.generateInvoicePdf(context, inv, customer, billItems, profile, meltingRecords, settings.goldRate24K)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        }
    }

    fun handlePrint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        pairedPrinters = viewModel.bondedPrinters(context)
        showPrinterDialog = true
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(invoice?.invoiceNumber ?: "Invoice", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = {
                    IconButton(onClick = ::handlePrint) { Icon(Icons.Default.Print, null, tint = AuraColors.PrimaryContainer) }
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
            val invoiceRate = inv.goldRate24K.takeIf { it > 0.0 } ?: settings.goldRate24K
            val balanceGold = GoldCalc.pendingPureGold(inv.remainingBalance, invoiceRate).coerceAtLeast(0.0)
            val balanceTodayCash = GoldCalc.pendingCashAtRate(inv.remainingBalance, invoiceRate, settings.goldRate24K)
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    printMessage?.let {
                        Text(it, color = AuraColors.PrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
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

                item {
                    val shopName = inv.customerShopName.ifEmpty { customer?.companyName?.ifEmpty { customer?.name ?: "" } ?: "" }
                    val ownerName = inv.customerOwnerName.ifEmpty { customer?.name ?: "" }
                    val address = inv.customerAddress.ifEmpty { customer?.address ?: "" }
                    val phone = inv.customerPhone.ifEmpty { customer?.phone ?: "" }
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("SOLD TO", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 1.sp)
                            Text(shopName, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(ownerName, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                            Text(address, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                            Text(phone, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
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
                                InvoiceMetric("Making %", "${String.format("%.2f", item.makingChargePercent.takeIf { it > 0.0 } ?: item.makingChargePerGram)}%")
                                InvoiceMetric("Amount", "₹${String.format("%,.2f", item.itemAmount)}")
                            }
                        }
                    }
                }

                if (payments.isNotEmpty()) {
                    item { Text("PAYMENT HISTORY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(payments) { p ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(if (p.paymentMode == "CASH") "Cash Payment" else "Gold Payment (${p.goldKarat}K)", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface)
                                        Text(sdf.format(p.date), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (p.paymentMode == "CASH") "₹${String.format("%,.0f", p.amount)}" else "${String.format("%.3f", p.goldGrams)}g",
                                            style = MaterialTheme.typography.titleMedium, color = AuraColors.Primary)
                                        IconButton(onClick = { editingPayment = p }) {
                                            Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { deletePaymentTarget = p }) {
                                            Icon(Icons.Default.Delete, null, tint = AuraColors.Error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                if (p.attachmentUris.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(p.attachmentUris) { uri ->
                                            AssistChip(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setData(android.net.Uri.parse(uri))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    runCatching { context.startActivity(intent) }
                                                },
                                                label = { Text("Attachment") },
                                                leadingIcon = { Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp)) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        null,
                                                        modifier = Modifier.size(16.dp).clickable {
                                                            viewModel.updatePaymentAttachments(p, p.attachmentUris - uri)
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (meltingRecords.isNotEmpty()) {
                    item { Text("MELTING / PURITY CHECK", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(meltingRecords) { record ->
                        val difference = GoldCalc.roundGrams(record.rawWeightGrams - record.finalPureWeightGrams)
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Purity check adjustment", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    InvoiceMetric("Raw received", "${String.format("%.3f", record.rawWeightGrams)}g")
                                    InvoiceMetric("Tested purity", "${String.format("%.2f", record.purityPercent)}%")
                                    InvoiceMetric("Pure received", "${String.format("%.3f", record.finalPureWeightGrams)}g")
                                }
                                if (difference > 0.0) {
                                    Text(
                                        "Shortage adjusted: ${String.format("%.3f", difference)}g pure gold",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AuraColors.Error
                                    )
                                }
                            }
                        }
                    }
                }

                item { Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PaymentRow("Sub Total Grams", "${String.format("%.3f", inv.totalWeightGrams)}g")
                            PaymentRow("Amount Subtotal", "₹${String.format("%,.2f", inv.subtotal)}")
                            PaymentRow("GST (${inv.gstPercent}%)", "₹${String.format("%,.2f", inv.gstAmount)}")
                            Divider(color = AuraColors.GlassWhite10)
                            PaymentRow("Total", "₹${String.format("%,.2f", inv.totalAmount)}", bold = true, color = AuraColors.OnSurface)
                            Divider(color = AuraColors.GlassWhite5)
                            PaymentRow("Balance Due Today", "₹${String.format("%,.2f", balanceTodayCash)}", bold = true, color = if (inv.remainingBalance > 0) AuraColors.Error else AuraColors.Primary)
                            PaymentRow("Balance Gold", "${String.format("%.3f", balanceGold)}g pure", bold = true, color = if (inv.remainingBalance > 0) AuraColors.Error else AuraColors.Primary)
                            PaymentRow("Invoice Rate Balance", "₹${String.format("%,.2f", inv.remainingBalance)}", color = AuraColors.OnSurfaceVariant)
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
            currentRate24K = settings.goldRate24K,
            onDismiss = { showPaymentDialog = false },
            onSave = { amt, gold, karat, mode, attachments ->
                viewModel.addPayment(invoice!!, amt, gold, karat, mode, attachments)
                showPaymentDialog = false
            }
        )
    }

    if (editingPayment != null && invoice != null) {
        AddPaymentDialog(
            invoice = invoice!!,
            existing = editingPayment,
            currentRate24K = settings.goldRate24K,
            onDismiss = { editingPayment = null },
            onSave = { amt, gold, karat, mode, attachments ->
                viewModel.updatePayment(invoice!!, editingPayment!!, amt, gold, karat, mode, attachments)
                editingPayment = null
            }
        )
    }

    deletePaymentTarget?.let { payment ->
        AlertDialog(
            onDismissRequest = { deletePaymentTarget = null },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Delete payment?", color = AuraColors.OnSurface) },
            text = { Text("This will update the invoice balance. Are you sure you want to delete this payment?", color = AuraColors.OnSurfaceVariant) },
            confirmButton = {
                GoldButton("Delete", onClick = {
                    invoice?.let { viewModel.deletePayment(it, payment) }
                    deletePaymentTarget = null
                })
            },
            dismissButton = { TextButton(onClick = { deletePaymentTarget = null }) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
        )
    }

    if (showPrinterDialog && invoice != null) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Select thermal printer", color = AuraColors.OnSurface) },
            text = {
                if (pairedPrinters.isEmpty()) {
                    Text("No paired Bluetooth printers found. Pair the printer in Android Bluetooth settings first.", color = AuraColors.OnSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairedPrinters.forEach { device ->
                            val deviceName = runCatching { device.name }.getOrNull().orEmpty().ifEmpty { "Bluetooth printer" }
                            OutlinedButton(
                                onClick = {
                                    val inv = invoice ?: return@OutlinedButton
                                    printMessage = "Printing to $deviceName..."
                                    showPrinterDialog = false
                                    viewModel.printInvoice(context, device, inv, billItems) { printed ->
                                        printMessage = if (printed) "Printed on $deviceName" else "Print failed. Check printer power and pairing."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(deviceName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterDialog = false }) {
                    Text("Close", color = AuraColors.PrimaryContainer)
                }
            }
        )
    }
}

@Composable
fun AddPaymentDialog(invoice: Invoice, existing: InvoicePayment? = null, currentRate24K: Double, onDismiss: () -> Unit, onSave: (Double, Double, Int, String, List<String>) -> Unit) {
    var mode by remember { mutableStateOf(existing?.paymentMode ?: "CASH") }
    var amount by remember { mutableStateOf(existing?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var gold by remember { mutableStateOf(existing?.goldGrams?.takeIf { it > 0 }?.toString() ?: "") }
    var karat by remember { mutableIntStateOf(existing?.goldKarat ?: 22) }
    var error by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(existing?.attachmentUris ?: emptyList()) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        attachments = (attachments + uris.map { it.toString() }).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text(if (existing == null) "Record Payment" else "Edit Payment", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val invoiceRate = invoice.goldRate24K.takeIf { it > 0.0 } ?: currentRate24K
                val balanceGold = GoldCalc.pendingPureGold(invoice.remainingBalance, invoiceRate).coerceAtLeast(0.0)
                val balanceCashToday = GoldCalc.pendingCashAtRate(invoice.remainingBalance, invoiceRate, currentRate24K)
                Text(
                    "Pending: ${String.format("%.3f", balanceGold)}g pure = ₹${String.format("%,.2f", balanceCashToday)} at today's rate",
                    color = AuraColors.PrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
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
                        listOf(24, 22, 20, 18).forEach { k ->
                            FilterChip(selected = karat == k, onClick = { karat = k }, label = { Text("${k}K") })
                        }
                    }
                }
                OutlinedButton(onClick = { attachmentPicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add attachments")
                }
                if (attachments.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        attachments.forEachIndexed { index, uri ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Attachment ${index + 1}", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { attachments = attachments - uri }) {
                                    Icon(Icons.Default.Delete, null, tint = AuraColors.Error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            GoldButton("Save Payment", onClick = {
                val amountValue = amount.toDoubleOrNull()
                val goldValue = gold.toDoubleOrNull()
                when {
                    mode == "CASH" && (amountValue == null || amountValue <= 0.0) -> error = "Enter valid number"
                    mode == "GOLD" && (goldValue == null || goldValue <= 0.0) -> error = "Enter valid number"
                    else -> onSave(amountValue ?: 0.0, goldValue ?: 0.0, karat, mode, attachments)
                }
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
