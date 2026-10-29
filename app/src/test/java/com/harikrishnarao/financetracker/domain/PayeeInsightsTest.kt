package com.harikrishnarao.financetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayeeInsightsTest {
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun txn(key: String, amount: Long, minute: Int, month: String = "2026-10", cat: Category = Category.UNCATEGORISED, name: String = key) =
        PayeeTxn(key, name, amount, minute, month, cat)

    @Test fun `office canteen at lunch time is suggested as food`() {
        val list = listOf(at(12, 46), at(13, 2), at(12, 30), at(13, 15), at(12, 55)).map { txn("xyz pay portal", 110_00, it) }
        val p = PayeeInsights.find(list).single()
        assertEquals(Category.FOOD, p.suggested)
        assertEquals(5, p.count)
        assertEquals(110_00L, p.averageAmount)
        assertTrue(p.needsAttention)
        assertTrue(p.reason.contains("meal"))
        assertEquals("12–2 PM", p.usualTime)
    }

    @Test fun `known name wins over timing`() {
        val list = (1..3).map { txn("uber", 250_00, at(9)) }
        assertEquals(Category.TRAVEL, PayeeInsights.find(list).single().suggested)
    }

    @Test fun `same amount once a month looks like a bill`() {
        val list = listOf("2026-08", "2026-09", "2026-10").map { txn("ravi kumar", 2_500_00, at(18), month = it) }
        assertEquals(Category.BILLS, PayeeInsights.find(list).single().suggested)
    }

    @Test fun `fewer than three payments is not frequent`() {
        assertTrue(PayeeInsights.find(listOf(txn("a", 100, at(12)), txn("a", 100, at(13)))).isEmpty())
    }

    @Test fun `already sorted payee does not need attention`() {
        val list = (1..4).map { txn("canteen", 110_00, at(13), cat = Category.FOOD) }
        assertEquals(false, PayeeInsights.find(list).single().needsAttention)
    }

    @Test fun `usual time text`() {
        assertEquals("8–10 AM", PayeeInsights.usualTime(listOf(at(8), at(8, 30), at(9, 15))))
        assertEquals("11 AM–1 PM", PayeeInsights.usualTime(listOf(at(11), at(12), at(12, 30))))
        assertNull(PayeeInsights.usualTime(listOf(at(7), at(9), at(15), at(21))))
    }
}

class DuplicateVerdictTest {
    private val min = 60_000L
    private fun c(amount: Long, t: Long, ref: String? = null) = TxnCandidate(TxnType.DEBIT, amount, "719", ref, t)

    @Test fun `second alert 16 minutes later is a possible duplicate`() =
        assertEquals(DupVerdict.POSSIBLE_DUPLICATE, Deduper.check(c(110_00, 16 * min), listOf(c(110_00, 0, "530212345678"))))

    @Test fun `same UPI ref is a sure duplicate even hours apart`() =
        assertEquals(DupVerdict.DUPLICATE, Deduper.check(c(110_00, 5 * 60 * min, "530212345678"), listOf(c(110_00, 0, "530212345678"))))

    @Test fun `two lunches with different refs are both real`() =
        assertEquals(DupVerdict.UNIQUE, Deduper.check(c(110_00, 16 * min, "A1234567"), listOf(c(110_00, 0, "B1234567"))))

    @Test fun `over an hour apart is unique`() =
        assertEquals(DupVerdict.UNIQUE, Deduper.check(c(110_00, 61 * min), listOf(c(110_00, 0))))
}
