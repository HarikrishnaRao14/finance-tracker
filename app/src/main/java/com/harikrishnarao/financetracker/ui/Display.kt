package com.harikrishnarao.financetracker.ui

import com.harikrishnarao.financetracker.data.AccountEntity
import com.harikrishnarao.financetracker.data.TransactionEntity
import com.harikrishnarao.financetracker.data.accountKey
import com.harikrishnarao.financetracker.data.counts
import com.harikrishnarao.financetracker.data.label
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.PayeeTxn
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.sms.Categorizer
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A transaction ready to show: friendly title and a short "UPI · ICICI ··719" line. */
data class UiTxn(
    val t: TransactionEntity,
    val title: String,
    val subtitle: String,
    val payeeKey: String?,
    val accountLabel: String?,
)

fun TransactionEntity.toUi(payeeNames: Map<String, String>, accounts: Map<String, AccountEntity>): UiTxn {
    val key = Categorizer.merchantKey(merchant)
    val title = key?.let { payeeNames[it] } ?: merchant ?: when {
        source == TxnSource.MANUAL -> if (type == TxnType.DEBIT) "Cash spend" else "Cash received"
        channel == "ATM" -> "ATM withdrawal"
        type == TxnType.CREDIT && isRefund -> "Refund"
        type == TxnType.CREDIT -> "Money received"
        isCard -> "Card payment"
        channel == "UPI" -> "UPI payment"
        channel == "Autopay" -> "Autopay"
        else -> "Payment"
    }
    val acc = accountKey?.let { accounts[it] }
    val accLabel = when {
        source == TxnSource.MANUAL -> "Cash"
        acc != null -> acc.nickname?.takeIf { it.isNotBlank() } ?: "${shortBank(acc.bankName)} ${if (acc.isCard) "Card " else ""}··${acc.last4}"
        bankName != null -> shortBank(bankName) + (accountLast4?.let { " ··$it" } ?: "")
        else -> null
    }
    val subtitle = listOfNotNull(channel, accLabel).joinToString(" · ")
    return UiTxn(this, title, subtitle, key, accLabel)
}

/** "State Bank of India" -> "SBI", "ICICI Bank" -> "ICICI". */
fun shortBank(name: String): String = when {
    name.startsWith("State Bank") -> "SBI"
    name.endsWith(" Bank") -> name.removeSuffix(" Bank")
    else -> name
}

/** Spend per category: debits minus refunds, leaving out own transfers, income and suspected duplicates. */
fun spendByCategory(txns: List<TransactionEntity>): Map<Category, Paise> =
    txns.filter { it.counts && it.category.isExpense }
        .groupBy { it.category }
        .mapValues { (_, list) ->
            list.sumOf { if (it.type == TxnType.DEBIT) it.amount else if (it.isRefund) -it.amount else 0L }.coerceAtLeast(0)
        }
        .filterValues { it > 0 }

fun incomeOf(txns: List<TransactionEntity>): Paise =
    txns.filter { it.counts && it.type == TxnType.CREDIT && it.category == Category.INCOME }.sumOf { it.amount }

/** Debits with a payee, in the shape the frequent-payee finder needs. */
fun List<TransactionEntity>.toPayeeTxns(payeeNames: Map<String, String>): List<PayeeTxn> {
    val zone = ZoneId.systemDefault()
    return filter { it.counts && it.type == TxnType.DEBIT && it.category != Category.TRANSFER }
        .mapNotNull { t ->
            val key = Categorizer.merchantKey(t.merchant) ?: return@mapNotNull null
            val time = Instant.ofEpochMilli(t.timestamp).atZone(zone)
            PayeeTxn(key, payeeNames[key] ?: t.merchant!!, t.amount, time.hour * 60 + time.minute,
                YearMonth.from(time).toString(), t.category)
        }
}

@Suppress("unused")
fun AccountEntity.displayLabel() = label
