package com.harikrishnarao.financetracker.ui

import com.harikrishnarao.financetracker.domain.Paise
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val india = Locale("en", "IN")

    /** ₹18,420 or ₹1,20,000.50 (Indian digit grouping, paise only when not whole). */
    fun money(p: Paise): String {
        val f = NumberFormat.getCurrencyInstance(india)
        f.maximumFractionDigits = if (p % 100 == 0L) 0 else 2
        f.minimumFractionDigits = f.maximumFractionDigits
        return f.format(p / 100.0)
    }

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private val wdFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    fun dayMonth(d: LocalDate): String = d.format(shortFmt)
    /** "Mon 30 Nov" */
    fun weekdayDayMonth(d: LocalDate): String = d.format(wdFmt)

    /** "12 Jun" */
    fun shortDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(shortFmt)

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)

    fun dateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fullFmt)

    fun dayHeader(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "TODAY"
            today.minusDays(1) -> "YESTERDAY"
            else -> date.format(dayFmt).uppercase()
        }
    }

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    /** "1,234.50" or "500" typed by the user -> paise, or null if invalid. */
    fun parseRupees(input: String): Paise? {
        val clean = input.replace(",", "").replace("₹", "").trim()
        if (clean.isEmpty()) return null
        val v = clean.toBigDecimalOrNull() ?: return null
        if (v.signum() <= 0 || v.scale() > 2) return null
        return v.movePointRight(2).toLong()
    }
}
