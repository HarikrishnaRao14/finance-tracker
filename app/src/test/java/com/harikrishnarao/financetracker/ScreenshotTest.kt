package com.harikrishnarao.financetracker

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.harikrishnarao.financetracker.data.AccountEntity
import com.harikrishnarao.financetracker.data.AppTheme
import com.harikrishnarao.financetracker.data.Bill
import com.harikrishnarao.financetracker.data.DetectedSender
import com.harikrishnarao.financetracker.data.SmsLogEntry
import com.harikrishnarao.financetracker.data.SmsVerdict
import com.harikrishnarao.financetracker.data.TransactionEntity
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.sms.SmsParser
import com.harikrishnarao.financetracker.update.UpdateInfo
import com.harikrishnarao.financetracker.update.UpdateState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Opens the real app on the JVM with sample data and takes a picture of every screen.
 * The pictures are published with each CI run so the UI is checked before a release.
 * It also fails if any screen cannot be opened or crashes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val problems = mutableListOf<String>()

    private fun save(name: String, node: SemanticsNodeInteraction) {
        compose.waitForIdle()
        val bmp = node.captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Runs one step; a failure is recorded (with a screenshot of the moment) and the run continues. */
    private fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            problems += "$name: ${t::class.simpleName}: ${t.message?.lineSequence()?.firstOrNull()}"
            runCatching { save("FAILED_$name", compose.onRoot()) }
        }
    }

    private fun shot(name: String) = step(name) { save(name, compose.onRoot()) }
    private fun dialogShot(name: String) = step(name) { save(name, compose.onNode(isDialog())) }

    private fun waitForText(text: String, timeout: Long = 10_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) =
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text, substring = true, ignoreCase = true))

    private fun click(text: String) = compose.onAllNodesWithText(text, substring = false).onFirst().performClick()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Settings are saved on a background thread; give the screen a moment to pick them up. */
    private fun settle() {
        repeat(5) { Thread.sleep(100); compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
    }

    @Test
    fun everyScreen() {
        val app = compose.activity.application as FinanceApp
        val c = app.container

        step("onboarding") { waitForText("Welcome to Arthix"); save("01_onboarding", compose.onRoot()) }

        runBlocking { seed(c) }

        step("home") { waitForText("CASH FLOW"); save("02_home", compose.onRoot()) }
        step("home_bottom") { scrollTo("Recent Transactions"); save("03_home_recent", compose.onRoot()) }

        step("spends") { click("Spends"); waitForText("Sync now"); save("04_spends", compose.onRoot()) }
        step("spends_list") { scrollTo("Swiggy"); save("05_spends_list", compose.onRoot()) }
        step("statement") {
            scrollTo("All accounts")
            click("All accounts")
            waitForText("ICICI Salary")
            click("ICICI Salary")
            waitForText("Statement")
            save("06_statement_icici", compose.onRoot())
            click("ICICI Salary") // reopen menu and go back to all accounts
            click("All accounts")
        }
        step("edit_dialog") {
            scrollTo("Swiggy")
            click("Swiggy")
            compose.waitUntil(5_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
            save("07_edit_dialog", compose.onNode(isDialog()))
            click("Cancel")
        }

        step("analysis") { click("Analysis"); waitForText("Where your money went"); save("08_analysis", compose.onRoot()) }
        step("analysis_budget") { scrollTo("Smart budget"); save("09_analysis_budget", compose.onRoot()) }

        step("bills") { click("Bills"); waitForText("Internet"); save("10_bills", compose.onRoot()) }

        step("settings") {
            click("Home")
            waitForText("CASH FLOW")
            click("HR")
            waitForText("Your name")
            save("11_settings", compose.onRoot())
        }
        step("settings_accounts") { scrollTo("Your accounts and cards"); save("12_settings_accounts", compose.onRoot()) }
        step("sms_activity") {
            scrollTo("SMS activity: what happened")
            click("SMS activity: what happened to each SMS")
            waitForText("Bank SMS from your tracked banks")
            save("13_sms_activity", compose.onRoot())
            back()
            back()
        }

        step("update_banner") {
            c.updater.showForTest(UpdateState.Available(UpdateInfo(99, "0.2.99", "New look and fixes", "https://example.invalid")))
            waitForText("is ready")
            save("14_update_banner", compose.onRoot())
            c.updater.dismiss()
        }
        step("theme_crimson") {
            runBlocking { c.settings.setTheme(AppTheme.CRIMSON) }
            settle()
            save("15_home_crimson", compose.onRoot())
        }
        step("theme_nebula") {
            runBlocking { c.settings.setTheme(AppTheme.NEBULA) }
            settle()
            save("16_home_nebula", compose.onRoot())
            runBlocking { c.settings.setTheme(AppTheme.COBALT) }
            settle()
        }
        step("quick_add") {
            compose.onNodeWithContentDescription("Add a transaction").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
            save("17_add_dialog", compose.onNode(isDialog()))
            click("Cancel")
        }

        File(outDir, "problems.txt").writeText(problems.joinToString("\n").ifEmpty { "none" })
        check(problems.isEmpty()) { "Screens with problems:\n" + problems.joinToString("\n") }
    }

    // ---------------- Sample data, like a real month ----------------

    private suspend fun seed(c: AppContainer) {
        val db = c.database
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        var sms = 1000L
        fun at(daysAgo: Long, h: Int, m: Int = 0) =
            LocalDate.now().minusDays(daysAgo).atTime(h, m).atZone(zone).toInstant().toEpochMilli().coerceAtMost(now - 60_000)

        suspend fun txn(
            amount: Long, type: TxnType, cat: Category, merchant: String?, bank: String?, last4: String?, time: Long,
            card: Boolean = false, channel: String? = "UPI", dup: Boolean = false, balance: Long? = null, source: TxnSource = TxnSource.SMS,
        ) {
            db.transactions().insert(
                TransactionEntity(
                    amount = amount, type = type, category = cat, merchant = merchant, accountLast4 = last4, bankName = bank,
                    senderCode = if (bank == null) null else "ICICIB", refNo = null, timestamp = time, source = source,
                    smsId = if (source == TxnSource.SMS) sms++ else null, isCard = card, channel = if (source == TxnSource.SMS) channel else null,
                    possibleDuplicate = dup, balance = balance,
                    rawText = if (source == TxnSource.SMS) "ICICI Bank Acct XX$last4 debited for Rs ${amount / 100}.00; ${merchant ?: ""} credited. UPI:5302${sms}" else null,
                ),
            )
        }

        c.repository.saveSenders(
            listOf(DetectedSender("ICICIB", "ICICI Bank", true, 40), DetectedSender("UNIONB", "Union Bank of India", true, 12)),
            setOf("ICICIB", "UNIONB"),
        )
        db.accounts().insertIfNew(AccountEntity("ICICI Bank|719", "ICICI Bank", "719", false, "ICICI Salary"))
        db.accounts().insertIfNew(AccountEntity("ICICI Bank|5678", "ICICI Bank", "5678", true, "ICICI Credit Card"))
        db.accounts().insertIfNew(AccountEntity("Union Bank of India|1234", "Union Bank of India", "1234", false, null))

        val icici = "ICICI Bank"
        val union = "Union Bank of India"
        // This month
        txn(40_000_00, TxnType.CREDIT, Category.INCOME, "Acme Pvt Ltd", icici, "719", at(6, 10), channel = "NEFT", balance = 52_300_00)
        for (d in 0L..4L) txn(110_00, TxnType.DEBIT, Category.UNCATEGORISED, "Sodexo Pay", icici, "719", at(d, 12, 50 + d.toInt()))
        txn(110_00, TxnType.DEBIT, Category.UNCATEGORISED, "Sodexo Pay", icici, "719", at(0, 13, 10), dup = true)
        txn(340_00, TxnType.DEBIT, Category.FOOD, "Swiggy", icici, "719", at(1, 20, 15))
        txn(1_499_00, TxnType.DEBIT, Category.SHOPPING, "Amazon", icici, "5678", at(2, 18), card = true, channel = "Card")
        txn(215_00, TxnType.DEBIT, Category.TRAVEL, "Uber", union, "1234", at(3, 9), balance = 8_420_00)
        txn(2_300_00, TxnType.DEBIT, Category.BILLS, "Mahadiscom Electricity", icici, "719", at(4, 11))
        txn(5_000_00, TxnType.DEBIT, Category.TRANSFER, null, icici, "719", at(5, 19))
        txn(5_000_00, TxnType.CREDIT, Category.TRANSFER, null, icici, "5678", at(5, 19, 20), card = true, channel = "Card")
        txn(60_00, TxnType.DEBIT, Category.FOOD, "Tea", null, null, at(0, 16), source = TxnSource.MANUAL)
        // Previous months, for the smart budget
        for (mAgo in 1L..3L) {
            val base = 30 * mAgo
            txn(40_000_00, TxnType.CREDIT, Category.INCOME, "Acme Pvt Ltd", icici, "719", at(base, 10), channel = "NEFT")
            txn(5_200_00, TxnType.DEBIT, Category.FOOD, "Swiggy", icici, "719", at(base + 2, 20))
            txn(3_600_00, TxnType.DEBIT, Category.SHOPPING, "Amazon", icici, "5678", at(base + 3, 18), card = true)
            txn(2_250_00, TxnType.DEBIT, Category.BILLS, "Mahadiscom Electricity", icici, "719", at(base + 4, 11))
            txn(1_900_00, TxnType.DEBIT, Category.TRAVEL, "Uber", union, "1234", at(base + 5, 9))
        }

        val today = LocalDate.now()
        c.repository.saveBill(Bill(name = "Internet", amount = 999_00, dueDay = today.plusDays(2).dayOfMonth, isAutopay = true))
        c.repository.saveBill(Bill(name = "Electricity", amount = 2_300_00, dueDay = today.plusDays(9).dayOfMonth, isAutopay = false))

        db.smsLog().upsert(SmsLogEntry(1000, at(0, 12, 50), "ICICIB", icici, "ICICI Bank Acct XX719 debited for Rs 110.00; SODEXO PAY credited. UPI:530212345678.", SmsVerdict.ADDED, null, 2))
        db.smsLog().upsert(SmsLogEntry(9001, at(0, 12, 49), "ICICIB", icici, "123456 is the OTP for your transaction of Rs 110.00. Do not share it.", SmsVerdict.SKIPPED, "OTP / security code", null))
        db.smsLog().upsert(SmsLogEntry(9002, at(0, 13, 10), "ICICIB", icici, "Rs 110.00 debited from your ICICI Bank Acct XX719.", SmsVerdict.POSSIBLE_DUPLICATE, "Same amount and account within an hour of another payment", 7))
        db.smsLog().upsert(SmsLogEntry(9003, at(1, 9), "UNIONB", union, "Get a pre-approved personal loan of up to Rs 5,00,000. Apply now!", SmsVerdict.SKIPPED, "Offer or promotion", null))

        c.settings.setUserName("Harikrishna Rao")
        c.settings.setSmsEnabled(true)
        c.settings.setParserVersion(SmsParser.VERSION)
        c.settings.recordSync(now - hour, now - hour, read = 6, added = 4, skipped = "1 OTP · 1 promo")
        c.settings.setOnboardingDone(smsEnabled = true)
    }
}
