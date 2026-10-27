package com.harikrishnarao.financetracker.domain

import com.harikrishnarao.financetracker.data.Bill
import com.harikrishnarao.financetracker.ui.Format
import com.harikrishnarao.financetracker.work.BillSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private const val MIN = 60_000L

class DeduperTest {
    private fun c(amount: Long, t: Long, ref: String? = null, acc: String? = "1234", type: TxnType = TxnType.DEBIT) =
        TxnCandidate(type, amount, acc, ref, t)

    @Test fun `same ref is a duplicate`() =
        assertTrue(Deduper.isDuplicate(c(500, 0, "R1"), listOf(c(500, 9 * 60 * MIN, "r1"))))

    @Test fun `two alerts without refs within 10 minutes are one payment`() =
        assertTrue(Deduper.isDuplicate(c(500, 5 * MIN), listOf(c(500, 0, "R1"))))

    @Test fun `alerts 11 minutes apart are two payments`() =
        assertFalse(Deduper.isDuplicate(c(500, 11 * MIN), listOf(c(500, 0))))

    @Test fun `different refs prove two real payments`() =
        assertFalse(Deduper.isDuplicate(c(2000, MIN, "R2"), listOf(c(2000, 0, "R1"))))

    @Test fun `different amount or account or direction is not a duplicate`() {
        assertFalse(Deduper.isDuplicate(c(501, MIN), listOf(c(500, 0))))
        assertFalse(Deduper.isDuplicate(c(500, MIN, acc = "9999"), listOf(c(500, 0))))
        assertFalse(Deduper.isDuplicate(c(500, MIN, type = TxnType.CREDIT), listOf(c(500, 0))))
    }

    @Test fun `missing account on one alert still matches`() =
        assertTrue(Deduper.isDuplicate(c(500, MIN, acc = null), listOf(c(500, 0))))
}

class TransferMatcherTest {
    @Test fun `debit in one own account and credit in another is a transfer`() {
        val debit = TxnCandidate(TxnType.DEBIT, 10_000_00, "1111", null, 0)
        val credit = TxnCandidate(TxnType.CREDIT, 10_000_00, "2222", null, 20 * MIN)
        assertEquals(0, TransferMatcher.findPair(debit, listOf(credit)))
    }

    @Test fun `same account or far apart is not a transfer`() {
        val debit = TxnCandidate(TxnType.DEBIT, 500_00, "1111", null, 0)
        assertNull(TransferMatcher.findPair(debit, listOf(TxnCandidate(TxnType.CREDIT, 500_00, "1111", null, MIN))))
        assertNull(TransferMatcher.findPair(debit, listOf(TxnCandidate(TxnType.CREDIT, 500_00, "2222", null, 61 * MIN))))
    }
}

class BillTest {
    private val bill = Bill(id = 1, name = "Internet", amount = 999_00, dueDay = 8, isAutopay = true, remindDaysBefore = 3)

    @Test fun `debit matches bill within tolerance`() {
        assertTrue(BillMatcher.matches(999_00, 8, 999_00, 8))
        assertTrue(BillMatcher.matches(1000_00, 3, 999_00, 8))
        assertFalse(BillMatcher.matches(1200_00, 8, 999_00, 8))
        assertFalse(BillMatcher.matches(999_00, 20, 999_00, 8))
    }

    @Test fun `due day 31 becomes last day of short months`() {
        assertEquals(LocalDate.of(2027, 2, 28), BillSchedule.nextDueDate(31, LocalDate.of(2027, 2, 10), false))
        assertEquals(LocalDate.of(2028, 2, 29), BillSchedule.nextDueDate(31, LocalDate.of(2028, 2, 10), false))
    }

    @Test fun `paid or passed bills move to next month`() {
        assertEquals(LocalDate.of(2026, 12, 8), BillSchedule.nextDueDate(8, LocalDate.of(2026, 11, 5), true))
        assertEquals(LocalDate.of(2026, 12, 8), BillSchedule.nextDueDate(8, LocalDate.of(2026, 11, 9), false))
    }

    @Test fun `reminder window`() {
        assertEquals(3L, BillSchedule.reminderDue(bill, LocalDate.of(2026, 11, 5)))
        assertEquals(0L, BillSchedule.reminderDue(bill, LocalDate.of(2026, 11, 8)))
        assertNull(BillSchedule.reminderDue(bill, LocalDate.of(2026, 11, 1)))
        assertNull(BillSchedule.reminderDue(bill.copy(lastPaidMonth = "2026-11"), LocalDate.of(2026, 11, 7)))
    }

    @Test fun `reminder crosses month end`() {
        val b = bill.copy(dueDay = 1)
        assertEquals(2L, BillSchedule.reminderDue(b, LocalDate.of(2026, 10, 30)))
    }
}

class BudgetEngineTest {
    @Test fun `no history gives no advice but still splits income`() {
        val s = BudgetEngine.suggest(emptyList(), emptyList(), incomeOverride = 40_000_00)
        assertEquals(0, s.monthsOfData)
        assertTrue(s.advice.isEmpty())
        assertEquals(20_000_00L, s.needs)
        assertEquals(8_000_00L, s.savings)
    }

    @Test fun `no history and no income`() {
        val s = BudgetEngine.suggest(emptyList(), emptyList())
        assertNull(s.monthlyIncome)
        assertTrue(s.advice.isEmpty())
    }

    @Test fun `one month of data averages over one month`() {
        val s = BudgetEngine.suggest(listOf(BudgetTxn(Category.BILLS, 3_000_00, 0)), listOf(40_000_00))
        assertEquals(1, s.monthsOfData)
        val bills = s.advice.single { it.category == Category.BILLS }
        assertEquals(3_000_00L, bills.usualMonthly)
        assertEquals(3_000_00L, bills.suggestedLimit) // needs are not trimmed
    }

    @Test fun `wants are trimmed 10 percent and rounded up to 100`() {
        val h = (0..2).map { BudgetTxn(Category.FOOD, 5_300_00, it) }
        val food = BudgetEngine.suggest(h, listOf(40_000_00, 40_000_00, 40_000_00)).advice.single()
        assertEquals(5_300_00L, food.usualMonthly)
        assertEquals(4_800_00L, food.suggestedLimit) // 4,770 -> 4,800
    }

    @Test fun `one-off big spend is left out`() {
        val h = (0..2).map { BudgetTxn(Category.SHOPPING, 2_000_00, it) } + BudgetTxn(Category.SHOPPING, 30_000_00, 1)
        val shop = BudgetEngine.suggest(h, listOf(50_000_00)).advice.single()
        assertEquals(2_000_00L, shop.usualMonthly)
    }

    @Test fun `refunds reduce usual spend`() {
        val h = listOf(BudgetTxn(Category.SHOPPING, 3_000_00, 0), BudgetTxn(Category.SHOPPING, 1_000_00, 0, isRefund = true))
        assertEquals(2_000_00L, BudgetEngine.suggest(h, emptyList()).advice.single().usualMonthly)
    }

    @Test fun `overspending is scaled to protect savings`() {
        val h = listOf(BudgetTxn(Category.BILLS, 20_000_00, 0), BudgetTxn(Category.SHOPPING, 15_000_00, 0))
        val s = BudgetEngine.suggest(h, listOf(30_000_00))
        assertTrue(s.scaledToIncome)
        val shop = s.advice.single { it.category == Category.SHOPPING }
        assertTrue(shop.suggestedLimit < 13_500_00)
        assertTrue(shop.suggestedLimit >= 7_500_00) // never below half of usual
    }

    @Test fun `pace`() {
        assertEquals(Pace.ON_TRACK, BudgetEngine.pace(500, 1000, 15, 30))
        assertEquals(Pace.WATCH, BudgetEngine.pace(700, 1000, 15, 30))
        assertEquals(Pace.OVER, BudgetEngine.pace(1001, 1000, 15, 30))
        assertEquals(Pace.ON_TRACK, BudgetEngine.pace(500, 0, 15, 30))
    }
}

class FormatTest {
    @Test fun `typed rupees become paise`() {
        assertEquals(50000L, Format.parseRupees("500"))
        assertEquals(123450L, Format.parseRupees("1,234.50"))
        assertNull(Format.parseRupees(""))
        assertNull(Format.parseRupees("0"))
        assertNull(Format.parseRupees("1.234"))
        assertNull(Format.parseRupees("abc"))
    }
}
