package com.harikrishnarao.financetracker.domain

/** Money is always stored as paise (Long) to avoid floating-point rounding errors. */
typealias Paise = Long

enum class TxnType { DEBIT, CREDIT }

enum class TxnSource { SMS, MANUAL }

enum class Category(val label: String, val isExpense: Boolean, val isWant: Boolean) {
    FOOD("Food", true, true),
    TRAVEL("Travel", true, false),
    SHOPPING("Shopping", true, true),
    BILLS("Bills", true, false),
    HEALTH("Health", true, false),
    ENTERTAINMENT("Entertainment", true, true),
    OTHER("Other", true, true),
    UNCATEGORISED("Uncategorised", true, true),
    INCOME("Income", false, false),
    /** Money moved between the user's own accounts. Never counted as spend or income. */
    TRANSFER("Own transfer", false, false);

    companion object {
        val expenseChoices = listOf(FOOD, TRAVEL, SHOPPING, BILLS, HEALTH, ENTERTAINMENT, OTHER)
        val budgetable = listOf(FOOD, TRAVEL, SHOPPING, BILLS, HEALTH, ENTERTAINMENT, OTHER)
        val userChoices = expenseChoices + listOf(INCOME, TRANSFER)
    }
}

/** What the SMS parser pulls out of one bank message. */
data class ParsedSms(
    val type: TxnType,
    val amount: Paise,
    val accountLast4: String?,
    val merchant: String?,
    val refNo: String?,
    val balance: Paise?,
    val isRefund: Boolean,
)

/** A transaction before it is saved, used by the duplicate and transfer checks. */
data class TxnCandidate(
    val type: TxnType,
    val amount: Paise,
    val accountLast4: String?,
    val refNo: String?,
    val timestamp: Long,
)
