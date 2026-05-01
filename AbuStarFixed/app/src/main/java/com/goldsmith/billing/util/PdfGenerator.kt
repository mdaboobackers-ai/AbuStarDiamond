package com.goldsmith.billing.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.goldsmith.billing.data.model.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * ═══════════════════════════════════════════════════════════════
 *  ABU STAR DIAMONDS — PROFESSIONAL JEWELLERY INVOICE v2.0
 *  Modern, elegant invoice for WhatsApp sharing
 * ═══════════════════════════════════════════════════════════════
 */
object PdfGenerator {

    private const val PAGE_W  = 595   // A4 pt
    private const val PAGE_H  = 842
    private const val M       = 36f   // margin

    // Colour palette
    private val COL_DARK  = Color.parseColor("#0A0A0A")   // near black
    private val COL_GOLD  = Color.parseColor("#C9A84C")   // gold
    private val COL_GOLD2 = Color.parseColor("#F0D080")   // light gold
    private val COL_WHITE = Color.WHITE
    private val COL_GRAY  = Color.parseColor("#6B6B6B")
    private val COL_LGRAY = Color.parseColor("#F4F4F4")
    private val COL_MGRAY = Color.parseColor("#DDDDDD")
    private val COL_GREEN = Color.parseColor("#2E7D32")
    private val COL_RED   = Color.parseColor("#C62828")
    private val COL_CREAM = Color.parseColor("#FFFEF5")

    private fun bold(size: Float)   = Paint().apply { color = COL_DARK; textSize = size; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    private fun regular(size: Float) = Paint().apply { color = COL_DARK; textSize = size; isAntiAlias = true }
    private fun colored(size: Float, col: Int, bold: Boolean = false) = Paint().apply { color = col; textSize = size; if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    private fun rightAlign(p: Paint) = p.also { it.textAlign = Paint.Align.RIGHT }
    private fun centerAlign(p: Paint) = p.also { it.textAlign = Paint.Align.CENTER }
    private fun fill(col: Int) = Paint().apply { color = col; style = Paint.Style.FILL }
    private fun stroke(col: Int, width: Float = 0.8f) = Paint().apply { color = col; style = Paint.Style.STROKE; strokeWidth = width }

    fun generateInvoicePdf(
        context: Context,
        invoice: Invoice,
        customer: Customer?,
        billItems: List<BillItem>,
        profile: CompanyProfile?
    ): Uri? {
        val doc  = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c    = page.canvas
        var y    = 0f

        // ── 1. DARK HEADER BLOCK ─────────────────────────────────────────
        // Dark background
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 105f, fill(COL_DARK))
        // Gold top accent line
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 5f, fill(COL_GOLD))
        // Gold left accent column
        c.drawRect(0f, 5f, 6f, 105f, fill(COL_GOLD))

        // Company name
        c.drawText(
            (profile?.companyName ?: "ABU STAR DIAMONDS").uppercase(),
            52f, 34f, colored(19f, COL_GOLD, bold = true)
        )
        // Tagline / owner
        val ownerLine = buildString {
            if (!profile?.ownerName.isNullOrEmpty()) append(profile!!.ownerName)
            if (!profile?.mobileNumber.isNullOrEmpty()) append("  ·  Ph: ${profile!!.mobileNumber}")
        }
        if (ownerLine.isNotEmpty()) c.drawText(ownerLine, 52f, 52f, colored(9f, COL_GOLD2))

        // Address
        val addrLine = buildString {
            if (!profile?.address1.isNullOrEmpty()) append(profile!!.address1)
            if (!profile?.city.isNullOrEmpty()) append(", ${profile!!.city}")
            if (!profile?.state.isNullOrEmpty()) append(", ${profile!!.state}")
            if (!profile?.pincode.isNullOrEmpty()) append(" – ${profile!!.pincode}")
        }
        if (addrLine.isNotEmpty()) c.drawText(addrLine, 52f, 66f, colored(8.5f, Color.parseColor("#B0B0B0")))
        if (!profile?.gstNumber.isNullOrEmpty()) c.drawText("GSTIN: ${profile!!.gstNumber}", 52f, 79f, colored(8f, Color.parseColor("#909090")))

        // Diamond icon placeholder (right side)
        c.drawText("◈", PAGE_W - M - 30f, 55f, colored(40f, COL_GOLD))

        // Invoice Number + Date (right aligned)
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        c.drawText("#${invoice.invoiceNumber}", PAGE_W - M, 34f, rightAlign(colored(14f, COL_GOLD2, bold = true)))
        c.drawText("${sdf.format(invoice.date)}  ${sdfTime.format(invoice.date)}", PAGE_W - M, 50f, rightAlign(colored(8.5f, Color.parseColor("#A0A0A0"))))

        // Status badge
        val statusCol = when (invoice.paymentStatus) { PaymentStatus.PAID -> COL_GREEN; PaymentStatus.PARTIAL -> COL_GOLD; else -> COL_RED }
        c.drawRoundRect(RectF((PAGE_W - M - 60f), 58f, (PAGE_W - M).toFloat(), 76f), 4f, 4f, fill(statusCol))
        c.drawText(invoice.paymentStatus.name, PAGE_W - M - 30f, 71f, centerAlign(colored(8.5f, COL_WHITE, bold = true)))

        y = 115f

        // ── 2. BILL FROM / BILL TO in two columns ───────────────────────
        // Bill From (shop)
        val col1X = M
        val col2X = PAGE_W / 2f + 10f

        c.drawText("BILL FROM", col1X, y, colored(8f, COL_GOLD, bold = true).also { it.letterSpacing = 0.15f })
        c.drawText("BILLED TO", col2X, y, colored(8f, COL_GOLD, bold = true).also { it.letterSpacing = 0.15f })
        y += 13f

        // Bill From details
        c.drawText(profile?.companyName ?: "Abu Star Diamonds", col1X, y, bold(10.5f))
        if (!profile?.ownerName.isNullOrEmpty()) c.drawText(profile!!.ownerName, col1X, y + 13f, regular(9f))
        if (!profile?.address1.isNullOrEmpty()) c.drawText(profile!!.address1, col1X, y + 24f, colored(8.5f, COL_GRAY))
        if (!profile?.mobileNumber.isNullOrEmpty()) c.drawText("Ph: ${profile!!.mobileNumber}", col1X, y + 35f, colored(8.5f, COL_GRAY))

        // Bill To — FIX: use snapshot OR customer object (whichever has data)
        val billToName    = invoice.customerNameSnapshot.ifEmpty { customer?.name ?: "—" }
        val billToPhone   = invoice.customerPhoneSnapshot.ifEmpty { customer?.phone ?: "" }
        val billToAddress = invoice.customerAddressSnapshot.ifEmpty { customer?.address ?: "" }
        val billToGst     = invoice.customerGstSnapshot.ifEmpty { customer?.gstNumber ?: "" }

        c.drawText(billToName, col2X, y, bold(10.5f))
        if (billToPhone.isNotEmpty()) c.drawText("Ph: $billToPhone", col2X, y + 13f, regular(9f))
        if (billToAddress.isNotEmpty()) {
            val addr = if (billToAddress.length > 40) billToAddress.take(40) + "…" else billToAddress
            c.drawText(addr, col2X, y + 24f, colored(8.5f, COL_GRAY))
        }
        if (billToGst.isNotEmpty()) c.drawText("GSTIN: $billToGst", col2X, y + 35f, colored(8.5f, COL_GRAY))

        y += 50f

        // Gold rate strip
        c.drawRect(M, y, PAGE_W - M, y + 18f, fill(Color.parseColor("#1A1A1A")))
        c.drawText("Gold Rate at Billing: ₹${String.format("%,.2f", invoice.goldRate24K)}/g (24K)  ·  Date: ${sdf.format(invoice.date)}",
            PAGE_W / 2f, y + 13f, centerAlign(colored(8.5f, COL_GOLD2)))
        y += 26f

        // ── 3. ITEMS TABLE ───────────────────────────────────────────────
        c.drawText("ITEMS", M, y + 10f, colored(8f, COL_GOLD, bold = true).also { it.letterSpacing = 0.15f })
        y += 14f

        // Table header
        c.drawRect(M, y, PAGE_W - M, y + 22f, fill(COL_DARK))
        val tw = PAGE_W - 2 * M
        val colDesc    = M + 2f
        val colKarat   = M + tw * 0.24f
        val colGross   = M + tw * 0.35f
        val colLess    = M + tw * 0.44f
        val colNet     = M + tw * 0.53f
        val colEqG     = M + tw * 0.63f
        val colMaking  = M + tw * 0.74f
        val colAmt     = PAGE_W - M - 2f

        val hdrP = colored(7.5f, COL_GOLD2, bold = true)
        val hdrs = listOf("DESCRIPTION" to colDesc, "KARAT" to colKarat, "GROSS" to colGross,
            "LESS" to colLess, "NET" to colNet, "EQ.g" to colEqG, "MAKING" to colMaking)
        hdrs.forEach { (h, x) -> c.drawText(h, x, y + 15f, hdrP) }
        c.drawText("AMOUNT", colAmt, y + 15f, rightAlign(hdrP))
        y += 24f

        // Item rows
        billItems.forEachIndexed { idx, item ->
            val rowH = 20f
            if (idx % 2 == 1) c.drawRect(M, y - 2f, PAGE_W - M, y + rowH, fill(COL_LGRAY))
            val rowP = regular(8.5f)
            val desc = item.description.take(20).ifEmpty { "Item ${idx + 1}" }
            c.drawText(desc,                                     colDesc, y + 14f, rowP)
            c.drawText(item.karatLabel,                          colKarat, y + 14f, rowP)
            c.drawText(String.format("%.3f", item.grossWeightGrams), colGross, y + 14f, rowP)
            c.drawText(String.format("%.3f", item.lessWeightGrams),  colLess,  y + 14f, rowP)
            c.drawText(String.format("%.3f", item.netWeightGrams),   colNet,   y + 14f, colored(8.5f, COL_DARK, bold = true))
            c.drawText(String.format("%.4f", item.eqGrams),          colEqG,   y + 14f, colored(8.5f, COL_GOLD, bold = true))
            c.drawText("${String.format("%.1f", item.makingChargePercent)}%", colMaking, y + 14f, rowP)
            c.drawText("₹${String.format("%,.0f", item.itemCashValue)}", colAmt, y + 14f, rightAlign(bold(8.5f)))
            y += rowH
        }

        // Divider
        c.drawRect(M, y, PAGE_W - M, y + 1.5f, fill(COL_GOLD))
        y += 10f

        // ── 4. GOLD SUMMARY (cream background) ──────────────────────────
        c.drawRect(M, y, PAGE_W - M, y + 54f, fill(COL_CREAM))
        c.drawRect(M, y, M + 4f, y + 54f, fill(COL_GOLD))
        c.drawRect(M, y, PAGE_W - M, y + 1f, fill(COL_GOLD))
        c.drawRect(M, y + 53f, PAGE_W - M, y + 54f, fill(COL_GOLD))

        c.drawText("GOLD SUMMARY (GRAMS)", M + 10f, y + 14f, colored(8f, COL_GOLD, bold = true).also { it.letterSpacing = 0.12f })
        val gsCols = listOf(
            "Total Net Weight" to "${String.format("%.3f", invoice.totalNetWeightGrams)} g",
            "Pure Gold (24K)"  to "${String.format("%.4f", invoice.totalPureGoldGrams)} g",
            "22K Jewel (÷0.916)" to "${String.format("%.3f", invoice.totalEq22KGrams)} g",
            "18K Jewel (÷0.75)"  to "${String.format("%.3f", invoice.totalEq18KGrams)} g"
        )
        val gsColW = (PAGE_W - 2 * M - 10f) / 4f
        gsCols.forEachIndexed { i, (lbl, v) ->
            val gx = M + 10f + i * gsColW
            c.drawText(lbl, gx, y + 30f, colored(7.5f, COL_GRAY))
            c.drawText(v,   gx, y + 44f, colored(10.5f, if (i == 1) COL_GOLD else COL_DARK, bold = i == 1))
        }
        y += 62f

        // ── 5. PAYMENT SUMMARY ───────────────────────────────────────────
        // Two-column layout: left = summary, right = payment details
        val sumRight  = PAGE_W / 2f - 5f
        val payLeft   = PAGE_W / 2f + 5f

        c.drawText("AMOUNT SUMMARY", M, y + 12f, colored(8f, COL_GOLD, bold = true).also { it.letterSpacing = 0.12f })
        c.drawText("PAYMENT DETAILS", payLeft, y + 12f, colored(8f, COL_GOLD, bold = true).also { it.letterSpacing = 0.12f })
        y += 18f

        fun pSumRow(lbl: String, v: String, yOff: Float, bold: Boolean = false, col: Int = COL_DARK) {
            c.drawText(lbl, M, yOff, colored(8.5f, COL_GRAY)); c.drawText(v, sumRight, yOff, rightAlign(colored(9f, col, bold)))
        }
        fun pPayRow(lbl: String, v: String, yOff: Float, col: Int = COL_DARK) {
            c.drawText(lbl, payLeft, yOff, colored(8.5f, COL_GRAY)); c.drawText(v, PAGE_W - M, yOff, rightAlign(colored(9f, col, bold = false)))
        }

        pSumRow("Sub Total (Cash)", "₹${String.format("%,.2f", invoice.subtotalCash)}", y)
        pSumRow("GST (${invoice.gstPercent}%)", "₹${String.format("%,.2f", invoice.gstAmount)}", y + 14f)
        c.drawLine(M, y + 18f, sumRight, y + 18f, stroke(COL_MGRAY))
        pSumRow("TOTAL AMOUNT", "₹${String.format("%,.2f", invoice.totalAmount)}", y + 28f, bold = true, col = COL_DARK)

        // Payment details
        pPayRow("Cash Paid", "₹${String.format("%,.2f", invoice.cashPaid)}", y, col = COL_GREEN)
        var pOff = 14f
        if (invoice.totalGoldPaidCash > 0) {
            pPayRow("Gold Paid (value)", "₹${String.format("%,.2f", invoice.totalGoldPaidCash)}", y + pOff, col = COL_GREEN)
            pOff += 14f
        }
        c.drawLine(payLeft, y + pOff + 4f, PAGE_W - M, y + pOff + 4f, stroke(COL_MGRAY))

        y += 38f

        // Balance row (prominent)
        val balColor = if (invoice.remainingCash > 0.01) COL_RED else COL_GREEN
        c.drawRect(M, y, PAGE_W - M, y + 36f, fill(if (invoice.remainingCash > 0.01) Color.parseColor("#FFF3F3") else Color.parseColor("#F3FFF3")))
        c.drawRect(M, y, M + 3f, y + 36f, fill(balColor))
        c.drawText("BALANCE DUE:", M + 8f, y + 14f, colored(9f, COL_GRAY))
        c.drawText("₹${String.format("%,.2f", invoice.remainingCash)}", PAGE_W / 2f, y + 22f, centerAlign(colored(16f, balColor, bold = true)))
        if (invoice.remainingCash > 0.01 && invoice.goldRate24K > 0) {
            c.drawText("OR  ${String.format("%.3f", invoice.remainingGoldGrams)} g (pure gold)",
                PAGE_W - M, y + 28f, rightAlign(colored(8.5f, balColor)))
        }
        y += 44f

        // ── 6. FOOTER ───────────────────────────────────────────────────
        if (y < PAGE_H - 60f) {
            y = (PAGE_H - 55f)
        }
        c.drawRect(0f, y, PAGE_W.toFloat(), y + 1f, fill(COL_GOLD))
        c.drawRect(0f, y + 1f, PAGE_W.toFloat(), PAGE_H.toFloat(), fill(COL_DARK))
        c.drawText("Thank you for your business!", PAGE_W / 2f, y + 18f, centerAlign(colored(10f, COL_GOLD2)))
        c.drawText("${profile?.companyName ?: "Abu Star Diamonds"}  ·  Trust · Purity · Elegance",
            PAGE_W / 2f, y + 34f, centerAlign(colored(8f, Color.parseColor("#808080"))))
        c.drawRect(0f, PAGE_H - 4f, PAGE_W.toFloat(), PAGE_H.toFloat(), fill(COL_GOLD))

        doc.finishPage(page)

        return try {
            val dir  = File(context.filesDir, "pdfs").also { it.mkdirs() }
            val file = File(dir, "invoice_${invoice.invoiceNumber.replace("/", "-")}.pdf")
            doc.writeTo(FileOutputStream(file))
            doc.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            doc.close(); null
        }
    }

    fun shareViaWhatsApp(context: Context, pdfUri: Uri, phone: String = "") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            if (phone.isNotEmpty()) putExtra("jid", "${phone.filter { it.isDigit() }}@s.whatsapp.net")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(intent) }
        catch (_: Exception) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share Invoice"))
        }
    }

    fun printViaBluetooth(context: Context, pdfUri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) { shareViaWhatsApp(context, pdfUri) }
    }
}
