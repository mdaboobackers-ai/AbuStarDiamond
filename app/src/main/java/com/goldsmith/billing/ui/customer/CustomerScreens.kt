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
import androidx.compose.ui.graphics.Color
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
import java.util.Date
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
}

// ─── Customer List Screen ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    onBack: () -> Unit,
    onCustomerDetail: (Long) -> Unit,
    onNewBillForCustomer: (Long) -> Unit,
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val totalCustomers by viewModel.totalCustomers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var filterTab by remember { mutableStateOf(0) }

    val filtered = when (filterTab) {
        1 -> customers.filter { it.goldBalanceGrams > 0 }
        2 -> customers.filter { it.goldBalanceGrams < 0 }
        else -> customers
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("CLIENTELE", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, null, tint = AuraColors.PrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("TOTAL ACTIVE CLIENTS", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface.copy(alpha = 0.4f), letterSpacing = 1.sp, fontSize = 9.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text("$totalCustomers", style = MaterialTheme.typography.displayLarge, color = AuraColors.OnSurface, fontSize = 36.sp)
                                Icon(Icons.Default.Group, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                    GlassCard(Modifier.weight(1.5f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("GLOBAL NET GOLD POSITION", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface.copy(alpha = 0.4f), letterSpacing = 1.sp, fontSize = 9.sp)
                            Spacer(Modifier.height(8.dp))
                            val totalGold = customers.sumOf { it.goldBalanceGrams }
                            Text(
                                (if (totalGold >= 0) "+" else "") + String.format("%.2f", totalGold),
                                style = MaterialTheme.typography.titleLarge, color = if (totalGold >= 0) AuraColors.Primary else AuraColors.Error, fontSize = 24.sp
                            )
                            Text("GRAMS", style = MaterialTheme.typography.labelSmall, color = AuraColors.Primary.copy(alpha = 0.6f), letterSpacing = 2.sp)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search name, phone, or company ID...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AuraColors.PrimaryContainer, unfocusedBorderColor = AuraColors.GlassWhite20, focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface, focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5),
                    shape = RoundedCornerShape(16.dp), singleLine = true
                )
            }

            item {
                Row(Modifier.fillMaxWidth().background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp)).border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(12.dp)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All", "Debtors", "Creditors").forEachIndexed { idx, label ->
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (filterTab == idx) AuraColors.GlassWhite10 else Color.Transparent).clickable { filterTab = idx }, contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (filterTab == idx) AuraColors.PrimaryContainer else AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    }
                }
            }

            items(filtered, key = { it.id }) { customer ->
                CustomerCard(
                    customer = customer,
                    onViewLedger = { onCustomerDetail(customer.id) },
                    onQuickBill = { onNewBillForCustomer(customer.id) },
                    onEdit = { customerToEdit = customer }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddCustomerDialog(onDismiss = { showAddDialog = false }, onSave = { c -> viewModel.saveCustomer(c) { showAddDialog = false } })
    }

    if (customerToEdit != null) {
        AddCustomerDialog(initial = customerToEdit, onDismiss = { customerToEdit = null }, onSave = { c -> viewModel.updateCustomer(c); customerToEdit = null })
    }
}

@Composable
fun CustomerCard(customer: Customer, onViewLedger: () -> Unit, onQuickBill: () -> Unit, onEdit: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AuraColors.SurfaceContainerHighest).border(1.dp, AuraColors.GlassBorder, CircleShape), contentAlignment = Alignment.Center) {
                    if (customer.photoUri.isNotEmpty()) {
                        coil.compose.AsyncImage(customer.photoUri, null, Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Text(customer.name.take(2).uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(customer.name, style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface, fontSize = 17.sp)
                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                    if (customer.phone.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Call, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                            Text(customer.phone, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface.copy(alpha = 0.4f))
                        }
                    }
                }
                BalanceChip(customer.goldBalanceGrams, customer.cashBalance)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewLedger, modifier = Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface), border = BorderStroke(1.dp, AuraColors.GlassWhite10), shape = RoundedCornerShape(10.dp)) {
                    Text("VIEW LEDGER", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Button(onClick = onQuickBill, modifier = Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = AuraColors.PrimaryContainer, contentColor = AuraColors.OnPrimary), shape = RoundedCornerShape(10.dp)) {
                    Text("QUICK BILL", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(customerId: Long, onBack: () -> Unit, onNewBill: () -> Unit, viewModel: CustomerViewModel = hiltViewModel()) {
    val invoices by viewModel.getInvoicesForCustomer(customerId).collectAsState(emptyList())

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("CLIENT LEDGER", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 2.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                actions = { GoldButton("New Bill", onClick = onNewBill, modifier = Modifier.padding(end = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(invoices) { invoice ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(invoice.invoiceNumber, style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer)
                            Text("₹${String.format("%,.2f", invoice.totalAmount)}", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                        }
                        StatusBadge(invoice.paymentStatus.name)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, bg) = when (status) {
        "PAID" -> AuraColors.Primary to AuraColors.Primary.copy(alpha = 0.15f)
        "PARTIAL" -> AuraColors.PrimaryContainer to AuraColors.PrimaryContainer.copy(alpha = 0.15f)
        else -> AuraColors.Error to AuraColors.Error.copy(alpha = 0.15f)
    }
    Box(
        Modifier.background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(status, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp) }
}

@Composable
fun AddCustomerDialog(initial: Customer? = null, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var company by remember { mutableStateOf(initial?.companyName ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var gst by remember { mutableStateOf(initial?.gstNumber ?: "") }
    var dob by remember { mutableStateOf(initial?.dob) }
    var anniversary by remember { mutableStateOf(initial?.anniversary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (initial == null) Icons.Default.PersonAdd else Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer)
            Text(if (initial == null) "New Client" else "Edit Client", color = AuraColors.OnSurface)
        }},
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostTextField(name, { name = it }, "Full Name", placeholder = "e.g. Alistair Penhaligon")
                GhostTextField(phone, { phone = it }, "Phone Number", placeholder = "+91 98765 43210", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone))
                GhostTextField(company, { company = it }, "Company Name (Optional)")
                GhostTextField(address, { address = it }, "Address", singleLine = false)
                GhostTextField(gst, { gst = it }, "GST Number (Optional)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(label = "Date of Birth", selectedDate = dob, onDateSelected = { dob = it }, modifier = Modifier.weight(1f))
                    DatePickerField(label = "Anniversary", selectedDate = anniversary, onDateSelected = { anniversary = it }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            GoldButton(if (initial == null) "Register & Continue" else "Save Changes", onClick = {
                if (name.isNotEmpty()) {
                    onSave(initial?.copy(name = name, phone = phone, companyName = company, address = address, gstNumber = gst, dob = dob, anniversary = anniversary)
                        ?: Customer(name = name, phone = phone, companyName = company, address = address, gstNumber = gst, dob = dob, anniversary = anniversary))
                }
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(label: String, selectedDate: java.util.Date?, onDateSelected: (java.util.Date) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()) }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(8.dp)).clickable { showDialog = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = if (selectedDate != null) sdf.format(selectedDate) else "DD/MM/YYYY", color = if (selectedDate != null) AuraColors.OnSurface else AuraColors.OnSurface.copy(alpha = 0.3f), style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { onDateSelected(java.util.Date(it)) }; showDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}
