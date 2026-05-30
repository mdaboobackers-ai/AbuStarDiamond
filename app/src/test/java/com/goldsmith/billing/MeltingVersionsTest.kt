package com.goldsmith.billing

import com.goldsmith.billing.data.model.MeltingRecord
import com.goldsmith.billing.data.model.MeltingStatus
import com.goldsmith.billing.data.model.Invoice
import com.goldsmith.billing.data.model.PaymentStatus
import com.goldsmith.billing.util.MeltingVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MeltingVersionsTest {
    @Test
    fun `records for the same payment collapse into latest version with history`() {
        val versionOne = MeltingRecord(
            id = 1,
            customerId = 7,
            rawWeightGrams = 10.0,
            finalPureWeightGrams = 9.16,
            purityPercent = 91.6,
            status = MeltingStatus.TESTED.name,
            linkedInvoiceId = 4,
            linkedPaymentId = 9,
            createdAt = Date(1_000)
        )
        val versionTwo = versionOne.copy(
            id = 2,
            finalPureWeightGrams = 9.0,
            purityPercent = 90.0,
            status = MeltingStatus.ADJUSTED.name,
            createdAt = Date(2_000)
        )

        val groups = MeltingVersions.latestGroups(listOf(versionOne, versionTwo))

        assertEquals(1, groups.size)
        assertEquals(versionTwo.id, groups.first().latest.id)
        assertEquals(2, groups.first().versionNumber)
        assertEquals(listOf(versionOne), groups.first().previousVersions)
    }

    @Test
    fun `approved linked melting cannot be edited from melting tab`() {
        val pending = MeltingRecord(
            id = 1,
            customerId = 7,
            rawWeightGrams = 10.0,
            finalPureWeightGrams = 9.16,
            purityPercent = 91.6,
            status = MeltingStatus.PENDING.name,
            linkedInvoiceId = 4,
            linkedPaymentId = 9
        )
        val approved = pending.copy(status = MeltingStatus.TESTED.name)

        assertTrue(MeltingVersions.canEditFromMeltingTab(pending))
        assertFalse(MeltingVersions.canEditFromMeltingTab(approved))
    }

    @Test
    fun `manual melting pure gold allocates from oldest pending invoices first`() {
        val older = invoice(id = 1, remainingPure = 2.0, date = Date(1_000))
        val middle = invoice(id = 2, remainingPure = 3.0, date = Date(2_000))
        val newer = invoice(id = 3, remainingPure = 4.0, date = Date(3_000))

        val allocations = MeltingVersions.allocatePureGoldToOldestInvoices(
            pureGoldGrams = 6.5,
            invoices = listOf(newer, older, middle),
            fallbackRate24K = 10_000.0
        )

        assertEquals(listOf(1L, 2L, 3L), allocations.map { it.invoice.id })
        assertEquals(listOf(2.0, 3.0, 1.5), allocations.map { it.appliedPureGoldGrams })
        assertEquals(listOf(0.0, 0.0, 25_000.0), allocations.map { it.remainingBalanceAfter })
    }

    private fun invoice(id: Long, remainingPure: Double, date: Date): Invoice =
        Invoice(
            id = id,
            invoiceNumber = "B-$id",
            customerId = 7,
            date = date,
            totalAmount = remainingPure * 10_000.0,
            remainingBalance = remainingPure * 10_000.0,
            paymentStatus = PaymentStatus.PARTIAL,
            goldRate24K = 10_000.0
        )
}
