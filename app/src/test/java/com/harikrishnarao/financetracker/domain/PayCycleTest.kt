package com.harikrishnarao.financetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PayCycleTest {
    private val last = PayCycleConfig(PayRule.LAST_WORKING_DAY)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun `last working day skips weekends`() {
        assertEquals(d(2026, 10, 30), PayCycle.payday(YearMonth.of(2026, 10), last)) // Fri
        assertEquals(d(2026, 11, 30), PayCycle.payday(YearMonth.of(2026, 11), last)) // Mon
        assertEquals(d(2027, 1, 29), PayCycle.payday(YearMonth.of(2027, 1), last)) // 31 Jan is Sunday
    }

    @Test fun `user example - salary today runs to Monday 30 Nov`() {
        val c = PayCycle.current(d(2026, 10, 30), last)!!
        assertEquals(d(2026, 10, 30), c.start)
        assertEquals(d(2026, 11, 30), c.nextPayday)
        assertEquals(d(2026, 11, 29), c.lastDay)
        assertEquals(31L, c.daysLeft(d(2026, 10, 30)))
    }

    @Test fun `day before payday still belongs to the old cycle`() {
        val c = PayCycle.current(d(2026, 10, 29), last)!!
        assertEquals(d(2026, 9, 30), c.start)
        assertEquals(d(2026, 10, 30), c.nextPayday)
        assertEquals(1L, c.daysLeft(d(2026, 10, 29)))
    }

    @Test fun `fixed date on a weekend moves to the working day before`() {
        val cfg = PayCycleConfig(PayRule.FIXED_DAY, fixedDay = 7)
        assertEquals(d(2026, 11, 6), PayCycle.payday(YearMonth.of(2026, 11), cfg)) // 7 Nov is Saturday
        assertEquals(d(2027, 2, 26), PayCycle.payday(YearMonth.of(2027, 2), PayCycleConfig(PayRule.FIXED_DAY, 31))) // 28 Feb Sun
    }

    @Test fun `first working day`() {
        assertEquals(d(2026, 11, 2), PayCycle.payday(YearMonth.of(2026, 11), PayCycleConfig(PayRule.FIRST_WORKING_DAY)))
    }

    @Test fun `early salary starts the cycle early and next payday stays a month away`() {
        val cfg = last.copy(actualPayday = d(2026, 11, 27)) // came Friday, before Monday 30 Nov
        val c = PayCycle.current(d(2026, 11, 28), cfg)!!
        assertEquals(d(2026, 11, 27), c.start)
        assertEquals(d(2026, 12, 31), c.nextPayday)
        assertEquals(d(2026, 11, 27), PayCycle.current(d(2026, 11, 30), cfg)!!.start) // no reset on 30 Nov
    }

    @Test fun `late salary keeps the old cycle until it arrives`() {
        val cfg = last.copy(actualPayday = d(2026, 12, 2))
        assertEquals(d(2026, 10, 30), PayCycle.current(d(2026, 12, 1), cfg)!!.start)
        val c = PayCycle.current(d(2026, 12, 2), cfg)!!
        assertEquals(d(2026, 12, 2), c.start)
        assertEquals(d(2026, 12, 31), c.nextPayday)
    }

    @Test fun `old correction has no effect months later`() {
        val cfg = last.copy(actualPayday = d(2026, 11, 27))
        val c = PayCycle.current(d(2027, 3, 10), cfg)!!
        assertEquals(d(2027, 2, 26), c.start)
    }

    @Test fun `not set means no cycle`() = assertNull(PayCycle.current(d(2026, 10, 30), PayCycleConfig()))
}
