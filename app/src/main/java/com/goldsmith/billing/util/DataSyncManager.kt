package com.goldsmith.billing.util

import android.content.Context
import com.goldsmith.billing.data.db.GoldsmithDatabase
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.security.KeystoreManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class SyncPayload(
    val customers: List<Customer>,
    val invoices: List<Invoice>,
    val billItems: List<BillItem>,
    val invoicePayments: List<InvoicePayment>? = emptyList(),
    val meltingRecords: List<MeltingRecord>,
    val goldRates: List<GoldRate>,
    val companyProfile: CompanyProfile?,
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DataSyncManager(private val context: Context) {
    private val db = GoldsmithDatabase.getDatabase(context)
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()
    private val driveHelper = GoogleDriveHelper(context)
    private val keystoreManager = KeystoreManager(context)

    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteFile = File(context.cacheDir, "remote_sync.json")
            val success = driveHelper.downloadFile(DriveBackupConfig.REMOTE_FILE, remoteFile) ||
                driveHelper.downloadFile(DriveBackupConfig.LEGACY_REMOTE_FILE, remoteFile)
            
            if (success) {
                readPayload(remoteFile)?.let { smartMerge(it) }
            }
            
            // After merge, export current state
            val allInvoices = db.invoiceDao().getAllInvoices().first()
            val allCustomers = db.customerDao().getAllCustomers().first()
            val allMelting = db.meltingDao().getAllMeltingRecords().first()
            val allRates = db.goldRateDao().getRateHistory().first()
            val allPayments = db.invoicePaymentDao().getAllPayments().first()
            
            val payload = SyncPayload(
                customers = allCustomers,
                invoices = allInvoices,
                billItems = fetchAllBillItems(allInvoices),
                invoicePayments = allPayments,
                meltingRecords = allMelting,
                goldRates = allRates,
                companyProfile = db.companyProfileDao().getProfileSync(),
                deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            )
            
            val localFile = File(context.cacheDir, "local_sync.json")
            writeEncryptedPayload(localFile, payload)
            driveHelper.uploadFile(localFile, DriveBackupConfig.REMOTE_FILE) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun performBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val localFile = File(context.cacheDir, "local_sync.json")
            writeEncryptedPayload(localFile, buildPayload())
            driveHelper.uploadFile(localFile, DriveBackupConfig.REMOTE_FILE) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun performRestore(): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteFile = File(context.cacheDir, "remote_sync.json")
            val downloaded = driveHelper.downloadFile(DriveBackupConfig.REMOTE_FILE, remoteFile) ||
                driveHelper.downloadFile(DriveBackupConfig.LEGACY_REMOTE_FILE, remoteFile)
            if (!downloaded) return@withContext false
            val remotePayload = readPayload(remoteFile) ?: return@withContext false
            smartMerge(remotePayload)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeEncryptedPayload(file: File, payload: SyncPayload) {
        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        file.writeBytes(keystoreManager.encryptBytes(jsonBytes))
    }

    private fun readPayload(file: File): SyncPayload? {
        return try {
            val decrypted = keystoreManager.decryptBytes(file.readBytes()).toString(Charsets.UTF_8)
            gson.fromJson(decrypted, SyncPayload::class.java)
        } catch (_: Exception) {
            runCatching { gson.fromJson(file.readText(), SyncPayload::class.java) }.getOrNull()
        }
    }

    private suspend fun buildPayload(): SyncPayload {
        val allInvoices = db.invoiceDao().getAllInvoices().first()
        val allCustomers = db.customerDao().getAllCustomers().first()
        val allMelting = db.meltingDao().getAllMeltingRecords().first()
        val allRates = db.goldRateDao().getRateHistory().first()
        val allPayments = db.invoicePaymentDao().getAllPayments().first()

        return SyncPayload(
            customers = allCustomers,
            invoices = allInvoices,
            billItems = fetchAllBillItems(allInvoices),
            invoicePayments = allPayments,
            meltingRecords = allMelting,
            goldRates = allRates,
            companyProfile = db.companyProfileDao().getProfileSync(),
            deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        )
    }

    private suspend fun fetchAllBillItems(invoices: List<Invoice>): List<BillItem> {
        val items = mutableListOf<BillItem>()
        invoices.forEach { 
            items.addAll(db.billItemDao().getBillItemsForInvoiceSync(it.id))
        }
        return items
    }

    private suspend fun smartMerge(remote: SyncPayload) {
        // 1. Customers merge
        remote.customers.forEach { remoteCust ->
            val localCust = db.customerDao().getCustomerById(remoteCust.id)
            if (localCust == null) {
                db.customerDao().insertCustomer(remoteCust)
            } else if (remoteCust.updatedAt.after(localCust.updatedAt)) {
                db.customerDao().updateCustomer(remoteCust)
            }
        }

        // 2. Invoices merge
        remote.invoices.forEach { remoteInv ->
            val localInv = db.invoiceDao().getInvoiceById(remoteInv.id)
            if (localInv == null) {
                db.invoiceDao().insertInvoice(remoteInv)
            } else if (remoteInv.updatedAt.after(localInv.updatedAt)) {
                db.invoiceDao().updateInvoice(remoteInv)
            }
        }
        
        // 3. Bill Items
        remote.billItems.forEach { db.billItemDao().insertBillItem(it) }

        // 4. Payments
        remote.invoicePayments.orEmpty().forEach { db.invoicePaymentDao().insertPayment(it) }

        // 5. Melting
        remote.meltingRecords.forEach { remoteMelt ->
            val localMelt = db.meltingDao().getMeltingRecordById(remoteMelt.id)
            if (localMelt == null) {
                db.meltingDao().insertMeltingRecord(remoteMelt)
            } else if (remoteMelt.updatedAt.after(localMelt.updatedAt)) {
                db.meltingDao().updateMeltingRecord(remoteMelt)
            }
        }

        remote.companyProfile?.let { db.companyProfileDao().upsertProfile(it) }
    }
}
