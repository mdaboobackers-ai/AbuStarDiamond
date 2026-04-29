package com.goldsmith.billing.data.repository

import com.goldsmith.billing.data.dao.BillItemDao
import com.goldsmith.billing.data.dao.CompanyProfileDao
import com.goldsmith.billing.data.dao.CustomerDao
import com.goldsmith.billing.data.dao.InvoiceDao
import com.goldsmith.billing.data.model.BillItem
import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.Invoice
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class FullInvoice(
    val invoice: Invoice,
    val customer: Customer?,
    val items: List<BillItem>
)

@Singleton
class InvoiceRepository @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val billItemDao: BillItemDao,
    private val customerDao: CustomerDao,
    private val companyProfileDao: CompanyProfileDao
) {
    suspend fun getFullInvoice(invoiceId: Long): FullInvoice? {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return null
        val customer = customerDao.getCustomerById(invoice.customerId)
        val items = billItemDao.getBillItemsForInvoiceSync(invoiceId)
        return FullInvoice(invoice, customer, items)
    }

    fun getAllInvoices(): Flow<List<Invoice>> = invoiceDao.getAllInvoices()

    suspend fun deleteInvoice(invoice: Invoice) {
        invoiceDao.deleteInvoice(invoice)
    }
}
