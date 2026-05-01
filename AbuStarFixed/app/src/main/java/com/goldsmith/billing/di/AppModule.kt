package com.goldsmith.billing.di

import android.content.Context
import com.goldsmith.billing.data.dao.*
import com.goldsmith.billing.data.db.GoldsmithDatabase
import com.goldsmith.billing.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideKeystoreManager(@ApplicationContext ctx: Context): KeystoreManager =
        KeystoreManager(ctx)

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context
    ): GoldsmithDatabase = GoldsmithDatabase.buildEncryptedDatabase(ctx)

    @Provides fun provideCustomerDao(db: GoldsmithDatabase): CustomerDao = db.customerDao()
    @Provides fun provideInvoiceDao(db: GoldsmithDatabase): InvoiceDao = db.invoiceDao()
    @Provides fun provideBillItemDao(db: GoldsmithDatabase): BillItemDao = db.billItemDao()
    @Provides fun provideGoldRateDao(db: GoldsmithDatabase): GoldRateDao = db.goldRateDao()
    @Provides fun provideMeltingDao(db: GoldsmithDatabase): MeltingDao = db.meltingDao()
    @Provides fun provideCompanyProfileDao(db: GoldsmithDatabase): CompanyProfileDao = db.companyProfileDao()
}
// Note: InvoiceRepository, SettingsRepository are @Singleton with @Inject constructor
// so Hilt auto-provides them. No explicit @Provides needed.
