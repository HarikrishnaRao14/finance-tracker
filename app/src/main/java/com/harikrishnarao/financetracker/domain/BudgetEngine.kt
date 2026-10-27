package com.harikrishnarao.financetracker.domain

/** One expense used for budget history. [monthIndex] 0..2 = how many complete months ago (0 = last month). */
data class BudgetTxn(val category: Category, val amount: Paise, val monthIndex: Int, val isRefund: Boolean = false)

data class CategoryAdvice(
    val category: Category,
    val usualMonthly: Paise,
    val suggestedLimit: Paise,
)

data class BudgetSuggestion(
    val monthsOfData: Int,
    val monthlyIncome: Paise?,
    val needs: Paise?,
    val wants: Paise?,
    val savings: Paise?,
    val advice: List<CategoryAdvice>,
    /** How much more the user keeps per month by following the suggestion. */
    val extraSaved: Paise,
    val scaledToIncome: Boolean,
)

enum class Pace { ON_TRACK, WATCH, OVER }

object BudgetEngine {
    const val HISTORY_MONTHS = 3
    private const val ROUND_TO: Paise = 100_00 // nearest ₹100
    private const val ONE_OFF_MIN: Paise = 5_000_00 // a single spend of ₹5,000+ ...
    private const val ONE_OFF_MULTIPLE = 3.0 // ... over 3x the category's other monthly spend is a one-off
    private const val WANT_TRIM = 0.9 // suggest 10% less on "wants"

    fun suggest(history: List<BudgetTxn>, incomeByMonth: List<Paise>, incomeOverride: Paise? = null): BudgetSuggestion {
        val months = (history.map { it.monthIndex } + incomeByMonth.indices.filter { incomeByMonth[it] > 0 })
            .filter { it in 0 until HISTORY_MONTHS }
            .distinct().size

        val income = incomeOverride ?: incomeByMonth.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

        if (months == 0) {
            return BudgetSuggestion(0, income, income?.let { it * 50 / 100 }, income?.let { it * 30 / 100 },
                income?.let { it * 20 / 100 }, emptyList(), 0, false)
        }

        var advice = Category.budgetable.map { cat ->
            val txns = history.filter { it.category == cat && it.monthIndex in 0 until HISTORY_MONTHS }
            val gross = txns.filter { !it.isRefund }.sumOf { it.amount }
            // A one-off is a big spend far above what this category normally costs in a month.
            // With only one month of data nothing can be called "normal", so nothing is removed.
            val oneOffs = if (months < 2) emptyList() else txns.filter { t ->
                val otherMonthly = (gross - t.amount).toDouble() / months
                !t.isRefund && t.amount >= ONE_OFF_MIN && t.amount > ONE_OFF_MULTIPLE * otherMonthly
            }
            val refunds = txns.filter { it.isRefund }.sumOf { it.amount }
            val net = (gross - oneOffs.sumOf { it.amount } - refunds).coerceAtLeast(0)
            val usual = net / months
            val target = if (cat.isWant) (usual * WANT_TRIM).toLong() else usual
            CategoryAdvice(cat, usual, roundUp(target))
        }.filter { it.usualMonthly > 0 }

        var scaled = false
        if (income != null && income > 0) {
            val cap = income * 80 / 100 // leave at least 20% for savings
            val total = advice.sumOf { it.suggestedLimit }
            if (total > cap) {
                val fixed = advice.filter { !it.category.isWant }.sumOf { it.suggestedLimit }
                val wantTotal = total - fixed
                val room = (cap - fixed).coerceAtLeast(0)
                if (wantTotal > 0) {
                    val ratio = room.toDouble() / wantTotal
                    advice = advice.map { a ->
                        if (!a.category.isWant) a
                        else {
                            val floor = a.usualMonthly / 2 // never suggest cutting a want by more than half
                            a.copy(suggestedLimit = roundUp(maxOf(floor, (a.suggestedLimit * ratio).toLong())))
                        }
                    }
                    scaled = true
                }
            }
        }

        val extra = (advice.sumOf { it.usualMonthly } - advice.sumOf { it.suggestedLimit }).coerceAtLeast(0)
        return BudgetSuggestion(
            monthsOfData = months,
            monthlyIncome = income,
            needs = income?.let { it * 50 / 100 },
            wants = income?.let { it * 30 / 100 },
            savings = income?.let { it * 20 / 100 },
            advice = advice.sortedByDescending { it.suggestedLimit },
            extraSaved = extra,
            scaledToIncome = scaled,
        )
    }

    fun pace(spent: Paise, limit: Paise, dayOfMonth: Int, daysInMonth: Int): Pace {
        if (limit <= 0) return Pace.ON_TRACK
        if (spent > limit) return Pace.OVER
        val usedShare = spent.toDouble() / limit
        val timeShare = dayOfMonth.toDouble() / daysInMonth
        return if (usedShare > timeShare + 0.10) Pace.WATCH else Pace.ON_TRACK
    }

    private fun roundUp(p: Paise): Paise = if (p <= 0) 0 else ((p + ROUND_TO - 1) / ROUND_TO) * ROUND_TO
}
