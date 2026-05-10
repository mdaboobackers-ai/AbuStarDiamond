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
}
