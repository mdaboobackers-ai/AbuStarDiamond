package com.goldsmith.billing.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.model.*
import com.goldsmith.billing.security.KeystoreManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        Customer::class,
        Invoice::class,
        BillItem::class,
        GoldRate::class,
        MeltingRecord::class,
        CompanyProfile::class
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

    companion object {
        const val DATABASE_NAME = "goldsmith_vault.db"

        // v1 → v2: DOB, anniversary, devicePrefix, equivalents
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN dateOfBirth INTEGER")
                db.execSQL("ALTER TABLE customers ADD COLUMN anniversary INTEGER")
                db.execSQL("ALTER TABLE invoices ADD COLUMN devicePrefix TEXT NOT NULL DEFAULT 'A'")
                db.execSQL("ALTER TABLE invoices ADD COLUMN equivalent22KGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN equivalent18KGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE gold_rates ADD COLUMN rate20K REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN goldCreditGrams REAL NOT NULL DEFAULT 0.0")
            }
        }

        // v2 → v3: customer snapshot, eqGrams, split gold payments, melting enhancements
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Invoice: customer snapshot + new gram fields
                db.execSQL("ALTER TABLE invoices ADD COLUMN customerNameSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN customerPhoneSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN customerAddressSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN customerGstSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN totalNetWeightGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN totalPureGoldGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN totalEq22KGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN totalEq18KGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN subtotalCash REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN goldPayments TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE invoices ADD COLUMN totalGoldPaidCash REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN remainingCash REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN remainingGoldGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN attachmentUris TEXT NOT NULL DEFAULT ''")
                // BillItem: eqGrams, itemCashValue, imageUris as string
                db.execSQL("ALTER TABLE bill_items ADD COLUMN eqGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE bill_items ADD COLUMN itemCashValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE bill_items ADD COLUMN imageUris TEXT NOT NULL DEFAULT ''")
                // Melting: new fields
                db.execSQL("ALTER TABLE melting_records ADD COLUMN customerNameSnapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN inputKarat INTEGER NOT NULL DEFAULT 22")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN inputPurityPct REAL NOT NULL DEFAULT 91.6")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN testedPurityPct REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN adjustmentGrams REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN status TEXT NOT NULL DEFAULT 'RECEIVED'")
                db.execSQL("ALTER TABLE melting_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun buildEncryptedDatabase(context: Context): GoldsmithDatabase {
            val km = KeystoreManager(context)
            val factory = SupportFactory(SQLiteDatabase.getBytes(km.getOrCreateDatabaseKey().toCharArray()))
            return Room.databaseBuilder(
                context.applicationContext,
                GoldsmithDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
