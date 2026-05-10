package com.goldsmith.billing.ui.melting

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.MeltingDao
import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.MeltingRecord
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class MeltingViewModel @Inject constructor(
    private val meltingDao: MeltingDao,
    private val customerDao: CustomerDao
) : ViewModel() {

    val records: StateFlow<List<MeltingRecord>> = meltingDao.getAllMeltingRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _customerQuery = MutableStateFlow("")
    val customerQuery = _customerQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val suggestedCustomers = _customerQuery
        .debounce(200)
        .flatMapLatest { q ->
            if (q.length >= 1) customerDao.searchCustomers(q)
            else customerDao.getRecentCustomers()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    fun setCustomerQuery(q: String) { _customerQuery.value = q }

    fun saveRecord(record: MeltingRecord, previous: MeltingRecord? = null, onDone: () -> Unit) = viewModelScope.launch {
        meltingDao.insertMeltingRecord(record)
        customerDao.getCustomerById(record.customerId)?.let { customer ->
            val newGoldBalance = if (previous == null) {
                customer.goldBalanceGrams - record.finalPureWeightGrams
            } else {
                customer.goldBalanceGrams + previous.finalPureWeightGrams - record.finalPureWeightGrams
            }
            customerDao.updateCustomer(customer.copy(goldBalanceGrams = newGoldBalance, updatedAt = Date()))
        }
        onDone()
    }

    fun deleteRecord(record: MeltingRecord) = viewModelScope.launch {
        meltingDao.deleteMeltingRecord(record)
        customerDao.getCustomerById(record.customerId)?.let { customer ->
            val newGoldBalance = customer.goldBalanceGrams + record.finalPureWeightGrams
            customerDao.updateCustomer(customer.copy(goldBalanceGrams = newGoldBalance, updatedAt = Date()))
        }
    }

    suspend fun getCustomer(id: Long): Customer? = customerDao.getCustomerById(id)
}

// ─── Melting Screen ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeltingScreen(
    onBack: () -> Unit,
    viewModel: MeltingViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MeltingRecord?>(null) }
    var editTarget by remember { mutableStateOf<MeltingRecord?>(null) }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Whatshot, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                        Text("MELTING MODULE", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 3.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer)
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
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(AuraColors.PrimaryContainer.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Whatshot, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("Old Jewellery Exchange", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("Record raw gold received & final pure weight", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Whatshot, null, tint = AuraColors.OnSurface.copy(alpha = 0.15f), modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("No melting records yet", color = AuraColors.OnSurface.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    MeltingRecordCard(record = record, onEdit = { editTarget = record }, onDelete = { deleteTarget = record })
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddMeltingDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave = { record ->
                viewModel.saveRecord(record) { showAddDialog = false }
            }
        )
    }

    editTarget?.let { target ->
        AddMeltingDialog(
            viewModel = viewModel,
            current = target,
            onDismiss = { editTarget = null },
            onSave = { record ->
                viewModel.saveRecord(record.copy(id = target.id, createdAt = target.createdAt, updatedAt = Date()), target) { editTarget = null }
            }
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Delete melting record?", color = AuraColors.OnSurface) },
            text = { Text("This will adjust the customer's gold balance. Are you sure?", color = AuraColors.OnSurfaceVariant) },
            confirmButton = { GoldButton("Delete", onClick = { viewModel.deleteRecord(record); deleteTarget = null }) },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
        )
    }
}

@Composable
fun MeltingRecordCard(record: MeltingRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val purityGain = if (record.rawWeightGrams > 0)
        (record.finalPureWeightGrams / record.rawWeightGrams * 100)
    else 0.0

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).background(AuraColors.PrimaryContainer.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Whatshot, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Melt #${record.id}", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(sdf.format(record.createdAt), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 10.sp)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = AuraColors.Error.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp)); Divider(color = AuraColors.GlassWhite5); Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MeltMetric("Raw Weight", "${String.format("%.3f", record.rawWeightGrams)}g")
                MeltMetric("Pure Weight", "${String.format("%.3f", record.finalPureWeightGrams)}g")
                MeltMetric("Purity", "${String.format("%.1f", record.purityPercent)}%")
                MeltMetric("Yield", "${String.format("%.1f", purityGain)}%")
            }

            if (record.notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(record.notes, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun MeltMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 8.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddMeltingDialog(viewModel: MeltingViewModel, current: MeltingRecord? = null, onDismiss: () -> Unit, onSave: (MeltingRecord) -> Unit) {
    var rawWeight by remember { mutableStateOf(current?.rawWeightGrams?.toString() ?: "") }
    var finalWeight by remember { mutableStateOf(current?.finalPureWeightGrams?.toString() ?: "") }
    var purity by remember { mutableStateOf(current?.purityPercent?.toString() ?: "91.6") }
    var notes by remember { mutableStateOf(current?.notes ?: "") }
    var customerQuery by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var attachedImages by remember { mutableStateOf(current?.imageUris ?: listOf<String>()) }
    val suggestions by viewModel.suggestedCustomers.collectAsState()
    var showSourceChooser by remember { mutableStateOf(false) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris -> attachedImages = attachedImages + uris.map { it.toString() } }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> /* In production, save bitmap to file and add URI to attachedImages */ }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Whatshot, null, tint = AuraColors.PrimaryContainer)
            Text(if (current == null) "New Melting Record" else "Edit Melting Record", color = AuraColors.OnSurface)
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = customerQuery,
                    onValueChange = { customerQuery = it; viewModel.setCustomerQuery(it); selectedCustomer = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CUSTOMER", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, letterSpacing = 1.sp) },
                    placeholder = { Text("Search customer...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AuraColors.PrimaryContainer, unfocusedBorderColor = AuraColors.GlassWhite20, focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface, focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = AuraColors.GlassWhite5),
                    shape = RoundedCornerShape(10.dp), singleLine = true
                )

                if (suggestions.isNotEmpty() && selectedCustomer == null) {
                    Column(Modifier.background(AuraColors.SurfaceContainerHighest, RoundedCornerShape(10.dp)).border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(10.dp))) {
                        suggestions.take(3).forEach { c ->
                            Row(Modifier.fillMaxWidth().clickable { selectedCustomer = c; customerQuery = c.name }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Person, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text("${c.name} • ${c.phone}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
                            }
                        }
                    }
                }

                GhostTextField(rawWeight, { rawWeight = it }, "Raw Weight (grams)", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GhostTextField(finalWeight, { finalWeight = it }, "Final Pure Weight (grams)", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GhostTextField(purity, { purity = it }, "Purity %", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GhostTextField(notes, { notes = it }, "Notes (Optional)", singleLine = false)

                Text("Attachments", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachedImages.forEach { uri ->
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) {
                            coil.compose.AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            IconButton(onClick = { attachedImages = attachedImages - uri }, Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(0.5f), CircleShape)) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    if (attachedImages.size < 5) {
                        Box(Modifier.size(64.dp).background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp)).border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(8.dp)).clickable { showSourceChooser = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AddAPhoto, null, tint = AuraColors.PrimaryContainer)
                        }
                    }
                }
            }
        },
        confirmButton = { GoldButton("Save Record", onClick = {
            val cust = selectedCustomer
            val customerId = cust?.id ?: current?.customerId
            if (customerId != null && rawWeight.isNotEmpty() && finalWeight.isNotEmpty()) {
                onSave(MeltingRecord(customerId = customerId, rawWeightGrams = rawWeight.toDoubleOrNull() ?: 0.0, finalPureWeightGrams = finalWeight.toDoubleOrNull() ?: 0.0, purityPercent = purity.toDoubleOrNull() ?: 91.6, notes = notes, imageUris = attachedImages, linkedInvoiceId = current?.linkedInvoiceId))
            }
        })},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )

    if (showSourceChooser) {
        AlertDialog(
            onDismissRequest = { showSourceChooser = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Attach Image", color = AuraColors.OnSurface) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { cameraLauncher.launch(null); showSourceChooser = false }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CameraAlt, null, tint = AuraColors.PrimaryContainer)
                        Text("Camera", color = AuraColors.OnSurface)
                    }
                    Row(Modifier.fillMaxWidth().clickable { galleryLauncher.launch("image/*"); showSourceChooser = false }.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = AuraColors.PrimaryContainer)
                        Text("Gallery", color = AuraColors.OnSurface)
                    }
                }
            },
            confirmButton = {}
        )
    }
}
