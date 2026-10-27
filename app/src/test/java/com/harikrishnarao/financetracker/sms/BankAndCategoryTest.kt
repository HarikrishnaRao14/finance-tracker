package com.harikrishnarao.financetracker.sms

import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDirectoryTest {
    @Test fun `route prefix and type suffix are stripped`() {
        assertEquals("HDFCBK", BankDirectory.coreCode("AX-HDFCBK"))
        assertEquals("HDFCBK", BankDirectory.coreCode("JD-HDFCBK-S"))
        assertEquals("SBIINB", BankDirectory.coreCode("vm-sbiinb-t"))
        assertEquals("HDFCBK", BankDirectory.coreCode("HDFCBK"))
    }

    @Test fun `phone numbers can never be a bank`() {
        assertNull(BankDirectory.coreCode("+919876543210"))
        assertNull(BankDirectory.coreCode("9876543210"))
        assertNull(BankDirectory.coreCode(null))
        assertNull(BankDirectory.coreCode(""))
    }

    @Test fun `known and unknown banks`() {
        assertTrue(BankDirectory.isKnownBank("HDFCBK"))
        assertEquals("State Bank of India", BankDirectory.bankName("SBIINB"))
        assertFalse(BankDirectory.isKnownBank("ZOMATO"))
    }
}

class CategorizerTest {
    private fun cat(m: String?, type: TxnType = TxnType.DEBIT, refund: Boolean = false, rules: Map<String, Category> = emptyMap()) =
        Categorizer.categorise(m, type, refund, rules)

    @Test fun `built-in keywords`() {
        assertEquals(Category.FOOD, cat("Swiggy"))
        assertEquals(Category.FOOD, cat("Zomato LTD"))
        assertEquals(Category.SHOPPING, cat("Amazon PAY IN"))
        assertEquals(Category.TRAVEL, cat("UBER INDIA"))
        assertEquals(Category.ENTERTAINMENT, cat("netflix.bd@hdfcbank"))
        assertEquals(Category.HEALTH, cat("1mg Technologies"))
    }

    @Test fun `keyword must start a word`() {
        // "current" contains "rent" but must not become Bills
        assertEquals(Category.UNCATEGORISED, cat("Current Traders"))
    }

    @Test fun `unknown merchant waits for the user`() {
        assertEquals(Category.UNCATEGORISED, cat("ramesh123@oksbi"))
        assertEquals(Category.UNCATEGORISED, cat(null))
    }

    @Test fun `learned rule beats keywords`() {
        val rules = mapOf("swiggy" to Category.OTHER)
        assertEquals(Category.OTHER, cat("SWIGGY8@ybl", rules = rules))
    }

    @Test fun `credits are income, refunds keep their category`() {
        assertEquals(Category.INCOME, cat("employer@okaxis", TxnType.CREDIT))
        assertEquals(Category.SHOPPING, cat("Amazon", TxnType.CREDIT, refund = true))
        assertEquals(Category.OTHER, cat(null, TxnType.CREDIT, refund = true))
    }

    @Test fun `merchant key is stable`() {
        assertEquals("swiggy", Categorizer.merchantKey("SWIGGY8@ybl"))
        assertEquals("zomato ltd", Categorizer.merchantKey("Zomato LTD"))
        assertNull(Categorizer.merchantKey("  "))
    }
}
