package com.goldsmith.billing.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

// ─── Type Converters ───────────────────────────────────────────────────────────
class DateConverter {
    @TypeConverter fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(date: Date?): Long? = date?.time
}

class ListConverter {
    @TypeConverter fun fromString(value: String?): List<String> =
        value?.split("||")?.filter { it.isNotEmpty() } ?: emptyList()
    @TypeConverter fun listToString(list: List<String>?): String =
        list?.joinToString("||") ?: ""
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
    val dob: Date? = null,
    val anniversary: Date? = null,
    // Balance: positive = customer owes gold (grams), negative = we owe gold
    val goldBalanceGrams: Double = 0.0,
    // Cash balance: positive = customer owes cash, negative = we owe cash
    val cashBalance: Double = 0.0,
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
    val userPrefix: String = "", // F, Y, B
    val invoiceNumber: String,  // e.g. F-8829-2024
    val customerId: Long,
    val date: Date = Date(),
    // Totals
    val totalWeightGrams: Double = 0.0,
    val totalFineGoldGrams: Double = 0.0,
    val total916Grams: Double = 0.0, // 91.6 conversion
    val subtotal: Double = 0.0,
    val gstPercent: Double = 3.0,
    val gstAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    // Payment
    val cashPaid: Double = 0.0,
    val goldPaidGrams: Double = 0.0,
    val goldPaidKarat: Int = 24,
    val previousBalanceAdjusted: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    // Gold rate at time of invoice
    val goldRate24K: Double = 0.0,
    val notes: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

enum class PaymentStatus { PENDING, PARTIAL, PAID }

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
    val purityPercent: Double,   // e.g. 91.6 for 22K
    val karatLabel: String,      // "24K", "22K", "20K", "18K", "Custom"
    val makingChargePerGram: Double = 0.0,
    val makingChargePercent: Double = 0.0,
    val isPercentMaking: Boolean = false,
    val stoneValue: Double = 0.0,
    val imageUri: String = "",
    // Computed (stored for PDF)
    val netWeightGrams: Double = grossWeightGrams - lessWeightGrams,
    val fineGoldGrams: Double = netWeightGrams * (purityPercent / 100.0),
    val gramsWithMaking: Double = 0.0, // Formula: NetW * ((Purity + Making) / 100)
    val itemAmount: Double = 0.0,
    val updatedAt: Date = Date()
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
    val isManualOverride: Boolean = false,
    val updatedAt: Date = Date()
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
    val rawWeightGrams: Double,
    val finalPureWeightGrams: Double,
    val purityPercent: Double,
    val imageUris: List<String> = emptyList(),
    val notes: String = "",
    val linkedInvoiceId: Long? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// ─── Invoice Payment ──────────────────────────────────────────────────────────
@Entity(
    tableName = "invoice_payments",
    foreignKeys = [ForeignKey(
        entity = Invoice::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId")]
)
data class InvoicePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val amount: Double = 0.0,
    val goldGrams: Double = 0.0,
    val goldKarat: Int = 24,
    val paymentMode: String = "CASH", // CASH, GOLD
    val date: Date = Date(),
    val notes: String = ""
)

// ─── Company Profile ──────────────────────────────────────────────────────────
@Entity(tableName = "company_profile")
data class CompanyProfile(
    @PrimaryKey val id: Int = 1,   // singleton
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
