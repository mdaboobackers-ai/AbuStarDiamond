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
    fun `item amount uses pure gold plus making and stone value`() {
        val amount = GoldCalc.itemAmount(
            netWeight = 9.0,
            purityPercent = 91.6,
            rate24K = 7000.0,
            makingPerGram = 25.0,
            stoneValue = 100.0
        )

        assertEquals(58033.0, amount, 0.001)
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
    fun `decimal validation rejects invalid payment input`() {
        assertTrue(GoldCalc.isValidDecimal("12.45"))
        assertTrue(GoldCalc.isValidDecimal("0"))
        assertFalse(GoldCalc.isValidDecimal(""))
        assertFalse(GoldCalc.isValidDecimal("12..45"))
        assertFalse(GoldCalc.isValidDecimal("abc"))
    }
}
