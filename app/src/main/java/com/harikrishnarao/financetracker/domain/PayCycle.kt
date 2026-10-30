package com.harikrishnarao.financetracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** How the user's salary date is decided. Chosen by hand in Settings. */
enum class PayRule(val label: String) {
    NONE("Not set"),
    LAST_WORKING_DAY("Last working day"),
    FIRST_WORKING_DAY("First working day"),
    FIXED_DAY("Fixed date"),
}

/**
 * [fixedDay] is used with FIXED_DAY (1..31). [actualPayday] is set when a salary came on a
 * different day than the rule says (bank holiday, early or late credit). It replaces the rule's
 * payday that is within 10 days of it, so the cycle follows the real credit date.
 */
data class PayCycleConfig(
    val rule: PayRule = PayRule.NONE,
    val fixedDay: Int = 1,
    val actualPayday: LocalDate? = null,
) {
    val isSet: Boolean get() = rule != PayRule.NONE
}

/** A salary-to-salary period: from the last payday up to (not including) the next one. */
data class Cycle(val start: LocalDate, val nextPayday: LocalDate) {
    fun daysLeft(today: LocalDate): Long = ChronoUnit.DAYS.between(today, nextPayday).coerceAtLeast(0)
    val lastDay: LocalDate get() = nextPayday.minusDays(1)
}

object PayCycle {
    private const val MATCH_DAYS = 10L

    private fun isWorkingDay(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    /** The payday the rule gives for [month]. Weekends move to the working day before (as banks do). */
    fun payday(month: YearMonth, cfg: PayCycleConfig): LocalDate {
        return when (cfg.rule) {
            PayRule.FIRST_WORKING_DAY -> {
                var d = month.atDay(1)
                while (!isWorkingDay(d)) d = d.plusDays(1)
                d
            }
            PayRule.FIXED_DAY -> {
                var d = month.atDay(cfg.fixedDay.coerceIn(1, month.lengthOfMonth()))
                while (!isWorkingDay(d)) d = d.minusDays(1)
                d
            }
            PayRule.LAST_WORKING_DAY, PayRule.NONE -> {
                var d = month.atEndOfMonth()
                while (!isWorkingDay(d)) d = d.minusDays(1)
                d
            }
        }
    }

    /** Paydays around [today] after the real-payday correction, oldest first. */
    fun paydays(today: LocalDate, cfg: PayCycleConfig): List<LocalDate> {
        val base = YearMonth.from(today)
        val days = (-2L..2L).map { payday(base.plusMonths(it), cfg) }.toMutableList()
        cfg.actualPayday?.let { actual ->
            val i = days.indexOfFirst { kotlin.math.abs(ChronoUnit.DAYS.between(it, actual)) <= MATCH_DAYS }
            if (i >= 0) days[i] = actual
        }
        return days.distinct().sorted()
    }

    /** The cycle [today] falls in, or null when no pay rule is set. */
    fun current(today: LocalDate, cfg: PayCycleConfig): Cycle? {
        if (!cfg.isSet) return null
        val days = paydays(today, cfg)
        val start = days.last { !it.isAfter(today) }
        val next = days.first { it.isAfter(today) }
        return Cycle(start, next)
    }
}
