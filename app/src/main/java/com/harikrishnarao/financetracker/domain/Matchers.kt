package com.harikrishnarao.financetracker.domain

import kotlin.math.abs

private const val MINUTE = 60_000L

/**
 * Banks often send two alerts for one payment (UPI app + bank, or a card alert repeated).
 * A new transaction is a duplicate when an existing one has the same reference number,
 * or the same direction, amount and account within 10 minutes and the reference numbers
 * do not prove they are different payments.
 */
object Deduper {
    const val WINDOW_MS = 10 * MINUTE

    fun isDuplicate(new: TxnCandidate, existing: List<TxnCandidate>): Boolean = existing.any { old ->
        if (new.refNo != null && old.refNo != null) {
            return@any new.refNo.equals(old.refNo, ignoreCase = true)
        }
        old.type == new.type &&
            old.amount == new.amount &&
            accountsCompatible(old.accountLast4, new.accountLast4) &&
            abs(old.timestamp - new.timestamp) <= WINDOW_MS
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
