package com.goldsmith.billing.util

import com.goldsmith.billing.data.model.Invoice
import com.goldsmith.billing.data.model.MeltingRecord
import com.goldsmith.billing.data.model.MeltingStatus
import java.util.Locale

data class MeltingVersionGroup(
    val latest: MeltingRecord,
    val previousVersions: List<MeltingRecord>,
    val versionNumber: Int
)

data class MeltingInvoiceAllocation(
    val invoice: Invoice,
    val appliedPureGoldGrams: Double,
    val remainingBalanceAfter: Double
)

object MeltingVersions {
    private const val MELTING_PAYMENT_NOTE_PREFIX = "Created from melting"

    fun latestGroups(records: List<MeltingRecord>): List<MeltingVersionGroup> =
        records
            .groupBy(::versionKey)
            .values
            .mapNotNull { group ->
                val sorted = group.sortedWith(compareBy<MeltingRecord> { it.createdAt }.thenBy { it.id })
                val latest = sorted.lastOrNull() ?: return@mapNotNull null
                MeltingVersionGroup(
                    latest = latest,
                    previousVersions = sorted.dropLast(1).asReversed(),
                    versionNumber = sorted.size
                )
            }
            .sortedWith(compareByDescending<MeltingVersionGroup> { it.latest.createdAt }.thenByDescending { it.latest.id })

    fun canEditFromMeltingTab(record: MeltingRecord): Boolean =
        normalizedStatus(record.status) == MeltingStatus.PENDING.name

    fun normalizedStatus(status: String): String =
        if (status.equals("APPROVED", ignoreCase = true)) MeltingStatus.TESTED.name else status

    fun sourceLabel(record: MeltingRecord): String = when {
        record.linkedPaymentId != null -> "Payment history"
        record.linkedInvoiceId != null -> "Billing history"
        else -> "Melting page"
    }

    fun isMeltingGeneratedPayment(notes: String): Boolean =
        notes.trim().lowercase(Locale.US).startsWith(MELTING_PAYMENT_NOTE_PREFIX.lowercase(Locale.US))

    fun meltingPaymentNote(meltingId: Long): String =
        "$MELTING_PAYMENT_NOTE_PREFIX #$meltingId"

    fun allocatePureGoldToOldestInvoices(
        pureGoldGrams: Double,
        invoices: List<Invoice>,
        fallbackRate24K: Double
    ): List<MeltingInvoiceAllocation> {
        var balancePure = GoldCalc.roundGrams(pureGoldGrams.coerceAtLeast(0.0))
        if (balancePure <= 0.0) return emptyList()

        val allocations = mutableListOf<MeltingInvoiceAllocation>()
        invoices
            .filter { invoice -> kotlin.math.abs(invoice.remainingBalance) > 0.005 }
            .sortedWith(compareBy<Invoice> { it.date }.thenBy { it.id })
            .forEach { invoice ->
                if (balancePure <= 0.0) return@forEach
                val invoiceRate = invoice.goldRate24K.takeIf { it > 0.0 } ?: fallbackRate24K
                if (invoiceRate <= 0.0 || invoice.remainingBalance <= 0.0) return@forEach
                val duePure = GoldCalc.balancePureGold(invoice.remainingBalance, invoiceRate).coerceAtLeast(0.0)
                val appliedPure = GoldCalc.roundGrams(minOf(balancePure, duePure))
                if (appliedPure <= 0.0) return@forEach
                val remainingAfter = GoldCalc.roundMoney(invoice.remainingBalance - (appliedPure * invoiceRate))
                allocations += MeltingInvoiceAllocation(
                    invoice = invoice,
                    appliedPureGoldGrams = appliedPure,
                    remainingBalanceAfter = if (kotlin.math.abs(remainingAfter) <= 0.005) 0.0 else remainingAfter
                )
                balancePure = GoldCalc.roundGrams(balancePure - appliedPure)
            }
        return allocations
    }

    private fun versionKey(record: MeltingRecord): String = when {
        record.linkedPaymentId != null -> "payment:${record.linkedPaymentId}"
        record.linkedInvoiceId != null -> "invoice:${record.linkedInvoiceId}:raw:${record.rawWeightGrams}"
        else -> "manual:${record.id}"
    }
}
