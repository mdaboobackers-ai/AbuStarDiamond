package com.goldsmith.billing.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.security.KeystoreManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Customer::class,
        Invoice::class,
        BillItem::class,
        GoldRate::class,
        MeltingRecord::class,
        CompanyProfile::class,
        InvoicePayment::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(DateConverter::class, ListConverter::class)
abstract class GoldsmithDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun billItemDao(): BillItemDao
    abstract fun goldRateDao(): GoldRateDao
    abstract fun meltingDao(): MeltingDao
    abstract fun companyProfileDao(): CompanyProfileDao
    abstract fun invoicePaymentDao(): InvoicePaymentDao

    companion object {
        const val DATABASE_NAME = "goldsmith_vault.db"

        @Volatile
        private var INSTANCE: GoldsmithDatabase? = null

        fun getDatabase(context: Context): GoldsmithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildEncryptedDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        fun buildEncryptedDatabase(context: Context): GoldsmithDatabase {
            // Get or generate DB encryption key from Android Keystore
            val keystoreManager = KeystoreManager(context)
            val passphrase = keystoreManager.getOrCreateDatabaseKey()
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))

            return Room.databaseBuilder(
                context.applicationContext,
                GoldsmithDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `invoice_payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `invoiceId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL DEFAULT 0.0,
                        `goldGrams` REAL NOT NULL DEFAULT 0.0,
                        `goldKarat` INTEGER NOT NULL DEFAULT 24,
                        `paymentMode` TEXT NOT NULL DEFAULT 'CASH',
                        `date` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_payments_invoiceId` ON `invoice_payments` (`invoiceId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerShopName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerOwnerName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerAddress` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerPhone` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `invoice_payments` ADD COLUMN `attachmentUris` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
