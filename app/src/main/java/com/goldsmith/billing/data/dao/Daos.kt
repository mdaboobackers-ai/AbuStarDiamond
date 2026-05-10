package com.goldsmith.billing.data.dao

import androidx.room.*
import com.goldsmith.billing.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

// ─── Customer DAO ─────────────────────────────────────────────────────────────
@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR companyName LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 20")
    fun searchCustomers(query: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCount(): Flow<Int>

    @Query("SELECT SUM(goldBalanceGrams) FROM customers WHERE goldBalanceGrams > 0")
    fun getTotalGoldOwedByCustomers(): Flow<Double?>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC LIMIT 10")
    fun getRecentCustomers(): Flow<List<Customer>>
}

// ─── Invoice DAO ──────────────────────────────────────────────────────────────
@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY date DESC")
    fun getInvoicesByCustomer(customerId: Long): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: Long): Invoice?

    @Query("SELECT * FROM invoices WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getInvoicesByDateRange(startDate: Date, endDate: Date): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE paymentStatus = :status ORDER BY date DESC")
    fun getInvoicesByStatus(status: PaymentStatus): Flow<List<Invoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("SELECT COUNT(*) FROM invoices WHERE date >= :todayStart")
    fun getTodayInvoiceCount(todayStart: Date): Flow<Int>

    @Query("SELECT SUM(totalAmount) FROM invoices WHERE date >= :todayStart")
    fun getTodaySalesAmount(todayStart: Date): Flow<Double?>

    @Query("SELECT SUM(totalWeightGrams) FROM invoices WHERE date >= :todayStart")
    fun getTodasTotalWeight(todayStart: Date): Flow<Double?>

    @Query("SELECT SUM(remainingBalance) FROM invoices WHERE paymentStatus != 'PAID'")
    fun getTotalPendingAmount(): Flow<Double?>

    @Query("SELECT SUM(totalAmount) FROM invoices WHERE date >= :monthStart")
    fun getMonthlySalesAmount(monthStart: Date): Flow<Double?>

    @Query("SELECT MAX(id) FROM invoices")
    suspend fun getLastInvoiceId(): Long?

    // Search invoices
    @Query("""SELECT * FROM invoices WHERE invoiceNumber LIKE '%' || :query || '%' 
        ORDER BY date DESC""")
    fun searchInvoices(query: String): Flow<List<Invoice>>
}

// ─── Bill Item DAO ────────────────────────────────────────────────────────────
@Dao
interface BillItemDao {
    @Query("SELECT * FROM bill_items WHERE invoiceId = :invoiceId")
    fun getBillItemsForInvoice(invoiceId: Long): Flow<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE invoiceId = :invoiceId")
    suspend fun getBillItemsForInvoiceSync(invoiceId: Long): List<BillItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItem(item: BillItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItem>)

    @Update
    suspend fun updateBillItem(item: BillItem)

    @Delete
    suspend fun deleteBillItem(item: BillItem)

    @Query("DELETE FROM bill_items WHERE invoiceId = :invoiceId")
    suspend fun deleteAllItemsForInvoice(invoiceId: Long)
}

// ─── Gold Rate DAO ────────────────────────────────────────────────────────────
@Dao
interface GoldRateDao {
    @Query("SELECT * FROM gold_rates ORDER BY recordedAt DESC LIMIT 1")
    fun getLatestRate(): Flow<GoldRate?>

    @Query("SELECT * FROM gold_rates ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatestRateSync(): GoldRate?

    @Query("SELECT * FROM gold_rates ORDER BY recordedAt DESC LIMIT 30")
    fun getRateHistory(): Flow<List<GoldRate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: GoldRate): Long
}

// ─── Melting DAO ──────────────────────────────────────────────────────────────
@Dao
interface MeltingDao {
    @Query("SELECT * FROM melting_records ORDER BY createdAt DESC")
    fun getAllMeltingRecords(): Flow<List<MeltingRecord>>

    @Query("SELECT * FROM melting_records WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getMeltingRecordsByCustomer(customerId: Long): Flow<List<MeltingRecord>>

    @Query("SELECT * FROM melting_records WHERE linkedInvoiceId = :invoiceId ORDER BY createdAt DESC")
    fun getMeltingRecordsByInvoice(invoiceId: Long): Flow<List<MeltingRecord>>

    @Query("SELECT * FROM melting_records WHERE id = :id")
    suspend fun getMeltingRecordById(id: Long): MeltingRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeltingRecord(record: MeltingRecord): Long

    @Update
    suspend fun updateMeltingRecord(record: MeltingRecord)

    @Delete
    suspend fun deleteMeltingRecord(record: MeltingRecord)
}

// ─── Company Profile DAO ──────────────────────────────────────────────────────
@Dao
interface CompanyProfileDao {
    @Query("SELECT * FROM company_profile WHERE id = 1")
    fun getProfile(): Flow<CompanyProfile?>

    @Query("SELECT * FROM company_profile WHERE id = 1")
    suspend fun getProfileSync(): CompanyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: CompanyProfile)
}

// ─── Invoice Payment DAO ──────────────────────────────────────────────────────
@Dao
interface InvoicePaymentDao {
    @Query("SELECT * FROM invoice_payments WHERE invoiceId = :invoiceId ORDER BY date DESC")
    fun getPaymentsForInvoice(invoiceId: Long): Flow<List<InvoicePayment>>

    @Query("SELECT * FROM invoice_payments ORDER BY date DESC")
    fun getAllPayments(): Flow<List<InvoicePayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: InvoicePayment): Long

    @Update
    suspend fun updatePayment(payment: InvoicePayment)

    @Delete
    suspend fun deletePayment(payment: InvoicePayment)
}
