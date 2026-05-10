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
                "address_line_1",
                "address_line_2",
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
        assertEquals("12 Market Street, First Floor, Chennai, Tamil Nadu, 600001", parsed.rows[0].address)
        assertEquals("Legacy Address", parsed.rows[1].address)
        assertTrue(parsed.skipped == 0)
    }
}
