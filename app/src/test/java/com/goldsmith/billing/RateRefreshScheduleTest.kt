package com.goldsmith.billing

import com.goldsmith.billing.util.RateRefreshSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class RateRefreshScheduleTest {

    @Test
    fun `daily slots match requested gold rate refresh times`() {
        assertEquals(
            listOf("10:00", "15:00", "18:00"),
            RateRefreshSchedule.DAILY_SLOTS.map { "%02d:%02d".format(it.hour, it.minute) }
        )
    }
}
