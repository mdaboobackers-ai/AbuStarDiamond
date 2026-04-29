package com.goldsmith.billing.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
        CompanyProfile::class,
        InvoicePayment::class
    ],
    version = 2,
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
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
