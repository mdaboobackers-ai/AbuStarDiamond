package com.goldsmith.billing.util

import android.content.Context
import android.net.Uri
import com.goldsmith.billing.data.db.GoldsmithDatabase
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.security.KeystoreManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

object BackupFileConfig {
    const val MIME_TYPE = "application/octet-stream"
    const val EXTENSION = "asdb"

    fun defaultFileName(timestamp: Long = System.currentTimeMillis()): String =
        "abu_star_backup_$timestamp.$EXTENSION"

    fun dailyAutoFileName(date: Date = Date()): String =
        "abu_star_auto_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(date)}.$EXTENSION"
}

object LocalBackupStore {
    const val FOLDER_NAME = "Backups"

    fun backupDir(context: Context): File {
        val mediaRoot = context.externalMediaDirs.firstOrNull()
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(mediaRoot, FOLDER_NAME).apply { mkdirs() }
    }

    fun backupFile(context: Context, fileName: String = BackupFileConfig.dailyAutoFileName()): File =
        File(backupDir(context), fileName)
}

class DataSyncManager(
    private val context: Context,
    signedInAccount: GoogleSignInAccount? = null
) {
    private val db = GoldsmithDatabase.getDatabase(context)
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()
    private val driveHelper = GoogleDriveHelper(context, signedInAccount)
    private val keystoreManager = KeystoreManager(context)
    var lastErrorMessage: String? = null
        private set

    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
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
            val uploaded = driveHelper.uploadFile(localFile, DriveBackupConfig.REMOTE_FILE) != null
            if (!uploaded) lastErrorMessage = "Google Drive upload did not return a file id."
            uploaded
        } catch (e: Exception) {
            lastErrorMessage = backupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun performBackup(): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            val localFile = File(context.cacheDir, "local_sync.json")
            writeEncryptedPayload(localFile, buildPayload())
            val uploaded = driveHelper.uploadFile(localFile, DriveBackupConfig.REMOTE_FILE) != null
            if (!uploaded) lastErrorMessage = "Google Drive upload did not return a file id."
            uploaded
        } catch (e: Exception) {
            lastErrorMessage = backupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun performRestore(): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            val remoteFile = File(context.cacheDir, "remote_sync.json")
            val downloaded = driveHelper.downloadFile(DriveBackupConfig.REMOTE_FILE, remoteFile) ||
                driveHelper.downloadFile(DriveBackupConfig.LEGACY_REMOTE_FILE, remoteFile)
            if (!downloaded) {
                lastErrorMessage = "No backup file was found in the selected Google account."
                return@withContext false
            }
            val remotePayload = readPayload(remoteFile)
            if (remotePayload == null) {
                lastErrorMessage = "Backup file could not be read or decrypted."
                return@withContext false
            }
            smartMerge(remotePayload)
            true
        } catch (e: Exception) {
            lastErrorMessage = backupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun exportBackupToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            val backupBytes = encryptedPayloadBytes(buildPayload())
            val resolver = context.contentResolver
            val opened = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(uri)
            if (opened == null) {
                lastErrorMessage = "Unable to open the selected backup file for writing."
                return@withContext false
            }
            opened.use { it.write(backupBytes) }
            true
        } catch (e: Exception) {
            lastErrorMessage = fileBackupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun mergeBackupFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
            }
            if (bytes == null || bytes.isEmpty()) {
                lastErrorMessage = "Unable to read the selected backup file."
                return@withContext false
            }
            val remotePayload = readPayload(bytes)
            if (remotePayload == null) {
                lastErrorMessage = "Backup file could not be read. Please select a valid Abu Star backup file."
                return@withContext false
            }
            smartMerge(remotePayload)
            true
        } catch (e: Exception) {
            lastErrorMessage = fileBackupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun exportAutoBackupFile(fileName: String = BackupFileConfig.dailyAutoFileName()): Boolean =
        withContext(Dispatchers.IO) {
            lastErrorMessage = null
            try {
                val backupFile = LocalBackupStore.backupFile(context, fileName)
                writeEncryptedPayload(backupFile, buildPayload())
                true
            } catch (e: Exception) {
                lastErrorMessage = fileBackupErrorMessage(e)
                e.printStackTrace()
                false
            }
        }

    private fun backupErrorMessage(error: Exception): String {
        val clean = error.message?.trim().orEmpty()
        return when {
            error is DriveBackupException && clean.isNotBlank() -> clean
            clean.contains("UserRecoverableAuthIOException", ignoreCase = true) ->
                "Google Drive needs permission again. Please change account and allow Drive backup access."
            clean.contains("401", ignoreCase = true) || clean.contains("unauthorized", ignoreCase = true) ->
                "Google account authorization expired. Please choose the account again."
            clean.contains("403", ignoreCase = true) || clean.contains("insufficient", ignoreCase = true) ->
                "Google Drive permission was denied. Please allow Drive backup access."
            clean.contains("Unable to resolve host", ignoreCase = true) || clean.contains("timeout", ignoreCase = true) ->
                "Internet connection failed while contacting Google Drive."
            clean.isNotBlank() -> clean
            else -> "Google Drive backup failed. Please try again."
        }
    }

    private fun writeEncryptedPayload(file: File, payload: SyncPayload) {
        file.writeBytes(encryptedPayloadBytes(payload))
    }

    private fun encryptedPayloadBytes(payload: SyncPayload): ByteArray {
        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        return keystoreManager.encryptPortableBackupBytes(jsonBytes)
    }

    private fun readPayload(file: File): SyncPayload? {
        return try {
            val decrypted = keystoreManager.decryptBackupBytes(file.readBytes()).toString(Charsets.UTF_8)
            gson.fromJson(decrypted, SyncPayload::class.java)
        } catch (_: Exception) {
            runCatching { gson.fromJson(file.readText(), SyncPayload::class.java) }.getOrNull()
        }
    }

    private fun readPayload(bytes: ByteArray): SyncPayload? {
        return try {
            val decrypted = keystoreManager.decryptBackupBytes(bytes).toString(Charsets.UTF_8)
            gson.fromJson(decrypted, SyncPayload::class.java)
        } catch (_: Exception) {
            runCatching { gson.fromJson(bytes.toString(Charsets.UTF_8), SyncPayload::class.java) }.getOrNull()
        }
    }

    private fun fileBackupErrorMessage(error: Exception): String {
        val clean = error.message?.trim().orEmpty()
        return when {
            clean.contains("permission", ignoreCase = true) ->
                "File permission was denied. Please choose the backup file again."
            clean.contains("No such file", ignoreCase = true) ->
                "Backup file was not found. Please choose the backup file again."
            clean.isNotBlank() -> clean
            else -> "Backup file operation failed. Please try again."
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
        val customerIdMap = mutableMapOf<Long, Long>()
        val invoiceIdMap = mutableMapOf<Long, Long>()
        val paymentIdMap = mutableMapOf<Long, Long>()
        val invoiceIdsReceivingRemoteRows = mutableSetOf<Long>()

        // 1. Customers merge by phone first. Local Room ids collide across offline devices.
        remote.customers.forEach { remoteCust ->
            val localCust = remoteCust.phone.takeIf { it.isNotBlank() }?.let { db.customerDao().getCustomerByPhone(it) }
                ?: db.customerDao().getCustomerById(remoteCust.id)
            val localId = if (localCust == null) {
                db.customerDao().insertCustomer(remoteCust.copy(id = 0))
            } else {
                if (remoteCust.updatedAt.after(localCust.updatedAt)) {
                    db.customerDao().updateCustomer(remoteCust.copy(id = localCust.id))
                }
                localCust.id
            }
            customerIdMap[remoteCust.id] = localId
        }

        // 2. Invoices merge by invoice number. This preserves independent offline bills.
        remote.invoices.forEach { remoteInv ->
            val mappedCustomerId = customerIdMap[remoteInv.customerId] ?: remoteInv.customerId
            val normalizedRemote = remoteInv.copy(customerId = mappedCustomerId)
            val localInv = db.invoiceDao().getInvoiceByNumber(remoteInv.invoiceNumber)
            val localId = if (localInv == null) {
                db.invoiceDao().insertInvoice(normalizedRemote.copy(id = 0)).also {
                    invoiceIdsReceivingRemoteRows += it
                }
            } else {
                if (remoteInv.updatedAt.after(localInv.updatedAt)) {
                    db.invoiceDao().updateInvoice(normalizedRemote.copy(id = localInv.id))
                    invoiceIdsReceivingRemoteRows += localInv.id
                }
                localInv.id
            }
            invoiceIdMap[remoteInv.id] = localId
        }
        
        // 3. Bill Items: replace item rows only for invoices we mapped from the remote payload.
        remote.billItems.groupBy { it.invoiceId }.forEach { (remoteInvoiceId, items) ->
            val localInvoiceId = invoiceIdMap[remoteInvoiceId] ?: return@forEach
            if (localInvoiceId !in invoiceIdsReceivingRemoteRows) return@forEach
            db.billItemDao().deleteAllItemsForInvoice(localInvoiceId)
            db.billItemDao().insertBillItems(items.map { it.copy(id = 0, invoiceId = localInvoiceId) })
        }

        // 4. Payments: merge by invoice + payment details so offline payment rows do not collide by id.
        remote.invoicePayments.orEmpty().forEach { remotePayment ->
            val localInvoiceId = invoiceIdMap[remotePayment.invoiceId] ?: return@forEach
            val existing = db.invoicePaymentDao().getPaymentsForInvoiceSync(localInvoiceId)
                .firstOrNull { it.samePaymentAs(remotePayment) }
            val localPaymentId = existing?.id ?: db.invoicePaymentDao().insertPayment(remotePayment.copy(id = 0, invoiceId = localInvoiceId))
            paymentIdMap[remotePayment.id] = localPaymentId
        }

        // 5. Melting: preserve links to the remapped invoice/payment rows.
        val localMelting = db.meltingDao().getAllMeltingRecordsSync()
        remote.meltingRecords.forEach { remoteMelt ->
            val mappedInvoiceId = remoteMelt.linkedInvoiceId?.let { invoiceIdMap[it] }
            val mappedPaymentId = remoteMelt.linkedPaymentId?.let { paymentIdMap[it] }
            val mappedCustomerId = customerIdMap[remoteMelt.customerId] ?: remoteMelt.customerId
            val normalizedRemote = remoteMelt.copy(
                customerId = mappedCustomerId,
                linkedInvoiceId = mappedInvoiceId,
                linkedPaymentId = mappedPaymentId
            )
            val localMelt = localMelting.firstOrNull { it.sameMeltingAs(normalizedRemote) }
                ?: db.meltingDao().getMeltingRecordById(remoteMelt.id)
            if (localMelt == null) {
                db.meltingDao().insertMeltingRecord(normalizedRemote.copy(id = 0))
            } else if (remoteMelt.updatedAt.after(localMelt.updatedAt)) {
                db.meltingDao().updateMeltingRecord(normalizedRemote.copy(id = localMelt.id))
            }
        }

        remote.companyProfile?.let { db.companyProfileDao().upsertProfile(it) }
    }

    private fun InvoicePayment.samePaymentAs(other: InvoicePayment): Boolean =
        paymentMode == other.paymentMode &&
            kotlin.math.abs(amount - other.amount) < 0.005 &&
            kotlin.math.abs(goldGrams - other.goldGrams) < 0.0005 &&
            goldKarat == other.goldKarat &&
            kotlin.math.abs(date.time - other.date.time) < 1000L &&
            notes == other.notes

    private fun MeltingRecord.sameMeltingAs(other: MeltingRecord): Boolean {
        if (linkedPaymentId != null && linkedPaymentId == other.linkedPaymentId) return true
        if (linkedInvoiceId != null && linkedInvoiceId == other.linkedInvoiceId &&
            kotlin.math.abs(rawWeightGrams - other.rawWeightGrams) < 0.0005 &&
            kotlin.math.abs(createdAt.time - other.createdAt.time) < 1000L
        ) return true
        return customerId == other.customerId &&
            kotlin.math.abs(rawWeightGrams - other.rawWeightGrams) < 0.0005 &&
            kotlin.math.abs(finalPureWeightGrams - other.finalPureWeightGrams) < 0.0005 &&
            kotlin.math.abs(createdAt.time - other.createdAt.time) < 1000L
    }
}
