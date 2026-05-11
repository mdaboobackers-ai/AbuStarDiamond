package com.goldsmith.billing

import com.goldsmith.billing.util.GoldCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingLogicTest {

    @Test
    fun `net weight never drops below zero`() {
        assertEquals(9.0, GoldCalc.netWeight(10.0, 1.0), 0.001)
        assertEquals(0.0, GoldCalc.netWeight(1.0, 2.0), 0.001)
    }

    @Test
    fun `pure gold and 916 conversion use three decimal precision`() {
        val pureGold = GoldCalc.fineGold(9.0, 91.6)
        assertEquals(8.244, pureGold, 0.001)
        assertEquals(9.0, GoldCalc.equivalent916(pureGold), 0.001)
    }

    @Test
    fun `pure gold calculation does not round customer gold upward`() {
        val pureGold = GoldCalc.fineGold(54.0, 91.6)

        assertEquals(49.464, pureGold, 0.000001)
        assertFalse("Gold must not be rounded to 49.500g", pureGold == 49.5)
    }

    @Test
    fun `item amount uses pure gold plus making and stone value`() {
        val amount = GoldCalc.itemAmount(
            netWeight = 9.0,
            purityPercent = 91.6,
            rate24K = 7000.0,
            makingPercent = 7.0,
            stoneValue = 100.0
        )

        assertEquals(62218.0, amount, 0.001)
    }

    @Test
    fun `making percentage is included in equivalent grams`() {
        val eq = GoldCalc.gramsWithMaking(
            netWeight = 34.95,
            purityPercent = 91.6,
            makingPercent = 7.0
        )

        assertEquals(34.461, eq, 0.001)
    }

    @Test
    fun `stone value converts to gold and is included in equivalent grams`() {
        val eq = GoldCalc.equivalentGramsWithStone(
            netWeight = 34.95,
            purityPercent = 91.6,
            makingPercent = 7.0,
            stoneValue = 700.0,
            rate24K = 7000.0
        )

        assertEquals(34.561, eq, 0.001)
        assertEquals(241927.0, GoldCalc.itemAmount(34.95, 91.6, 7000.0, 7.0, 700.0), 0.001)
    }

    @Test
    fun `subtotal grams uses sum of equivalent grams not net weight`() {
        val eq1 = GoldCalc.equivalentGramsWithStone(34.95, 91.6, 7.0, 700.0, 7000.0)
        val eq2 = GoldCalc.equivalentGramsWithStone(10.0, 75.0, 5.0, 0.0, 7000.0)

        assertEquals(42.561, GoldCalc.subtotalEquivalentGrams(listOf(eq1, eq2)), 0.001)
    }

    @Test
    fun `gold payment converts karat to pure gold and cash value`() {
        assertEquals(2.0, GoldCalc.pureGoldFromKarat(2.0, 24), 0.001)
        assertEquals(1.833, GoldCalc.pureGoldFromKarat(2.0, 22), 0.001)
        assertEquals(12831.0, GoldCalc.goldPaymentValue(2.0, 22, 7000.0), 0.001)
    }

    @Test
    fun `remaining balance reports cash and pure gold equivalent`() {
        val balance = GoldCalc.remainingBalance(
            totalAmount = 100000.0,
            cashPaid = 10000.0,
            goldPayments = listOf(2.0 to 22),
            rate24K = 7000.0
        )

        assertEquals(77169.0, balance.cash, 0.001)
        assertEquals(11.024, balance.pureGoldGrams, 0.001)
    }

    @Test
    fun `pending invoice gold is valued at current gold rate for later cash payment`() {
        val invoiceDayBalance = 70000.0
        val invoiceDayRate = 7000.0
        val todayRate = 8000.0

        assertEquals(10.0, GoldCalc.pendingPureGold(invoiceDayBalance, invoiceDayRate), 0.001)
        assertEquals(80000.0, GoldCalc.pendingCashAtRate(invoiceDayBalance, invoiceDayRate, todayRate), 0.001)

        val remainingAfterTodayCash = GoldCalc.invoiceBalanceAfterPaymentAtCurrentRate(
            invoiceRemainingBalance = invoiceDayBalance,
            invoiceRate24K = invoiceDayRate,
            currentRate24K = todayRate,
            cashPaid = 40000.0,
            goldGrams = 0.0,
            goldKarat = 24
        )

        assertEquals(35000.0, remainingAfterTodayCash, 0.001)
    }

    @Test
    fun `previous balance is added to payable total and partial payment leaves correct due`() {
        val currentBill = 100000.0
        val previousDue = 50000.0
        val payable = GoldCalc.payableWithPreviousBalance(currentBill, previousDue)
        val remaining = GoldCalc.remainingAfterSettlement(payable, cashPaid = 75000.0, goldValue = 0.0)

        assertEquals(150000.0, payable, 0.001)
        assertEquals(75000.0, remaining, 0.001)
    }

    @Test
    fun `previous credit reduces payable total and can create customer credit`() {
        val currentBill = 100000.0
        val previousCredit = -20000.0
        val payable = GoldCalc.payableWithPreviousBalance(currentBill, previousCredit)
        val remaining = GoldCalc.remainingAfterSettlement(payable, cashPaid = 90000.0, goldValue = 0.0)

        assertEquals(80000.0, payable, 0.001)
        assertEquals(-10000.0, remaining, 0.001)
        assertEquals(-1.25, GoldCalc.balancePureGold(remaining, 8000.0), 0.001)
        assertEquals(-10000.0, GoldCalc.balanceCashAtRate(remaining, 8000.0, 8000.0), 0.001)
    }

    @Test
    fun `overpayment is preserved as credit not hidden as zero`() {
        val remaining = GoldCalc.invoiceBalanceAfterPaymentAtCurrentRate(
            invoiceRemainingBalance = 70000.0,
            invoiceRate24K = 7000.0,
            currentRate24K = 7000.0,
            cashPaid = 80000.0,
            goldGrams = 0.0,
            goldKarat = 24
        )

        assertEquals(-10000.0, remaining, 0.001)
        assertEquals(-10000.0, GoldCalc.balanceCashAtRate(remaining, 7000.0, 7000.0), 0.001)
    }

    @Test
    fun `decimal validation rejects invalid payment input`() {
        assertTrue(GoldCalc.isValidDecimal("12.45"))
        assertTrue(GoldCalc.isValidDecimal("0"))
        assertFalse(GoldCalc.isValidDecimal(""))
        assertFalse(GoldCalc.isValidDecimal("12..45"))
        assertFalse(GoldCalc.isValidDecimal("abc"))
    }

    @Test
    fun `blank optional cash payment is accepted as zero when gold is entered`() {
        assertTrue(GoldCalc.isValidOptionalDecimal(""))
        assertTrue(GoldCalc.isValidOptionalDecimal("   "))
        assertTrue(GoldCalc.isValidOptionalDecimal("1250.75"))
        assertFalse(GoldCalc.isValidOptionalDecimal("12..45"))
        assertFalse(GoldCalc.isValidOptionalDecimal("-1"))
        assertEquals(0.0, GoldCalc.decimalOrZero(""), 0.001)
        assertEquals(1250.75, GoldCalc.decimalOrZero("1250.75"), 0.001)
    }

    @Test
    fun `cash payments round paise to nearest rupee`() {
        assertEquals(1251.0, GoldCalc.cashOrZero("1250.75"), 0.001)
        assertEquals(1250.0, GoldCalc.cashOrZero("1250.49"), 0.001)
        assertEquals(0.0, GoldCalc.cashOrZero(""), 0.001)
    }

    @Test
    fun `money totals are stored as whole rupees`() {
        assertEquals(1001.0, GoldCalc.roundMoney(1000.50), 0.001)
        assertEquals(1000.0, GoldCalc.roundMoney(1000.49), 0.001)
    }
}
