package com.harikrishnarao.financetracker.domain

import com.harikrishnarao.financetracker.sms.Categorizer
import kotlin.math.abs

/** One debit to a payee, reduced to what the insights need. */
data class PayeeTxn(
    val key: String,
    val name: String,
    val amount: Paise,
    /** Minutes since midnight, e.g. 13:05 -> 785. */
    val minuteOfDay: Int,
    /** "2026-10" */
    val month: String,
    val category: Category,
)

data class FrequentPayee(
    val key: String,
    val name: String,
    val count: Int,
    val averageAmount: Paise,
    val totalAmount: Paise,
    /** e.g. "12–2 PM", or null when payments are spread across the day. */
    val usualTime: String?,
    val currentCategory: Category,
    val suggested: Category,
    val reason: String,
    /** Some of its payments are still "Uncategorised" or differ from the suggestion. */
    val needsAttention: Boolean,
)

/**
 * Finds payees you pay again and again (canteen, milkman, auto stand...) and suggests a
 * category from the name, the time of day and how regular the amount is.
 */
object PayeeInsights {
    const val MIN_PAYMENTS = 3
    const val LOOKBACK_DAYS = 60L

    private val LUNCH = 11 * 60..15 * 60 + 30
    private val DINNER = 19 * 60..23 * 60
    private val MORNING = 6 * 60 + 30..10 * 60 + 30
    private const val MEAL_MAX: Paise = 600_00
    private const val SNACK_MAX: Paise = 300_00

    fun find(txns: List<PayeeTxn>): List<FrequentPayee> = txns
        .groupBy { it.key }
        .filterValues { it.size >= MIN_PAYMENTS }
        .map { (key, list) -> analyse(key, list) }
        .sortedWith(compareByDescending<FrequentPayee> { it.needsAttention }.thenByDescending { it.count })

    private fun analyse(key: String, list: List<PayeeTxn>): FrequentPayee {
        val count = list.size
        val total = list.sumOf { it.amount }
        val avg = total / count
        val current = list.groupingBy { it.category }.eachCount().maxByOrNull { it.value }!!.key
        val minutes = list.map { it.minuteOfDay }

        val mealShare = minutes.count { it in LUNCH || it in DINNER }.toDouble() / count
        val morningShare = minutes.count { it in MORNING }.toDouble() / count
        val months = list.map { it.month }.distinct().size
        val sameAmount = list.count { abs(it.amount - avg) <= maxOf(100L, avg / 50) }.toDouble() / count
        val perMonth = count.toDouble() / months

        val byName = Categorizer.categorise(list.first().name, TxnType.DEBIT, false, emptyMap())
        val time = usualTime(minutes)
        val (suggested, reason) = when {
            byName != Category.UNCATEGORISED -> byName to "The name looks like ${byName.label.lowercase()}"
            mealShare >= 0.6 && avg <= MEAL_MAX ->
                Category.FOOD to "Paid $count times, mostly at meal times${time?.let { " ($it)" } ?: ""}, about ${rupees(avg)} each"
            morningShare >= 0.6 && avg <= SNACK_MAX ->
                Category.FOOD to "Small morning payments${time?.let { " ($it)" } ?: ""}, like breakfast or tea"
            months >= 2 && perMonth <= 2 && sameAmount >= 0.8 ->
                Category.BILLS to "Same amount every month, like a bill or subscription"
            else -> Category.OTHER to "You pay them often: $count times in the last ${LOOKBACK_DAYS} days"
        }
        val attention = list.any { it.category == Category.UNCATEGORISED } || current != suggested && current == Category.OTHER

        return FrequentPayee(key, list.first().name, count, avg, total, time, current, suggested, reason, attention)
    }

    /** Middle half of the payment times, rounded to hours: "12–2 PM". Null if spread over 6+ hours. */
    fun usualTime(minutes: List<Int>): String? {
        if (minutes.isEmpty()) return null
        val sorted = minutes.sorted()
        val lo = sorted[(sorted.size - 1) / 4] / 60
        val hi = (sorted[sorted.size - 1 - sorted.size / 4] + 59) / 60
        if (hi - lo >= 6) return null
        val end = if (hi == lo) lo + 1 else hi
        return "${h12(lo, end >= 12 && lo < 12)}–${h12(end, true)}"
    }

    private fun h12(hour: Int, withSuffix: Boolean): String {
        val h = ((hour + 11) % 12) + 1
        val suffix = if (hour % 24 < 12) "AM" else "PM"
        return if (withSuffix) "$h $suffix" else "$h"
    }

    private fun rupees(p: Paise) = "₹" + (p / 100)
}
