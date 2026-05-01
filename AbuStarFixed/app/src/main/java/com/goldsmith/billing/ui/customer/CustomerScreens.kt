@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.customer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.Invoice
import com.goldsmith.billing.ui.components.*
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
class CustomerViewModel @Inject constructor(
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    @OptIn(FlowPreview::class)
    val customers: StateFlow<List<Customer>> = _query
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isEmpty()) customerDao.getAllCustomers()
            else customerDao.searchCustomers(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCustomers = customerDao.getCustomerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    fun setQuery(q: String) { _query.value = q }

    fun saveCustomer(customer: Customer, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = customerDao.insertCustomer(customer)
        onDone(id)
    }

    fun updateCustomer(customer: Customer) = viewModelScope.launch {
        customerDao.updateCustomer(customer)
    }

    fun deleteCustomer(customer: Customer) = viewModelScope.launch {
        customerDao.deleteCustomer(customer)
    }

    fun getInvoicesForCustomer(customerId: Long): Flow<List<Invoice>> =
        invoiceDao.getInvoicesByCustomer(customerId)

    suspend fun getCustomerById(id: Long): Customer? = customerDao.getCustomerById(id)
}

// ─── Customer List Screen ─────────────────────────────────────────────────────
@Composable
fun CustomerListScreen(
    onBack: () -> Unit,
    onCustomerDetail: (Long) -> Unit,
    onNewBillForCustomer: (Long) -> Unit,
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val query          by viewModel.query.collectAsState()
    val customers      by viewModel.customers.collectAsState()
    val totalCustomers by viewModel.totalCustomers.collectAsState()
    var showAddDialog  by remember { mutableStateOf(false) }
    var filterTab      by remember { mutableIntStateOf(0) }

    val filtered = when (filterTab) {
        1 -> customers.filter { it.goldBalanceGrams > 0 || it.cashBalance > 0 }
        2 -> customers.filter { it.goldBalanceGrams < 0 || it.cashBalance < 0 }
        else -> customers
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text("CLIENTELE", style = MaterialTheme.typography.labelSmall,
                        color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, null, tint = AuraColors.PrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AuraColors.PrimaryContainer,
                contentColor   = AuraColors.OnPrimary,
                shape          = CircleShape
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary bento
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("TOTAL CLIENTS", style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("$totalCustomers", style = MaterialTheme.typography.displayLarge,
                                color = AuraColors.OnSurface, fontSize = 32.sp)
                        }
                    }
                    GlassCard(Modifier.weight(1.5f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("NET GOLD POSITION", style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.height(6.dp))
                            val totalGold = customers.sumOf { it.goldBalanceGrams }
                            Text(
                                "${String.format("%.2f", totalGold)}g",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (totalGold >= 0) AuraColors.Primary else AuraColors.Error,
                                fontSize = 22.sp
                            )
                        }
                    }
                }
            }

            // Search
            item {
                OutlinedTextField(
                    value = query, onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search name, phone...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = AuraColors.PrimaryContainer,
                        unfocusedBorderColor    = AuraColors.GlassWhite20,
                        focusedTextColor        = AuraColors.OnSurface,
                        unfocusedTextColor      = AuraColors.OnSurface,
                        focusedContainerColor   = AuraColors.GlassWhite5,
                        unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(16.dp), singleLine = true
                )
            }

            // Filter tabs
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
                        .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("All", "Debtors", "Creditors").forEachIndexed { idx, label ->
                        Box(
                            Modifier.weight(1f).height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (filterTab == idx) AuraColors.GlassWhite10 else AuraColors.Background)
                                .clickable { filterTab = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = if (filterTab == idx) AuraColors.PrimaryContainer else AuraColors.OnSurface.copy(alpha = 0.4f),
                                fontSize = 11.sp)
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Group, null, tint = AuraColors.OnSurface.copy(alpha = 0.2f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No customers yet", color = AuraColors.OnSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { customer ->
                    CustomerCard(
                        customer    = customer,
                        onViewLedger = { onCustomerDetail(customer.id) },
                        onQuickBill  = { onNewBillForCustomer(customer.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddEditCustomerDialog(
            customer  = null,
            onDismiss = { showAddDialog = false },
            onSave    = { c -> viewModel.saveCustomer(c) { showAddDialog = false } }
        )
    }
}

// ─── Customer Card ────────────────────────────────────────────────────────────
@Composable
fun CustomerCard(customer: Customer, onViewLedger: () -> Unit, onQuickBill: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(AuraColors.SurfaceContainerHighest)
                        .border(1.dp, AuraColors.GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (customer.photoUri.isNotEmpty()) {
                        coil.compose.AsyncImage(
                            model = customer.photoUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(customer.name.take(2).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(customer.name, style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface, fontSize = 16.sp)
                    if (customer.phone.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Call, null, tint = AuraColors.OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(11.dp))
                            Text(customer.phone, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface.copy(alpha = 0.4f))
                        }
                    }
                    // FIX: Show upcoming birthday/anniversary
                    customer.dateOfBirth?.let { dob ->
                        val today = Calendar.getInstance()
                        val dobCal = Calendar.getInstance().apply { timeInMillis = dob }
                        if (dobCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            dobCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Cake, null, tint = AuraColors.Primary, modifier = Modifier.size(11.dp))
                                Text("🎂 Birthday Today!", style = MaterialTheme.typography.labelSmall, color = AuraColors.Primary, fontSize = 10.sp)
                            }
                        }
                    }
                }
                BalanceChip(customer.goldBalanceGrams, customer.cashBalance)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onViewLedger,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface),
                    border = BorderStroke(1.dp, AuraColors.GlassWhite10),
                    shape  = RoundedCornerShape(10.dp)
                ) { Text("VIEW LEDGER", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp) }

                Button(
                    onClick = onQuickBill,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraColors.PrimaryContainer,
                        contentColor   = AuraColors.OnPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("QUICK BILL", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Customer Detail ──────────────────────────────────────────────────────────
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    onBack: () -> Unit,
    onNewBill: () -> Unit,
    viewModel: CustomerViewModel = hiltViewModel()
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    val invoices by viewModel.getInvoicesForCustomer(customerId).collectAsState(emptyList())
    var showEditDialog by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(customerId) {
        customer = viewModel.getCustomerById(customerId)
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("CLIENT LEDGER", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 2.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = {
                    // FIX #17: Date shown in top right
                    Text(
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer)
                    }
                    GoldButton("New Bill", onClick = onNewBill, modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            customer?.let { c ->
                item {
                    GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                        Column(Modifier.padding(16.dp)) {
                            Text(c.name, style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                            if (c.phone.isNotEmpty()) Text(c.phone, color = AuraColors.OnSurfaceVariant)
                            if (c.address.isNotEmpty()) Text(c.address, color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            if (c.gstNumber.isNotEmpty()) Text("GST: ${c.gstNumber}", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            // FIX: Show DOB and Anniversary
                            c.dateOfBirth?.let { Text("🎂 DOB: ${sdf.format(Date(it))}", color = AuraColors.Primary, style = MaterialTheme.typography.bodyMedium) }
                            c.anniversary?.let { Text("💍 Anniversary: ${sdf.format(Date(it))}", color = AuraColors.Primary, style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BalanceStat("Cash Balance", "₹${String.format("%,.2f", c.cashBalance)}", c.cashBalance)
                                BalanceStat("Gold Balance", "${String.format("%.3f", c.goldBalanceGrams)}g", c.goldBalanceGrams)
                            }
                        }
                    }
                }
            }

            if (invoices.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No invoices yet", color = AuraColors.OnSurface.copy(alpha = 0.4f))
                    }
                }
            } else {
                items(invoices) { inv ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inv.invoiceNumber, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer)
                                Text("₹${String.format("%,.2f", inv.totalAmount)}", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface, fontSize = 18.sp)
                                Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(inv.date), style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                            }
                            StatusBadge(inv.paymentStatus.name)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showEditDialog) {
        customer?.let { c ->
            AddEditCustomerDialog(
                customer  = c,
                onDismiss = { showEditDialog = false },
                onSave    = { updated -> viewModel.updateCustomer(updated); showEditDialog = false; customer = updated }
            )
        }
    }
}

@Composable
private fun BalanceStat(label: String, value: String, amount: Double) {
    Column(
        Modifier.background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (amount > 0) AuraColors.Error else if (amount < 0) AuraColors.Primary else AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, bg) = when (status) {
        "PAID"    -> AuraColors.Primary to AuraColors.Primary.copy(alpha = 0.15f)
        "PARTIAL" -> AuraColors.PrimaryContainer to AuraColors.PrimaryContainer.copy(alpha = 0.15f)
        else      -> AuraColors.Error to AuraColors.Error.copy(alpha = 0.15f)
    }
    Box(
        Modifier.background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(status, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp) }
}

// ─── Add / Edit Customer Dialog (FIX: includes DOB + Anniversary) ────────────
@Composable
fun AddEditCustomerDialog(
    customer: Customer?,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name        by remember { mutableStateOf(customer?.name ?: "") }
    var phone       by remember { mutableStateOf(customer?.phone ?: "") }
    var company     by remember { mutableStateOf(customer?.companyName ?: "") }
    var address     by remember { mutableStateOf(customer?.address ?: "") }
    var gst         by remember { mutableStateOf(customer?.gstNumber ?: "") }
    var email       by remember { mutableStateOf(customer?.email ?: "") }
    // FIX: DOB and Anniversary
    var dobText     by remember { mutableStateOf(customer?.dateOfBirth?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "") }
    var annivText   by remember { mutableStateOf(customer?.anniversary?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "") }
    var error       by remember { mutableStateOf("") }
    val dateFmt     = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun parseDate(s: String): Long? = try {
        if (s.isBlank()) null else dateFmt.parse(s.trim())?.time
    } catch (e: Exception) { null }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = AuraColors.SurfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PersonAdd, null, tint = AuraColors.PrimaryContainer)
                Text(if (customer == null) "New Client" else "Edit Client", color = AuraColors.OnSurface)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostTextField(name,    { name = it },    "Full Name *",  placeholder = "e.g. Rajesh Kumar")
                GhostTextField(phone,   { phone = it },   "Phone *", placeholder = "+91 98765 43210",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone))
                GhostTextField(company, { company = it }, "Company Name (Optional)")
                GhostTextField(address, { address = it }, "Address", singleLine = false)
                GhostTextField(gst,     { gst = it },     "GST Number (Optional)")
                GhostTextField(email,   { email = it },   "Email (Optional)")
                // FIX: DOB and Anniversary date fields
                GhostTextField(dobText,   { dobText = it },   "Date of Birth (dd/MM/yyyy)", placeholder = "01/01/1990",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                GhostTextField(annivText, { annivText = it }, "Anniversary (dd/MM/yyyy)", placeholder = "15/08/2010",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                if (error.isNotEmpty()) Text(error, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            GoldButton("Save", onClick = {
                if (name.isBlank()) { error = "Name is required"; return@GoldButton }
                if (phone.isBlank()) { error = "Phone is required"; return@GoldButton }
                onSave(
                    (customer ?: Customer(name = name, phone = phone)).copy(
                        name         = name.trim(),
                        phone        = phone.trim(),
                        companyName  = company.trim(),
                        address      = address.trim(),
                        gstNumber    = gst.trim(),
                        email        = email.trim(),
                        dateOfBirth  = parseDate(dobText),
                        anniversary  = parseDate(annivText),
                        updatedAt    = Date()
                    )
                )
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}
