package com.harikrishnarao.financetracker.sms

import com.harikrishnarao.financetracker.domain.ParsedSms
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.TxnType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Turns one bank SMS body into a transaction, or returns null when the message is not a
 * completed transaction (OTP, promo, failed payment, "will be debited" notice, balance alert...).
 *
 * Pure Kotlin with no Android calls, so every rule is covered by plain JVM unit tests.
 */
object SmsParser {

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
        """\b(debited|debit of|spent|paid|withdrawn|withdrawal|sent|purchased?|deducted|transferred to|""" +
            """debit alert)\b""",
        IC,
    )
    private val CREDIT = Regex("""\b(credited|credit of|received|deposited|refund(?:ed)?|reversed|reversal|credit alert)\b""", IC)
    private val REFUND = Regex("""\b(refund(?:ed)?|reversed|reversal)\b""", IC)

    // ---- Fields ----
    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val AMOUNT = Regex("""(?:\brs\.?|\binr|₹)\s*:?\s*$NUM""", IC)
    private val AMOUNT_AFTER_VERB = Regex("""\b(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*$NUM""", IC)
    private val BALANCE = Regex("""\b(?:avl|avbl|available|clear|closing)\.?\s*bal(?:ance)?\.?\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*:?\s*$NUM""", IC)
    private val ACCOUNT = Regex(
        """\b(?:a/c|ac|acct|account|card)(?:\s*(?:no\.?|number|ending(?:\s*(?:with|in))?))?\s*[:.]?\s*[x*.]+\s*(\d{3,6})""",
        IC,
    )
    private val REF = Regex(
        """\b(?:upi\s*ref(?:\s*no)?|ref(?:erence)?(?:\s*(?:no|num|number|id))?|refno|utr(?:\s*no)?|rrn|""" +
            """txn\s*(?:id|no)|transaction\s*(?:id|ref(?:erence)?)(?:\s*no)?)\b\.?\s*[:#-]?\s*([a-z0-9]{6,})""",
        IC,
    )
    private val REF_UPI_BRACKET = Regex("""\(\s*upi(?:\s*ref)?[\s:]*(\d{9,})\s*\)""", IC)
    private val VPA = Regex("""\b(?:to|from|by)\s+(?:vpa\s+)?([a-z0-9._-]{2,}@[a-z]{2,})""", IC)
    private val AT_MERCHANT = Regex("""\bat\s+([a-z0-9][a-z0-9&'._ -]{1,40}?)(?=\s+on\b|\s+via\b|\s+using\b|\s+ref|\.\s|,|\.$|$)""", IC)
    private val TO_MERCHANT = Regex("""\b(?:trf to|transfer to|to)\s+([a-z][a-z0-9&'._ -]{1,40}?)(?=\s+on\b|\s+ref|\s+upi|\s+avl|\s+via\b|\.\s|,|\.$|$)""", IC)
    private val FROM_MERCHANT = Regex("""\b(?:from|by)\s+([a-z][a-z0-9&'._ -]{1,40}?)(?=\s+on\b|\s+ref|\s+upi|\s+avl|\s+via\b|\(|\.\s|,|\.$|$)""", IC)
    private val INFO = Regex("""\binfo[:\s-]+(?:upi/[a-z0-9]+/\d+/)?([a-z0-9][a-z0-9 &._-]{1,30})""", IC)
    private val NOT_A_MERCHANT = Regex("""\b(a/c|acct|account|your|card|bank|ac\b|you)""", IC)

    fun parse(body: String): ParsedSms? {
        val text = body.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return null
        if (OTP.containsMatchIn(text) || FAILED.containsMatchIn(text) || NOT_YET.containsMatchIn(text)) return null
        if (PROMO.containsMatchIn(text)) return null

        val type = direction(text) ?: return null
        val amount = amount(text) ?: return null
        if (amount <= 0) return null

        return ParsedSms(
            type = type,
            amount = amount,
            accountLast4 = ACCOUNT.find(text)?.groupValues?.get(1)?.takeLast(4),
            merchant = merchant(text, type),
            refNo = ref(text),
            balance = BALANCE.find(text)?.groupValues?.get(1)?.let(::toPaise),
            isRefund = type == TxnType.CREDIT && REFUND.containsMatchIn(text),
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
        return REF.findAll(text).map { it.groupValues[1] }.firstOrNull { v -> v.any(Char::isDigit) }
    }

    private fun merchant(text: String, type: TxnType): String? {
        VPA.find(text)?.let { return it.groupValues[1].lowercase() }
        val candidates = buildList {
            AT_MERCHANT.find(text)?.let { add(it.groupValues[1]) }
            if (type == TxnType.DEBIT) TO_MERCHANT.findAll(text).forEach { add(it.groupValues[1]) }
            else FROM_MERCHANT.findAll(text).forEach { add(it.groupValues[1]) }
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
