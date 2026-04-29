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

            // Header
            esc.setAlignCenter()
            esc.setBold(true)
            esc.setTextSize(1, 1)
            esc.printLine(profile?.companyName ?: "ABU STAR DIAMONDS")
            esc.setBold(false)
            esc.setTextSize(0, 0)
            if (profile?.ownerName?.isNotEmpty() == true) esc.printLine(profile.ownerName)
            if (profile?.mobileNumber?.isNotEmpty() == true) esc.printLine("Ph: ${profile.mobileNumber}")
            esc.printLine("--------------------------------")

            // Invoice Info
            esc.setAlignLeft()
            esc.printLine("Invoice: #${invoice.invoiceNumber}")
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            esc.printLine("Date: ${sdf.format(invoice.date)}")
            esc.printLine("--------------------------------")

            // Items
            esc.printLine("Item          Qty   Karat   Amt")
            esc.printLine("--------------------------------")
            billItems.forEach { item ->
                val desc = item.description.take(12).padEnd(12)
                val wt = String.format("%.2f", item.grossWeightGrams).padStart(6)
                val kt = item.karatLabel.take(5).padStart(5)
                val amt = String.format("%.0f", item.itemAmount).padStart(7)
                esc.printLine("$desc $wt $kt $amt")
            }
            esc.printLine("--------------------------------")

            // Totals
            esc.setAlignRight()
            esc.printLine("Subtotal: ${String.format("%.2f", invoice.subtotal)}")
            esc.printLine("GST (${invoice.gstPercent}%): ${String.format("%.2f", invoice.gstAmount)}")
            esc.setBold(true)
            esc.printLine("TOTAL: RS ${String.format("%.2f", invoice.totalAmount)}")
            esc.setBold(false)
            esc.printLine("--------------------------------")

            esc.setAlignCenter()
            esc.printLine("Thank you for your business!")
            esc.printLine("Secured by Abu Star")
            esc.printLine("\n\n\n") // Feed paper

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            outputStream?.close()
            socket?.close()
        }
    }

    private class ESCPOS(private val outputStream: OutputStream) {
        fun printLine(text: String) {
            outputStream.write((text + "\n").toByteArray(charset("GBK")))
        }

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
    }
}
