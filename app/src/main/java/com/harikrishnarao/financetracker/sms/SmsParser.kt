package com.harikrishnarao.financetracker.sms

import com.harikrishnarao.financetracker.domain.ParsedSms
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.TxnType
import java.math.BigDecimal
import java.math.RoundingMode

/** Why a bank SMS was not turned into a transaction. Shown to the user in SMS activity. */
enum class SkipReason(val label: String) {
    OTP("OTP / security code"),
    PROMO("Offer or promotion"),
    FAILED("Failed or declined payment"),
    NOT_YET("Reminder or future debit (not paid yet)"),
    NO_AMOUNT("No amount found"),
    NOT_TRANSACTION("Not a transaction (info / balance alert)"),
}

sealed interface ParseOutcome {
    data class Transaction(val sms: ParsedSms) : ParseOutcome
    data class Skipped(val reason: SkipReason) : ParseOutcome
}

/**
 * Turns one bank SMS body into a transaction, or explains why it is not one.
 * Pure Kotlin with no Android calls, so every rule is covered by plain JVM unit tests.
 *
 * Bump [VERSION] whenever a rule changes: the app then rebuilds SMS transactions once.
 */
object SmsParser {
    const val VERSION = 2

    private val IC = setOf(RegexOption.IGNORE_CASE)

    // ---- Messages that must never become a transaction ----
    private val OTP = Regex("""\b(otp|one[\s-]?time[\s-]?password|verification code|passcode|security code)\b""", IC)
    private val FAILED = Regex("""\b(failed|declined|unsuccessful|could not be processed|rejected|insufficient)\b""", IC)
    private val NOT_YET = Regex(
        """\b(will be debited|will be credited|to be debited|is due|due on|due date|payment due|amount due|""" +
            """requested|request for|collect request|scheduled|upcoming|reminder|mandate (?:created|registered|set up))\b""",
        IC,
    )
    private val PROMO = Regex(
        """\b(pre-?approved|offer|eligible|apply now|avail now|lucky|congratulations|win|get up ?to|up ?to rs|""" +
            """instant loan|personal loan of|credit limit of up)\b""",
        IC,
    )

    // ---- Direction ----
    private val DEBIT = Regex(
        """\b(debited|debit of|spent|paid|withdrawn|withdrawal|sent|purchased?|deducted|transferred to|debit alert)\b""",
        IC,
    )
    private val CREDIT = Regex("""\b(credited|credit of|received|deposited|refund(?:ed)?|reversed|reversal|credit alert)\b""", IC)
    private val REFUND = Regex("""\b(refund(?:ed)?|reversed|reversal)\b""", IC)

    // ---- Fields ----
    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val AMOUNT = Regex("""(?:\brs\.?|\binr|₹)\s*:?\s*$NUM""", IC)
    private val AMOUNT_AFTER_VERB = Regex("""\b(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*:?\s*$NUM""", IC)
    private val BALANCE = Regex("""\b(?:avl|avbl|available|clear|closing)\.?\s*bal(?:ance)?\.?\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*:?\s*$NUM""", IC)
    private val ACCOUNT = Regex(
        """\b(a/c|ac|acct|account|card)(?:\s*(?:no\.?|number|ending(?:\s*(?:with|in))?))?\s*[:.]?\s*[x*.]+\s*(\d{3,6})""",
        IC,
    )
    private val CREDIT_CARD = Regex("""\bcredit\s*card\b""", IC)
    private val DEBIT_CARD = Regex("""\bdebit\s*card\b""", IC)
    private val REF = Regex(
        """\b(?:upi\s*ref(?:\s*no)?|ref(?:erence)?(?:\s*(?:no|num|number|id))?|refno|utr(?:\s*no)?|rrn|""" +
            """txn\s*(?:id|no)|transaction\s*(?:id|ref(?:erence)?)(?:\s*no)?)\b\.?\s*[:#-]?\s*([a-z0-9]{6,})""",
        IC,
    )
    private val REF_UPI_BRACKET = Regex("""\(\s*upi(?:\s*ref)?[\s:]*(\d{9,})\s*\)""", IC)
    /** ICICI style "UPI:529812345678". */
    private val REF_UPI_COLON = Regex("""\bupi\s*[:/-]\s*(\d{9,})""", IC)
    private val VPA = Regex("""\b(?:to|from|by)\s+(?:vpa\s+)?([a-z0-9._-]{2,}@[a-z]{2,})""", IC)
    /** ICICI style "debited for Rs 110.00 on 29-Oct-26; CANTEEN NAME credited." */
    private val PAYEE_CREDITED = Regex("""[;,]\s*([a-z0-9][a-z0-9&'._ -]{1,40}?)\s+credited\b""", IC)
    private val AT_MERCHANT = Regex("""\bat\s+([a-z0-9][a-z0-9&'._ -]{1,40}?)(?=\s+on\b|\s+via\b|\s+using\b|\s+ref|\.\s|,|\.$|$)""", IC)
    private val TO_MERCHANT = Regex("""\b(?:trf to|transfer to|to)\s+([a-z][a-z0-9&'._ -]{1,40}?)(?=\s+on\b|\s+ref|\s+upi|\s+avl|\s+via\b|\.\s|,|\.$|$)""", IC)
    private val FROM_MERCHANT = Regex("""\b(?:from|by)\s+([a-z][a-z0-9&'._ -]{1,40}?)(?=\s+on\b|\s+ref|\s+upi|\s+avl|\s+via\b|\(|\.\s|,|\.$|$)""", IC)
    /** Card style "spent ... on 29-Oct-26 on AMAZON." */
    private val ON_MERCHANT = Regex("""\bon\s+([a-z][a-z0-9&'._ -]{1,40}?)(?=\.\s|,|\.$|$)""", IC)
    private val INFO = Regex("""\binfo[:\s-]+(?:upi/[a-z0-9]+/\d+/)?([a-z0-9][a-z0-9 &._-]{1,30})""", IC)
    private val NOT_A_MERCHANT = Regex("""\b(a/c|acct|account|your|card|bank|ac|you|date|dispute|customer)\b""", IC)

    private val CHANNELS = listOf(
        "ATM" to Regex("""\batm\b""", IC),
        "UPI" to Regex("""\b(upi|vpa)\b""", IC),
        "Card" to Regex("""\bcard\b""", IC),
        "NEFT" to Regex("""\bneft\b""", IC),
        "IMPS" to Regex("""\bimps\b""", IC),
        "RTGS" to Regex("""\brtgs\b""", IC),
        "Autopay" to Regex("""\b(nach|ecs|autopay|auto-debit|si\b|standing instruction|mandate)""", IC),
        "Net banking" to Regex("""\b(net\s*banking|netbanking|mob bk|mobile banking)\b""", IC),
    )

    fun parse(body: String): ParsedSms? = (classify(body) as? ParseOutcome.Transaction)?.sms

    fun classify(body: String): ParseOutcome {
        val text = body.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return ParseOutcome.Skipped(SkipReason.NOT_TRANSACTION)
        if (OTP.containsMatchIn(text)) return ParseOutcome.Skipped(SkipReason.OTP)
        if (FAILED.containsMatchIn(text)) return ParseOutcome.Skipped(SkipReason.FAILED)
        if (NOT_YET.containsMatchIn(text)) return ParseOutcome.Skipped(SkipReason.NOT_YET)
        if (PROMO.containsMatchIn(text)) return ParseOutcome.Skipped(SkipReason.PROMO)

        val type = direction(text) ?: return ParseOutcome.Skipped(SkipReason.NOT_TRANSACTION)
        val amount = amount(text) ?: return ParseOutcome.Skipped(SkipReason.NO_AMOUNT)
        if (amount <= 0) return ParseOutcome.Skipped(SkipReason.NO_AMOUNT)

        val acc = ACCOUNT.find(text)
        val isCard = CREDIT_CARD.containsMatchIn(text) ||
            (acc?.groupValues?.get(1)?.equals("card", ignoreCase = true) == true && !DEBIT_CARD.containsMatchIn(text))

        return ParseOutcome.Transaction(
            ParsedSms(
                type = type,
                amount = amount,
                accountLast4 = acc?.groupValues?.get(2)?.takeLast(4),
                merchant = merchant(text, type, isCard),
                refNo = ref(text),
                balance = BALANCE.find(text)?.groupValues?.get(1)?.let(::toPaise),
                isRefund = type == TxnType.CREDIT && REFUND.containsMatchIn(text),
                isCard = isCard,
                channel = CHANNELS.firstOrNull { it.second.containsMatchIn(text) }?.first,
            ),
        )
    }

    /** Whichever direction word comes first wins: "Rs 500 debited from you and credited to X" is a debit. */
    private fun direction(text: String): TxnType? {
        val d = DEBIT.find(text)?.range?.first
        val c = CREDIT.find(text)?.range?.first
        return when {
            d == null && c == null -> null
            c == null -> TxnType.DEBIT
            d == null -> TxnType.CREDIT
            d < c -> TxnType.DEBIT
            else -> TxnType.CREDIT
        }
    }

    /** First amount that is not a balance or limit figure. */
    private fun amount(text: String): Paise? {
        val balanceStarts = BALANCE.findAll(text).map { it.groups[1]!!.range.first }.toSet()
        val fromCurrency = AMOUNT.findAll(text).firstOrNull { m ->
            val numStart = m.groups[1]!!.range.first
            val before = text.substring(maxOf(0, m.range.first - 16), m.range.first).lowercase()
            numStart !in balanceStarts && "limit" !in before && "lmt" !in before && "bal" !in before
        }
        val raw = fromCurrency?.groupValues?.get(1) ?: AMOUNT_AFTER_VERB.find(text)?.groupValues?.get(1)
        return raw?.let(::toPaise)
    }

    private fun ref(text: String): String? {
        REF_UPI_BRACKET.find(text)?.let { return it.groupValues[1] }
        REF_UPI_COLON.find(text)?.let { return it.groupValues[1] }
        return REF.findAll(text).map { it.groupValues[1] }.firstOrNull { v -> v.any(Char::isDigit) }
    }

    private fun merchant(text: String, type: TxnType, isCard: Boolean): String? {
        VPA.find(text)?.let { return it.groupValues[1].lowercase() }
        val candidates = buildList {
            if (type == TxnType.DEBIT) PAYEE_CREDITED.find(text)?.let { add(it.groupValues[1]) }
            AT_MERCHANT.find(text)?.let { add(it.groupValues[1]) }
            if (type == TxnType.DEBIT) TO_MERCHANT.findAll(text).forEach { add(it.groupValues[1]) }
            else FROM_MERCHANT.findAll(text).forEach { add(it.groupValues[1]) }
            if (isCard) ON_MERCHANT.findAll(text).forEach { add(it.groupValues[1]) }
            INFO.find(text)?.let { add(it.groupValues[1]) }
        }
        return candidates
            .map { it.trim().trimEnd('.', '-', ' ') }
            .firstOrNull { it.length >= 2 && !NOT_A_MERCHANT.containsMatchIn(it) && !it.first().isDigit() }
            ?.let(::tidy)
    }

    private fun tidy(s: String): String =
        s.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() }) w
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }

    fun toPaise(raw: String): Paise? = runCatching {
        BigDecimal(raw.replace(",", "")).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }.getOrNull()
}
