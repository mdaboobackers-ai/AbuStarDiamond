package com.goldsmith.billing.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.goldsmith.billing.R
import com.goldsmith.billing.data.model.BillItem
import com.goldsmith.billing.data.model.CompanyProfile
import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.Invoice
import com.goldsmith.billing.data.model.MeltingRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object PdfGenerator {

    private const val PAGE_WIDTH = 595  // A4 width pts
    private const val PAGE_HEIGHT = 842 // A4 height pts
    private const val MARGIN = 40f
    private val GOLD = Color.parseColor("#D4AF37")
    private val DARK = Color.parseColor("#131313")
    private val GRAY = Color.parseColor("#666666")
    private val LIGHT_GRAY = Color.parseColor("#EEEEEE")

    fun generateInvoicePdf(
        context: Context,
        invoice: Invoice,
        customer: Customer,
        billItems: List<BillItem>,
        profile: CompanyProfile?,
        meltingRecords: List<MeltingRecord> = emptyList(),
        currentRate24K: Double = invoice.goldRate24K
    ): Uri? {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        var y = MARGIN

        // ── Header ────────────────────────────────────────────────────────
        val headerBorder = Paint().apply { color = DARK; style = Paint.Style.STROKE; strokeWidth = 1.4f }
        val headerFill = Paint().apply { color = Color.parseColor("#FFFDF6"); style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 92f, headerFill)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 92f, headerBorder)

        val logoBitmap = runCatching {
            if (!profile?.logoUri.isNullOrBlank()) {
                context.contentResolver.openInputStream(Uri.parse(profile!!.logoUri))?.use { BitmapFactory.decodeStream(it) }
            } else {
                BitmapFactory.decodeResource(context.resources, R.drawable.abu_star_logo)
            }
        }.getOrNull() ?: BitmapFactory.decodeResource(context.resources, R.drawable.abu_star_logo)
        logoBitmap?.let { canvas.drawBitmap(it, null, android.graphics.RectF(MARGIN + 12f, y + 16f, MARGIN + 58f, y + 62f), null) }

        val textStart = MARGIN + if (logoBitmap != null) 68f else 14f

        // Company name
        val titlePaint = Paint().apply {
            color = DARK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        drawTextWithin(canvas, profile?.companyName?.uppercase() ?: "ABU STAR DIAMONDS", textStart, y + 26f, 330f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = GRAY; textSize = 9f; isAntiAlias = true
        }
        var headerTextY = y + 42f
        if (profile?.ownerName?.isNotEmpty() == true) {
            drawTextWithin(canvas, "Owner: ${profile.ownerName}", textStart, headerTextY, 310f, subTitlePaint)
            headerTextY += 12f
        }
        val profileAddress = profile?.let { p ->
            listOf(p.address1, p.address2, p.city, p.state, p.pincode).filter { it.isNotEmpty() }.joinToString(", ")
        }.orEmpty()
        if (profileAddress.isNotEmpty()) {
            drawTextWithin(canvas, profileAddress, textStart, headerTextY, 310f, subTitlePaint)
            headerTextY += 12f
        }
        if (profile?.mobileNumber?.isNotEmpty() == true) {
            drawTextWithin(canvas, "Phone: ${profile.mobileNumber}", textStart, headerTextY, 310f, subTitlePaint)
        }

        // Invoice number (right-aligned)
        val invNumPaint = Paint().apply {
            color = GOLD; textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT; isAntiAlias = true
        }
        canvas.drawText("#${invoice.invoiceNumber}", PAGE_WIDTH - MARGIN - 14f, y + 28f, invNumPaint)
        val datePaint = Paint().apply {
            color = GRAY; textSize = 9f
            textAlign = Paint.Align.RIGHT; isAntiAlias = true
        }
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        canvas.drawText(sdf.format(invoice.date), PAGE_WIDTH - MARGIN - 14f, y + 44f, datePaint)

        y += 108f

        // ── Company Address + GST ─────────────────────────────────────────
        val labelPaint = Paint().apply { color = GRAY; textSize = 9f; isAntiAlias = true }
        val valuePaint = Paint().apply {
            color = DARK; textSize = 10f; isAntiAlias = true
        }
        // ── Billed To ─────────────────────────────────────────────────────
        val sectionPaint = Paint().apply {
            color = GOLD; textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f; isAntiAlias = true
        }
        canvas.drawText("BILL TO", MARGIN, y + 10f, sectionPaint)
        y += 24f
        val shopName = invoice.customerShopName.ifEmpty { customer.companyName.ifEmpty { customer.name } }
        val ownerName = invoice.customerOwnerName.ifEmpty { customer.name }
        val address = invoice.customerAddress.ifEmpty { customer.address }
        val phone = invoice.customerPhone.ifEmpty { customer.phone }
        drawTextWithin(canvas, shopName, MARGIN, y, 320f, valuePaint)
        y += 14f
        if (ownerName.isNotEmpty()) { drawTextWithin(canvas, ownerName, MARGIN, y, 320f, labelPaint); y += 13f }
        if (address.isNotEmpty()) { drawTextWithin(canvas, address, MARGIN, y, 320f, labelPaint); y += 13f }
        if (phone.isNotEmpty()) { canvas.drawText("Phone: $phone", MARGIN, y, labelPaint); y += 13f }
        if (customer.gstNumber.isNotEmpty()) {
            canvas.drawText("GSTIN: ${customer.gstNumber}", MARGIN, y, labelPaint); y += 13f
        }

        y += 10f
        drawDivider(canvas, y, GOLD)
        y += 14f

        // ── Bill Items Table ──────────────────────────────────────────────
        canvas.drawText("ITEMS", MARGIN, y, sectionPaint)
        y += 12f

        // Table header
        val headerBg = Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20f, headerBg)

        val colPaint = Paint().apply {
            color = DARK; textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DESCRIPTION", MARGIN + 2f, y + 14f, colPaint)
        drawRight(canvas, "KARAT", 198f, y + 14f, colPaint)
        drawRight(canvas, "GROSS", 238f, y + 14f, colPaint)
        drawRight(canvas, "LESS", 278f, y + 14f, colPaint)
        drawRight(canvas, "NET", 318f, y + 14f, colPaint)
        drawRight(canvas, "EQ.G", 365f, y + 14f, colPaint)
        drawRight(canvas, "MAKING", 418f, y + 14f, colPaint)
        drawRight(canvas, "AMOUNT", PAGE_WIDTH - MARGIN - 2f, y + 14f, colPaint)
        y += 22f

        // Item rows
        val rowPaint = Paint().apply { color = DARK; textSize = 9f; isAntiAlias = true }
        billItems.forEachIndexed { idx, item ->
            if (idx % 2 == 1) {
                val rowBg = Paint().apply { color = Color.parseColor("#FAFAFA"); style = Paint.Style.FILL }
                canvas.drawRect(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y + 16f, rowBg)
            }
            drawTextWithin(canvas, item.description.ifEmpty { "Jewellery Item" }, MARGIN + 2f, y + 11f, 108f, rowPaint)
            drawRight(canvas, item.karatLabel.substringBefore(" "), 198f, y + 11f, rowPaint)
            drawRight(canvas, String.format("%.2f", item.grossWeightGrams), 238f, y + 11f, rowPaint)
            drawRight(canvas, String.format("%.2f", item.lessWeightGrams), 278f, y + 11f, rowPaint)
            drawRight(canvas, String.format("%.2f", item.netWeightGrams), 318f, y + 11f, rowPaint)
            drawRight(canvas, String.format("%.3f", item.gramsWithMaking.takeIf { it > 0.0 } ?: item.fineGoldGrams), 365f, y + 11f, rowPaint)
            drawRight(canvas, String.format("%.2f%%", item.makingChargePercent.takeIf { it > 0.0 } ?: item.makingChargePerGram), 418f, y + 11f, rowPaint)
            val amtPaint = Paint().apply {
                color = DARK; textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
            }
            drawRight(canvas, "₹${String.format("%,.0f", item.itemAmount)}", PAGE_WIDTH - MARGIN - 2f, y + 11f, amtPaint)
            y += 20f
        }

        drawDivider(canvas, y, LIGHT_GRAY)
        y += 12f

        // ── Payment Summary ───────────────────────────────────────────────
        canvas.drawText("PAYMENT SUMMARY", MARGIN, y, sectionPaint)
        y += 14f

        val summaryRight = PAGE_WIDTH - MARGIN
        fun drawSummaryRow(label: String, value: String, bold: Boolean = false, color: Int = DARK) {
            val lPaint = Paint().apply { this.color = GRAY; textSize = 9f; isAntiAlias = true }
            val vPaint = Paint().apply {
                this.color = color; textSize = 10f; isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(label, MARGIN, y, lPaint)
            canvas.drawText(value, summaryRight.toFloat(), y, vPaint)
        }

        drawSummaryRow("Total Weight", "${String.format("%.3f", invoice.totalWeightGrams)}g")
        y += 14f
        drawSummaryRow("Pure Gold (24K)", "${String.format("%.3f", invoice.totalFineGoldGrams)}g")
        y += 14f
        drawSummaryRow("91.6 Gold", "${String.format("%.3f", invoice.total916Grams)}g")
        y += 14f
        drawSummaryRow("Subtotal", "₹${String.format("%,.2f", invoice.subtotal)}")
        y += 14f
        drawSummaryRow("GST (${invoice.gstPercent}%)", "₹${String.format("%,.2f", invoice.gstAmount)}")
        y += 14f
        drawDivider(canvas, y, LIGHT_GRAY)
        y += 8f
        drawSummaryRow("TOTAL", "₹${String.format("%,.2f", invoice.totalAmount)}", bold = true, color = DARK)
        y += 18f
        drawDivider(canvas, y, LIGHT_GRAY)
        y += 8f
        if (invoice.cashPaid > 0) {
            drawSummaryRow("Cash Paid", "₹${String.format("%,.2f", invoice.cashPaid)}", color = Color.parseColor("#2E7D32"))
            y += 14f
        }
        if (invoice.goldPaidGrams > 0) {
            drawSummaryRow("Gold Paid", "${String.format("%.3f", invoice.goldPaidGrams)}g", color = Color.parseColor("#2E7D32"))
            y += 14f
        }
        meltingRecords.forEach { record ->
            val shortage = GoldCalc.roundGrams(record.rawWeightGrams - record.finalPureWeightGrams).coerceAtLeast(0.0)
            drawSummaryRow(
                "Melting / Purity Check",
                "${String.format("%.2f", record.purityPercent)}% -> ${String.format("%.3f", record.finalPureWeightGrams)}g pure",
                color = if (shortage > 0.0) Color.parseColor("#C62828") else GRAY
            )
            y += 14f
            if (shortage > 0.0) {
                drawSummaryRow("Purity Difference", "${String.format("%.3f", shortage)}g pure short", color = Color.parseColor("#C62828"))
                y += 14f
            }
        }
        y += 4f
        drawDivider(canvas, y, GOLD)
        y += 10f
        drawSummaryRow(
            "BALANCE",
            "₹${String.format("%,.2f", GoldCalc.pendingCashAtRate(invoice.remainingBalance, invoice.goldRate24K.coerceAtLeast(1.0), currentRate24K.coerceAtLeast(1.0)))}",
            bold = true,
            color = if (invoice.remainingBalance > 0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        )
        y += 14f
        drawSummaryRow(
            "BALANCE GOLD",
            "${String.format("%.3f", GoldCalc.pendingPureGold(invoice.remainingBalance, invoice.goldRate24K.coerceAtLeast(1.0)).coerceAtLeast(0.0))}g pure",
            bold = true,
            color = if (invoice.remainingBalance > 0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        )
        y += 24f

        // ── Gold rates at time ────────────────────────────────────────────
        val ratePaint = Paint().apply { color = GRAY; textSize = 8f; isAntiAlias = true }
        canvas.drawText(
            "Gold rate at time of billing: 24K ₹${String.format("%.2f", invoice.goldRate24K)}/g",
            MARGIN, y, ratePaint
        )
        y += 24f

        // ── Footer ────────────────────────────────────────────────────────
        drawDivider(canvas, y, GOLD)
        y += 12f
        val footerPaint = Paint().apply { color = GOLD; textSize = 8f; letterSpacing = 0.1f; isAntiAlias = true }
        canvas.drawText("Thank you for your business! | Secured by Abu Star Diamonds", MARGIN, y, footerPaint)

        doc.finishPage(page)

        // Save to file
        return try {
            val pdfDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
            val pdfFile = File(pdfDir, "invoice_${invoice.invoiceNumber}.pdf")
            doc.writeTo(FileOutputStream(pdfFile))
            doc.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            doc.close()
            null
        }
    }

    private fun drawDivider(canvas: Canvas, y: Float, color: Int) {
        val paint = Paint().apply { this.color = color; strokeWidth = 0.8f }
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
    }

    private fun drawRight(canvas: Canvas, text: String, rightX: Float, baseline: Float, paint: Paint) {
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(text, rightX, baseline, paint)
        paint.textAlign = originalAlign
    }

    private fun drawTextWithin(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint) {
        val fitted = paint.breakText(text, true, maxWidth, null).let { count ->
            if (count >= text.length) text else text.take((count - 1).coerceAtLeast(0)).trimEnd() + "…"
        }
        canvas.drawText(fitted, x, baseline, paint)
    }

    fun shareViaWhatsApp(context: Context, pdfUri: Uri, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra("jid", "${phoneNumber.filter { it.isDigit() }}@s.whatsapp.net")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // WhatsApp not installed — fallback to generic share
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Share Invoice"))
        }
    }
}
