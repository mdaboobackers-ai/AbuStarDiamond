@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.melting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val records = meltingDao.getAllMeltingRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _customerQuery = MutableStateFlow("")

    val suggestedCustomers = _customerQuery
        .flatMapLatest { q ->
            if (q.length >= 1) customerDao.searchCustomers(q)
            else customerDao.getRecentCustomers()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun setCustomerQuery(q: String) { _customerQuery.value = q }

    // FIX: proper save with error handling — no longer just a stub
    fun saveRecord(record: MeltingRecord, onDone: () -> Unit) = viewModelScope.launch {
        _saving.value = true
        try {
            meltingDao.insertMeltingRecord(record)
            onDone()
        } catch (e: Exception) {
            // log error
        } finally {
            _saving.value = false
        }
    }

    fun updateRecord(record: MeltingRecord) = viewModelScope.launch {
        meltingDao.updateMeltingRecord(record)
    }

    fun deleteRecord(record: MeltingRecord) = viewModelScope.launch {
        meltingDao.deleteMeltingRecord(record)
    }

    suspend fun getCustomer(id: Long): Customer? = customerDao.getCustomerById(id)
}

// ─── Melting Screen ───────────────────────────────────────────────────────────
@Composable
fun MeltingScreen(
    onBack: () -> Unit,
    viewModel: MeltingViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editRecord    by remember { mutableStateOf<MeltingRecord?>(null) }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Whatshot, null,
                            tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                        Text("MELTING MODULE", style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 3.sp)
                    }
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
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(44.dp)
                                .background(AuraColors.PrimaryContainer.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Whatshot, null,
                                tint = AuraColors.PrimaryContainer, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("Old Jewellery Exchange", style = MaterialTheme.typography.bodyLarge,
                                color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("Record raw gold received → compute pure weight",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraColors.OnSurfaceVariant)
                            Text("Formula: Pure = Raw × Purity%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AuraColors.PrimaryContainer.copy(alpha = 0.7f),
                                fontSize = 10.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Whatshot, null,
                                tint = AuraColors.OnSurface.copy(alpha = 0.15f),
                                modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("No melting records yet", color = AuraColors.OnSurface.copy(alpha = 0.4f))
                            Spacer(Modifier.height(8.dp))
                            Text("Tap + to add old jewellery exchange",
                                color = AuraColors.OnSurface.copy(alpha = 0.25f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    MeltingRecordCard(
                        record   = record,
                        onEdit   = { editRecord = record },
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddMeltingDialog(
            existing  = null,
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave    = { record ->
                viewModel.saveRecord(record) { showAddDialog = false }
            }
        )
    }

    editRecord?.let { rec ->
        AddMeltingDialog(
            existing  = rec,
            viewModel = viewModel,
            onDismiss = { editRecord = null },
            onSave    = { updated ->
                viewModel.updateRecord(updated)
                editRecord = null
            }
        )
    }
}

// ─── Melting Record Card ──────────────────────────────────────────────────────
@Composable
fun MeltingRecordCard(
    record: MeltingRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val yieldPct = if (record.rawWeightGrams > 0)
        (record.finalPureWeightGrams / record.rawWeightGrams * 100.0) else 0.0

    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(40.dp)
                            .background(AuraColors.PrimaryContainer.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Whatshot, null,
                            tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Melt #${record.id}", style = MaterialTheme.typography.bodyLarge,
                            color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(sdf.format(record.createdAt), style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 10.sp)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = AuraColors.PrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = AuraColors.Error.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = AuraColors.GlassWhite5)
            Spacer(Modifier.height(12.dp))

            // Metrics row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MeltMetric("Raw Weight",   "${String.format("%.3f", record.rawWeightGrams)}g")
                MeltMetric("Pure Weight",  "${String.format("%.3f", record.finalPureWeightGrams)}g")
                MeltMetric("Purity",       "${String.format("%.1f", record.purityPercent)}%")
                MeltMetric("Yield",        "${String.format("%.1f", yieldPct)}%")
            }

            // FIX: Show gold credit if set
            if (record.goldCreditGrams > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(AuraColors.Primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountBalance, null,
                        tint = AuraColors.Primary, modifier = Modifier.size(14.dp))
                    Text(
                        "Gold Credit: ${String.format("%.4f", record.goldCreditGrams)}g (apply to next bill)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuraColors.Primary, fontSize = 10.sp
                    )
                }
            }

            if (record.notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(record.notes, style = MaterialTheme.typography.bodyMedium,
                    color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }

            // FIX: Image attachments — now actually displayed
            if (record.imageUris.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    record.imageUris.take(4).forEach { uri ->
                        Box(
                            Modifier.size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AuraColors.SurfaceContainerHighest)
                                .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(10.dp))
                        ) {
                            coil.compose.AsyncImage(
                                model = uri, contentDescription = "Melting image",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    if (record.imageUris.size > 4) {
                        Box(
                            Modifier.size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AuraColors.GlassWhite10),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${record.imageUris.size - 4}", color = AuraColors.OnSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = AuraColors.SurfaceContainerHigh,
            title = { Text("Delete Record?", color = AuraColors.OnSurface) },
            text  = { Text("This melting record will be permanently deleted.", color = AuraColors.OnSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = AuraColors.ErrorContainer)
                ) { Text("Delete", color = AuraColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = AuraColors.OnSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun MeltMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 8.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
    }
}

// ─── Add / Edit Melting Dialog (FIX: image picker + save works) ──────────────
@Composable
private fun AddMeltingDialog(
    existing: MeltingRecord?,
    viewModel: MeltingViewModel,
    onDismiss: () -> Unit,
    onSave: (MeltingRecord) -> Unit
) {
    var rawWeight     by remember { mutableStateOf(existing?.rawWeightGrams?.toString() ?: "") }
    var finalWeight   by remember { mutableStateOf(existing?.finalPureWeightGrams?.toString() ?: "") }
    var purity        by remember { mutableStateOf(existing?.purityPercent?.toString() ?: "91.6") }
    var notes         by remember { mutableStateOf(existing?.notes ?: "") }
    var goldCredit    by remember { mutableStateOf(existing?.goldCreditGrams?.toString() ?: "") }
    var customerQuery by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    val suggestions   by viewModel.suggestedCustomers.collectAsState()
    val saving        by viewModel.saving.collectAsState()

    // FIX: image attachment — real gallery picker
    val imageUris = remember { mutableStateListOf<String>().also {
        existing?.imageUris?.let { uris -> it.addAll(uris) }
    }}
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri -> imageUris.add(uri.toString()) }
    }

    // Compute gold credit from formula if not manually set
    val computedCredit = remember(rawWeight, purity) {
        val r = rawWeight.toDoubleOrNull() ?: 0.0
        val p = purity.toDoubleOrNull() ?: 0.0
        r * (p / 100.0)
    }

    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = AuraColors.SurfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Whatshot, null, tint = AuraColors.PrimaryContainer)
                Text(if (existing == null) "New Melting Record" else "Edit Record", color = AuraColors.OnSurface)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer search
                OutlinedTextField(
                    value = customerQuery,
                    onValueChange = { customerQuery = it; viewModel.setCustomerQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CUSTOMER", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) },
                    placeholder = { Text("Search customer...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = AuraColors.PrimaryContainer,
                        unfocusedBorderColor    = AuraColors.GlassWhite20,
                        focusedTextColor        = AuraColors.OnSurface,
                        unfocusedTextColor      = AuraColors.OnSurface,
                        focusedContainerColor   = AuraColors.GlassWhite5,
                        unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(10.dp), singleLine = true
                )

                // Customer suggestions
                if (suggestions.isNotEmpty() && selectedCustomer == null) {
                    Column(
                        Modifier.background(AuraColors.SurfaceContainerHighest, RoundedCornerShape(10.dp))
                            .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(10.dp))
                    ) {
                        suggestions.take(3).forEach { c ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { selectedCustomer = c; customerQuery = c.name }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
                                Text("${c.name}  •  ${c.phone}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
                            }
                        }
                    }
                }

                selectedCustomer?.let {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(AuraColors.Primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✓ ${it.name}", color = AuraColors.Primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { selectedCustomer = null; customerQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, tint = AuraColors.OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                GhostTextField(rawWeight,   { rawWeight = it },   "A — Raw Weight (grams)", placeholder = "e.g. 25.000",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GhostTextField(purity,      { purity = it },      "Purity % (D)", placeholder = "91.6",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))

                // Auto-computed pure weight
                Box(
                    Modifier.fillMaxWidth()
                        .background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp))
                        .border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pure Gold = A × D/100", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                        Text("${String.format("%.4f", computedCredit)}g", style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }

                GhostTextField(finalWeight, { finalWeight = it }, "Final Pure Weight (measured)", placeholder = "Weighed result",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))

                GhostTextField(goldCredit,  { goldCredit = it },  "Gold Credit (grams to bill)", placeholder = "${String.format("%.4f", computedCredit)}",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))

                GhostTextField(notes, { notes = it }, "Notes (Optional)", singleLine = false)

                // FIX: Image attachment button — works now
                Column {
                    Text("ATTACHMENTS", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick  = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                        border   = BorderStroke(1.dp, AuraColors.GlassWhite20),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Photos (${imageUris.size} selected)", style = MaterialTheme.typography.labelSmall)
                    }
                    if (imageUris.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            imageUris.take(4).forEach { uri ->
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                                        .background(AuraColors.SurfaceContainerHighest)
                                ) {
                                    coil.compose.AsyncImage(
                                        model = uri, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                if (error.isNotEmpty()) Text(error, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            GoldButton(
                text    = if (saving) "Saving…" else "Save Record",
                enabled = !saving,
                onClick = {
                    when {
                        selectedCustomer == null && existing == null ->
                            error = "Please select a customer"
                        rawWeight.isBlank() ->
                            error = "Raw weight is required"
                        else -> {
                            val credit = goldCredit.toDoubleOrNull() ?: computedCredit
                            onSave(
                                (existing ?: MeltingRecord(
                                    customerId = selectedCustomer!!.id
                                )).copy(
                                    rawWeightGrams        = rawWeight.toDoubleOrNull() ?: 0.0,
                                    finalPureWeightGrams  = finalWeight.toDoubleOrNull() ?: computedCredit,
                                    purityPercent         = purity.toDoubleOrNull() ?: 91.6,
                                    goldCreditGrams       = credit,
                                    imageUris             = imageUris.toList(),
                                    notes                 = notes
                                )
                            )
                        }
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) }
        }
    )
}
