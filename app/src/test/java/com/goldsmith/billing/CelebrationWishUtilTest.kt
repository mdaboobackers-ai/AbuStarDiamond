package com.goldsmith.billing

import com.goldsmith.billing.util.CelebrationWishUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationWishUtilTest {

    @Test
    fun `birthday message includes customer and sender`() {
        val message = CelebrationWishUtil.buildWishMessage(
            customerName = "Rahman",
            eventType = "Birthday",
            senderName = "ABU STAR DIAMONDS"
        )

        assertTrue(message.contains("Happy Birthday"))
        assertTrue(message.contains("Rahman"))
        assertTrue(message.contains("ABU STAR DIAMONDS"))
    }

    @Test
    fun `tamil nadu local mobile gets india country code`() {
        assertEquals("919876543210", CelebrationWishUtil.whatsappPhoneNumber("98765 43210"))
    }

    @Test
    fun `leading zero mobile gets normalized for whatsapp`() {
        assertEquals("919876543210", CelebrationWishUtil.whatsappPhoneNumber("09876543210"))
    }

    @Test
    fun `invalid short phone returns null`() {
        assertNull(CelebrationWishUtil.whatsappPhoneNumber("12345"))
    }
}
