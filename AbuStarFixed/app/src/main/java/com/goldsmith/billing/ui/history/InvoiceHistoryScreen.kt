@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.history

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.PdfGenerator
import com.goldsmith.billing.util.g3
import com.goldsmith.billing.util.g4
import com.goldsmith.billing.util.inr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val billItemDao: BillItemDao,
    private val companyDao: CompanyProfileDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _query      = MutableStateFlow("")
    private val _dateFilter = MutableStateFlow(0)
    val query      = _query.asStateFlow()
    val dateFilter = _dateFilter.asStateFlow()

    val allInvoices = invoiceDao.getAllInvoices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered = combine(_query, _dateFilter, allInvoices) { q, df, all ->
        var r = if (q.isNotEmpty()) all.filter {
            it.invoiceNumber.contains(q, true) ||
            it.customerNameSnapshot.contains(q, true)
        } else all
        val cal = Calendar.getInstance()
        r = when (df) {
            1 -> { cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0); cal.set(Calendar.SECOND,0); r.filter { it.date >= cal.time } }
            2 -> { cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek); cal.set(Calendar.HOUR_OF_DAY,0); r.filter { it.date >= cal.time } }
            3 -> { cal.set(Calendar.DAY_OF_MONTH,1); cal.set(Calendar.HOUR_OF_DAY,0); r.filter { it.date >= cal.time } }
            else -> r
        }
        r
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String)     { _query.value = q }
    fun setDateFilter(f: Int)   { _dateFilter.value = f }

    fun getInvoiceById(id: Long): Flow<Invoice?> = flow { emit(invoiceDao.getInvoiceById(id)) }
    fun getBillItems(id: Long): Flow<List<BillItem>> = billItemDao.getBillItemsForInvoice(id)

    // FIX: properly loads customer for PDF — uses snapshot first, falls back to DB
    fun generatePdf(context: Context, invoice: Invoice, onUri: (android.net.Uri?) -> Unit) =
        viewModelScope.launch {
            val customer = customerDao.getCustomerById(invoice.customerId)
            val items    = billItemDao.getBillItemsForInvoiceSync(invoice.id)
            val profile  = companyDao.getProfileSync()
            val uri      = PdfGenerator.generateInvoicePdf(context, invoice, customer, items, profile)
            onUri(uri)
        }

    // FIX: Delete with confirmation handled in UI
    fun deleteInvoice(invoice: Invoice, onDone: () -> Unit) = viewModelScope.launch {
        invoiceDao.deleteInvoice(invoice)
        onDone()
    }

    fun updateInvoice(invoice: Invoice) = viewModelScope.launch {
        invoiceDao.updateInvoice(invoice)
    }
}

// ─── Invoice History Screen ───────────────────────────────────────────────────
@Composable
fun InvoiceHistoryScreen(
    onBack: () -> Unit,
    onInvoiceDetail: (Long) -> Unit,
    onNewBill: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val query   by viewModel.query.collectAsState()
    val df      by viewModel.dateFilter.collectAsState()
    val invoices by viewModel.filtered.collectAsState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("INVOICE HISTORY", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewBill, containerColor = AuraColors.PrimaryContainer, contentColor = AuraColors.OnPrimary, shape = CircleShape) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(value = query, onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search invoice # or customer name...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                    label = { Text("SEARCH RECORDS", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer, unfocusedBorderColor = AuraColors.GlassWhite20,
                        focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5
                    ), shape = RoundedCornerShape(12.dp), singleLine = true)
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(listOf("All","Today","This Week","This Month")) { idx, lbl ->
                        val sel = df == idx
                        Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                            .background(if (sel) AuraColors.PrimaryContainer.copy(alpha = 0.15f) else AuraColors.GlassWhite5)
                            .border(1.dp, if (sel) AuraColors.PrimaryContainer else AuraColors.GlassWhite10, RoundedCornerShape(17.dp))
                            .clickable { viewModel.setDateFilter(idx) }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center) {
                            Text(lbl, style = MaterialTheme.typography.labelSmall,
                                color = if (sel) AuraColors.PrimaryContainer else AuraColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                }
            }

            if (invoices.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, null, tint = AuraColors.OnSurface.copy(alpha = 0.2f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No invoices found", color = AuraColors.OnSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            } else {
                items(invoices, key = { it.id }) { inv ->
                    InvoiceHistoryCard(invoice = inv, onClick = { onInvoiceDetail(inv.id) }, viewModel = viewModel)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun InvoiceHistoryCard(invoice: Invoice, onClick: () -> Unit, viewModel: HistoryViewModel) {
    val context    = LocalContext.current
    val sdf        = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()) }
    var pdfLoading by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Description, null,
                            tint = when (invoice.paymentStatus) { PaymentStatus.PAID -> AuraColors.Primary; PaymentStatus.PARTIAL -> AuraColors.PrimaryContainer; else -> AuraColors.OnSurfaceVariant },
                            modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("#${invoice.invoiceNumber}", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(sdf.format(invoice.date), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 10.sp)
                    }
                }
                StatusBadge(invoice.paymentStatus.name)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AuraColors.GlassWhite5)
            Spacer(Modifier.height(8.dp))

            // FIX: Show customer name from snapshot
            if (invoice.customerNameSnapshot.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Person, null, tint = AuraColors.OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                    Text(invoice.customerNameSnapshot, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface.copy(alpha = 0.7f))
                    if (invoice.customerPhoneSnapshot.isNotEmpty()) {
                        Text("·", color = AuraColors.OnSurface.copy(alpha = 0.3f))
                        Text(invoice.customerPhoneSnapshot, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Net: ${invoice.totalNetWeightGrams.g3()}g  Pure: ${invoice.totalPureGoldGrams.g4()}g", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                    Text("22K eq: ${invoice.totalEq22KGrams.g3()}g", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
                }
                Text(invoice.totalAmount.inr(), style = MaterialTheme.typography.titleLarge, color = AuraColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionBtn(Icons.Default.Visibility, "View",  onClick)
                ActionBtn(if (pdfLoading) Icons.Default.HourglassEmpty else Icons.Default.PictureAsPdf, "PDF") {
                    if (!pdfLoading) {
                        pdfLoading = true
                        viewModel.generatePdf(context, invoice) { uri ->
                            pdfLoading = false
                            uri?.let { PdfGenerator.shareViaWhatsApp(context, it) }
                        }
                    }
                }
                ActionBtn(Icons.Default.Share, "Share") {
                    pdfLoading = true
                    viewModel.generatePdf(context, invoice) { uri ->
                        pdfLoading = false
                        uri?.let { PdfGenerator.shareViaWhatsApp(context, it) }
                    }
                }
                ActionBtn(Icons.Default.Print, "Print") {
                    viewModel.generatePdf(context, invoice) { uri ->
                        uri?.let { PdfGenerator.printViaBluetooth(context, it) }
                    }
                }
                // FIX: Editable/deletable — show delete with confirmation
                ActionBtn(Icons.Default.DeleteOutline, "Delete", tint = AuraColors.Error.copy(alpha = 0.6f)) {
                    showDelete = true
                }
            }
        }
    }

    // FIX: Confirm before delete
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            icon = { Icon(Icons.Default.Warning, null, tint = AuraColors.Error) },
            title = { Text("Delete Invoice?", color = AuraColors.OnSurface) },
            text = { Text("Invoice #${invoice.invoiceNumber} will be permanently deleted. This cannot be undone.", color = AuraColors.OnSurfaceVariant) },
            confirmButton = {
                Button(onClick = { viewModel.deleteInvoice(invoice) {}; showDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.ErrorContainer)) {
                    Text("Yes, Delete", color = AuraColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
        )
    }
}

@Composable
private fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = AuraColors.OnSurface.copy(alpha = 0.6f), onClick: () -> Unit) {
    Column(Modifier.background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 8.sp)
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, bg) = when (status) {
        "PAID"    -> AuraColors.Primary to AuraColors.Primary.copy(alpha = 0.15f)
        "PARTIAL" -> AuraColors.PrimaryContainer to AuraColors.PrimaryContainer.copy(alpha = 0.15f)
        else      -> AuraColors.Error to AuraColors.Error.copy(alpha = 0.15f)
    }
    Box(Modifier.background(bg, RoundedCornerShape(20.dp)).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(status, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
    }
}

// ─── Invoice Detail Screen ────────────────────────────────────────────────────
@Composable
fun InvoiceDetailScreen(
    invoiceId: Long,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val context   = LocalContext.current
    var invoice   by remember { mutableStateOf<Invoice?>(null) }
    val billItems by viewModel.getBillItems(invoiceId).collectAsState(emptyList())
    var pdfLoading by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val dateShort = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(invoiceId) { viewModel.getInvoiceById(invoiceId).collect { invoice = it } }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(invoice?.invoiceNumber ?: "Invoice", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = {
                    IconButton(onClick = { if (!pdfLoading) { pdfLoading = true; invoice?.let { inv -> viewModel.generatePdf(context, inv) { uri -> pdfLoading = false; uri?.let { PdfGenerator.shareViaWhatsApp(context, it) } } } } }) {
                        Icon(if (pdfLoading) Icons.Default.HourglassEmpty else Icons.Default.Share, null, tint = AuraColors.PrimaryContainer)
                    }
                    IconButton(onClick = { invoice?.let { inv -> viewModel.generatePdf(context, inv) { uri -> uri?.let { PdfGenerator.printViaBluetooth(context, it) } } } }) {
                        Icon(Icons.Default.Print, null, tint = AuraColors.PrimaryContainer)
                    }
                    // FIX: Delete from detail
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, null, tint = AuraColors.Error.copy(alpha = 0.7f)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        invoice?.let { inv ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Invoice Header ──────────────────────────────────────
                item {
                    GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                // FIX: Invoice No top left, date top right
                                Column {
                                    Text("#${inv.invoiceNumber}", style = MaterialTheme.typography.headlineMedium, color = AuraColors.PrimaryContainer)
                                    Text(sdf.format(inv.date), style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    StatusBadge(inv.paymentStatus.name)
                                    Spacer(Modifier.height(4.dp))
                                    // FIX: Date shown in top right
                                    Text(dateShort.format(inv.date), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = AuraColors.GlassWhite10)
                            Spacer(Modifier.height(10.dp))
                            // FIX: Always show customer details from snapshot
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("BILLED TO", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 8.sp, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(inv.customerNameSnapshot.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                    if (inv.customerPhoneSnapshot.isNotEmpty()) Text("Ph: ${inv.customerPhoneSnapshot}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                                    if (inv.customerAddressSnapshot.isNotEmpty()) Text(inv.customerAddressSnapshot, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant, fontSize = 12.sp)
                                    if (inv.customerGstSnapshot.isNotEmpty()) Text("GST: ${inv.customerGstSnapshot}", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("GOLD RATE", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 8.sp)
                                    Text("₹${String.format("%,.2f", inv.goldRate24K)}/g", style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                                    Text("24K at billing", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                // ── Gold Gram Summary ───────────────────────────────────
                item {
                    GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("GOLD SUMMARY (GRAMS)", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp, fontSize = 9.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                GStat("Total Net",  "${inv.totalNetWeightGrams.g3()}g")
                                GStat("Pure Gold\n(24K)", "${inv.totalPureGoldGrams.g4()}g", hi = true)
                                GStat("22K Jewel\n(÷0.916)", "${inv.totalEq22KGrams.g3()}g")
                                GStat("18K Jewel\n(÷0.75)",  "${inv.totalEq18KGrams.g3()}g")
                            }
                        }
                    }
                }

                // ── Bill Items ──────────────────────────────────────────
                if (billItems.isNotEmpty()) {
                    item { Text("ITEMS", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(billItems) { item ->
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.description.ifEmpty { "Item" }, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text(item.karatLabel, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    GStat("Gross",  "${item.grossWeightGrams.g3()}g")
                                    GStat("Less",   "${item.lessWeightGrams.g3()}g")
                                    GStat("Net",    "${item.netWeightGrams.g3()}g")
                                    GStat("Eq.g",   "${item.eqGrams.g4()}g", hi = true)
                                    GStat("Making", "${item.makingChargePercent}%")
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text("Value: ${item.itemCashValue.inr()}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
                                }
                                // Item attachments
                                if (item.imageUris.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item.imageUris.split("||").filter { it.isNotEmpty() }.take(4).forEach { uri ->
                                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(AuraColors.GlassWhite10)) {
                                                coil.compose.AsyncImage(uri, null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Payment Summary ─────────────────────────────────────
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PAYMENT SUMMARY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, fontSize = 9.sp)
                            PRow("Sub Total", inv.subtotalCash.inr())
                            PRow("GST (${inv.gstPercent}%)", inv.gstAmount.inr())
                            HorizontalDivider(color = AuraColors.GlassWhite10)
                            PRow("Total", inv.totalAmount.inr(), bold = true)
                            HorizontalDivider(color = AuraColors.GlassWhite5)
                            if (inv.cashPaid > 0) PRow("Cash Paid", inv.cashPaid.inr(), color = AuraColors.Primary)
                            if (inv.totalGoldPaidCash > 0) PRow("Gold Paid (value)", inv.totalGoldPaidCash.inr(), color = AuraColors.Primary)
                            HorizontalDivider(color = AuraColors.GlassWhite10)
                            // FIX: Balance shows both ₹ and grams
                            Column(Modifier.fillMaxWidth()) {
                                PRow("Balance (₹)", inv.remainingCash.inr(), bold = true, color = if (inv.remainingCash > 0.01) AuraColors.Error else AuraColors.Primary)
                                if (inv.remainingCash > 0.01 && inv.goldRate24K > 0) {
                                    Text("  OR  ${inv.remainingGoldGrams.g3()} g (pure gold)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AuraColors.Error, fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp, start = 4.dp))
                                }
                            }
                        }
                    }
                }

                // ── Actions ─────────────────────────────────────────────
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { if (!pdfLoading) { pdfLoading = true; viewModel.generatePdf(context, inv) { uri -> pdfLoading = false; uri?.let { PdfGenerator.shareViaWhatsApp(context, it) } } } },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.Primary),
                            border = BorderStroke(1.dp, AuraColors.Primary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text("WHATSAPP", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                        GoldButton("PDF", onClick = { viewModel.generatePdf(context, inv) { uri -> uri?.let { PdfGenerator.shareViaWhatsApp(context, it) } } },
                            modifier = Modifier.weight(1f).height(50.dp),
                            icon = { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp)) })
                        OutlinedButton(
                            onClick = { inv.let { i -> viewModel.generatePdf(context, i) { uri -> uri?.let { PdfGenerator.printViaBluetooth(context, it) } } } },
                            modifier = Modifier.weight(1f).height(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface),
                            border = BorderStroke(1.dp, AuraColors.GlassWhite20),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text("PRINT", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AuraColors.PrimaryContainer)
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            icon = { Icon(Icons.Default.Warning, null, tint = AuraColors.Error) },
            title = { Text("Delete Invoice?", color = AuraColors.OnSurface) },
            text = { Text("Invoice #${invoice?.invoiceNumber} will be permanently deleted.", color = AuraColors.OnSurfaceVariant) },
            confirmButton = {
                Button(onClick = { invoice?.let { viewModel.deleteInvoice(it) { onBack() } }; showDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.ErrorContainer)) {
                    Text("Yes, Delete", color = AuraColors.Error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
        )
    }
}

@Composable
private fun GStat(label: String, value: String, hi: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 7.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (hi) AuraColors.PrimaryContainer else AuraColors.OnSurface, fontWeight = if (hi) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
    }
}

@Composable
private fun PRow(label: String, value: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = AuraColors.OnSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = if (bold) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
