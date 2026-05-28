package com.goldsmith.billing.util

import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.Invoice
import java.util.Locale

object BackupMergeIdentity {
    fun customerKey(customer: Customer): String {
        val phone = phoneKey(customer.phone)
        val business = normalized(customer.companyName.ifBlank { customer.name })
        val owner = normalized(customer.name)
        val address = normalized(customer.fullAddress())
        return when {
            phone.isNotBlank() && (business.isNotBlank() || owner.isNotBlank()) ->
                listOf(phone, business, owner).joinToString("|")
            phone.isNotBlank() -> "phone|$phone"
            else -> listOf(business, owner, address).joinToString("|")
        }
    }

    fun customerKey(invoice: Invoice): String {
        val phone = phoneKey(invoice.customerPhone)
        val business = normalized(invoice.customerShopName.ifBlank { invoice.customerOwnerName })
        val owner = normalized(invoice.customerOwnerName)
        val address = normalized(invoice.customerAddress)
        return when {
            phone.isNotBlank() && (business.isNotBlank() || owner.isNotBlank()) ->
                listOf(phone, business, owner).joinToString("|")
            phone.isNotBlank() -> "phone|$phone"
            else -> listOf(business, owner, address).joinToString("|")
        }
    }

    fun isSameCustomer(left: Customer, right: Customer): Boolean =
        customerKey(left) == customerKey(right)

    fun phoneKey(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.length == 12 && digits.startsWith("91") -> digits.takeLast(10)
            digits.length > 10 -> digits.takeLast(10)
            else -> digits
        }
    }

    private fun normalized(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .filter { it.isLetterOrDigit() || it == ' ' }
}
