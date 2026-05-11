package com.goldsmith.billing.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object GoldCalc {
    fun rate22K(rate24K: Double) = rate24K * 0.916
    fun rate20K(rate24K: Double) = rate24K * (20.0 / 24.0)
    fun rate18K(rate24K: Double) = rate24K * 0.75
    fun rate14K(rate24K: Double) = rate24K * 0.585

    fun purityFromKarat(karat: Int): Double = when (karat) {
        24 -> 100.0
        22 -> 91.6
        18 -> 75.0
        14 -> 58.5
        else -> 91.6
    }

    fun karatFromPurity(purity: Double): String = when {
        purity >= 99.0 -> "24K"
        purity >= 91.0 -> "22K"
        purity >= 74.0 -> "18K"
        purity >= 58.0 -> "14K"
        else -> "Custom"
    }

    fun netWeight(grossWeight: Double, lessWeight: Double): Double =
        roundGrams((grossWeight - lessWeight).coerceAtLeast(0.0))

    fun fineGold(netWeight: Double, purityPercent: Double): Double =
        roundGrams(netWeight * (purityPercent / 100.0))

    fun gramsWithMaking(
        netWeight: Double,
        purityPercent: Double,
        makingPercent: Double
    ): Double = roundGrams(netWeight * ((purityPercent + makingPercent) / 100.0))

    fun stoneEquivalentGrams(stoneValue: Double, rate24K: Double): Double =
        if (rate24K > 0.0) roundGrams(stoneValue.coerceAtLeast(0.0) / rate24K) else 0.0

    fun equivalentGramsWithStone(
        netWeight: Double,
        purityPercent: Double,
        makingPercent: Double,
        stoneValue: Double,
        rate24K: Double
    ): Double = roundGrams(gramsWithMaking(netWeight, purityPercent, makingPercent) + stoneEquivalentGrams(stoneValue, rate24K))

    fun subtotalEquivalentGrams(values: Iterable<Double>): Double =
        roundGrams(values.sum())

    fun itemAmount(
        netWeight: Double,
        purityPercent: Double,
        rate24K: Double,
        makingPercent: Double,
        stoneValue: Double = 0.0
    ): Double {
        return roundMoney((gramsWithMaking(netWeight, purityPercent, makingPercent) * rate24K) + stoneValue)
    }

    fun equivalent916(fineGoldGrams: Double): Double = roundGrams(fineGoldGrams / 0.916)
    fun equivalent22K(fineGoldGrams: Double): Double = equivalent916(fineGoldGrams)
    fun equivalent18K(fineGoldGrams: Double): Double = fineGoldGrams / 0.75

    fun pureGoldFromKarat(goldGrams: Double, karat: Int): Double =
        roundGrams(goldGrams * (karat.coerceIn(1, 24) / 24.0))

    fun goldPaymentValue(goldGrams: Double, karat: Int, rate24K: Double): Double =
        roundMoney(pureGoldFromKarat(goldGrams, karat) * rate24K)

    fun pureGoldFromCash(cashAmount: Double, rate24K: Double): Double =
        if (rate24K > 0.0) cashAmount / rate24K else 0.0

    fun pendingPureGold(invoiceRemainingBalance: Double, invoiceRate24K: Double): Double =
        if (invoiceRate24K > 0.0) invoiceRemainingBalance / invoiceRate24K else 0.0

    fun pendingCashAtRate(invoiceRemainingBalance: Double, invoiceRate24K: Double, currentRate24K: Double): Double =
        roundMoney(pendingPureGold(invoiceRemainingBalance, invoiceRate24K).coerceAtLeast(0.0) * currentRate24K)

    fun balanceCashAtRate(invoiceRemainingBalance: Double, invoiceRate24K: Double, currentRate24K: Double): Double =
        roundMoney(pendingPureGold(invoiceRemainingBalance, invoiceRate24K) * currentRate24K)

    fun balancePureGold(invoiceRemainingBalance: Double, invoiceRate24K: Double): Double =
        roundGrams(pendingPureGold(invoiceRemainingBalance, invoiceRate24K))

    fun payableWithPreviousBalance(currentBillTotal: Double, previousBalance: Double): Double =
        roundMoney(currentBillTotal + previousBalance)

    fun remainingAfterSettlement(payableTotal: Double, cashPaid: Double, goldValue: Double): Double =
        roundMoney(payableTotal - cashPaid - goldValue)

    fun invoiceBalanceAfterPaymentAtCurrentRate(
        invoiceRemainingBalance: Double,
        invoiceRate24K: Double,
        currentRate24K: Double,
        cashPaid: Double,
        goldGrams: Double,
        goldKarat: Int
    ): Double {
        val pendingPure = pendingPureGold(invoiceRemainingBalance, invoiceRate24K)
        val paidPure = pureGoldFromCash(cashPaid, currentRate24K) + pureGoldFromKarat(goldGrams, goldKarat)
        return roundMoney((pendingPure - paidPure) * invoiceRate24K)
    }

    fun invoiceBalanceAfterReversingPaymentAtCurrentRate(
        invoiceRemainingBalance: Double,
        invoiceRate24K: Double,
        currentRate24K: Double,
        cashPaid: Double,
        goldGrams: Double,
        goldKarat: Int
    ): Double {
        val pendingPure = pendingPureGold(invoiceRemainingBalance, invoiceRate24K)
        val reversedPure = pureGoldFromCash(cashPaid, currentRate24K) + pureGoldFromKarat(goldGrams, goldKarat)
        return roundMoney((pendingPure + reversedPure) * invoiceRate24K)
    }

    fun remainingBalance(
        totalAmount: Double,
        cashPaid: Double,
        goldPayments: List<Pair<Double, Int>>,
        rate24K: Double
    ): GoldBalance {
        val goldValue = goldPayments.sumOf { (grams, karat) -> goldPaymentValue(grams, karat, rate24K) }
        val remainingCash = roundMoney(totalAmount - cashPaid - goldValue)
        val pureGold = if (rate24K > 0) roundGrams(remainingCash.coerceAtLeast(0.0) / rate24K) else 0.0
        return GoldBalance(cash = remainingCash, pureGoldGrams = pureGold)
    }

    fun isValidDecimal(value: String, allowZero: Boolean = true): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val parsed = trimmed.toDoubleOrNull() ?: return false
        return if (allowZero) parsed >= 0.0 else parsed > 0.0
    }

    fun isValidOptionalDecimal(value: String, allowZero: Boolean = true): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true
        val parsed = trimmed.toDoubleOrNull() ?: return false
        return if (allowZero) parsed >= 0.0 else parsed > 0.0
    }

    fun decimalOrZero(value: String): Double =
        value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0

    fun cashOrZero(value: String): Double =
        roundMoney(decimalOrZero(value))

    fun roundGrams(value: Double): Double =
        BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toDouble()

    fun roundMoney(value: Double): Double =
        BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toDouble()
}

data class GoldBalance(
    val cash: Double,
    val pureGoldGrams: Double
)

fun Double.formatGrams(decimals: Int = 3): String = String.format("%.${decimals}f", this)

fun Double.formatCurrency(): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = 0
    return "₹${nf.format(this)}"
}

fun Double.formatCurrencyShort(): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = 0
    return "₹${nf.format(this)}"
}
