@file:OptIn(ExperimentalMaterial3Api::class)
package com.goldsmith.billing.ui.billing

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.data.repository.SettingsRepository
import com.goldsmith.billing.ui.components.*
import com.goldsmith.billing.ui.customer.CustomerCard
import com.goldsmith.billing.ui.theme.AuraColors
import com.goldsmith.billing.util.GoldCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

// ─── BillItemDraft ────────────────────────────────────────────────────────────
data class BillItemDraft(
    val id: Int = System.currentTimeMillis().toInt(),
    val description: String = "",
    val grossWeight: String = "",
    val lessWeight: String = "0",
    val purityLabel: String = "22K (91.6%)",
    val purityPercent: String = "91.6",
    val makingPerGram: String = "0",
    val stoneValue: String = "0",
    val imageUri: String = ""
) {
    val grossW get() = grossWeight.toDoubleOrNull() ?: 0.0
    val lessW get() = lessWeight.toDoubleOrNull() ?: 0.0
    val netW get() = GoldCalc.netWeight(grossW, lessW)
    val purity get() = purityPercent.toDoubleOrNull() ?: 91.6
    val fineGold get() = GoldCalc.fineGold(netW, purity)
    val makingPg get() = makingPerGram.toDoubleOrNull() ?: 0.0
    val gramsWithMaking get() = GoldCalc.gramsWithMaking(netW, purity, makingPg)
    
    val stoneVal get() = stoneValue.toDoubleOrNull() ?: 0.0
    
    fun eqGrams(rate24K: Double) = GoldCalc.equivalentGramsWithStone(netW, purity, makingPg, stoneVal, rate24K)
    fun amount(rate24K: Double) = GoldCalc.itemAmount(netW, purity, rate24K, makingPg, stoneVal)
}

data class LessWeightRow(
    val count: String = "",
    val grams: String = ""
) {
    val total get() = (count.toDoubleOrNull() ?: 0.0) * (grams.toDoubleOrNull() ?: 0.0)
}

data class GoldPaymentDraft(
    val id: Int = System.currentTimeMillis().toInt(),
    val grams: String = "",
    val karat: Int = 22
) {
    val gramsValue get() = grams.toDoubleOrNull() ?: 0.0
    val pureGold get() = GoldCalc.pureGoldFromKarat(gramsValue, karat)
}

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val customerDao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val billItemDao: BillItemDao,
    private val invoicePaymentDao: InvoicePaymentDao,
    private val meltingDao: MeltingDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    val settings = settingsRepo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, com.goldsmith.billing.data.repository.AppSettings())

    private val _customerQuery = MutableStateFlow("")
    val customerQuery = _customerQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val suggestedCustomers = _customerQuery
        .debounce(200)
        .flatMapLatest { q -> if (q.length >= 1) customerDao.searchCustomers(q) else customerDao.getRecentCustomers() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())

    var selectedCustomer by mutableStateOf<Customer?>(null)
    var items by mutableStateOf(listOf(BillItemDraft()))
    var currentStep by mutableIntStateOf(0)

    // Payment
    var cashPaid by mutableStateOf("0")
    var goldPayments by mutableStateOf(listOf(GoldPaymentDraft()))
    var usePreviousBalance by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())

    fun initialize(customerId: Long?) {
        if (customerId != null && selectedCustomer == null) {
            viewModelScope.launch {
                val customer = customerDao.getCustomerById(customerId)
                if (customer != null) {
                    selectCustomer(customer)
                    currentStep = 1 // Skip to items
                }
            }
        }
    }

    fun setCustomerQuery(q: String) { _customerQuery.value = q }
    fun selectCustomer(c: Customer) { selectedCustomer = c; _customerQuery.value = c.name }

    fun addItem() { items = items + BillItemDraft() }
    fun removeItem(idx: Int) { if (items.size > 1) items = items.toMutableList().also { it.removeAt(idx) } }
    fun updateItem(idx: Int, draft: BillItemDraft) { items = items.toMutableList().also { it[idx] = draft } }
    fun addGoldPayment() { goldPayments = goldPayments + GoldPaymentDraft() }
    fun removeGoldPayment(idx: Int) { if (goldPayments.size > 1) goldPayments = goldPayments.toMutableList().also { it.removeAt(idx) } }
    fun updateGoldPayment(idx: Int, draft: GoldPaymentDraft) { goldPayments = goldPayments.toMutableList().also { it[idx] = draft } }

    fun totalAmount(rate: Double) = items.sumOf { it.amount(rate) }
    fun totalWeight() = items.sumOf { it.netW }
    fun totalFineGold(rate: Double) = GoldCalc.subtotalEquivalentGrams(items.map { it.eqGrams(rate) })
    fun totalGramsWithMaking(rate: Double) = totalFineGold(rate)
    fun totalGoldPaidValue(rate: Double) = goldPayments.sumOf { GoldCalc.goldPaymentValue(it.gramsValue, it.karat, rate) }
    fun totalGoldPaidPure() = goldPayments.sumOf { it.pureGold }

    private fun validateBeforeSave(): Boolean {
        val errors = linkedMapOf<String, String>()
        if (selectedCustomer == null) errors["customer"] = "Select customer"
        items.forEachIndexed { idx, item ->
            if (!GoldCalc.isValidDecimal(item.grossWeight, allowZero = false)) errors["gross_$idx"] = "Enter valid number"
            if (!GoldCalc.isValidDecimal(item.lessWeight)) errors["less_$idx"] = "Enter valid number"
            if (!GoldCalc.isValidDecimal(item.purityPercent, allowZero = false)) errors["purity_$idx"] = "Enter valid number"
            if (!GoldCalc.isValidDecimal(item.makingPerGram)) errors["making_$idx"] = "Enter valid number"
        }
        if (!GoldCalc.isValidDecimal(cashPaid)) errors["cash"] = "Enter valid number"
        goldPayments.forEachIndexed { idx, payment ->
            if (payment.grams.isNotBlank() && !GoldCalc.isValidDecimal(payment.grams, allowZero = false)) {
                errors["gold_$idx"] = "Enter valid number"
            }
        }
        fieldErrors = errors
        return errors.isEmpty()
    }

    fun saveInvoice(onSaved: (Long) -> Unit) = viewModelScope.launch {
        if (!validateBeforeSave()) return@launch
        val customer = selectedCustomer ?: return@launch
        val s = settings.value
        val rate = s.goldRate24K
        val gst = s.gstPercent
        
        val subtotal = items.sumOf { it.amount(rate) }
        val gstAmount = subtotal * gst / 100.0
        val total = subtotal + gstAmount
        
        val cash = cashPaid.toDoubleOrNull() ?: 0.0
        val payableGoldPayments = goldPayments.filter { it.gramsValue > 0.0 }
        val goldGrams = payableGoldPayments.sumOf { it.gramsValue }
        val goldPaidVal = payableGoldPayments.sumOf { GoldCalc.goldPaymentValue(it.gramsValue, it.karat, rate) }
        val prevBal = if (usePreviousBalance) customer.cashBalance else 0.0
        val remaining = GoldCalc.roundMoney(total - cash - goldPaidVal - prevBal)

        val invoiceNum = settingsRepo.nextInvoiceNumber(s.userPrefix)

        val invoice = Invoice(
            userPrefix = s.userPrefix,
            invoiceNumber = invoiceNum,
            customerId = customer.id,
            customerShopName = customer.companyName.ifEmpty { customer.name },
            customerOwnerName = customer.name,
            customerAddress = customer.address,
            customerPhone = customer.phone,
            date = Date(),
            totalWeightGrams = totalWeight(),
            totalFineGoldGrams = totalFineGold(rate),
            total916Grams = GoldCalc.equivalent916(totalFineGold(rate)),
            subtotal = subtotal,
            gstPercent = gst,
            gstAmount = gstAmount,
            totalAmount = total,
            cashPaid = cash,
            goldPaidGrams = goldGrams,
            goldPaidKarat = payableGoldPayments.firstOrNull()?.karat ?: 24,
            previousBalanceAdjusted = prevBal,
            remainingBalance = remaining,
            paymentStatus = when {
                remaining <= 0 -> PaymentStatus.PAID
                cash + goldPaidVal > 0 -> PaymentStatus.PARTIAL
                else -> PaymentStatus.PENDING
            },
            goldRate24K = rate
        )
        val invoiceId = invoiceDao.insertInvoice(invoice)

        // Save bill items
        val billItems = items.mapIndexed { _, draft ->
            BillItem(
                invoiceId = invoiceId,
                description = draft.description,
                grossWeightGrams = draft.grossW,
                lessWeightGrams = draft.lessW,
                purityPercent = draft.purity,
                karatLabel = draft.purityLabel,
                makingChargePerGram = draft.makingPg,
                makingChargePercent = draft.makingPg,
                stoneValue = draft.stoneVal,
                imageUri = draft.imageUri,
                fineGoldGrams = draft.eqGrams(rate),
                gramsWithMaking = draft.eqGrams(rate),
                itemAmount = draft.amount(rate)
            )
        }
        billItemDao.insertBillItems(billItems)

        if (cash > 0.0) {
            invoicePaymentDao.insertPayment(InvoicePayment(
                invoiceId = invoiceId,
                amount = cash,
                paymentMode = "CASH",
                notes = "Initial invoice payment"
            ))
        }
        payableGoldPayments.forEach { payment ->
            val paymentId = invoicePaymentDao.insertPayment(InvoicePayment(
                invoiceId = invoiceId,
                goldGrams = payment.gramsValue,
                goldKarat = payment.karat,
                paymentMode = "GOLD",
                notes = "Initial invoice gold payment"
            ))
            val pureEquivalent = payment.pureGold
            meltingDao.insertMeltingRecord(MeltingRecord(
                customerId = customer.id,
                rawWeightGrams = payment.gramsValue,
                finalPureWeightGrams = pureEquivalent,
                purityPercent = (payment.karat / 24.0) * 100.0,
                expectedPureWeightGrams = pureEquivalent,
                expectedPurityPercent = (payment.karat / 24.0) * 100.0,
                status = MeltingStatus.PENDING.name,
                notes = "Auto-generated from Invoice #$invoiceNum",
                linkedInvoiceId = invoiceId,
                linkedPaymentId = paymentId
            ))
        }

        // Update customer balance
        customerDao.updateCustomer(customer.copy(cashBalance = remaining, goldBalanceGrams = customer.goldBalanceGrams - totalGoldPaidPure(), updatedAt = Date()))
        onSaved(invoiceId)
    }
}

// ─── Billing Screen ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    preselectedCustomerId: Long?,
    onBack: () -> Unit,
    onInvoiceCreated: (Long) -> Unit,
    viewModel: BillingViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(preselectedCustomerId) {
        viewModel.initialize(preselectedCustomerId)
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Diamond, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
                        Text("ABU STAR DIAMONDS", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 14.sp, letterSpacing = 3.sp)
                    }
                },
                navigationIcon = { 
                    IconButton(onClick = { if (viewModel.currentStep == 0) onBack() else viewModel.currentStep-- }) { 
                        Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        },
        bottomBar = {
            if (viewModel.currentStep >= 1) {
                BillingBottomBar(
                    settings = settings,
                    viewModel = viewModel,
                    step = viewModel.currentStep,
                    onNext = { if (viewModel.currentStep < 3) viewModel.currentStep++ else viewModel.saveInvoice(onInvoiceCreated) },
                    onBack = { if (viewModel.currentStep > 0) viewModel.currentStep-- }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Step indicator
            StepIndicator(viewModel.currentStep) { step ->
                if (step < viewModel.currentStep) viewModel.currentStep = step
            }

            when (viewModel.currentStep) {
                0 -> CustomerStep(viewModel) { viewModel.currentStep = 1 }
                1 -> ItemsStep(viewModel, settings.goldRate24K)
                2 -> PaymentStep(viewModel, settings)
                3 -> ReviewStep(viewModel, settings)
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, onStepClick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        listOf("Customer", "Inventory", "Payment", "Review").forEachIndexed { idx, label ->
            val active = idx == current
            val done = idx < current

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = idx < current) { onStepClick(idx) }
                        .background(if (active || done) AuraColors.PrimaryContainer else AuraColors.GlassWhite5)
                        .border(1.dp, if (active || done) AuraColors.PrimaryContainer else AuraColors.GlassWhite20, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Default.Check, null, tint = AuraColors.OnPrimary, modifier = Modifier.size(18.dp))
                    else Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = if (active) AuraColors.OnPrimary else AuraColors.OnSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (active) AuraColors.PrimaryContainer else AuraColors.OnSurface.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
            }

            if (idx < 3) {
                Box(Modifier.weight(1f).height(1.dp).background(AuraColors.GlassWhite10).padding(bottom = 20.dp))
            }
        }
    }
}

// ─── Step 1: Customer ─────────────────────────────────────────────────────────
@Composable
private fun CustomerStep(viewModel: BillingViewModel, onProceed: () -> Unit) {
    val query by viewModel.customerQuery.collectAsState()
    val suggestions by viewModel.suggestedCustomers.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PersonSearch, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(20.dp))
                        Text("Identify Client", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setCustomerQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name, phone or email...", color = AuraColors.OnSurface.copy(alpha = 0.3f)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = AuraColors.OnSurface.copy(alpha = 0.4f)) },
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
                    Spacer(Modifier.height(12.dp))

                    // Suggestions
                    if (suggestions.isNotEmpty()) {
                        Column(
                            Modifier
                                .background(AuraColors.GlassWhite5, RoundedCornerShape(12.dp))
                                .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(12.dp))
                        ) {
                            suggestions.take(5).forEachIndexed { idx, c ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectCustomer(c); onProceed() }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(
                                            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(AuraColors.SurfaceContainerHighest)
                                                .border(1.dp, AuraColors.GlassBorder, androidx.compose.foundation.shape.CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant, fontWeight = FontWeight.Bold)
                                        }
                                        Column {
                                            Text(c.name, style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                                            Text(c.phone, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface.copy(alpha = 0.4f))
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = AuraColors.PrimaryContainer, modifier = Modifier.size(18.dp))
                                }
                                if (idx < suggestions.size - 1) HorizontalDivider(color = AuraColors.GlassWhite5)
                            }
                        }
                    }
                }
            }
        }

        item {
            // Selected customer confirmation
            viewModel.selectedCustomer?.let { c ->
                GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = AuraColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Selected: ${c.name}", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(c.phone, color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        GoldButton("Proceed →", onClick = onProceed)
                    }
                }
            }
        }
    }
}

// ─── Step 2: Bill Items ───────────────────────────────────────────────────────
@Composable
private fun ItemsStep(viewModel: BillingViewModel, rate24K: Double) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("TRANSACTION ENTRY", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp, fontSize = 10.sp)
                    Text("Bill Items", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                }
                Text("#INV-XXXX-2024", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 11.sp)
            }
        }

        itemsIndexed(viewModel.items, key = { _, item -> item.id }) { idx, draft ->
            BillItemCard(
                draft = draft,
                rate24K = rate24K,
                errors = viewModel.fieldErrors,
                index = idx,
                onUpdate = { viewModel.updateItem(idx, it) },
                onRemove = { viewModel.removeItem(idx) },
                canRemove = viewModel.items.size > 1
            )
        }

        item {
            OutlinedButton(
                onClick = viewModel::addItem,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraColors.PrimaryContainer),
                border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("ADD ITEM TO BILL", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun BillItemCard(
    draft: BillItemDraft,
    rate24K: Double,
    errors: Map<String, String>,
    index: Int,
    onUpdate: (BillItemDraft) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    val puritiesMap = mapOf(
        "24K (100%)" to 100.0, 
        "22K (91.6%)" to 91.6, 
        "20K (85%)" to 85.0,
        "18K (75%)" to 75.0, 
        "14K (58.5%)" to 58.5
    )
    var expandedDropdown by remember { mutableStateOf(false) }
    var showLessDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("DESCRIPTION", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 1.sp, fontSize = 9.sp)
                if (canRemove) IconButton(onClick = { confirmRemove = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = AuraColors.Error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
            OutlinedTextField(
                value = draft.description,
                onValueChange = { onUpdate(draft.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Antique Gold Filigree Necklace", color = AuraColors.OnSurface.copy(alpha = 0.2f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraColors.PrimaryContainer,
                    unfocusedBorderColor = AuraColors.GlassWhite10,
                    focusedTextColor = AuraColors.OnSurface,
                    unfocusedTextColor = AuraColors.OnSurface,
                    focusedContainerColor = AuraColors.GlassWhite5,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            // Weights row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BillField("GROSS WT (G)", draft.grossWeight, { onUpdate(draft.copy(grossWeight = it)) }, Modifier.weight(1f), errors["gross_$index"])
                Column(Modifier.weight(1f).clickable { showLessDialog = true }) {
                    BillField("LESS WT (G)", draft.lessWeight, { onUpdate(draft.copy(lessWeight = it)) }, Modifier.fillMaxWidth(), errors["less_$index"])
                    TextButton(onClick = { showLessDialog = true }, modifier = Modifier.align(Alignment.End)) {
                        Text("OPEN LESS TABLE", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer, fontSize = 9.sp)
                    }
                }
            }

            // Net weight display
            Box(
                Modifier.fillMaxWidth()
                    .background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp))
                    .border(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("NET WT (G)", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                    Text(String.format("%.3f", draft.netW), style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }

            // Purity + Making
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("PURITY %", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    ExposedDropdownMenuBox(expanded = expandedDropdown, onExpandedChange = { expandedDropdown = it }) {
                        OutlinedTextField(
                            value = draft.purityLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedDropdown) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AuraColors.PrimaryContainer,
                                unfocusedBorderColor = AuraColors.GlassWhite10,
                                focusedTextColor = AuraColors.OnSurface,
                                unfocusedTextColor = AuraColors.OnSurface,
                                focusedContainerColor = AuraColors.GlassWhite5,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.background(AuraColors.SurfaceContainerHigh)) {
                            puritiesMap.forEach { (label, pct) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = AuraColors.OnSurface) },
                                    onClick = { onUpdate(draft.copy(purityLabel = label, purityPercent = pct.toString())); expandedDropdown = false }
                                )
                            }
                        }
                    }
                }
                BillField("MAKING %", draft.makingPerGram, { onUpdate(draft.copy(makingPerGram = it)) }, Modifier.weight(1f), errors["making_$index"])
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BillField("STONE VAL", draft.stoneValue, { onUpdate(draft.copy(stoneValue = it)) }, Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    Text("EQ. GRAMS", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Color.Transparent, RoundedCornerShape(8.dp))
                            .border(1.dp, AuraColors.GlassWhite10, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(String.format("%.3f", draft.eqGrams(rate24K)), color = AuraColors.OnSurface)
                    }
                }
            }

            // Item total
            Box(
                Modifier.fillMaxWidth()
                    .background(AuraColors.GlassWhite5, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ITEM TOTAL", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
                    Text("₹ ${String.format("%,.2f", draft.amount(rate24K))}", style = MaterialTheme.typography.bodyLarge, color = AuraColors.OnSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showLessDialog) {
        LessWeightDialog(
            currentLess = draft.lessWeight,
            onDismiss = { showLessDialog = false },
            onSave = { total ->
                onUpdate(draft.copy(lessWeight = String.format("%.3f", total)))
                showLessDialog = false
            }
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = AuraColors.SurfaceContainerHigh,
            title = { Text("Remove item?", color = AuraColors.OnSurface) },
            text = { Text("Are you sure you want to delete this bill item?", color = AuraColors.OnSurfaceVariant) },
            confirmButton = { GoldButton("Delete", onClick = { confirmRemove = false; onRemove() }) },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
        )
    }
}

@Composable
private fun BillField(
    label: String,
    value: String,
    onUpdate: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onUpdate,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraColors.PrimaryContainer,
                unfocusedBorderColor = AuraColors.GlassWhite10,
                focusedTextColor = AuraColors.OnSurface,
                unfocusedTextColor = AuraColors.OnSurface,
                focusedContainerColor = AuraColors.GlassWhite5,
                unfocusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        if (error != null) {
            Text(error, color = AuraColors.Error, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LessWeightDialog(currentLess: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var rows by remember { mutableStateOf(listOf(LessWeightRow())) }
    val total = rows.sumOf { it.total }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.SurfaceContainerHigh,
        title = { Text("Less Weight Table", color = AuraColors.OnSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Count", Modifier.weight(1f), color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("Gram", Modifier.weight(1f), color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("Total", Modifier.weight(1f), color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                rows.forEachIndexed { idx, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(row.count, {
                            rows = rows.toMutableList().also { list ->
                                list[idx] = row.copy(count = it)
                                if (idx == list.lastIndex && (it.isNotBlank() || row.grams.isNotBlank())) list.add(LessWeightRow())
                            }
                        }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(row.grams, {
                            rows = rows.toMutableList().also { list ->
                                list[idx] = row.copy(grams = it)
                                if (idx == list.lastIndex && (it.isNotBlank() || row.count.isNotBlank())) list.add(LessWeightRow())
                            }
                        }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(String.format("%.3f", row.total), color = AuraColors.OnSurface)
                            if (rows.size > 1 && idx < rows.lastIndex) {
                                IconButton(onClick = { rows = rows.toMutableList().also { it.removeAt(idx) } }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = AuraColors.Error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = AuraColors.GlassWhite10)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Existing", color = AuraColors.OnSurfaceVariant)
                    Text("${currentLess.ifBlank { "0" }}g", color = AuraColors.OnSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", color = AuraColors.OnSurface, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.3f", total)}g", color = AuraColors.PrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { GoldButton("Apply Less", onClick = { onSave(total) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AuraColors.OnSurfaceVariant) } }
    )
}

// ─── Step 3: Payment ──────────────────────────────────────────────────────────
@Composable
private fun PaymentStep(viewModel: BillingViewModel, settings: com.goldsmith.billing.data.repository.AppSettings) {
    val subtotal = viewModel.totalAmount(settings.goldRate24K)
    val gstAmount = subtotal * settings.gstPercent / 100.0
    val total = subtotal + gstAmount
    val cashP = viewModel.cashPaid.toDoubleOrNull() ?: 0.0
    val goldPaidValue = viewModel.totalGoldPaidValue(settings.goldRate24K)
    val previousBalance = if (viewModel.usePreviousBalance) viewModel.selectedCustomer?.cashBalance ?: 0.0 else 0.0
    val remaining = total - cashP - goldPaidValue - previousBalance
    val remainingGold = if (settings.goldRate24K > 0) (remaining.coerceAtLeast(0.0) / settings.goldRate24K) else 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bill Summary", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    SummaryRow("Total Weight", "${String.format("%.3f", viewModel.totalWeight())}g")
                    SummaryRow("Pure Gold (24K)", "${String.format("%.3f", viewModel.totalFineGold(settings.goldRate24K))}g")
                    SummaryRow("91.6 Gold", "${String.format("%.3f", GoldCalc.equivalent916(viewModel.totalFineGold(settings.goldRate24K)))}g")
                    HorizontalDivider(color = AuraColors.GlassWhite10)
                    SummaryRow("Sub Total Grams", "${String.format("%.3f", viewModel.totalFineGold(settings.goldRate24K))}g")
                    SummaryRow("Amount Subtotal", "₹${String.format("%,.2f", subtotal)}")
                    SummaryRow("GST (${settings.gstPercent}%)", "₹${String.format("%,.2f", gstAmount)}")
                    HorizontalDivider(color = AuraColors.PrimaryContainer.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL PAYABLE", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurface, letterSpacing = 1.sp)
                        Text("₹${String.format("%,.2f", total)}", style = MaterialTheme.typography.titleLarge, color = AuraColors.PrimaryContainer, fontSize = 22.sp)
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Payment", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    BillField("CASH PAID (₹)", viewModel.cashPaid, { viewModel.cashPaid = it }, Modifier.fillMaxWidth(), viewModel.fieldErrors["cash"])
                    Text("Gold Payment", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    viewModel.goldPayments.forEachIndexed { idx, payment ->
                        GoldPaymentRow(
                            payment = payment,
                            error = viewModel.fieldErrors["gold_$idx"],
                            canRemove = viewModel.goldPayments.size > 1,
                            onUpdate = { viewModel.updateGoldPayment(idx, it) },
                            onRemove = { viewModel.removeGoldPayment(idx) }
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::addGoldPayment,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, AuraColors.PrimaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ADD GOLD SPLIT", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.usePreviousBalance,
                            onCheckedChange = { viewModel.usePreviousBalance = it },
                            colors = CheckboxDefaults.colors(checkedColor = AuraColors.PrimaryContainer)
                        )
                        Text("Include previous balance (${viewModel.selectedCustomer?.cashBalance?.let { "₹${String.format("%,.2f", it)}" } ?: "N/A"})", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = AuraColors.GlassWhite10)
                    SummaryRow(
                        "Remaining Balance",
                        "₹${String.format("%,.2f", remaining.coerceAtLeast(0.0))} OR ${String.format("%.3f", remainingGold)}g pure gold",
                        valueColor = if (remaining > 0) AuraColors.Error else AuraColors.Primary
                    )
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun GoldPaymentRow(
    payment: GoldPaymentDraft,
    error: String?,
    canRemove: Boolean,
    onUpdate: (GoldPaymentDraft) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        BillField("GRAMS", payment.grams, { onUpdate(payment.copy(grams = it)) }, Modifier.weight(1f), error)
        Column(Modifier.weight(1f)) {
            Text("CARAT", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = "${payment.karat}K",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuraColors.PrimaryContainer,
                        unfocusedBorderColor = AuraColors.GlassWhite10,
                        focusedTextColor = AuraColors.OnSurface,
                        unfocusedTextColor = AuraColors.OnSurface,
                        focusedContainerColor = AuraColors.GlassWhite5,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(24, 22, 20, 18).forEach { karat ->
                        DropdownMenuItem(
                            text = { Text("${karat}K") },
                            onClick = {
                                onUpdate(payment.copy(karat = karat))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 18.dp)) {
                Icon(Icons.Default.Delete, null, tint = AuraColors.Error.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ReviewStep(viewModel: BillingViewModel, settings: com.goldsmith.billing.data.repository.AppSettings) {
    val subtotal = viewModel.totalAmount(settings.goldRate24K)
    val gstAmount = subtotal * settings.gstPercent / 100.0
    val total = subtotal + gstAmount
    val cashP = viewModel.cashPaid.toDoubleOrNull() ?: 0.0
    val goldPaidPure = viewModel.totalGoldPaidPure()
    val goldPaidValue = viewModel.totalGoldPaidValue(settings.goldRate24K)
    val previousBalance = if (viewModel.usePreviousBalance) viewModel.selectedCustomer?.cashBalance ?: 0.0 else 0.0
    val remaining = GoldCalc.roundMoney(total - cashP - goldPaidValue - previousBalance)
    val remainingGold = if (settings.goldRate24K > 0) GoldCalc.roundGrams(remaining.coerceAtLeast(0.0) / settings.goldRate24K) else 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Review & Seal", style = MaterialTheme.typography.headlineMedium, color = AuraColors.OnSurface)
                    Text("Confirm customer, item Eq.g, payment and ledger impact before saving.", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = AuraColors.GlassWhite10)
                    SummaryRow("Customer", viewModel.selectedCustomer?.let { it.companyName.ifEmpty { it.name } } ?: "Not selected")
                    SummaryRow("Items", "${viewModel.items.size}")
                    SummaryRow("Sub Total Grams", "${String.format("%.3f", viewModel.totalFineGold(settings.goldRate24K))}g")
                    SummaryRow("Amount Subtotal", "₹${String.format("%,.2f", subtotal)}")
                    SummaryRow("GST", "₹${String.format("%,.2f", gstAmount)}")
                    SummaryRow("Total Payable", "₹${String.format("%,.2f", total)}", AuraColors.PrimaryContainer)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Settlement Impact", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                    SummaryRow("Cash Paid", "₹${String.format("%,.2f", cashP)}", AuraColors.Primary)
                    SummaryRow("Gold Paid Pure", "${String.format("%.3f", goldPaidPure)}g", AuraColors.Primary)
                    SummaryRow("Previous Balance Adjusted", "₹${String.format("%,.2f", previousBalance)}")
                    HorizontalDivider(color = AuraColors.GlassWhite10)
                    SummaryRow("Balance Due", "₹${String.format("%,.2f", remaining.coerceAtLeast(0.0))}", if (remaining > 0) AuraColors.Error else AuraColors.Primary)
                    SummaryRow("Balance Gold", "${String.format("%.3f", remainingGold)}g pure", if (remaining > 0) AuraColors.Error else AuraColors.Primary)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Melting Queue Preview", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                    val pendingGold = viewModel.goldPayments.filter { it.gramsValue > 0.0 }
                    if (pendingGold.isEmpty()) {
                        Text("No old gold payment in this bill.", color = AuraColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        pendingGold.forEach { payment ->
                            SummaryRow("${payment.karat}K raw gold", "${String.format("%.3f", payment.gramsValue)}g → ${String.format("%.3f", payment.pureGold)}g pure")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = AuraColors.OnSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 0.5.sp)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

// ─── Billing Bottom Bar ────────────────────────────────────────────────────────
@Composable
private fun BillingBottomBar(
    settings: com.goldsmith.billing.data.repository.AppSettings,
    viewModel: BillingViewModel,
    step: Int,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val total = viewModel.totalAmount(settings.goldRate24K)
    val gst = total * settings.gstPercent / 100.0

    Surface(
        color = AuraColors.SurfaceContainerLowest.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = AuraColors.GlassWhite5)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text("${String.format("%.2f", viewModel.totalWeight())}g", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
                        }
                        Column {
                            Text("SUB TOTAL", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text("₹${String.format("%,.0f", total)}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
                        }
                        Column {
                            Text("GST (${settings.gstPercent}%)", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text("₹${String.format("%,.0f", gst)}", style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurface)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TOTAL PAYABLE", style = MaterialTheme.typography.labelSmall, color = AuraColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 8.sp, letterSpacing = 1.sp)
                        Text("₹${String.format("%,.2f", total + gst)}", style = MaterialTheme.typography.titleLarge, color = AuraColors.PrimaryContainer, fontSize = 18.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(
                        text = if (step == 3) "Seal Invoice" else if (step == 2) "Review" else "Next",
                        onClick = onNext,
                        modifier = Modifier.height(48.dp).defaultMinSize(minWidth = 150.dp)
                    )
                }
            }
        }
    }
}
