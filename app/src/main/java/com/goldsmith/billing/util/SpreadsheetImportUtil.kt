package com.goldsmith.billing.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ImportedCustomerRow(
    val customerId: String = "",
    val name: String,
    val phone: String,
    val companyName: String = "",
    val doorNo: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
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

data class SpreadsheetReadResult(
    val displayName: String,
    val byteCount: Long,
    val rows: List<Map<String, String>>
)

data class GoogleImportSource(
    val id: String,
    val kind: Kind,
    val gid: String = "0"
) {
    enum class Kind { Sheet, DriveFile }
}

object SpreadsheetImportUtil {
    private const val GOOGLE_IMPORT_ERROR =
        "Paste a valid Google Sheet or Google Drive file link. The file must be shared as anyone-with-link viewer."

    fun customerTemplateRows(): List<List<String>> = listOf(
        listOf(
            "name",
            "phone",
            "shop_name",
            "door_no",
            "address",
            "city",
            "state",
            "pincode",
            "gst",
            "email",
            "dob",
            "anniversary"
        )
    )

    fun customerExportHeaderRows(): List<List<String>> = listOf(
        listOf("customer_id") + customerTemplateRows().first()
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
        readContentFromUri(context, uri).rows
    }

    suspend fun readContentFromUri(context: Context, uri: Uri): SpreadsheetReadResult = withContext(Dispatchers.IO) {
        val bytes = readBytesFromUri(context, uri)
            ?: error("Unable to open the selected file. Please choose it again from Files or Downloads.")
        if (bytes.isEmpty()) error("The selected file opened as 0 bytes. Please choose the actual CSV/XLSX file from Files or Downloads.")
        val name = displayName(context, uri).ifBlank { uri.lastPathSegment.orEmpty() }
        SpreadsheetReadResult(
            displayName = name.ifBlank { "selected file" },
            byteCount = bytes.size.toLong(),
            rows = readRowsFromBytes(name, bytes)
        )
    }

    fun readRowsFromBytes(fileName: String, bytes: ByteArray): List<Map<String, String>> =
        if (fileName.lowercase(Locale.ROOT).endsWith(".xlsx") || looksLikeXlsx(bytes)) parseXlsx(bytes) else parseCsv(bytes.toString(Charsets.UTF_8))

    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.isNotEmpty()) return bytes
        }
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.createInputStream().use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty()) return bytes
            }
        }
        if (uri.scheme == "file") {
            val path = uri.path.orEmpty()
            if (path.isNotBlank()) {
                val file = File(path)
                if (file.exists() && file.length() > 0L) {
                    FileInputStream(file).use { return it.readBytes() }
                }
            }
        }
        return null
    }

    fun formatByteCount(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun validateGoogleImportUrl(importUrl: String): String? =
        if (parseGoogleImportSource(importUrl) == null) GOOGLE_IMPORT_ERROR else null

    suspend fun readRowsFromGoogleSheet(sheetUrl: String): List<Map<String, String>> = readRowsFromGoogleUrl(sheetUrl)

    suspend fun readRowsFromGoogleUrl(importUrl: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val source = parseGoogleImportSource(importUrl) ?: error(GOOGLE_IMPORT_ERROR)
        if (source.kind == GoogleImportSource.Kind.DriveFile) {
            val bytes = downloadBytes("https://drive.google.com/uc?export=download&id=${source.id}")
            return@withContext readRowsFromBytes("google_drive_import", bytes)
        }
        var lastError: String? = null
        for (exportUrl in toCsvExportUrls(source)) {
            val result = runCatching {
                val text = downloadText(exportUrl)
                val rows = if (looksLikeHtml(text)) parseHtmlTable(text) else parseCsv(text)
                if (rows.isEmpty()) error("No rows found at $exportUrl")
                rows
            }
            if (result.isSuccess) return@withContext result.getOrThrow()
            lastError = result.exceptionOrNull()?.message
        }
        error(lastError ?: "Google Sheet could not be downloaded. Share it as anyone-with-link viewer and try again.")
    }

    private fun downloadText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AbuStarDiamonds/4.0")
            setRequestProperty("Accept", "text/csv,text/plain,text/html,*/*")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            error("Google Sheet could not be downloaded. Share it as anyone-with-link viewer and try again. HTTP $code")
        }
        return connection.inputStream.use { it.bufferedReader().readText() }
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AbuStarDiamonds/4.0")
            setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv,text/plain,*/*")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            error("Google Drive file could not be downloaded. Share it as anyone-with-link viewer and try again. HTTP $code")
        }
        return connection.inputStream.use { it.readBytes() }
    }

    fun parseCustomers(rows: List<Map<String, String>>): ImportPreview<ImportedCustomerRow> {
        var skipped = 0
        val parsed = rows.mapNotNull { row ->
            val name = row.pick("name", "customer", "customername", "ownername", "fullname")
            val phone = cleanPhone(row.pick("phone", "phonenumber", "mobile", "mobilenumber", "contact"))
            if (name.isBlank() || phone.isBlank()) {
                skipped++
                null
            } else {
                ImportedCustomerRow(
                    customerId = CustomerIdentity.normalizeExternalId(row.pick("customerid", "customer_id", "externalid", "external_id", "shopid", "shop_id")),
                    name = name,
                    phone = phone,
                    companyName = row.pick("shop", "shopname", "companyname", "company", "businessname"),
                    doorNo = row.pick("doorno", "door", "addressline1", "address1", "addressone"),
                    address = row.pick("address", "addressline2", "address2", "addresstwo", "street", "area", "location"),
                    city = row.pick("city", "town"),
                    state = row.pick("state"),
                    pincode = row.pick("pincode", "pin", "zipcode", "postalcode"),
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
                val value = cellValue(cell)
                values[col] = when (type) {
                    "s" -> sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> cell.getElementsByTagName("t").item(0)?.textContent.orEmpty()
                    "str" -> value
                    else -> value
                }
            }
            (0..(values.keys.maxOrNull() ?: -1)).map { values[it].orEmpty().trim() }
        }
    }

    private fun rowsToMaps(rows: List<List<String>>): List<Map<String, String>> {
        val headerRowIndex = rows.indexOfFirst { row -> row.any { normalizeHeader(it).isNotBlank() } }
        if (headerRowIndex < 0) return emptyList()
        val header = rows[headerRowIndex].map(::normalizeHeader)
        if (header.isEmpty()) return emptyList()
        return rows.drop(headerRowIndex + 1).map { row ->
            header.mapIndexedNotNull { index, key ->
                if (key.isBlank()) null else key to row.getOrElse(index) { "" }
            }.toMap()
        }.filter { row -> row.values.any { it.isNotBlank() } }
    }

    private fun cellValue(cell: Element): String {
        cell.getElementsByTagName("v").item(0)?.textContent?.let { return it }
        cell.getElementsByTagName("t").item(0)?.textContent?.let { return it }
        return cell.textContent.orEmpty().trim()
    }

    private fun newDocument(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun Map<String, String>.pick(vararg keys: String): String =
        keys.firstNotNullOfOrNull { this[normalizeHeader(it)]?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

    private fun normalizeHeader(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun cleanPhone(value: String): String {
        val trimmed = value.trim()
        if ('E' !in trimmed.uppercase(Locale.ROOT)) return trimmed
        return runCatching {
            java.math.BigDecimal(trimmed).toPlainString().substringBefore('.')
        }.getOrDefault(trimmed)
    }

    private fun columnIndex(col: String): Int =
        col.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1

    private fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")

    private fun toCsvExportUrls(source: GoogleImportSource): List<String> =
        listOf(
            "https://docs.google.com/spreadsheets/d/${source.id}/export?format=csv&gid=${source.gid}",
            "https://docs.google.com/spreadsheets/d/${source.id}/gviz/tq?tqx=out:csv&gid=${source.gid}"
        )

    private fun parseGoogleImportSource(importUrl: String): GoogleImportSource? {
        val trimmed = importUrl.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val path = uri.path.orEmpty()
        val query = parseQuery(uri.rawQuery.orEmpty())
        val gid = query["gid"] ?: "0"
        return when {
            host == "docs.google.com" && path.contains("/spreadsheets/d/") -> {
                val id = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(path)?.groupValues?.getOrNull(1)
                id?.let { GoogleImportSource(it, GoogleImportSource.Kind.Sheet, gid) }
            }
            host == "drive.google.com" && path.contains("/file/d/") -> {
                val id = Regex("/file/d/([a-zA-Z0-9-_]+)").find(path)?.groupValues?.getOrNull(1)
                id?.let { GoogleImportSource(it, GoogleImportSource.Kind.DriveFile) }
            }
            host == "drive.google.com" && (path == "/open" || path == "/uc") -> {
                query["id"]?.takeIf { it.isNotBlank() }
                    ?.let { GoogleImportSource(it, GoogleImportSource.Kind.DriveFile) }
            }
            else -> null
        }
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

    private fun looksLikeHtml(text: String): Boolean =
        text.trimStart().startsWith("<", ignoreCase = true)

    private fun parseHtmlTable(html: String): List<Map<String, String>> {
        val rows = Regex("<tr[\\s\\S]*?</tr>", RegexOption.IGNORE_CASE).findAll(html).map { rowMatch ->
            Regex("<t[dh][^>]*>([\\s\\S]*?)</t[dh]>", RegexOption.IGNORE_CASE)
                .findAll(rowMatch.value)
                .map { cellMatch -> htmlCellText(cellMatch.groupValues[1]) }
                .toList()
        }.filter { it.any(String::isNotBlank) }.toList()
        return rowsToMaps(rows)
    }

    private fun htmlCellText(value: String): String =
        value.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

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
