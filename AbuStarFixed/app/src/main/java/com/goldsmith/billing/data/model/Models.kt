package com.goldsmith.billing.data.model

import androidx.room.*
import java.util.Date

// ─── Type Converters ──────────────────────────────────────────────────────────
class DateConverter {
    @TypeConverter fun fromTimestamp(v: Long?): Date? = v?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(d: Date?): Long? = d?.time
}

class ListConverter {
    @TypeConverter fun fromString(v: String?): List<String> =
        v?.split("||")?.filter { it.isNotEmpty() } ?: emptyList()
    @TypeConverter fun listToString(l: List<String>?): String = l?.joinToString("||") ?: ""
}

// ─── Customer ─────────────────────────────────────────────────────────────────
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val companyName: String = "",
    val phone: String,
    val address: String = "",
    val gstNumber: String = "",
    val email: String = "",
    val photoUri: String = "",
    val goldBalanceGrams: Double = 0.0,
    val cashBalance: Double = 0.0,
    val dateOfBirth: Long? = null,
    val anniversary: Long? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// ─── Invoice ──────────────────────────────────────────────────────────────────
@Entity(
    tableName = "invoices",
    foreignKeys = [ForeignKey(
        entity = Customer::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("customerId")]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val customerId: Long,
    val date: Date = Date(),
    // ── Gram totals (the PRIMARY values) ──
    val totalNetWeightGrams: Double = 0.0,    // ΣC
    val totalPureGoldGrams: Double = 0.0,     // Σ eqGrams (C × D/100) — "Pure Gold 24K"
    val totalFineGoldGrams: Double = 0.0,     // Σ fineGold (C × (D+E)/100) — billing base
    val totalEq22KGrams: Double = 0.0,        // totalPureGold / 0.916
    val totalEq18KGrams: Double = 0.0,        // totalPureGold / 0.75
    // ── Cash summary ──
    val subtotalCash: Double = 0.0,           // Σ item cashValue (before GST)
    val gstPercent: Double = 3.0,
    val gstAmount: Double = 0.0,
    val totalAmount: Double = 0.0,            // subtotal + GST
    // ── Payment ──
    val cashPaid: Double = 0.0,
    val goldPayments: String = "",            // JSON: [{karat,grams,cashValue}]
    val totalGoldPaidCash: Double = 0.0,      // total gold payments in cash equiv
    val previousBalanceAdjusted: Double = 0.0,
    val remainingCash: Double = 0.0,
    val remainingGoldGrams: Double = 0.0,     // remainingCash / rate24K
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    // ── Meta ──
    val goldRate24K: Double = 0.0,
    val devicePrefix: String = "A",
    val notes: String = "",
    val attachmentUris: String = "",           // || separated
    val createdAt: Date = Date(),
    // FIX: Denormalised customer snapshot so history ALWAYS shows customer details
    val customerNameSnapshot: String = "",
    val customerPhoneSnapshot: String = "",
    val customerAddressSnapshot: String = "",
    val customerGstSnapshot: String = ""
)

enum class PaymentStatus { PENDING, PARTIAL, PAID }

// ─── Gold Payment Entry (for split gold payments) ─────────────────────────────
data class GoldPaymentEntry(
    val karat: Int,           // 24, 22, 20, 18
    val grams: Double,
    val purityPct: Double,
    val cashEquivalent: Double
)

// ─── Bill Item ────────────────────────────────────────────────────────────────
@Entity(
    tableName = "bill_items",
    foreignKeys = [ForeignKey(
        entity = Invoice::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId")]
)
data class BillItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val description: String,
    val grossWeightGrams: Double,
    val lessWeightGrams: Double = 0.0,
    val purityPercent: Double,
    val karatLabel: String,
    val makingChargePercent: Double = 0.0,
    val makingChargePerGram: Double = 0.0,
    val isPercentMaking: Boolean = true,
    val stoneValue: Double = 0.0,
    val imageUris: String = "",               // || separated attachment URIs
    // Computed & stored
    val netWeightGrams: Double = 0.0,         // C = A - B
    val eqGrams: Double = 0.0,               // C × D/100 (pure gold equiv)
    val fineGoldGrams: Double = 0.0,          // C × (D+E)/100 (billing)
    val itemCashValue: Double = 0.0           // no GST — GST only at invoice level
)

// ─── Gold Rate ────────────────────────────────────────────────────────────────
@Entity(tableName = "gold_rates")
data class GoldRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rate24K: Double,
    val rate22K: Double = rate24K * 0.916,
    val rate20K: Double = rate24K * 0.85,
    val rate18K: Double = rate24K * 0.75,
    val recordedAt: Date = Date(),
    val isManualOverride: Boolean = false
)

// ─── Melting Record ───────────────────────────────────────────────────────────
@Entity(
    tableName = "melting_records",
    foreignKeys = [ForeignKey(
        entity = Customer::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("customerId")]
)
@TypeConverters(ListConverter::class)
data class MeltingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerNameSnapshot: String = "",
    // Input
    val rawWeightGrams: Double,
    val inputKarat: Int = 22,               // karat of metal received
    val inputPurityPct: Double = 91.6,
    // After melting/testing
    val finalPureWeightGrams: Double = 0.0,
    val testedPurityPct: Double = 0.0,
    // Credit calculation
    val goldCreditGrams: Double = 0.0,      // pure gold credit = finalPure
    val adjustmentGrams: Double = 0.0,      // +/- to give or take
    val linkedInvoiceId: Long? = null,
    val imageUris: List<String> = emptyList(),
    val notes: String = "",
    val status: MeltingStatus = MeltingStatus.RECEIVED,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

enum class MeltingStatus { RECEIVED, MELTED, TESTED, SETTLED }

// ─── Company Profile ──────────────────────────────────────────────────────────
@Entity(tableName = "company_profile")
data class CompanyProfile(
    @PrimaryKey val id: Int = 1,
    val companyName: String = "",
    val ownerName: String = "",
    val mobileNumber: String = "",
    val address1: String = "",
    val address2: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val gstNumber: String = "",
    val logoUri: String = "",
    val updatedAt: Date = Date()
)
