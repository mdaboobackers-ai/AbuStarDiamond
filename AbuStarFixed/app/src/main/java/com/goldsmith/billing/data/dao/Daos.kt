package com.goldsmith.billing.data.dao

import androidx.room.*
import com.goldsmith.billing.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name LIKE '%'||:q||'%' OR phone LIKE '%'||:q||'%' OR companyName LIKE '%'||:q||'%' ORDER BY name ASC LIMIT 20")
    fun searchCustomers(q: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(c: Customer): Long

    @Update
    suspend fun updateCustomer(c: Customer)

    @Delete
    suspend fun deleteCustomer(c: Customer)

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCount(): Flow<Int>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC LIMIT 10")
    fun getRecentCustomers(): Flow<List<Customer>>

    // Birthday alerts
    @Query("SELECT * FROM customers WHERE dateOfBirth IS NOT NULL")
    suspend fun getAllWithDob(): List<Customer>

    @Query("SELECT * FROM customers WHERE anniversary IS NOT NULL")
    suspend fun getAllWithAnniversary(): List<Customer>
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE customerId = :cid ORDER BY date DESC")
    fun getInvoicesByCustomer(cid: Long): Flow<List<Invoice>>

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

    @Query("SELECT COUNT(*) FROM invoices WHERE date >= :start")
    fun getTodayInvoiceCount(start: Date): Flow<Int>

    @Query("SELECT SUM(totalAmount) FROM invoices WHERE date >= :start")
    fun getTodaySalesAmount(start: Date): Flow<Double?>

    @Query("SELECT SUM(totalNetWeightGrams) FROM invoices WHERE date >= :start")
    fun getTodasTotalWeight(start: Date): Flow<Double?>

    @Query("SELECT SUM(remainingCash) FROM invoices WHERE paymentStatus != 'PAID'")
    fun getTotalPendingAmount(): Flow<Double?>

    @Query("SELECT * FROM invoices WHERE invoiceNumber LIKE '%'||:q||'%' OR customerNameSnapshot LIKE '%'||:q||'%' ORDER BY date DESC")
    fun searchInvoices(q: String): Flow<List<Invoice>>

    // Analytics
    @Query("SELECT * FROM invoices WHERE date >= :start AND date <= :end ORDER BY date ASC")
    suspend fun getInvoicesForRange(start: Date, end: Date): List<Invoice>

    @Query("SELECT * FROM invoices ORDER BY totalAmount DESC LIMIT :n")
    suspend fun getTopInvoicesByAmount(n: Int): List<Invoice>
}

@Dao
interface BillItemDao {
    @Query("SELECT * FROM bill_items WHERE invoiceId = :id")
    fun getBillItemsForInvoice(id: Long): Flow<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE invoiceId = :id")
    suspend fun getBillItemsForInvoiceSync(id: Long): List<BillItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItem>)

    @Update
    suspend fun updateBillItem(item: BillItem)

    @Delete
    suspend fun deleteBillItem(item: BillItem)

    @Query("DELETE FROM bill_items WHERE invoiceId = :id")
    suspend fun deleteAllForInvoice(id: Long)
}

@Dao
interface GoldRateDao {
    @Query("SELECT * FROM gold_rates ORDER BY recordedAt DESC LIMIT 1")
    fun getLatestRate(): Flow<GoldRate?>

    @Query("SELECT * FROM gold_rates ORDER BY recordedAt DESC LIMIT 1")
    suspend fun getLatestRateSync(): GoldRate?

    @Query("SELECT * FROM gold_rates ORDER BY recordedAt DESC LIMIT 60")
    fun getRateHistory(): Flow<List<GoldRate>>

    @Insert
    suspend fun insertRate(r: GoldRate): Long
}

@Dao
interface MeltingDao {
    @Query("SELECT * FROM melting_records ORDER BY createdAt DESC")
    fun getAllMeltingRecords(): Flow<List<MeltingRecord>>

    @Query("SELECT * FROM melting_records WHERE customerId = :cid ORDER BY createdAt DESC")
    fun getMeltingRecordsByCustomer(cid: Long): Flow<List<MeltingRecord>>

    @Query("SELECT * FROM melting_records WHERE id = :id")
    suspend fun getMeltingRecordById(id: Long): MeltingRecord?

    @Query("SELECT * FROM melting_records WHERE linkedInvoiceId = :invoiceId")
    suspend fun getMeltingForInvoice(invoiceId: Long): List<MeltingRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeltingRecord(r: MeltingRecord): Long

    @Update
    suspend fun updateMeltingRecord(r: MeltingRecord)

    @Delete
    suspend fun deleteMeltingRecord(r: MeltingRecord)
}

@Dao
interface CompanyProfileDao {
    @Query("SELECT * FROM company_profile WHERE id = 1")
    fun getProfile(): Flow<CompanyProfile?>

    @Query("SELECT * FROM company_profile WHERE id = 1")
    suspend fun getProfileSync(): CompanyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(p: CompanyProfile)
}
