package com.goldsmith.billing.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.goldsmith.billing.data.model.BillItem
import com.goldsmith.billing.data.model.CompanyProfile
import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.data.model.Invoice
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
        profile: CompanyProfile?
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

        // Company name
        val titlePaint = Paint().apply {
            color = DARK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(
            profile?.companyName?.uppercase() ?: "ABU STAR DIAMONDS",
            MARGIN + 14f, y + 26f, titlePaint
        )

        val subTitlePaint = Paint().apply {
            color = GRAY; textSize = 9f; isAntiAlias = true
        }
        var headerTextY = y + 42f
        if (profile?.ownerName?.isNotEmpty() == true) {
            canvas.drawText("Owner: ${profile.ownerName}", MARGIN + 14f, headerTextY, subTitlePaint)
            headerTextY += 12f
        }
        val profileAddress = profile?.let { p ->
            listOf(p.address1, p.address2, p.city, p.state, p.pincode).filter { it.isNotEmpty() }.joinToString(", ")
        }.orEmpty()
        if (profileAddress.isNotEmpty()) {
            canvas.drawText(profileAddress.take(72), MARGIN + 14f, headerTextY, subTitlePaint)
            headerTextY += 12f
        }
        if (profile?.mobileNumber?.isNotEmpty() == true) {
            canvas.drawText("Phone: ${profile.mobileNumber}", MARGIN + 14f, headerTextY, subTitlePaint)
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
        y += 16f
        val shopName = invoice.customerShopName.ifEmpty { customer.companyName.ifEmpty { customer.name } }
        val ownerName = invoice.customerOwnerName.ifEmpty { customer.name }
        val address = invoice.customerAddress.ifEmpty { customer.address }
        val phone = invoice.customerPhone.ifEmpty { customer.phone }
        canvas.drawText(shopName, MARGIN, y, valuePaint)
        y += 14f
        if (ownerName.isNotEmpty()) { canvas.drawText(ownerName, MARGIN, y, labelPaint); y += 13f }
        if (address.isNotEmpty()) { canvas.drawText(address.take(72), MARGIN, y, labelPaint); y += 13f }
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
        val cols = listOf("DESCRIPTION", "KARAT", "GROSS", "LESS", "NET", "EQ.G", "MAKING", "AMOUNT")
        val colX = listOf(MARGIN + 2f, 160f, 202f, 242f, 282f, 322f, 365f, 430f)
        cols.forEachIndexed { i, col -> canvas.drawText(col, colX[i], y + 14f, colPaint) }
        y += 22f

        // Item rows
        val rowPaint = Paint().apply { color = DARK; textSize = 9f; isAntiAlias = true }
        billItems.forEachIndexed { idx, item ->
            if (idx % 2 == 1) {
                val rowBg = Paint().apply { color = Color.parseColor("#FAFAFA"); style = Paint.Style.FILL }
                canvas.drawRect(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y + 16f, rowBg)
            }
            canvas.drawText(item.description.take(22), colX[0], y + 11f, rowPaint)
            canvas.drawText(item.karatLabel, colX[1], y + 11f, rowPaint)
            canvas.drawText(String.format("%.2f", item.grossWeightGrams), colX[2], y + 11f, rowPaint)
            canvas.drawText(String.format("%.2f", item.lessWeightGrams), colX[3], y + 11f, rowPaint)
            canvas.drawText(String.format("%.2f", item.netWeightGrams), colX[4], y + 11f, rowPaint)
            canvas.drawText(String.format("%.3f", item.fineGoldGrams), colX[5], y + 11f, rowPaint)
            canvas.drawText(String.format("%.0f", item.makingChargePerGram), colX[6], y + 11f, rowPaint)
            val amtPaint = Paint().apply {
                color = DARK; textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
            }
            canvas.drawText("₹${String.format("%,.0f", item.itemAmount)}", colX[7], y + 11f, amtPaint)
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
        y += 4f
        drawDivider(canvas, y, GOLD)
        y += 10f
        drawSummaryRow(
            "BALANCE",
            "₹${String.format("%,.2f", invoice.remainingBalance)}",
            bold = true,
            color = if (invoice.remainingBalance > 0) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
        )
        y += 14f
        drawSummaryRow(
            "BALANCE GOLD",
            "${String.format("%.3f", (invoice.remainingBalance.coerceAtLeast(0.0) / invoice.goldRate24K.coerceAtLeast(1.0)))}g pure",
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
