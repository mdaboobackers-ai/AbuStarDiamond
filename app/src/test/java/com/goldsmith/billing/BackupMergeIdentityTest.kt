package com.goldsmith.billing

import com.goldsmith.billing.data.model.Customer
import com.goldsmith.billing.util.BackupMergeIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupMergeIdentityTest {

    @Test
    fun `same customer matches across phone formatting`() {
        val local = Customer(name = "Raja", companyName = "Raja Jewels", phone = "9876543210")
        val restored = Customer(name = " Raja ", companyName = "Raja  Jewels", phone = "+91 98765 43210")

        assertEquals(BackupMergeIdentity.customerKey(local), BackupMergeIdentity.customerKey(restored))
    }

    @Test
    fun `different customers sharing phone are not collapsed`() {
        val first = Customer(name = "Raja", companyName = "Raja Jewels", phone = "9876543210")
        val second = Customer(name = "Kumar", companyName = "Kumar Gold", phone = "9876543210")

        assertNotEquals(BackupMergeIdentity.customerKey(first), BackupMergeIdentity.customerKey(second))
    }
}
