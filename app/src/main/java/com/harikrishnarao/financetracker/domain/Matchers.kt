package com.harikrishnarao.financetracker.domain

import kotlin.math.abs

private const val MINUTE = 60_000L

/**
 * Banks often send two alerts for one payment (UPI app + bank, or a card alert repeated).
 * A new transaction is a duplicate when an existing one has the same reference number,
 * or the same direction, amount and account within 10 minutes and the reference numbers
 * do not prove they are different payments.
 */
enum class DupVerdict { UNIQUE, DUPLICATE, POSSIBLE_DUPLICATE }

object Deduper {
    /** Same payment for sure: merged silently. */
    const val WINDOW_MS = 10 * MINUTE
    /** Maybe the same payment (late second alert): kept but flagged for the user, not counted. */
    const val POSSIBLE_WINDOW_MS = 60 * MINUTE

    fun isDuplicate(new: TxnCandidate, existing: List<TxnCandidate>): Boolean =
        check(new, existing) == DupVerdict.DUPLICATE

    fun check(new: TxnCandidate, existing: List<TxnCandidate>): DupVerdict {
        var verdict = DupVerdict.UNIQUE
        for (old in existing) {
            if (new.refNo != null && old.refNo != null) {
                // Reference numbers decide: equal = same payment, different = two real payments.
                if (new.refNo.equals(old.refNo, ignoreCase = true)) return DupVerdict.DUPLICATE
                continue
            }
            val same = old.type == new.type && old.amount == new.amount && accountsCompatible(old.accountLast4, new.accountLast4)
            if (!same) continue
            val gap = abs(old.timestamp - new.timestamp)
            if (gap <= WINDOW_MS) return DupVerdict.DUPLICATE
            if (gap <= POSSIBLE_WINDOW_MS) verdict = DupVerdict.POSSIBLE_DUPLICATE
        }
        return verdict
    }

    private fun accountsCompatible(a: String?, b: String?) = a == null || b == null || a == b
}

/**
 * Moving money between your own tracked accounts creates a debit in one and a credit in
 * the other. Counting both would inflate spend AND income, so the pair is marked TRANSFER.
 */
object TransferMatcher {
    const val WINDOW_MS = 60 * MINUTE

    /** Returns the index of the matching opposite-side transaction, or null. */
    fun findPair(new: TxnCandidate, others: List<TxnCandidate>): Int? = others.indexOfFirst { o ->
        o.type != new.type &&
            o.amount == new.amount &&
            o.accountLast4 != null && new.accountLast4 != null &&
            o.accountLast4 != new.accountLast4 &&
            abs(o.timestamp - new.timestamp) <= WINDOW_MS
    }.takeIf { it >= 0 }
}

/** Decides whether a debit pays one of the user's bills this month. */
object BillMatcher {
    /** Amount may differ by up to 1% (or ₹1), date by up to 7 days around the due day. */
    fun matches(debitAmount: Paise, debitDayOfMonth: Int, billAmount: Paise, dueDay: Int): Boolean {
        val tolerance = maxOf(100L, billAmount / 100)
        val amountOk = abs(debitAmount - billAmount) <= tolerance
        val dayOk = abs(debitDayOfMonth - dueDay) <= 7
        return amountOk && dayOk
    }
}
