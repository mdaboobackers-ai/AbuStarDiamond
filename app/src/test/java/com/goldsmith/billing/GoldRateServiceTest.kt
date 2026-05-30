package com.goldsmith.billing

import com.goldsmith.billing.data.remote.GoldRateService
import org.junit.Assert.assertEquals
import org.junit.Test

class GoldRateServiceTest {

    @Test
    fun `city estimate is deterministic for Tamil Nadu cities`() {
        val first = GoldRateService.estimateTamilNaduRate(7245.0, "Chennai")
        val second = GoldRateService.estimateTamilNaduRate(7245.0, "Chennai")

        assertEquals(first, second, 0.001)
        assertEquals(7253.0, first, 0.001)
    }

    @Test
    fun `unknown city keeps saved manual rate`() {
        assertEquals(7245.0, GoldRateService.estimateTamilNaduRate(7245.0, "Unknown"), 0.001)
        assertEquals(7245.0, GoldRateService.estimateTamilNaduRate(7245.0, null), 0.001)
    }

    @Test
    fun `lks todays rate text parses 24 and 22 karat values`() {
        val parsed = GoldRateService.parseLksRatesForTest(
            """
            Today's Rate
            Gold Rate ( 18k ) 11831
            Gold Rate ( 22k ) 14450
            Gold Rate ( 24k ) 15772
            Silver Rate 290
            """.trimIndent()
        )

        assertEquals(15772.0, parsed!!.rate24K, 0.001)
        assertEquals(14450.0, parsed.rate22K!!, 0.001)
        assertEquals(11831.0, parsed.rate18K!!, 0.001)
    }

    @Test
    fun `sln india gold row converts ten gram rate to one gram 24k`() {
        val parsed = GoldRateService.parseSlnRatesForTest(
            "\t141\tIndia Gold\t160865\t161049\t161591\t159833\t\r\n"
        )

        assertEquals(16086.5, parsed!!.rate24K, 0.001)
    }

    @Test
    fun `sln mjdma gold row derives 24k from 22k jewellery rate`() {
        val parsed = GoldRateService.parseSlnRatesForTest(
            "\t1633\tGOLD\t14630.00\t14630.00\t14630.00\t14600.00\t\r\n"
        )

        assertEquals(14630.0, parsed!!.rate22K!!, 0.001)
        assertEquals(15971.615, parsed.rate24K, 0.001)
    }
}
