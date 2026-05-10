package com.goldsmith.billing.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ImportedCustomerRow(
    val name: String,
    val phone: String,
    val companyName: String = "",
    val address: String = "",
    val gstNumber: String = "",
    val email: String = "",
    val dob: Date? = null,
    val anniversary: Date? = null
)

data class ImportedBillItemRow(
    val description: String = "",
    val grossWeight: String = "",
    val lessWeight: String = "0",
    val purityPercent: String = "91.6",
    val karatLabel: String = "22K (91.6%)",
    val makingPercent: String = "0",
    val stoneValue: String = "0"
)

data class ImportPreview<T>(
    val rows: List<T>,
    val skipped: Int
)

object SpreadsheetImportUtil {
    fun customerTemplateRows(): List<List<String>> = listOf(
        listOf(
            "name",
            "phone",
            "shop_name",
            "address_line_1",
            "address_line_2",
            "city",
            "state",
            "pincode",
            "gst",
            "email",
            "dob",
            "anniversary"
        )
    )

    fun billingTemplateRows(): List<List<String>> = listOf(
        listOf("invoice_no", "customer", "phone", "shop", "address", "description", "gross", "less", "purity", "karat", "making", "stone", "cash_paid")
    )

    fun buildXlsx(rows: List<List<String>>, sheetName: String = "Import"): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putText("[Content_Types].xml", contentTypesXml())
            zip.putText("_rels/.rels", relsXml())
            zip.putText("xl/workbook.xml", workbookXml(sheetName))
            zip.putText("xl/_rels/workbook.xml.rels", workbookRelsXml())
            zip.putText("xl/styles.xml", stylesXml())
            zip.putText("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
        return output.toByteArray()
    }

    suspend fun readRowsFromUri(context: Context, uri: Uri): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext emptyList()
        val name = uri.lastPathSegment.orEmpty().lowercase()
        if (name.endsWith(".xlsx") || looksLikeXlsx(bytes)) parseXlsx(bytes) else parseCsv(String(bytes))
    }

    suspend fun readRowsFromGoogleSheet(sheetUrl: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val exportUrl = toCsvExportUrl(sheetUrl)
        val connection = (URL(exportUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
        }
        connection.inputStream.use { parseCsv(it.bufferedReader().readText()) }
    }

    fun parseCustomers(rows: List<Map<String, String>>): ImportPreview<ImportedCustomerRow> {
        var skipped = 0
        val parsed = rows.mapNotNull { row ->
            val name = row.pick("name", "customername", "ownername", "fullname")
            val phone = row.pick("phone", "phonenumber", "mobile", "mobilenumber", "contact")
            if (name.isBlank() || phone.isBlank()) {
                skipped++
                null
            } else {
                ImportedCustomerRow(
                    name = name,
                    phone = phone,
                    companyName = row.pick("shopname", "companyname", "company", "businessname"),
                    address = customerAddress(row),
                    gstNumber = row.pick("gst", "gstnumber", "gstin"),
                    email = row.pick("email", "mail"),
                    dob = parseDate(row.pick("dob", "dateofbirth", "birthdate")),
                    anniversary = parseDate(row.pick("anniversary", "anniversarydate"))
                )
            }
        }
        return ImportPreview(parsed, skipped)
    }

    fun parseBillItems(rows: List<Map<String, String>>): ImportPreview<ImportedBillItemRow> {
        var skipped = 0
        val parsed = rows.mapNotNull { row ->
            val gross = row.pick("gross", "grossweight", "grosswt", "weight")
            if (gross.toDoubleOrNull() == null) {
                skipped++
                null
            } else {
                val purity = row.pick("purity", "puritypercent", "eqpurity").ifBlank {
                    purityFromKaratLabel(row.pick("karat", "carat", "k"))
                }
                ImportedBillItemRow(
                    description = row.pick("description", "item", "itemname", "jewellery", "particulars"),
                    grossWeight = gross,
                    lessWeight = row.pick("less", "lessweight", "lesswt").ifBlank { "0" },
                    purityPercent = purity.ifBlank { "91.6" },
                    karatLabel = labelFromPurity(purity.toDoubleOrNull() ?: 91.6),
                    makingPercent = row.pick("making", "makingpercent", "makingpercentage", "makingcharge").ifBlank { "0" },
                    stoneValue = row.pick("stone", "stonevalue", "stoners", "stoneamount").ifBlank { "0" }
                )
            }
        }
        return ImportPreview(parsed, skipped)
    }

    private fun parseCsv(text: String): List<Map<String, String>> {
        val rows = text.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .toList()
        return rowsToMaps(rows)
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    values += current.toString().trim()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        values += current.toString().trim()
        return values
    }

    private fun parseXlsx(bytes: ByteArray): List<Map<String, String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val firstSheet = entries.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") } ?: return emptyList()
        val rows = parseSheet(entries[firstSheet] ?: return emptyList(), sharedStrings)
        return rowsToMaps(rows)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = newDocument(bytes)
        val items = doc.getElementsByTagName("si")
        return (0 until items.length).map { idx ->
            val element = items.item(idx) as Element
            val textNodes = element.getElementsByTagName("t")
            buildString {
                for (i in 0 until textNodes.length) append(textNodes.item(i).textContent)
            }
        }
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val doc = newDocument(bytes)
        val rowNodes = doc.getElementsByTagName("row")
        return (0 until rowNodes.length).map { rowIndex ->
            val row = rowNodes.item(rowIndex) as Element
            val cells = row.getElementsByTagName("c")
            val values = mutableMapOf<Int, String>()
            for (i in 0 until cells.length) {
                val cell = cells.item(i) as Element
                val ref = cell.getAttribute("r")
                val col = columnIndex(ref.takeWhile { it.isLetter() })
                val type = cell.getAttribute("t")
                val value = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                values[col] = when (type) {
                    "s" -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> cell.getElementsByTagName("t").item(0)?.textContent.orEmpty()
                    else -> value
                }
            }
            (0..(values.keys.maxOrNull() ?: -1)).map { values[it].orEmpty().trim() }
        }
    }

    private fun rowsToMaps(rows: List<List<String>>): List<Map<String, String>> {
        val header = rows.firstOrNull()?.map(::normalizeHeader).orEmpty()
        if (header.isEmpty()) return emptyList()
        return rows.drop(1).map { row ->
            header.mapIndexedNotNull { index, key ->
                if (key.isBlank()) null else key to row.getOrElse(index) { "" }
            }.toMap()
        }.filter { row -> row.values.any { it.isNotBlank() } }
    }

    private fun newDocument(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun Map<String, String>.pick(vararg keys: String): String =
        keys.firstNotNullOfOrNull { this[normalizeHeader(it)]?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

    private fun customerAddress(row: Map<String, String>): String {
        val splitAddress = listOf(
            row.pick("addressline1", "address1", "addressone", "street"),
            row.pick("addressline2", "address2", "addresstwo", "area"),
            row.pick("city", "town"),
            row.pick("state"),
            row.pick("pincode", "pin", "zipcode", "postalcode")
        ).filter { it.isNotBlank() }.joinToString(", ")
        return splitAddress.ifBlank { row.pick("address", "location") }
    }

    private fun normalizeHeader(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun columnIndex(col: String): Int =
        col.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1

    private fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun toCsvExportUrl(sheetUrl: String): String {
        val trimmed = sheetUrl.trim()
        val id = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(trimmed)?.groupValues?.getOrNull(1)
        val gid = Regex("[?&]gid=([0-9]+)").find(trimmed)?.groupValues?.getOrNull(1) ?: "0"
        return if (id != null) "https://docs.google.com/spreadsheets/d/$id/export?format=csv&gid=$gid" else trimmed
    }

    private fun purityFromKaratLabel(value: String): String =
        when (value.filter { it.isDigit() }) {
            "24" -> "100"
            "22" -> "91.6"
            "20" -> "83.333"
            "18" -> "75"
            else -> ""
        }

    private fun labelFromPurity(purity: Double): String = when {
        purity >= 99.0 -> "24K (100%)"
        purity >= 91.0 -> "22K (91.6%)"
        purity >= 83.0 -> "20K (83.3%)"
        purity >= 74.0 -> "18K (75%)"
        else -> "Custom"
    }

    private fun parseDate(value: String): Date? {
        if (value.isBlank()) return null
        val formats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy")
        return formats.firstNotNullOfOrNull { format ->
            runCatching { SimpleDateFormat(format, Locale.getDefault()).parse(value) }.getOrNull()
        }
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheetXml(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIndex, row ->
            val excelRow = rowIndex + 1
            append("""<row r="$excelRow">""")
            row.forEachIndexed { colIndex, value ->
                val cellRef = "${columnName(colIndex)}$excelRow"
                append("""<c r="$cellRef" t="inlineStr"><is><t>${xml(value)}</t></is></c>""")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val result = StringBuilder()
        while (value > 0) {
            val rem = (value - 1) % 26
            result.insert(0, ('A'.code + rem).toChar())
            value = (value - rem - 1) / 26
        }
        return result.toString()
    }

    private fun contentTypesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun relsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(sheetName: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${xml(sheetName).take(31)}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellXfs></styleSheet>"""

    private fun xml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
