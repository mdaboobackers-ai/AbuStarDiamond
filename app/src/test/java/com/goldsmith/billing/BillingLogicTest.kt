package com.goldsmith.billing

import com.goldsmith.billing.ui.billing.BillItemDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingLogicTest {

    @Test
    fun `test billing formula F = C x ((D + E) divide 100)`() {
        // A = Gross Weight = 10g
        // B = Card Weight (Less Weight) = 1g
        // C = Net Weight = A - B = 9g
        // D = Purity = 91.6%
        // E = Making Charges = 2% (as per formula interpretation)
        
        val draft = BillItemDraft(
            grossWeight = "10.0",
            lessWeight = "1.0",
            purityPercent = "91.6",
            makingPerGram = "2.0"
        )
        
        // C = 10 - 1 = 9
        // F = 9 * ((91.6 + 2.0) / 100)
        // F = 9 * (93.6 / 100)
        // F = 9 * 0.936 = 8.424
        
        assertEquals(9.0, draft.netW, 0.001)
        assertEquals(8.424, draft.gramsWithMaking, 0.001)
        
        // Amount check at 7000/g
        // Amount = 8.424 * 7000 = 58968
        assertEquals(58968.0, draft.amount(7000.0), 0.001)
    }

    @Test
    fun `test custom purity and making charges`() {
        val draft = BillItemDraft(
            grossWeight = "20.0",
            lessWeight = "0.0",
            purityPercent = "85.0", // 20K
            makingPerGram = "5.0"
        )
        
        // C = 20
        // F = 20 * ((85 + 5) / 100) = 20 * 0.9 = 18.0
        assertEquals(18.0, draft.gramsWithMaking, 0.001)
    }
}
