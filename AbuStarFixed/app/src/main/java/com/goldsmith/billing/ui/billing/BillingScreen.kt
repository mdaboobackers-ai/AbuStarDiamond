@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.billing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.GoldCalc
import com.goldsmith.billing.util.g3
import com.goldsmith.billing.util.g4
import com.goldsmith.billing.util.inr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

// ─── Less Weight Drawer Row ───────────────────────────────────────────────────
data class LessRow(val id: Int = System.nanoTime().toInt(), val count: String = "", val gramsEach: String = "") {
    val countVal get() = count.toDoubleOrNull() ?: 0.0
    val gramsVal get() = gramsEach.toDoubleOrNull() ?: 0.0
    val total    get() = GoldCalc.netWeight(countVal * gramsVal, 0.0)
}

// ─── Gold Payment Entry (UI) ──────────────────────────────────────────────────
data class GoldPayDraft(val id: Int = System.nanoTime().toInt(), val karat: Int = 22, val grams: String = "") {
    val purityPct get() = when (karat) { 24 -> 100.0; 22 -> 91.6; 20 -> 85.0; 18 -> 75.0; else -> 75.0 }
    fun cashEquiv(rate24K: Double) = (grams.toDoubleOrNull() ?: 0.0) * rate24K * (purityPct / 100.0)
}

// ─── Bill Item Draft ──────────────────────────────────────────────────────────
data class BillItemDraft(
    val id: Int = System.nanoTime().toInt(),
    val description: String = "",
    val grossWeight: String = "",
    val lessWeight: String = "0",           // total from drawer OR typed
    val lessRows: List<LessRow> = emptyList(),
    val purityLabel: String = "22K (91.6%)",
    val purityPercent: String = "91.6",
    val customPurity: String = "",
    val makingChargePercent: String = "0",
    val makingPerGram: String = "0",
    val isPercentMaking: Boolean = true,
    val stoneValue: String = "0",
    val imageUris: List<String> = emptyList()
) {
    val grossW   get() = grossWeight.toDoubleOrNull() ?: 0.0
    val lessW    get() = if (lessRows.isNotEmpty()) lessRows.sumOf { it.total }
                        else (lessWeight.toDoubleOrNull() ?: 0.0)
    val netW     get() = GoldCalc.netWeight(grossW, lessW)
    val purity   get() = if (purityLabel == "Custom") (customPurity.toDoubleOrNull() ?: 0.0)
                        else (purityPercent.toDoubleOrNull() ?: 91.6)
    val makingE  get() = if (isPercentMaking) (makingChargePercent.toDoubleOrNull() ?: 0.0) else 0.0
    val stoneVal get() = stoneValue.toDoubleOrNull() ?: 0.0

    val eqGrams  get() = GoldCalc.eqGrams(netW, purity)
    val fineGold get() = GoldCalc.fineGoldGrams(netW, purity, makingE)
    fun cashValue(rate24K: Double) = GoldCalc.cashValue(fineGold, rate24K, stoneVal)

    // Validation
    fun validateErrors(): Map<String, String> = buildMap {
        if (grossWeight.isBlank() || !GoldCalc.isValidWeight(grossWeight)) put("gross", "Enter valid weight")
        if (!GoldCalc.isValidWeight(lessWeight) && lessRows.isEmpty()) put("less", "Enter valid weight")
        if (purityLabel == "Custom" && !GoldCalc.isValidPurity(customPurity)) put("purity", "Enter valid purity (0-100)")
        if (isPercentMaking && !GoldCalc.isValidPercent(makingChargePercent)) put("making", "Enter valid making %")
    }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val billItemDao: BillItemDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    val settings = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _customerQuery = MutableStateFlow("")
    val customerQuery = _customerQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val suggestedCustomers = _customerQuery.debounce(200)
        .flatMapLatest { q -> if (q.length >= 1) customerDao.searchCustomers(q) else customerDao.getRecentCustomers() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    var selectedCustomer by mutableStateOf<Customer?>(null)
    var items            by mutableStateOf(listOf(BillItemDraft()))
    var cashPaid         by mutableStateOf("")
    var goldPayments     by mutableStateOf(listOf<GoldPayDraft>())
    var usePreviousBalance by mutableStateOf(false)

    private val _saving    = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun setCustomerQuery(q: String) { _customerQuery.value = q }
    fun selectCustomer(c: Customer) { selectedCustomer = c; _customerQuery.value = c.name }
    fun addItem()  { items = items + BillItemDraft() }
    fun removeItem(idx: Int) { if (items.size > 1) items = items.toMutableList().also { it.removeAt(idx) } }
    fun updateItem(idx: Int, d: BillItemDraft) { items = items.toMutableList().also { it[idx] = d } }
    fun addGoldPayment()   { goldPayments = goldPayments + GoldPayDraft() }
    fun removeGoldPayment(idx: Int) { goldPayments = goldPayments.toMutableList().also { it.removeAt(idx) } }
    fun updateGoldPayment(idx: Int, d: GoldPayDraft) { goldPayments = goldPayments.toMutableList().also { it[idx] = d } }

    fun totalNetWeight()  = items.sumOf { it.netW }
    fun totalPureGold()   = items.sumOf { it.eqGrams }
    fun totalFineGold()   = items.sumOf { it.fineGold }
    fun totalCashPreGst(rate: Double) = items.sumOf { it.cashValue(rate) }
    fun totalGoldPaidCash(rate: Double) = goldPayments.sumOf { it.cashEquiv(rate) }
    fun equivalent22K()  = GoldCalc.eq22KJewel(totalPureGold())
    fun equivalent18K()  = GoldCalc.eq18KJewel(totalPureGold())

    fun saveInvoice(onSaved: (Long) -> Unit) = viewModelScope.launch {
        if (_saving.value) return@launch
        val customer = selectedCustomer ?: run { _saveError.value = "Please select a customer"; return@launch }
        if (items.any { it.validateErrors().isNotEmpty() }) { _saveError.value = "Fix item errors first"; return@launch }
        _saving.value = true; _saveError.value = null
        try {
            val s       = settings.value
            val rate    = s.goldRate24K
            val gstPct  = s.gstPercent
            val subTotal = totalCashPreGst(rate)
            val gstAmt   = subTotal * gstPct / 100.0
            val total    = subTotal + gstAmt
            val cash     = cashPaid.toDoubleOrNull() ?: 0.0
            val goldCash = totalGoldPaidCash(rate)
            val prevBal  = if (usePreviousBalance) customer.cashBalance.coerceAtLeast(0.0) else 0.0
            val remaining = total - cash - goldCash - prevBal
            val pureGold  = totalPureGold()

            val counter   = settingsRepo.incrementInvoiceCounter()
            val prefix    = s.devicePrefix.ifEmpty { "A" }
            val invNum    = "INV-$prefix-$counter"

            // Serialise gold payments
            val goldJson = goldPayments.filter { (it.grams.toDoubleOrNull() ?: 0.0) > 0 }
                .joinToString(";") { "${it.karat},${it.grams},${it.purityPct},${it.cashEquiv(rate)}" }

            val invoice = Invoice(
                invoiceNumber          = invNum,
                customerId             = customer.id,
                date                   = Date(),
                totalNetWeightGrams    = totalNetWeight(),
                totalPureGoldGrams     = pureGold,
                totalFineGoldGrams     = totalFineGold(),
                totalEq22KGrams        = equivalent22K(),
                totalEq18KGrams        = equivalent18K(),
                subtotalCash           = subTotal,
                gstPercent             = gstPct,
                gstAmount              = gstAmt,
                totalAmount            = total,
                cashPaid               = cash,
                goldPayments           = goldJson,
                totalGoldPaidCash      = goldCash,
                previousBalanceAdjusted = prevBal,
                remainingCash          = remaining.coerceAtLeast(0.0),
                remainingGoldGrams     = GoldCalc.cashToGoldGrams(remaining.coerceAtLeast(0.0), rate),
                paymentStatus          = when {
                    remaining <= 0.01 -> PaymentStatus.PAID
                    cash + goldCash > 0 -> PaymentStatus.PARTIAL
                    else -> PaymentStatus.PENDING
                },
                goldRate24K            = rate,
                devicePrefix           = prefix,
                // FIX: Always snapshot customer details
                customerNameSnapshot   = customer.name,
                customerPhoneSnapshot  = customer.phone,
                customerAddressSnapshot = customer.address,
                customerGstSnapshot    = customer.gstNumber
            )
            val invoiceId = invoiceDao.insertInvoice(invoice)

            billItemDao.insertBillItems(items.map { d ->
                BillItem(
                    invoiceId          = invoiceId,
                    description        = d.description,
                    grossWeightGrams   = d.grossW,
                    lessWeightGrams    = d.lessW,
                    purityPercent      = d.purity,
                    karatLabel         = d.purityLabel,
                    makingChargePercent = d.makingE,
                    makingChargePerGram = d.makingPerGram.toDoubleOrNull() ?: 0.0,
                    isPercentMaking    = d.isPercentMaking,
                    stoneValue         = d.stoneVal,
                    imageUris          = d.imageUris.joinToString("||"),
                    netWeightGrams     = d.netW,
                    eqGrams            = d.eqGrams,
                    fineGoldGrams      = d.fineGold,
                    itemCashValue      = d.cashValue(rate)
                )
            })

            customerDao.updateCustomer(customer.copy(
                cashBalance = customer.cashBalance + remaining.coerceAtLeast(0.0),
                updatedAt   = Date()
            ))
            onSaved(invoiceId)
        } catch (e: Exception) {
            _saveError.value = "Save failed: ${e.message}"
        } finally {
            _saving.value = false
        }
    }
}

// ─── Billing Screen ───────────────────────────────────────────────────────────
@Composable
fun BillingScreen(
    preselectedCustomerId: Long?,
    onBack: () -> Unit,
    onInvoiceCreated: (Long) -> Unit,
    viewModel: BillingViewModel = hiltViewModel()
) {
    var step       by remember { mutableIntStateOf(0) }
    val settings   by viewModel.settings.collectAsState()
    val saving     by viewModel.saving.collectAsState()
    val saveErr    by viewModel.saveError.collectAsState()

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
                        Text("NEW BILL", style = MaterialTheme.typography.labelSmall,
                            color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 3.sp)
                    }
                },
                navigationIcon = { if (step == 0) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        },
        bottomBar = {
            if (step >= 1) BillingBottomBar(settings, viewModel, step, saving,
                onNext = { if (step < 2) step++ else viewModel.saveInvoice(onInvoiceCreated) },
                // FIX: step indicator is clickable — tap step 1 from step 2 to go back
                onGoToStep = { s -> if (s < step) step = s }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // FIX: Clickable step indicator — tap any previous step to navigate back
            ClickableStepIndicator(current = step, onStepClick = { s -> if (s < step) step = s })

            saveErr?.let { err ->
                Row(Modifier.fillMaxWidth().background(AuraColors.ErrorContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = AuraColors.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = AuraColors.Error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            when (step) {
                0 -> CustomerStep(viewModel) { step = 1 }
                1 -> ItemsStep(viewModel, settings.goldRate24K, settings.gstPercent)
                2 -> PaymentStep(viewModel, settings)
            }
        }
    }
}

// ─── Clickable Step Indicator ─────────────────────────────────────────────────
@Composable
private fun ClickableStepIndicator(current: Int, onStepClick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        listOf("Customer", "Items", "Payment").forEachIndexed { idx, label ->
            val active = idx == current
            val done   = idx < current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(enabled = done) { onStepClick(idx) }
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(when { done -> AuraColors.Primary.copy(alpha = 0.8f); active -> AuraColors.PrimaryContainer; else -> AuraColors.GlassWhite5 })
                        .border(1.dp, if (active || done) AuraColors.PrimaryContainer else AuraColors.GlassWhite20, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Default.Check, null, tint = AuraColors.OnPrimary, modifier = Modifier.size(16.dp))
                    else Text("${idx+1}", style = MaterialTheme.typography.labelSmall, color = if (active) AuraColors.OnPrimary else AuraColors.OnSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(3.dp))
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = if (active) AuraColors.PrimaryContainer else if (done) AuraColors.Primary.copy(alpha = 0.7f) else AuraColors.OnSurface.copy(alpha = 0.35f),
                    fontSize = 9.sp, letterSpacing = 1.sp)
            }
            if (idx < 2) Box(Modifier.weight(1f).height(1.dp).background(if (idx < current) AuraColors.PrimaryContainer.copy(alpha = 0.4f) else AuraColors.GlassWhite10).padding(bottom = 18.dp))
        }
    }
}

// ─── Step 1: Customer ─────────────────────────────────────────────────────────
@Composable
private fun CustomerStep(viewModel: BillingViewModel, onProceed: () -> Unit) {
    val query       by viewModel.customerQuery.collectAsState()
    val suggestions by viewModel.suggestedCustomers.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PersonSearch, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                        Text("Identify Client", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query, onValueChange = viewModel::setCustomerQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name or phone...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f)) },
                        colors = goldTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp)).border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(12.dp))) {
                            suggestions.take(6).forEachIndexed { idx, c ->
                                Row(Modifier.fillMaxWidth().clickable { viewModel.selectCustomer(c); onProceed() }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(AuraColors.SurfaceContainerHighest), contentAlignment = Alignment.Center) {
                                        Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(c.name, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                        Text(c.phone, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface.copy(alpha = 0.4f))
                                    }
                                    if (c.cashBalance != 0.0) Text(
                                        if (c.cashBalance > 0) "Owes ${c.cashBalance.inr()}" else "Cr ${(-c.cashBalance).inr()}",
                                        style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                                        color = if (c.cashBalance > 0) AuraColors.Error else AuraColors.Primary
                                    )
                                    Icon(Icons.Default.ChevronRight, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(15.dp))
                                }
                                if (idx < suggestions.size - 1) HorizontalDivider(color = AuraColors.GlassWhite5)
                            }
                        }
                    }
                }
            }
        }
        item {
            viewModel.selectedCustomer?.let { c ->
                GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                Text(c.phone, color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                if (c.address.isNotBlank()) Text(c.address, color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                            }
                        }
                        if (c.cashBalance != 0.0) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = AuraColors.GlassWhite5)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Previous Balance:", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                                Text(c.cashBalance.inr(), style = MaterialTheme.typography.bodyLarge, color = if (c.cashBalance > 0) AuraColors.Error else AuraColors.Primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        GoldButton("Proceed to Items →", onClick = onProceed, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ─── Step 2: Bill Items ───────────────────────────────────────────────────────
@Composable
private fun ItemsStep(viewModel: BillingViewModel, rate24K: Double, gstPercent: Double) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("BILL ITEMS", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, fontSize = 10.sp)
                    Text("Transaction Entry", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("24K Rate", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 9.sp)
                    Text("₹${String.format("%,.2f", rate24K)}/g", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        itemsIndexed(viewModel.items, key = { _, d -> d.id }) { idx, draft ->
            BillItemCard(draft = draft, rate24K = rate24K, gstPercent = gstPercent,
                onUpdate = { viewModel.updateItem(idx, it) },
                onRemove = { viewModel.removeItem(idx) },
                canRemove = viewModel.items.size > 1,
                itemIndex = idx + 1)
        }

        item {
            OutlinedButton(onClick = viewModel::addItem, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp))
                Text("ADD ITEM TO BILL", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            }
        }

        // FIX: Summary shows GRAMS not cash
        item {
            GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GOLD SUMMARY", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp, fontSize = 9.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GramBox("Total Net", "${viewModel.totalNetWeight().g3()}g")
                        GramBox("Pure Gold\n(24K)", "${viewModel.totalPureGold().g4()}g", highlight = true)
                        GramBox("91.6 Jewel\n(22K)", "${viewModel.equivalent22K().g3()}g")
                        GramBox("75% Jewel\n(18K)", "${viewModel.equivalent18K().g3()}g")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun GramBox(label: String, value: String, highlight: Boolean = false) {
    Column(
        Modifier.background(if (highlight) AuraColors.PrimaryContainer.copy(alpha = 0.1f) else AuraColors.GlassWhite5, RoundedCornerShape(10.dp))
            .then(if (highlight) Modifier.border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 8.sp, letterSpacing = 0.3.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (highlight) AuraColors.PrimaryContainer else AuraColors.OnSurface, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
    }
}

// ─── Bill Item Card ───────────────────────────────────────────────────────────
@Composable
private fun BillItemCard(
    draft: BillItemDraft, rate24K: Double, gstPercent: Double,
    onUpdate: (BillItemDraft) -> Unit, onRemove: () -> Unit,
    canRemove: Boolean, itemIndex: Int
) {
    val errors           = draft.validateErrors()
    var showLessDrawer   by remember { mutableStateOf(false) }
    var expandedDropdown by remember { mutableStateOf(false) }
    val result           = GoldCalc.calculateItem(draft.grossW, draft.lessW, draft.purity, draft.makingE, rate24K, gstPercent, draft.stoneVal)
    val galleryLauncher  = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        onUpdate(draft.copy(imageUris = draft.imageUris + uris.map { it.toString() }))
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ITEM $itemIndex", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 1.sp, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (canRemove) IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = AuraColors.Error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }

            // Description
            ValidatedField(label = "DESCRIPTION", value = draft.description, onValue = { onUpdate(draft.copy(description = it)) }, placeholder = "e.g. Gold Chain 22K", error = null)

            // Gross | Less
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    ValidatedField("A — GROSS (g)", draft.grossWeight, { onUpdate(draft.copy(grossWeight = it)) }, "0.000", errors["gross"], KeyboardType.Decimal)
                }
                Column(Modifier.weight(1f)) {
                    // FIX: Less Weight with drawer button
                    Text("B — LESS (g)", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (draft.lessRows.isNotEmpty()) draft.lessW.g3() else draft.lessWeight,
                        onValueChange = { if (draft.lessRows.isEmpty()) onUpdate(draft.copy(lessWeight = it)) },
                        readOnly = draft.lessRows.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.000", color = AuraColors.OnSurface.copy(alpha = 0.2f)) },
                        trailingIcon = {
                            IconButton(onClick = { showLessDrawer = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.TableRows, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(16.dp))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = goldTextFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true,
                        isError = errors["less"] != null
                    )
                    errors["less"]?.let { Text(it, color = AuraColors.Error, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
                }
            }

            // Net weight display
            Box(Modifier.fillMaxWidth().background(AuraColors.PrimaryContainer.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("C = A − B  NET WEIGHT", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer.copy(alpha = 0.7f), fontSize = 9.sp)
                    Text("${draft.netW.g3()} g", style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }

            // Purity dropdown
            Column(Modifier.fillMaxWidth()) {
                Text("D — PURITY %", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(expanded = expandedDropdown, onExpandedChange = { expandedDropdown = it }) {
                    OutlinedTextField(value = draft.purityLabel, onValueChange = {}, readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedDropdown) },
                        colors = goldTextFieldColors(), shape = RoundedCornerShape(10.dp))
                    ExposedDropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.background(AuraColors.SurfaceContainerHigh)) {
                        GoldCalc.STANDARD_PURITIES.forEach { (label, pct) ->
                            DropdownMenuItem(text = { Text(label, color = AuraColors.OnSurface) }, onClick = {
                                onUpdate(draft.copy(purityLabel = label, purityPercent = if (pct > 0) pct.toString() else draft.purityPercent))
                                expandedDropdown = false
                            })
                        }
                    }
                }
                if (draft.purityLabel == "Custom") {
                    Spacer(Modifier.height(6.dp))
                    ValidatedField("CUSTOM PURITY %", draft.customPurity, { onUpdate(draft.copy(customPurity = it)) }, "e.g. 87.5", errors["purity"], KeyboardType.Decimal)
                }
            }

            // Making charges
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("E — MAKING", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(true to "%", false to "₹/g").forEach { (isPct, lbl) ->
                            FilterChip(selected = draft.isPercentMaking == isPct, onClick = { onUpdate(draft.copy(isPercentMaking = isPct)) },
                                label = { Text(lbl, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AuraColors.PrimaryContainer, selectedLabelColor = AuraColors.OnPrimary))
                        }
                    }
                }
                if (draft.isPercentMaking) {
                    ValidatedField("MAKING %", draft.makingChargePercent, { onUpdate(draft.copy(makingChargePercent = it)) }, "0", errors["making"], KeyboardType.Decimal, Modifier.weight(1f))
                } else {
                    ValidatedField("MAKING ₹/g", draft.makingPerGram, { onUpdate(draft.copy(makingPerGram = it)) }, "0", null, KeyboardType.Decimal, Modifier.weight(1f))
                }
            }

            ValidatedField("STONE / WASTAGE (₹)", draft.stoneValue, { onUpdate(draft.copy(stoneValue = it)) }, "0", null, KeyboardType.Decimal)

            // Attachments
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.height(36.dp),
                    border = BorderStroke(1.dp, AuraColors.GlassWhite20), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurfaceVariant)) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                    Text("Attach (${draft.imageUris.size})", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
                draft.imageUris.take(3).forEach { uri ->
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(AuraColors.GlassWhite10)) {
                        coil.compose.AsyncImage(uri, null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }

            HorizontalDivider(color = AuraColors.GlassWhite10)

            // Results — FIX: show Eq Grams prominently, not cash per item
            Column(Modifier.fillMaxWidth().background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("F = C × ((D+E)/100)", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 9.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ResultChip("Eq Grams", "${result.eqGrams.g4()}g", highlight = true)
                    ResultChip("Fine Gold", "${result.fineGoldBilling.g4()}g")
                    ResultChip("22K Equiv", "${GoldCalc.eq22KJewel(result.eqGrams).g3()}g")
                }
            }
        }
    }

    // Less Weight Drawer
    if (showLessDrawer) {
        LessWeightDrawer(
            rows = draft.lessRows,
            onDismiss = { showLessDrawer = false },
            onSave = { rows ->
                val total = rows.sumOf { it.total }
                onUpdate(draft.copy(lessRows = rows, lessWeight = total.g3()))
                showLessDrawer = false
            }
        )
    }
}

@Composable
private fun ResultChip(label: String, value: String, highlight: Boolean = false) {
    Column(
        Modifier.background(if (highlight) AuraColors.PrimaryContainer.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 8.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (highlight) AuraColors.PrimaryContainer else AuraColors.OnSurface, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

// ─── Less Weight Drawer (3-column table) ─────────────────────────────────────
@Composable
private fun LessWeightDrawer(rows: List<LessRow>, onDismiss: () -> Unit, onSave: (List<LessRow>) -> Unit) {
    var localRows by remember { mutableStateOf(if (rows.isEmpty()) listOf(LessRow()) else rows.toMutableList()) }

    fun ensureTrailing() {
        val last = localRows.lastOrNull()
        if (last == null || last.count.isNotEmpty() || last.gramsEach.isNotEmpty()) {
            localRows = localRows + LessRow()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuraColors.SurfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Less Weight Table", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface, modifier = Modifier.padding(bottom = 4.dp))
            Text("Enter count and weight per piece. Total is auto-calculated.", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))

            // Header row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TableHeader("COUNT", Modifier.weight(1f))
                TableHeader("GRAMS / PIECE", Modifier.weight(1f))
                TableHeader("TOTAL", Modifier.weight(1f))
                Spacer(Modifier.size(28.dp))
            }
            HorizontalDivider(color = AuraColors.GlassWhite20, modifier = Modifier.padding(vertical = 6.dp))

            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                itemsIndexed(localRows, key = { _, r -> r.id }) { idx, row ->
                    val isLast = idx == localRows.size - 1
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TableInput(row.count, Modifier.weight(1f)) { v ->
                            localRows = localRows.toMutableList().also { it[idx] = row.copy(count = v) }
                            ensureTrailing()
                        }
                        TableInput(row.gramsEach, Modifier.weight(1f)) { v ->
                            localRows = localRows.toMutableList().also { it[idx] = row.copy(gramsEach = v) }
                            ensureTrailing()
                        }
                        Box(Modifier.weight(1f).background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 12.dp)) {
                            Text(if (row.total > 0) row.total.g3() else "—", style = MaterialTheme.typography.bodyMedium, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                        if (!isLast) {
                            IconButton(onClick = { localRows = localRows.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.RemoveCircleOutline, null, tint = AuraColors.Error.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Spacer(Modifier.size(28.dp))
                        }
                    }
                }
            }

            // Totals row
            val grandTotal = localRows.sumOf { it.total }
            if (grandTotal > 0) {
                HorizontalDivider(color = AuraColors.PrimaryContainer.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("TOTAL LESS: ", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, letterSpacing = 1.sp)
                    Text("${grandTotal.g3()} g", style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), border = BorderStroke(1.dp, AuraColors.GlassWhite20), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurfaceVariant)) {
                    Text("CANCEL", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
                GoldButton("SAVE & APPLY", onClick = { onSave(localRows.filter { it.count.isNotEmpty() || it.gramsEach.isNotEmpty() }) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
}

@Composable
private fun TableInput(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = goldTextFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AuraColors.OnSurface))
}

// ─── Step 3: Payment ──────────────────────────────────────────────────────────
@Composable
private fun PaymentStep(viewModel: BillingViewModel, settings: AppSettings) {
    val rate     = settings.goldRate24K
    val gst      = settings.gstPercent
    val subTotal = viewModel.totalCashPreGst(rate)
    val gstAmt   = subTotal * gst / 100.0
    val total    = subTotal + gstAmt
    val cashP    = viewModel.cashPaid.toDoubleOrNull() ?: 0.0
    val goldCash = viewModel.totalGoldPaidCash(rate)
    val remaining = (total - cashP - goldCash).coerceAtLeast(0.0)
    val remainGoldGrams = GoldCalc.cashToGoldGrams(remaining, rate)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Gold Gram Summary
        item {
            GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("GOLD SUMMARY (GRAMS)", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, letterSpacing = 2.sp, fontSize = 9.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GramBox("Net Weight",   "${viewModel.totalNetWeight().g3()}g")
                        GramBox("Pure Gold\n(24K)", "${viewModel.totalPureGold().g4()}g", highlight = true)
                        GramBox("22K Equiv\n(÷0.916)", "${viewModel.equivalent22K().g3()}g")
                        GramBox("18K Equiv\n(÷0.75)", "${viewModel.equivalent18K().g3()}g")
                    }
                }
            }
        }

        // FIX: Subtotal is TOTAL GRAMS per user request
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CASH SUMMARY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, fontSize = 9.sp)
                    // FIX: Sub total = total grams (not cash)
                    SumRow("Sub Total (grams)", "${viewModel.totalNetWeight().g3()} g")
                    SumRow("Pure Gold Total",   "${viewModel.totalPureGold().g4()} g")
                    SumRow("Cash Value",        subTotal.inr())
                    SumRow("GST ($gst%)",       gstAmt.inr())
                    HorizontalDivider(color = AuraColors.PrimaryContainer.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL PAYABLE", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface, letterSpacing = 1.sp)
                        Text(total.inr(), style = MaterialTheme.typography.titleLarge, color = AuraColors.PrimaryContainer, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Payment inputs
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PAYMENT RECEIVED", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, fontSize = 9.sp)

                    // Cash input with validation
                    Column {
                        Text("CASH (₹)", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = viewModel.cashPaid,
                            onValueChange = { v ->
                                if (v.isEmpty() || v.all { it.isDigit() || it == '.' }) viewModel.cashPaid = v
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("0.00", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = goldTextFieldColors(), shape = RoundedCornerShape(10.dp), singleLine = true,
                            isError = viewModel.cashPaid.isNotEmpty() && !GoldCalc.isValidPayment(viewModel.cashPaid)
                        )
                        if (viewModel.cashPaid.isNotEmpty() && !GoldCalc.isValidPayment(viewModel.cashPaid)) {
                            Text("Enter valid number", color = AuraColors.Error, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }
                    }

                    // FIX: Split gold payments — multiple entries with karat dropdown
                    Text("GOLD PAYMENT", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.sp)
                    viewModel.goldPayments.forEachIndexed { idx, gp ->
                        GoldPayRow(gp = gp, rate24K = rate,
                            onUpdate = { viewModel.updateGoldPayment(idx, it) },
                            onRemove = { viewModel.removeGoldPayment(idx) })
                    }
                    OutlinedButton(onClick = viewModel::addGoldPayment, modifier = Modifier.fillMaxWidth().height(40.dp),
                        border = BorderStroke(1.dp, AuraColors.GlassWhite20), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                        Text("+ ADD GOLD PAYMENT (24K/22K/20K/18K)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    }

                    // Previous balance
                    viewModel.selectedCustomer?.let { c ->
                        if (c.cashBalance > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = viewModel.usePreviousBalance, onCheckedChange = { viewModel.usePreviousBalance = it },
                                    colors = CheckboxDefaults.colors(checkedColor = AuraColors.PrimaryContainer))
                                Text("Adjust prev. balance: ${c.cashBalance.inr()}", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    HorizontalDivider(color = AuraColors.GlassWhite10)

                    // FIX: Balance shows BOTH ₹ and grams
                    Column(Modifier.fillMaxWidth().background(
                        if (remaining > 0.01) AuraColors.Error.copy(alpha = 0.07f) else AuraColors.Primary.copy(alpha = 0.07f),
                        RoundedCornerShape(10.dp)
                    ).padding(12.dp)) {
                        Text("REMAINING BALANCE", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 1.sp, fontSize = 9.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Column {
                                Text(remaining.inr(), style = MaterialTheme.typography.titleLarge,
                                    color = if (remaining > 0.01) AuraColors.Error else AuraColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                if (remaining > 0.01 && rate > 0) {
                                    Text("OR  ${remainGoldGrams.g3()} g (pure gold)", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                            if (remaining <= 0.01) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(18.dp))
                                    Text("PAID IN FULL", style = MaterialTheme.typography.labelSmall, color = AuraColors.Primary, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun GoldPayRow(gp: GoldPayDraft, rate24K: Double, onUpdate: (GoldPayDraft) -> Unit, onRemove: () -> Unit) {
    var expandedKarat by remember { mutableStateOf(false) }
    val karats = listOf(24 to "24K (100%)", 22 to "22K (91.6%)", 20 to "20K (85.0%)", 18 to "18K (75.0%)")
    Row(Modifier.fillMaxWidth().background(AuraColors.GlassWhite5, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expandedKarat, onExpandedChange = { expandedKarat = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(value = karats.firstOrNull { it.first == gp.karat }?.second ?: "22K", onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedKarat) },
                colors = goldTextFieldColors(), shape = RoundedCornerShape(8.dp), label = { Text("Karat", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) })
            ExposedDropdownMenu(expanded = expandedKarat, onDismissRequest = { expandedKarat = false }, modifier = Modifier.background(AuraColors.SurfaceContainerHigh)) {
                karats.forEach { (k, lbl) -> DropdownMenuItem(text = { Text(lbl, color = AuraColors.OnSurface) }, onClick = { onUpdate(gp.copy(karat = k)); expandedKarat = false }) }
            }
        }
        Column(Modifier.weight(1f)) {
            OutlinedTextField(value = gp.grams, onValueChange = { onUpdate(gp.copy(grams = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Grams", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = goldTextFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true)
            val cash = gp.cashEquiv(rate24K)
            if (cash > 0) Text("≈ ${cash.inr()}", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 9.sp)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.RemoveCircleOutline, null, tint = AuraColors.Error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Billing Bottom Bar ───────────────────────────────────────────────────────
@Composable
private fun BillingBottomBar(settings: AppSettings, viewModel: BillingViewModel, step: Int, saving: Boolean,
    onNext: () -> Unit, onGoToStep: (Int) -> Unit) {
    val rate     = settings.goldRate24K
    val subTotal = viewModel.totalCashPreGst(rate)
    val total    = subTotal * (1 + settings.gstPercent / 100.0)
    val pureGold = viewModel.totalPureGold()

    Surface(color = AuraColors.SurfaceContainerLowest.copy(alpha = 0.97f), tonalElevation = 8.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = AuraColors.GlassWhite5)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MiniStat("Net", "${viewModel.totalNetWeight().g3()}g")
                    MiniStat("Pure\nGold", "${pureGold.g4()}g", highlight = true)
                    MiniStat("Total\n₹", total.inrShort())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // FIX: Remove back button from payment step — step indicator handles navigation
                    if (step == 1) {
                        OutlinedButton(onClick = { onGoToStep(0) }, modifier = Modifier.height(42.dp),
                            border = BorderStroke(1.dp, AuraColors.GlassWhite20), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.OnSurface)) {
                            Text("← Back", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // FIX: Fancy save button label
                    val btnLabel = when {
                        saving -> "Sealing…"
                        step == 2 -> "✦ Seal the Bill"
                        else -> "Next →"
                    }
                    GoldButton(btnLabel, onClick = onNext, enabled = !saving, modifier = Modifier.height(42.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.4f), fontSize = 7.sp, letterSpacing = 0.3.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (highlight) AuraColors.PrimaryContainer else AuraColors.OnSurface, fontSize = 11.sp, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SumRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ValidatedField(label: String, value: String, onValue: (String) -> Unit, placeholder: String = "",
    error: String? = null, keyboardType: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth()) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = AuraColors.OnSurface.copy(alpha = 0.2f)) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = goldTextFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true, isError = error != null)
        error?.let { Text(it, color = AuraColors.Error, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
    }
}

@Composable
fun goldTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AuraColors.PrimaryContainer, unfocusedBorderColor = AuraColors.GlassWhite10,
    focusedTextColor = AuraColors.OnSurface, unfocusedTextColor = AuraColors.OnSurface,
    focusedContainerColor = AuraColors.GlassWhite5, unfocusedContainerColor = Color.Transparent,
    errorBorderColor = AuraColors.Error, errorTextColor = AuraColors.OnSurface
)

private fun Double.inrShort(): String {
    val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
    nf.minimumFractionDigits = 0; nf.maximumFractionDigits = 0
    return "₹${nf.format(this)}"
}
