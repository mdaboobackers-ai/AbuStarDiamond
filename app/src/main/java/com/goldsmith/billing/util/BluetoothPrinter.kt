package com.goldsmith.billing.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.goldsmith.billing.data.model.BillItem
import com.goldsmith.billing.data.model.CompanyProfile
import com.goldsmith.billing.data.model.Invoice
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object BluetoothPrinter {

    private val PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    fun printInvoice(
        context: Context,
        device: BluetoothDevice,
        invoice: Invoice,
        billItems: List<BillItem>,
        profile: CompanyProfile?
    ): Boolean {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        return try {
            socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
            socket.connect()
            outputStream = socket.outputStream

            val esc = ESCPOS(outputStream)

            esc.initialize()

            // Header
            esc.setAlignCenter()
            esc.setBold(true)
            esc.setTextSize(1, 1)
            esc.printLine(center(profile?.companyName?.ifEmpty { null } ?: "ABU STAR DIAMONDS"))
            esc.setBold(false)
            esc.setTextSize(0, 0)
            if (profile?.ownerName?.isNotEmpty() == true) esc.printLine(center(profile.ownerName))
            if (profile?.mobileNumber?.isNotEmpty() == true) esc.printLine(center("Ph: ${profile.mobileNumber}"))
            esc.printLine("--------------------------------")

            // Invoice Info
            esc.setAlignLeft()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            esc.printLine(fitLine(invoice.invoiceNumber, dateFormat.format(invoice.date)))
            esc.printLine(fitLine("", timeFormat.format(invoice.date)))
            val billTo = invoice.customerShopName.ifEmpty { invoice.customerOwnerName }
            if (billTo.isNotEmpty()) esc.printLine("To: ${clip(billTo, 28)}")
            if (invoice.customerPhone.isNotEmpty()) esc.printLine("Ph: ${clip(invoice.customerPhone, 28)}")
            esc.printLine("--------------------------------")

            // Items
            esc.printLine("ITEM        NET     EQ.G   AMT")
            esc.printLine("--------------------------------")
            billItems.forEach { item ->
                val desc = clip(item.description.ifEmpty { "Item" }, 10).padEnd(10)
                val net = String.format(Locale.US, "%.3f", item.netWeightGrams).padStart(7)
                val eq = String.format(Locale.US, "%.3f", item.fineGoldGrams).padStart(7)
                val amt = String.format(Locale.US, "%.0f", item.itemAmount).padStart(6)
                esc.printLine("$desc$net$eq$amt")
                esc.printLine(" ${clip(item.karatLabel, 5)} Make:${String.format(Locale.US, "%.2f", item.makingChargePercent)}%")
            }
            esc.printLine("--------------------------------")

            // Totals
            esc.setAlignLeft()
            esc.printLine(fitLine("Total Eq.g", String.format(Locale.US, "%.3f", invoice.totalFineGoldGrams)))
            esc.printLine(fitLine("916 Eq.g", String.format(Locale.US, "%.3f", invoice.total916Grams)))
            esc.printLine(fitLine("Subtotal", "Rs ${String.format(Locale.US, "%.2f", invoice.subtotal)}"))
            esc.printLine(fitLine("GST ${String.format(Locale.US, "%.2f", invoice.gstPercent)}%", "Rs ${String.format(Locale.US, "%.2f", invoice.gstAmount)}"))
            if (kotlin.math.abs(invoice.previousBalanceAdjusted) > 0.005) {
                esc.printLine(fitLine(if (invoice.previousBalanceAdjusted >= 0.0) "Prev Bal" else "Prev Credit", "Rs ${String.format(Locale.US, "%.2f", kotlin.math.abs(invoice.previousBalanceAdjusted))}"))
            }
            esc.setBold(true)
            esc.printLine(fitLine("TOTAL", "Rs ${String.format(Locale.US, "%.2f", invoice.totalAmount)}"))
            esc.setBold(false)
            if (invoice.cashPaid > 0.0) esc.printLine(fitLine("Cash Paid", "Rs ${String.format(Locale.US, "%.2f", invoice.cashPaid)}"))
            if (invoice.goldPaidGrams > 0.0) esc.printLine(fitLine("Gold Paid", "${String.format(Locale.US, "%.3f", invoice.goldPaidGrams)}g"))
            if (kotlin.math.abs(invoice.remainingBalance) > 0.005) {
                esc.printLine(fitLine(if (invoice.remainingBalance >= 0.0) "Balance" else "Credit", "Rs ${String.format(Locale.US, "%.2f", kotlin.math.abs(invoice.remainingBalance))}"))
            }
            esc.printLine("--------------------------------")

            esc.setAlignCenter()
            esc.printLine("Thank you for your business!")
            esc.printLine("Secured by Abu Star")
            esc.printLine("\n\n\n") // Feed paper
            esc.flush()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            outputStream?.close()
            socket?.close()
        }
    }

    private fun clip(value: String, max: Int): String =
        if (value.length <= max) value else value.take(max)

    private fun center(value: String, width: Int = 32): String {
        val clipped = clip(value, width)
        val left = ((width - clipped.length) / 2).coerceAtLeast(0)
        return " ".repeat(left) + clipped
    }

    private fun fitLine(left: String, right: String, width: Int = 32): String {
        val cleanLeft = clip(left, width)
        val cleanRight = clip(right, width)
        val gap = (width - cleanLeft.length - cleanRight.length).coerceAtLeast(1)
        return cleanLeft + " ".repeat(gap) + cleanRight
    }

    private class ESCPOS(private val outputStream: OutputStream) {
        fun printLine(text: String) {
            outputStream.write((text + "\n").toByteArray(charset("GBK")))
        }

        fun initialize() { outputStream.write(byteArrayOf(0x1B, 0x40)) }

        fun setAlignCenter() { outputStream.write(byteArrayOf(0x1B, 0x61, 0x01)) }
        fun setAlignLeft() { outputStream.write(byteArrayOf(0x1B, 0x61, 0x00)) }
        fun setAlignRight() { outputStream.write(byteArrayOf(0x1B, 0x61, 0x02)) }

        fun setBold(enabled: Boolean) {
            outputStream.write(byteArrayOf(0x1B, 0x45, if (enabled) 0x01 else 0x00))
        }

        fun setTextSize(width: Int, height: Int) {
            val size = ((width and 0x07) shl 4) or (height and 0x07)
            outputStream.write(byteArrayOf(0x1D, 0x21, size.toByte()))
        }

        fun flush() { outputStream.flush() }
    }
}
