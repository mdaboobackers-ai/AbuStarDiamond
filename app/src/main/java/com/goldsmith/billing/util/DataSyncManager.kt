package com.goldsmith.billing.util

import android.content.Context
import android.net.Uri
import com.goldsmith.billing.data.db.GoldsmithDatabase
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.data.repository.AppSettings
import com.goldsmith.billing.data.repository.SettingsRepository
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
    val appSettings: AppSettings? = null,
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BackupCounts(
    val customers: Int = 0,
    val invoices: Int = 0,
    val billItems: Int = 0,
    val invoicePayments: Int = 0,
    val meltingRecords: Int = 0,
    val goldRates: Int = 0,
    val hasCompanyProfile: Boolean = false,
    val hasAppSettings: Boolean = false
) {
    fun summary(): String =
        "Customers $customers • Bills $invoices • Items $billItems • Payments $invoicePayments • Melting $meltingRecords"
}

fun SyncPayload.counts(): BackupCounts = BackupCounts(
    customers = customers.size,
    invoices = invoices.size,
    billItems = billItems.size,
    invoicePayments = invoicePayments.orEmpty().size,
    meltingRecords = meltingRecords.size,
    goldRates = goldRates.size,
    hasCompanyProfile = companyProfile != null,
    hasAppSettings = appSettings != null
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
    const val STALE_BACKUP_DAYS = 2L

    fun backupDir(context: Context): File {
        val mediaRoot = context.externalMediaDirs.firstOrNull()
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(mediaRoot, FOLDER_NAME).apply { mkdirs() }
    }

    fun backupFile(context: Context, fileName: String = BackupFileConfig.dailyAutoFileName()): File =
        File(backupDir(context), fileName)

    fun backupFiles(context: Context): List<File> =
        backupDir(context).listFiles { file -> file.isFile && file.extension.equals(BackupFileConfig.EXTENSION, ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }

    fun latestBackupFile(context: Context): File? = backupFiles(context).firstOrNull()

    fun isStale(file: File?, nowMillis: Long = System.currentTimeMillis()): Boolean =
        file == null || nowMillis - file.lastModified() > java.util.concurrent.TimeUnit.DAYS.toMillis(STALE_BACKUP_DAYS)
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
    private val settingsRepo = SettingsRepository(context)
    var lastErrorMessage: String? = null
        private set
    var lastBackupCounts: BackupCounts? = null
        private set

    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            val remoteFile = File(context.cacheDir, "remote_sync.json")
            val success = driveHelper.downloadFile(DriveBackupConfig.REMOTE_FILE, remoteFile) ||
                driveHelper.downloadFile(DriveBackupConfig.LEGACY_REMOTE_FILE, remoteFile)
            
            if (success) {
                readPayload(remoteFile)?.let {
                    lastBackupCounts = it.counts()
                    smartMerge(it)
                }
            }
            
            // After merge, export current state
            val localFile = File(context.cacheDir, "local_sync.json")
            val payload = buildPayload(System.currentTimeMillis())
            lastBackupCounts = payload.counts()
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
            val payload = buildPayload(System.currentTimeMillis())
            lastBackupCounts = payload.counts()
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
            lastBackupCounts = remotePayload.counts()
            smartMerge(remotePayload)
            true
        } catch (e: Exception) {
            lastErrorMessage = backupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun exportBackupToUri(uri: Uri, backupTime: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            val payload = buildPayload(backupTime)
            lastBackupCounts = payload.counts()
            val backupBytes = encryptedPayloadBytes(payload)
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
            lastBackupCounts = remotePayload.counts()
            smartMerge(remotePayload)
            true
        } catch (e: Exception) {
            lastErrorMessage = fileBackupErrorMessage(e)
            e.printStackTrace()
            false
        }
    }

    suspend fun validateBackupFile(file: File): Boolean = withContext(Dispatchers.IO) {
        lastErrorMessage = null
        try {
            if (!file.exists() || file.length() <= 0L) {
                lastErrorMessage = "Backup file is missing or empty."
                return@withContext false
            }
            val payload = readPayload(file)
            if (payload == null) {
                lastErrorMessage = "Backup file could not be read. It may be damaged."
                false
            } else {
                true
            }
        } catch (e: Exception) {
            lastErrorMessage = fileBackupErrorMessage(e)
            false
        }
    }

    suspend fun exportAutoBackupFile(fileName: String = BackupFileConfig.dailyAutoFileName()): Boolean =
        withContext(Dispatchers.IO) {
            lastErrorMessage = null
            try {
                createLocalBackupFile(fileName) != null
                true
            } catch (e: Exception) {
                lastErrorMessage = fileBackupErrorMessage(e)
                e.printStackTrace()
                false
            }
        }

    suspend fun createLocalBackupFile(fileName: String = BackupFileConfig.defaultFileName()): File? =
        withContext(Dispatchers.IO) {
            lastErrorMessage = null
            try {
                val backupFile = LocalBackupStore.backupFile(context, fileName)
                val payload = buildPayload(System.currentTimeMillis())
                lastBackupCounts = payload.counts()
                writeEncryptedPayload(backupFile, payload)
                backupFile
            } catch (e: Exception) {
                lastErrorMessage = fileBackupErrorMessage(e)
                e.printStackTrace()
                null
            }
        }

    suspend fun uploadLocalBackupToDrive(file: File = LocalBackupStore.latestBackupFile(context) ?: File("")): Boolean =
        withContext(Dispatchers.IO) {
            lastErrorMessage = null
            try {
                if (!file.exists() || file.length() <= 0L) {
                    lastErrorMessage = "No local backup file found. Save a local backup first."
                    return@withContext false
                }
                val uploaded = driveHelper.uploadFile(file, DriveBackupConfig.REMOTE_FILE) != null
                if (!uploaded) lastErrorMessage = "Google Drive upload did not return a file id."
                uploaded
            } catch (e: Exception) {
                lastErrorMessage = backupErrorMessage(e)
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

    private suspend fun buildPayload(backupTimeOverride: Long? = null): SyncPayload {
        // FIX: Use *Sync suspend functions instead of flow.first() to guarantee
        // all rows are read from the DB in a single consistent snapshot.
        // flow.first() can miss the last write if the Room invalidation hasn't fired yet.
        val allInvoices  = db.invoiceDao().getAllInvoicesSync()
        val allCustomers = db.customerDao().getAllCustomersSync()
        val allMelting   = db.meltingDao().getAllMeltingRecordsSync()
        val allRates     = db.goldRateDao().getRateHistory().first()   // no sync variant – flow is fine here
        val allPayments  = db.invoicePaymentDao().getAllPaymentsSync()
        val settings     = settingsRepo.settingsFlow.first().let { current ->
            backupTimeOverride?.let { current.copy(lastBackupTime = it) } ?: current
        }

        return SyncPayload(
            customers      = allCustomers,
            invoices       = allInvoices,
            billItems      = fetchAllBillItems(allInvoices),
            invoicePayments = allPayments,
            meltingRecords = allMelting,
            goldRates      = allRates,
            companyProfile = db.companyProfileDao().getProfileSync(),
            appSettings    = settings,
            deviceId       = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            )
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
        val customerIdMap  = mutableMapOf<Long, Long>()
        val invoiceIdMap   = mutableMapOf<Long, Long>()
        val paymentIdMap   = mutableMapOf<Long, Long>()

        val localCustomersByKey = db.customerDao().getAllCustomersSync()
            .associateBy { BackupMergeIdentity.customerKey(it) }
            .toMutableMap()

        // ── 1. Customers ──────────────────────────────────────────────────────
        remote.customers.forEach { remoteCust ->
            val remoteKey = BackupMergeIdentity.customerKey(remoteCust)
            val localCust = localCustomersByKey[remoteKey]
            val localId = if (localCust == null) {
                val inserted = db.customerDao().insertCustomer(remoteCust.copy(id = 0))
                localCustomersByKey[remoteKey] = remoteCust.copy(id = inserted)
                inserted
            } else {
                if (remoteCust.updatedAt.after(localCust.updatedAt)) {
                    val updated = remoteCust.copy(id = localCust.id)
                    db.customerDao().updateCustomer(updated)
                    localCustomersByKey[remoteKey] = updated
                }
                localCust.id
            }
            customerIdMap[remoteCust.id] = localId
        }

        // ── 2. Invoices ───────────────────────────────────────────────────────
        // FIX: Track which LOCAL invoice ids we actually processed from the remote
        // payload. We always replace bill items for these, regardless of timestamp.
        val processedLocalInvoiceIds = mutableSetOf<Long>()

        remote.invoices.forEach { remoteInv ->
            val mappedCustomerId = customerIdMap[remoteInv.customerId]
                ?: customerIdFromInvoiceSnapshot(remoteInv, localCustomersByKey).also {
                    customerIdMap[remoteInv.customerId] = it
                }
            val normalizedRemote = invoiceWithCustomerSnapshot(remoteInv, mappedCustomerId)
            val localInv = db.invoiceDao().getInvoiceByNumber(remoteInv.invoiceNumber)
            val localId = if (localInv == null) {
                db.invoiceDao().insertInvoice(normalizedRemote.copy(id = 0))
            } else {
                if (remoteInv.updatedAt.after(localInv.updatedAt)) {
                    db.invoiceDao().updateInvoice(normalizedRemote.copy(id = localInv.id))
                } else if (localInv.customerOwnerName.isBlank() ||
                    localInv.customerShopName.isBlank() ||
                    localInv.customerPhone.isBlank()
                ) {
                    db.invoiceDao().updateInvoice(
                        localInv.copy(
                            customerId        = mappedCustomerId,
                            customerShopName  = normalizedRemote.customerShopName,
                            customerOwnerName = normalizedRemote.customerOwnerName,
                            customerAddress   = normalizedRemote.customerAddress,
                            customerPhone     = normalizedRemote.customerPhone
                        )
                    )
                }
                localInv.id
            }
            invoiceIdMap[remoteInv.id] = localId
            processedLocalInvoiceIds += localId      // FIX: always mark this invoice
        }

        // ── 3. Bill Items ─────────────────────────────────────────────────────
        // FIX: Replace bill items for EVERY invoice that came from the remote payload.
        // Old code skipped invoices whose timestamp hadn't changed → items were NOT
        // refreshed on repeated import → first item was lost each cycle.
        remote.billItems.groupBy { it.invoiceId }.forEach { (remoteInvoiceId, items) ->
            val localInvoiceId = invoiceIdMap[remoteInvoiceId] ?: return@forEach
            if (localInvoiceId !in processedLocalInvoiceIds) return@forEach
            db.billItemDao().deleteAllItemsForInvoice(localInvoiceId)
            db.billItemDao().insertBillItems(
                items.map { it.copy(id = 0, invoiceId = localInvoiceId) }
            )
        }

        // ── 4. Payments ───────────────────────────────────────────────────────
        remote.invoicePayments.orEmpty().forEach { remotePayment ->
            val localInvoiceId = invoiceIdMap[remotePayment.invoiceId] ?: return@forEach
            val existing = db.invoicePaymentDao().getPaymentsForInvoiceSync(localInvoiceId)
                .firstOrNull { it.samePaymentAs(remotePayment) }
            val localPaymentId = existing?.id
                ?: db.invoicePaymentDao().insertPayment(
                    remotePayment.copy(id = 0, invoiceId = localInvoiceId)
                )
            paymentIdMap[remotePayment.id] = localPaymentId
        }

        // ── 5. Melting ────────────────────────────────────────────────────────
        val localMelting = db.meltingDao().getAllMeltingRecordsSync()
        remote.meltingRecords.forEach { remoteMelt ->
            val mappedInvoiceId  = remoteMelt.linkedInvoiceId?.let { invoiceIdMap[it] }
            val mappedPaymentId  = remoteMelt.linkedPaymentId?.let { paymentIdMap[it] }
            val mappedCustomerId = customerIdMap[remoteMelt.customerId] ?: remoteMelt.customerId
            val normalizedRemote = remoteMelt.copy(
                customerId      = mappedCustomerId,
                linkedInvoiceId = mappedInvoiceId,
                linkedPaymentId = mappedPaymentId
            )
            val localMelt = localMelting.firstOrNull { it.sameMeltingAs(normalizedRemote) }
            if (localMelt == null) {
                db.meltingDao().insertMeltingRecord(normalizedRemote.copy(id = 0))
            } else if (remoteMelt.updatedAt.after(localMelt.updatedAt)) {
                db.meltingDao().updateMeltingRecord(normalizedRemote.copy(id = localMelt.id))
            }
        }

        remote.companyProfile?.let { db.companyProfileDao().upsertProfile(it) }
        remote.appSettings?.let { settingsRepo.restoreFromBackup(it) }
    }

    private suspend fun customerIdFromInvoiceSnapshot(
        invoice: Invoice,
        localCustomersByKey: MutableMap<String, Customer>
    ): Long {
        val restored = Customer(
            name = invoice.customerOwnerName.ifBlank {
                invoice.customerShopName.ifBlank { "Restored Customer" }
            },
            phone = invoice.customerPhone,
            companyName = invoice.customerShopName,
            address = invoice.customerAddress,
            createdAt = invoice.createdAt,
            updatedAt = invoice.updatedAt
        )
        val key = BackupMergeIdentity.customerKey(invoice)
        localCustomersByKey[key]?.let { return it.id }
        val inserted = db.customerDao().insertCustomer(restored)
        localCustomersByKey[key] = restored.copy(id = inserted)
        return inserted
    }

    private suspend fun invoiceWithCustomerSnapshot(invoice: Invoice, customerId: Long): Invoice {
        val customer = db.customerDao().getCustomerById(customerId)
        return invoice.copy(
            customerId = customerId,
            customerShopName = invoice.customerShopName.ifBlank { customer?.companyName.orEmpty() },
            customerOwnerName = invoice.customerOwnerName.ifBlank { customer?.name.orEmpty() },
            customerAddress = invoice.customerAddress.ifBlank { customer?.fullAddress().orEmpty() },
            customerPhone = invoice.customerPhone.ifBlank { customer?.phone.orEmpty() }
        )
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
