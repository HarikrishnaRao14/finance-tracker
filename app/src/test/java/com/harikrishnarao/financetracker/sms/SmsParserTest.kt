package com.harikrishnarao.financetracker.sms

import com.harikrishnarao.financetracker.domain.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sample messages follow common Indian bank formats with made-up numbers.
 * Real (masked) samples from the user's phone get added here during SMS testing.
 */
class SmsParserTest {

    private fun parse(s: String) = SmsParser.parse(s)

    // ---------- Must be read as transactions ----------

    @Test fun `HDFC style UPI debit`() {
        val p = parse("Sent Rs.340.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 27/10/26\nRef 430012345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(34000L, p.amount)
        assertEquals("1234", p.accountLast4)
        assertEquals("Swiggy", p.merchant)
        assertEquals("430012345678", p.refNo)
    }

    @Test fun `HDFC style credit from VPA with bracket UPI ref`() {
        val p = parse("Credit Alert!\nRs.40,000.00 credited to HDFC Bank A/c XX1234 on 26-10-26 from VPA employer@okaxis (UPI 430098765432)")!!
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals(4_000_000L, p.amount)
        assertEquals("1234", p.accountLast4)
        assertEquals("employer@okaxis", p.merchant)
        assertEquals("430098765432", p.refNo)
        assertFalse(p.isRefund)
    }

    @Test fun `SBI style debit with amount after verb and no currency`() {
        val p = parse("Dear UPI user A/C X5678 debited by 500.0 on date 27Oct26 trf to ZOMATO LTD Refno 430011112222. If not u? call 1800111109. -SBI")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(50000L, p.amount)
        assertEquals("5678", p.accountLast4)
        assertEquals("Zomato LTD", p.merchant)
        assertEquals("430011112222", p.refNo)
    }

    @Test fun `card spend ignores the available limit amount`() {
        val p = parse("INR 1,499.00 spent on ICICI Bank Card XX9012 on 26-Oct-26 at AMAZON PAY IN. Avl Limit: INR 45,000.00. If not you, call 1800 2662")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(149900L, p.amount)
        assertEquals("9012", p.accountLast4)
        assertTrue(p.merchant!!.startsWith("Amazon"))
    }

    @Test fun `ATM withdrawal with balance`() {
        val p = parse("Rs 2000 withdrawn from A/c no. XX3456 at ATM SECTOR 17 on 25-10-2026. Avl Bal Rs 12,345.67")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(200000L, p.amount)
        assertEquals("3456", p.accountLast4)
        assertEquals(1234567L, p.balance)
    }

    @Test fun `balance appearing first is not taken as the amount`() {
        val p = parse("Avl Bal Rs 9,000.00. Rs 250.00 debited from A/c XX1234 to BLINKIT on 27-10-26")!!
        assertEquals(25000L, p.amount)
    }

    @Test fun `refund is a credit marked as refund`() {
        val p = parse("Refund of Rs 499.00 for your Amazon order has been credited to your a/c XX1234 on 26-10-26.")!!
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals(49900L, p.amount)
        assertTrue(p.isRefund)
    }

    @Test fun `VPA payee and UPI Ref no`() {
        val p = parse("Rs.1200.00 debited from a/c XX4321 on 27-10-26 to VPA ramesh123@oksbi (UPI Ref no 430055556666).")!!
        assertEquals("ramesh123@oksbi", p.merchant)
        assertEquals("430055556666", p.refNo)
        assertEquals("4321", p.accountLast4)
    }

    @Test fun `debit word before credit word means debit`() {
        val p = parse("Rs 500 debited from A/c XX1111 and credited to A/c XX2222 on 27-10-26")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals("1111", p.accountLast4)
    }

    @Test fun `rupee symbol and paise`() {
        assertEquals(1_200_000L, parse("₹12,000 debited from A/c XX1234 to LANDLORD on 01-11-26")!!.amount)
        assertEquals(123450L, parse("INR 1,234.50 debited from A/c XX1234 on 01-11-26")!!.amount)
    }

    // ---------- Must be ignored ----------

    @Test fun `OTP is ignored even with amount and merchant`() {
        assertNull(parse("123456 is your OTP for txn of Rs 2,500 at AMAZON. Valid 10 mins. Do not share. -HDFC Bank"))
    }

    @Test fun `loan promo is ignored`() {
        assertNull(parse("Congratulations! You are pre-approved for a Personal Loan of up to Rs 5,00,000. Apply now"))
    }

    @Test fun `balance alert is ignored`() {
        assertNull(parse("Your A/c XX1234 has an available balance of Rs 25,000.00 as on 27-10-26."))
    }

    @Test fun `failed payment is ignored`() {
        assertNull(parse("Your UPI transaction of Rs 300 to merchant@ybl has failed. Amount will be credited back if debited."))
    }

    @Test fun `future autopay notice is ignored`() {
        assertNull(parse("Your Netflix autopay of Rs 649 will be debited on 30-10-26 from A/c XX1234."))
    }

    @Test fun `card statement due is ignored`() {
        assertNull(parse("HDFC Credit Card statement: Total Amount Due Rs 5,000. Min amount due Rs 250. Due date 05-11-26."))
    }

    @Test fun `collect request is ignored`() {
        assertNull(parse("ramesh@oksbi has requested Rs 500 from you. Pay via any UPI app."))
    }

    @Test fun `empty and random text is ignored`() {
        assertNull(parse(""))
        assertNull(parse("Your parcel will arrive today"))
    }

    @Test fun `word containing rs is not read as rupees`() {
        val p = parse("Dear users A/C X5678 debited by 75 on 27Oct26 trf to TEA STALL Refno 430011119999")
        assertNotNull(p)
        assertEquals(7500L, p!!.amount)
    }

    // ---------- Formats seen on the user's phone (ICICI, Union Bank, ICICI credit card) ----------

    @Test fun `ICICI UPI debit gives payee and UPI ref`() {
        val p = parse("ICICI Bank Acct XX719 debited for Rs 110.00 on 29-Oct-26; CANTEEN SERVICES credited. UPI:530212345678. Call 18002662 for dispute. SMS BLOCK 719 to 9215676766.")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(11000L, p.amount)
        assertEquals("719", p.accountLast4)
        assertEquals("Canteen Services", p.merchant)
        assertEquals("530212345678", p.refNo)
        assertEquals("UPI", p.channel)
        assertFalse(p.isCard)
    }

    @Test fun `ICICI credit from a person`() {
        val p = parse("Dear Customer, Acct XX719 is credited with Rs 500.00 on 28-Oct-26 from PRATIBHA RAO AL. UPI:529812345678-ICICI Bank.")!!
        assertEquals(TxnType.CREDIT, p.type)
        assertEquals("Pratibha RAO AL", p.merchant)
        assertEquals("529812345678", p.refNo)
    }

    @Test fun `ICICI credit card spend is a card transaction`() {
        val p = parse("INR 1,234.00 spent using ICICI Bank Card XX5678 on 29-Oct-26 on AMAZON. Avl Limit: INR 50,000.00. If not you, call 1800 2662/SMS BLOCK 5678 to 9215676766.")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(123400L, p.amount)
        assertEquals("5678", p.accountLast4)
        assertTrue(p.isCard)
        assertEquals("Amazon", p.merchant)
        assertEquals("Card", p.channel)
    }

    @Test fun `credit card bill payment received`() {
        val p = parse("Payment of Rs 5,000.00 has been received on your ICICI Bank Credit Card Account XX5678 on 29-Oct-26. Thank you.")!!
        assertEquals(TxnType.CREDIT, p.type)
        assertTrue(p.isCard)
        assertEquals("5678", p.accountLast4)
    }

    @Test fun `debit card spend is not a credit card`() {
        val p = parse("Rs 250.00 spent on your Debit Card XX4444 at DMART on 29-10-26.")!!
        assertFalse(p.isCard)
    }

    @Test fun `Union Bank style debit with Rs colon`() {
        val p = parse("A/c *1234 Debited for Rs:110.00 on 29-10-2026 12:46:10 by Mob Bk ref no 123456789012 Avl Bal Rs:1234.56.If not you, Call 1800222243 -Union Bank of India")!!
        assertEquals(TxnType.DEBIT, p.type)
        assertEquals(11000L, p.amount)
        assertEquals("1234", p.accountLast4)
        assertEquals("123456789012", p.refNo)
        assertEquals(123456L, p.balance)
    }

    @Test fun `skip reasons are explained`() {
        assertEquals(ParseOutcome.Skipped(SkipReason.OTP), SmsParser.classify("123456 is your OTP. Do not share."))
        assertEquals(ParseOutcome.Skipped(SkipReason.PROMO), SmsParser.classify("You are eligible for a pre-approved loan"))
        assertEquals(ParseOutcome.Skipped(SkipReason.NOT_YET), SmsParser.classify("Rs 649 will be debited on 30-10-26 from A/c XX1234"))
        assertEquals(ParseOutcome.Skipped(SkipReason.FAILED), SmsParser.classify("Txn of Rs 300 failed"))
        assertEquals(ParseOutcome.Skipped(SkipReason.NOT_TRANSACTION), SmsParser.classify("Your A/c XX1234 balance is Rs 500"))
        assertEquals(ParseOutcome.Skipped(SkipReason.NO_AMOUNT), SmsParser.classify("Your account has been debited"))
    }
}
