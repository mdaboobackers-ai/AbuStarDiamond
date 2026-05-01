package com.goldsmith.billing.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════
 *  ABU STAR DIAMONDS — GOLD CALCULATION ENGINE v2.0
 * ═══════════════════════════════════════════════════════
 *
 *  CORRECT JEWELLERY BILLING FORMULA:
 *
 *  A = Gross Weight (grams)
 *  B = Less Weight  (stone/card deduction grams)
 *  C = Net Weight   = A − B
 *
 *  D = Purity %  (24K=100, 22K=91.6, 20K=85, 18K=75, 14K=58.5)
 *  E = Making %
 *
 *  EqGrams (per item) = C × (D / 100)          ← pure gold equivalent
 *  FineGold (billing) = C × ((D + E) / 100)     ← including making
 *
 *  SUMMARY:
 *  ─────────────────────────────────────────────
 *  Total Net Weight   = ΣC (all items)
 *  Total Pure Gold    = Σ EqGrams              ← "Pure Gold (24K)"
 *  91.6 Equiv Jewel   = Total Pure Gold / 0.916
 *  18K  Equiv Jewel   = Total Pure Gold / 0.75
 *
 *  Cash Value = FineGold × rate24K (per gram) + StoneValue
 *  GST        = Cash Value × gstPct / 100
 *
 *  Remaining Balance in GRAMS = RemainingCash / rate24K
 * ═══════════════════════════════════════════════════════
 */
object GoldCalc {

    // ── Standard purities ───────────────────────────────
    const val PURITY_24K = 100.0
    const val PURITY_22K = 91.6
    const val PURITY_20K = 85.0
    const val PURITY_18K = 75.0
    const val PURITY_14K = 58.5

    val STANDARD_PURITIES = linkedMapOf(
        "24K (100%)"   to PURITY_24K,
        "22K (91.6%)"  to PURITY_22K,
        "20K (85.0%)"  to PURITY_20K,
        "18K (75.0%)"  to PURITY_18K,
        "14K (58.5%)"  to PURITY_14K,
        "Custom"       to 0.0
    )

    // ── Precision: always use BigDecimal for financial calc ──
    private fun Double.bd(scale: Int = 10): BigDecimal =
        BigDecimal(this).setScale(scale, RoundingMode.HALF_UP)

    /** Net weight: C = A − B */
    fun netWeight(gross: Double, less: Double): Double =
        (gross.bd() - less.bd())
            .coerceAtLeast(BigDecimal.ZERO)
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()

    /**
     * Per-item pure gold equivalent (NO making charge):
     *   EqGrams = C × (D / 100)
     * This is what you display as "Pure Gold" / "Eq Grams"
     */
    fun eqGrams(netWeight: Double, purityPct: Double): Double =
        (netWeight.bd() * (purityPct.bd() / BigDecimal(100)))
            .setScale(4, RoundingMode.HALF_UP)
            .toDouble()

    /**
     * Fine gold FOR BILLING (includes making charge):
     *   FineGold = C × ((D + E) / 100)
     * This is what becomes the basis for cash value.
     */
    fun fineGoldGrams(netWeight: Double, purityPct: Double, makingPct: Double): Double =
        (netWeight.bd() * ((purityPct.bd() + makingPct.bd()) / BigDecimal(100)))
            .setScale(4, RoundingMode.HALF_UP)
            .toDouble()

    /** Cash value = fineGold × rate24K + stoneValue */
    fun cashValue(fineGold: Double, rate24K: Double, stoneValue: Double = 0.0): Double =
        ((fineGold.bd() * rate24K.bd()) + stoneValue.bd())
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()

    /**
     * 22K Jewel Equivalent from pure gold:
     *   eq22K = pureGold / 0.916
     */
    fun eq22KJewel(pureGoldGrams: Double): Double =
        if (pureGoldGrams <= 0.0) 0.0
        else (pureGoldGrams.bd() / BigDecimal("0.916"))
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()

    fun eq18KJewel(pureGoldGrams: Double): Double =
        if (pureGoldGrams <= 0.0) 0.0
        else (pureGoldGrams.bd() / BigDecimal("0.75"))
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()

    fun eq20KJewel(pureGoldGrams: Double): Double =
        if (pureGoldGrams <= 0.0) 0.0
        else (pureGoldGrams.bd() / BigDecimal("0.85"))
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()

    /** Convert remaining cash balance → gold grams */
    fun cashToGoldGrams(cashAmount: Double, rate24K: Double): Double {
        if (rate24K <= 0.0 || cashAmount <= 0.0) return 0.0
        return (cashAmount.bd() / rate24K.bd())
            .setScale(3, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** Full per-item result */
    data class ItemResult(
        val netWeight: Double,       // C = A - B
        val eqGrams: Double,         // C × D/100  (pure gold display)
        val fineGoldBilling: Double, // C × (D+E)/100 (billing base)
        val cashValue: Double,       // fineGold × rate
        val cashValueWithGst: Double
    )

    fun calculateItem(
        gross: Double,
        less: Double,
        purityPct: Double,
        makingPct: Double,
        rate24K: Double,
        gstPct: Double = 3.0,
        stoneValue: Double = 0.0
    ): ItemResult {
        val C    = netWeight(gross, less)
        val eq   = eqGrams(C, purityPct)
        val fine = fineGoldGrams(C, purityPct, makingPct)
        val cash = cashValue(fine, rate24K, stoneValue)
        val gst  = (cash.bd() * gstPct.bd() / BigDecimal(100))
                        .setScale(2, RoundingMode.HALF_UP).toDouble()
        return ItemResult(
            netWeight         = C,
            eqGrams           = eq,
            fineGoldBilling   = fine,
            cashValue         = cash,
            cashValueWithGst  = (cash.bd() + gst.bd()).setScale(2, RoundingMode.HALF_UP).toDouble()
        )
    }

    // ── Validation helpers ─────────────────────────────
    fun isValidWeight(s: String): Boolean = s.isNotBlank() && (s.toDoubleOrNull() ?: -1.0) >= 0.0
    fun isValidPercent(s: String): Boolean {
        val v = s.toDoubleOrNull() ?: return false
        return v in 0.0..100.0
    }
    fun isValidPayment(s: String): Boolean = s.isNotBlank() && (s.toDoubleOrNull() ?: -1.0) >= 0.0
    fun isValidPurity(s: String): Boolean {
        val v = s.toDoubleOrNull() ?: return false
        return v > 0.0 && v <= 100.0
    }

    fun karatLabel(purity: Double): String = when {
        purity >= 99.0 -> "24K"
        purity >= 91.0 -> "22K"
        purity >= 84.0 -> "20K"
        purity >= 74.0 -> "18K"
        purity >= 58.0 -> "14K"
        else           -> "Custom"
    }

    // ── Gold rate conversions ──────────────────────────
    fun rate22K(r24: Double) = (r24.bd() * BigDecimal("0.916")).setScale(2, RoundingMode.HALF_UP).toDouble()
    fun rate20K(r24: Double) = (r24.bd() * BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP).toDouble()
    fun rate18K(r24: Double) = (r24.bd() * BigDecimal("0.75")).setScale(2, RoundingMode.HALF_UP).toDouble()
}

// ── Formatting extensions ──────────────────────────────
fun Double.g3(): String = String.format("%.3f", this)  // 3-decimal grams
fun Double.g4(): String = String.format("%.4f", this)  // 4-decimal fine gold
fun Double.inr(): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 2; nf.maximumFractionDigits = 2
    return "₹${nf.format(this)}"
}
fun Double.inrShort(): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 0; nf.maximumFractionDigits = 0
    return "₹${nf.format(this)}"
}
fun Double.formatGrams(decimals: Int = 3): String = String.format("%.${decimals}f", this)
fun Double.formatCurrency() = inr()
fun Double.formatCurrencyShort() = inrShort()
