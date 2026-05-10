package com.goldsmith.billing.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goldsmith.billing.data.dao.BillItemDao
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.model.BillItem
import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.Invoice
import com.goldsmith.billing.data.model.PaymentStatus
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.security.KeystoreManager
import com.goldsmith.billing.ui.components.GhostTextField
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.GoldCalc
import com.goldsmith.billing.util.SpreadsheetImportUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ImportMode { Customers, Billing }
private enum class WriteAction { Template, Export }

data class DataImportState(
    val loading: Boolean = false,
    val message: String = "",
    val imported: Int = 0,
    val skipped: Int = 0
)

@HiltViewModel
class DataImportViewModel @Inject constructor(
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val billItemDao: BillItemDao,
    private val settingsRepo: SettingsRepository,
    private val keystoreManager: KeystoreManager
) : ViewModel() {
    private val _state = MutableStateFlow(DataImportState())
    val state = _state.asStateFlow()

    fun importFromFile(context: android.content.Context, uri: Uri, mode: ImportMode) = viewModelScope.launch {
        runImport(mode) { SpreadsheetImportUtil.readRowsFromUri(context, uri) }
    }

    fun importFromSheet(url: String, mode: ImportMode) = viewModelScope.launch {
        runImport(mode) { SpreadsheetImportUtil.readRowsFromGoogleSheet(url) }
    }

    fun verifyPin(pin: String): Boolean = keystoreManager.verifyPin(pin)

    fun writeTemplate(context: android.content.Context, uri: Uri, mode: ImportMode) = viewModelScope.launch {
        writeXlsx(context, uri, templateBytes(mode), "Blank ${mode.name.lowercase(Locale.ROOT)} template saved")
    }

    fun exportData(context: android.content.Context, uri: Uri, mode: ImportMode) = viewModelScope.launch {
        _state.value = DataImportState(loading = true, message = "Preparing export...")
        runCatching {
            val bytes = if (mode == ImportMode.Customers) customerExportBytes() else billingExportBytes()
            writeXlsx(context, uri, bytes, "${mode.name} export saved")
        }.onFailure { error ->
            _state.value = DataImportState(message = error.message ?: "Export failed")
        }
    }

    private suspend fun runImport(mode: ImportMode, readRows: suspend () -> List<Map<String, String>>) {
        _state.value = DataImportState(loading = true, message = "Reading spreadsheet...")
        runCatching {
            val rows = readRows()
            if (mode == ImportMode.Customers) importCustomers(rows) else importBilling(rows)
        }.onSuccess { result ->
            _state.value = result
        }.onFailure { error ->
            _state.value = DataImportState(message = error.message ?: "Import failed")
        }
    }

    private suspend fun importCustomers(rows: List<Map<String, String>>): DataImportState {
        val preview = SpreadsheetImportUtil.parseCustomers(rows)
        preview.rows.forEach { row ->
            val existing = customerDao.getCustomerByPhone(row.phone)
            val customer = existing?.copy(
                name = row.name,
                phone = row.phone,
                companyName = row.companyName,
                address = row.address,
                gstNumber = row.gstNumber,
                email = row.email,
                dob = row.dob,
                anniversary = row.anniversary,
                updatedAt = Date()
            ) ?: Customer(
                name = row.name,
                phone = row.phone,
                companyName = row.companyName,
                address = row.address,
                gstNumber = row.gstNumber,
                email = row.email,
                dob = row.dob,
                anniversary = row.anniversary
            )
            if (existing == null) customerDao.insertCustomer(customer) else customerDao.updateCustomer(customer)
        }
        return DataImportState(message = "Customer import completed", imported = preview.rows.size, skipped = preview.skipped)
    }

    private suspend fun importBilling(rows: List<Map<String, String>>): DataImportState {
        val settings = settingsRepo.settingsFlow.first()
        val rate = settings.goldRate24K
        val gst = settings.gstPercent
        var imported = 0
        var skipped = 0
        val groups = rows.groupByIndexed { index, row ->
            row.pick("invoice_no", "invoice", "bill_no", "billno").ifBlank { "ROW_$index" }
        }
        for ((_, invoiceRows) in groups) {
            val first = invoiceRows.first()
            val phone = first.pick("phone", "mobile", "customer_phone", "customerphone")
            val customerName = first.pick("customer", "customer_name", "customername", "name", "ownername").ifBlank { "Imported Customer" }
            if (phone.isBlank() && customerName == "Imported Customer") {
                skipped += invoiceRows.size
                continue
            }
            val customer = customerDao.getCustomerByPhone(phone).let { existing ->
                existing ?: Customer(
                    name = customerName,
                    phone = phone,
                    companyName = first.pick("shop", "shopname", "company", "companyname"),
                    address = first.importAddress()
                ).let { customerDao.getCustomerById(customerDao.insertCustomer(it))!! }
            }
            val itemPreview = SpreadsheetImportUtil.parseBillItems(invoiceRows)
            if (itemPreview.rows.isEmpty()) {
                skipped += invoiceRows.size
                continue
            }
            val billDrafts = itemPreview.rows.map { row ->
                val gross = row.grossWeight.toDoubleOrNull() ?: 0.0
                val less = row.lessWeight.toDoubleOrNull() ?: 0.0
                val net = GoldCalc.netWeight(gross, less)
                val purity = row.purityPercent.toDoubleOrNull() ?: 91.6
                val making = row.makingPercent.toDoubleOrNull() ?: 0.0
                val stone = row.stoneValue.toDoubleOrNull() ?: 0.0
                BillItem(
                    invoiceId = 0,
                    description = row.description.ifBlank { "Imported jewellery" },
                    grossWeightGrams = gross,
                    lessWeightGrams = less,
                    purityPercent = purity,
                    karatLabel = row.karatLabel,
                    makingChargePerGram = making,
                    makingChargePercent = making,
                    stoneValue = stone,
                    netWeightGrams = net,
                    fineGoldGrams = GoldCalc.equivalentGramsWithStone(net, purity, making, stone, rate),
                    gramsWithMaking = GoldCalc.equivalentGramsWithStone(net, purity, making, stone, rate),
                    itemAmount = GoldCalc.itemAmount(net, purity, rate, making, stone)
                )
            }
            val subtotal = billDrafts.sumOf { it.itemAmount }
            val totalFine = GoldCalc.roundGrams(billDrafts.sumOf { it.gramsWithMaking })
            val cashPaid = first.pick("cash_paid", "cashpaid", "paid", "payment").toDoubleOrNull() ?: 0.0
            val total = GoldCalc.roundMoney(subtotal + (subtotal * gst / 100.0))
            val remaining = GoldCalc.roundMoney(total - cashPaid)
            val invoiceNo = first.pick("invoice_no", "invoice", "bill_no", "billno").ifBlank { settingsRepo.nextInvoiceNumber(settings.userPrefix) }
            val invoiceId = invoiceDao.insertInvoice(
                Invoice(
                    userPrefix = settings.userPrefix,
                    invoiceNumber = invoiceNo,
                    customerId = customer.id,
                    customerShopName = customer.companyName.ifEmpty { customer.name },
                    customerOwnerName = customer.name,
                    customerAddress = customer.address,
                    customerPhone = customer.phone,
                    date = Date(),
                    totalWeightGrams = GoldCalc.roundGrams(billDrafts.sumOf { it.netWeightGrams }),
                    totalFineGoldGrams = totalFine,
                    total916Grams = GoldCalc.equivalent916(totalFine),
                    subtotal = subtotal,
                    gstPercent = gst,
                    gstAmount = GoldCalc.roundMoney(subtotal * gst / 100.0),
                    totalAmount = total,
                    cashPaid = cashPaid,
                    remainingBalance = remaining,
                    paymentStatus = if (remaining <= 0.0) PaymentStatus.PAID else if (cashPaid > 0.0) PaymentStatus.PARTIAL else PaymentStatus.PENDING,
                    goldRate24K = rate
                )
            )
            billItemDao.insertBillItems(billDrafts.map { it.copy(invoiceId = invoiceId) })
            imported++
            skipped += itemPreview.skipped
        }
        return DataImportState(message = "Billing import completed", imported = imported, skipped = skipped)
    }

    private fun templateBytes(mode: ImportMode): ByteArray =
        SpreadsheetImportUtil.buildXlsx(
            if (mode == ImportMode.Customers) SpreadsheetImportUtil.customerTemplateRows() else SpreadsheetImportUtil.billingTemplateRows(),
            if (mode == ImportMode.Customers) "Customer Import" else "Billing Import"
        )

    private suspend fun customerExportBytes(): ByteArray {
        val rows = mutableListOf(SpreadsheetImportUtil.customerTemplateRows().first())
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        customerDao.getAllCustomersSync().forEach { c ->
            rows += listOf(
                c.name,
                c.phone,
                c.companyName,
                c.address,
                "",
                "",
                "",
                "",
                c.gstNumber,
                c.email,
                c.dob?.let(dateFormat::format).orEmpty(),
                c.anniversary?.let(dateFormat::format).orEmpty()
            )
        }
        return SpreadsheetImportUtil.buildXlsx(rows, "Customers")
    }

    private suspend fun billingExportBytes(): ByteArray {
        val rows = mutableListOf(SpreadsheetImportUtil.billingTemplateRows().first())
        invoiceDao.getAllInvoicesSync().forEach { invoice ->
            val items = billItemDao.getBillItemsForInvoiceSync(invoice.id)
            items.forEach { item ->
                rows += listOf(
                    invoice.invoiceNumber,
                    invoice.customerOwnerName,
                    invoice.customerPhone,
                    invoice.customerShopName,
                    invoice.customerAddress,
                    item.description,
                    item.grossWeightGrams.toString(),
                    item.lessWeightGrams.toString(),
                    item.purityPercent.toString(),
                    item.karatLabel,
                    (item.makingChargePercent.takeIf { it > 0.0 } ?: item.makingChargePerGram).toString(),
                    item.stoneValue.toString(),
                    invoice.cashPaid.toString()
                )
            }
        }
        return SpreadsheetImportUtil.buildXlsx(rows, "Billing")
    }

    private fun writeXlsx(context: android.content.Context, uri: Uri, bytes: ByteArray, message: String) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        _state.value = DataImportState(message = message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportScreen(onBack: () -> Unit, viewModel: DataImportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var mode by remember { mutableStateOf(ImportMode.Customers) }
    var sheetUrl by remember { mutableStateOf("") }
    var pendingWrite by remember { mutableStateOf<WriteAction?>(null) }
    var showExportPin by remember { mutableStateOf(false) }
    var showExportDrawer by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importFromFile(context, it, mode) }
    }
    val xlsxWriter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val action = pendingWrite
        pendingWrite = null
        if (uri != null && action != null) {
            if (action == WriteAction.Template) viewModel.writeTemplate(context, uri, mode) else viewModel.exportData(context, uri, mode)
        }
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("DATA IMPORT", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 16.sp, letterSpacing = 3.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Import Type", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImportModeChip("Customers", Icons.Default.GroupAdd, mode == ImportMode.Customers) { mode = ImportMode.Customers }
                            ImportModeChip("Billing", Icons.Default.ReceiptLong, mode == ImportMode.Billing) { mode = ImportMode.Billing }
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("File Import", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                        Text(importHelp(mode), style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                        OutlinedButton(
                            onClick = { showExportDrawer = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export / Blank Format", color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, null, tint = AuraColors.PrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose CSV or XLSX", color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Google Sheet URL", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                        GhostTextField(
                            value = sheetUrl,
                            onValueChange = { sheetUrl = it },
                            label = "Paste shared Google Sheet link",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = false
                        )
                        GoldButton(
                            text = "Import from Google Sheet",
                            onClick = { if (sheetUrl.isNotBlank()) viewModel.importFromSheet(sheetUrl, mode) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        )
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CloudDownload, null, tint = AuraColors.PrimaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(if (state.loading) "Importing..." else state.message.ifBlank { "Ready to import" }, color = AuraColors.OnSurface, style = MaterialTheme.typography.bodyLarge)
                            Text("Imported: ${state.imported} | Skipped: ${state.skipped}", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showExportDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showExportDrawer = false },
            containerColor = AuraColors.SurfaceContainerHigh
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Export Options", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                ExportDrawerOption(
                    icon = Icons.Default.Download,
                    title = "Blank XLSX Format",
                    subtitle = "Creates an empty import file. No PIN required.",
                    onClick = {
                        showExportDrawer = false
                        pendingWrite = WriteAction.Template
                        xlsxWriter.launch(if (mode == ImportMode.Customers) "customer_import_template.xlsx" else "billing_import_template.xlsx")
                    }
                )
                ExportDrawerOption(
                    icon = Icons.Default.CloudDownload,
                    title = if (mode == ImportMode.Customers) "Export With Customer Details" else "Export With Billing Details",
                    subtitle = "Contains app data. Login PIN is required.",
                    onClick = {
                        showExportDrawer = false
                        showExportPin = true
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showExportPin) {
        ExportPinDialog(
            onDismiss = { showExportPin = false },
            onConfirm = { pin, setError ->
                if (viewModel.verifyPin(pin)) {
                    showExportPin = false
                    pendingWrite = WriteAction.Export
                    xlsxWriter.launch(if (mode == ImportMode.Customers) "customers_export.xlsx" else "billing_export.xlsx")
                } else {
                    setError("Incorrect PIN")
                }
            }
        )
    }
}

@Composable
private fun ImportModeChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .background(if (selected) AuraColors.PrimaryContainer.copy(alpha = 0.16f) else AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) AuraColors.PrimaryContainer else AuraColors.GlassWhite10, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = if (selected) AuraColors.PrimaryContainer else AuraColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, color = if (selected) AuraColors.PrimaryContainer else AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

private fun importHelp(mode: ImportMode): String =
    if (mode == ImportMode.Customers) {
        "Columns: name, phone, shop/company, address, gst, email, dob, anniversary."
    } else {
        "Columns: invoice_no, customer/name, phone, description, gross, less, purity or karat, making, stone, cash_paid."
    }

@Composable
private fun ExportDrawerOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AuraColors.GlassWhite5, RoundedCornerShape(14.dp))
            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(42.dp).background(AuraColors.PrimaryContainer.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = AuraColors.OnSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExportPinDialog(onDismiss: () -> Unit, onConfirm: (String, (String) -> Unit) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Confirm export", color = AuraColors.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter your PIN before exporting customer or billing data.", color = AuraColors.OnSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) { pin = it; error = "" } },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = error.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer,
                        unfocusedBorderColor = AuraColors.GlassWhite20,
                        focusedTextColor = AuraColors.OnSurface,
                        unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5,
                        unfocusedContainerColor = AuraColors.GlassWhite5
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                if (error.isNotEmpty()) Text(error, color = AuraColors.Error)
            }
        },
        confirmButton = { GoldButton("Export", onClick = { onConfirm(pin) { error = it } }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}

private fun Map<String, String>.pick(vararg keys: String): String =
    keys.firstNotNullOfOrNull { this[normalizeImportKey(it)]?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun Map<String, String>.importAddress(): String {
    val splitAddress = listOf(
        pick("address_line_1", "addressline1", "address1", "street"),
        pick("address_line_2", "addressline2", "address2", "area"),
        pick("city", "town"),
        pick("state"),
        pick("pincode", "pin", "zipcode", "postalcode")
    ).filter { it.isNotBlank() }.joinToString(", ")
    return splitAddress.ifBlank { pick("address", "location") }
}

private fun normalizeImportKey(value: String): String =
    value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private inline fun <T, K> Iterable<T>.groupByIndexed(keySelector: (Int, T) -> K): Map<K, List<T>> {
    val destination = LinkedHashMap<K, MutableList<T>>()
    forEachIndexed { index, element ->
        destination.getOrPut(keySelector(index, element)) { mutableListOf() }.add(element)
    }
    return destination
}
