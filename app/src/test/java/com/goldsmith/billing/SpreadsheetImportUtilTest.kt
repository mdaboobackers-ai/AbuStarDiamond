package com.goldsmith.billing

import com.goldsmith.billing.util.SpreadsheetImportUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetImportUtilTest {

    @Test
    fun `customer template matches create customer fields plus address parts`() {
        val headers = SpreadsheetImportUtil.customerTemplateRows().first()

        assertEquals(
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
            ),
            headers
        )
    }

    @Test
    fun `customer export includes hidden customer id but blank import template does not`() {
        assertEquals(
            listOf(
                "customer_id",
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
            ),
            SpreadsheetImportUtil.customerExportHeaderRows().first()
        )

        assertTrue("customer_id" !in SpreadsheetImportUtil.customerTemplateRows().first())
    }

    @Test
    fun `customer import reads optional hidden customer id`() {
        val parsed = SpreadsheetImportUtil.parseCustomers(
            listOf(
                mapOf(
                    "customerid" to " asd-00001234 ",
                    "name" to "Raja",
                    "phone" to "9999999999",
                    "shopname" to "Raja Jewels"
                )
            )
        )

        assertEquals("ASD-00001234", parsed.rows.first().customerId)
    }

    @Test
    fun `customer import combines split address fields and supports old address column`() {
        val parsed = SpreadsheetImportUtil.parseCustomers(
            listOf(
                mapOf(
                    "name" to "Raja",
                    "phone" to "9999999999",
                    "shopname" to "Raja Jewels",
                    "addressline1" to "12 Market Street",
                    "addressline2" to "First Floor",
                    "city" to "Chennai",
                    "state" to "Tamil Nadu",
                    "pincode" to "600001"
                ),
                mapOf(
                    "name" to "Old",
                    "phone" to "8888888888",
                    "address" to "Legacy Address"
                )
            )
        )

        assertEquals(2, parsed.rows.size)
        assertEquals("12 Market Street", parsed.rows[0].doorNo)
        assertEquals("First Floor", parsed.rows[0].address)
        assertEquals("Chennai", parsed.rows[0].city)
        assertEquals("Legacy Address", parsed.rows[1].address)
        assertTrue(parsed.skipped == 0)
    }

    @Test
    fun `billing import accepts template headers`() {
        val parsed = SpreadsheetImportUtil.parseBillItems(
            listOf(
                mapOf(
                    "description" to "Chain",
                    "gross" to "10.500",
                    "less" to "0.250",
                    "purity" to "91.6",
                    "karat" to "22K",
                    "making" to "7",
                    "stone" to "1250.75"
                )
            )
        )

        assertEquals(1, parsed.rows.size)
        assertEquals("10.500", parsed.rows.first().grossWeight)
        assertEquals("1250.75", parsed.rows.first().stoneValue)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun `generated customer export xlsx can be imported again`() {
        val bytes = SpreadsheetImportUtil.buildXlsx(
            listOf(
                SpreadsheetImportUtil.customerExportHeaderRows().first(),
                listOf("ASD-TEST-001", "Raja", "9876543210", "Raja Jewels", "12 Market Street", "", "Chennai", "Tamil Nadu", "600001", "", "", "", "")
            ),
            "Customers"
        )

        val rows = SpreadsheetImportUtil.readRowsFromBytes("customers_export.xlsx", bytes)
        val parsed = SpreadsheetImportUtil.parseCustomers(rows)

        assertEquals(1, parsed.rows.size)
        assertEquals("ASD-TEST-001", parsed.rows.first().customerId)
        assertEquals("Raja", parsed.rows.first().name)
        assertEquals("9876543210", parsed.rows.first().phone)
        assertEquals("12 Market Street", parsed.rows.first().doorNo)
        assertEquals("Chennai", parsed.rows.first().city)
    }

    @Test
    fun `customer import converts spreadsheet scientific phone values`() {
        val parsed = SpreadsheetImportUtil.parseCustomers(
            listOf(
                mapOf(
                    "name" to "Raja",
                    "phone" to "9.876543210E9"
                )
            )
        )

        assertEquals("9876543210", parsed.rows.first().phone)
    }

    @Test
    fun `byte count display does not round small imports down to zero`() {
        assertEquals("2.0 KB", SpreadsheetImportUtil.formatByteCount(2048))
        assertEquals("512 B", SpreadsheetImportUtil.formatByteCount(512))
    }

    @Test
    fun `google import validates sheet and drive links only`() {
        assertEquals(
            null,
            SpreadsheetImportUtil.validateGoogleImportUrl("https://docs.google.com/spreadsheets/d/abc123/edit?gid=0")
        )
        assertEquals(
            null,
            SpreadsheetImportUtil.validateGoogleImportUrl("https://drive.google.com/file/d/file123/view?usp=sharing")
        )

        val error = SpreadsheetImportUtil.validateGoogleImportUrl("https://example.com/not-a-sheet")
        assertTrue(error!!.contains("valid Google Sheet or Google Drive file link"))
    }
}
