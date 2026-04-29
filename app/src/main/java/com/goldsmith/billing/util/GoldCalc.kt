package com.goldsmith.billing.util

import java.text.NumberFormat
import java.util.Locale

object GoldCalc {
    fun rate22K(rate24K: Double) = rate24K * 0.916
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

    fun fineGold(netWeight: Double, purityPercent: Double): Double =
        netWeight * (purityPercent / 100.0)

    fun itemAmount(
        netWeight: Double,
        purityPercent: Double,
        rate24K: Double,
        makingPerGram: Double,
        stoneValue: Double = 0.0
    ): Double {
        val goldRate = rate24K * (purityPercent / 100.0)
        return (netWeight * goldRate) + (netWeight * makingPerGram) + stoneValue
    }

    fun equivalent22K(fineGoldGrams: Double): Double = fineGoldGrams / 0.916
    fun equivalent18K(fineGoldGrams: Double): Double = fineGoldGrams / 0.75
}

fun Double.formatGrams(decimals: Int = 3): String = String.format("%.${decimals}f", this)

fun Double.formatCurrency(): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return "₹${nf.format(this)}"
}

fun Double.formatCurrencyShort(): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = 0
    return "₹${nf.format(this)}"
}
