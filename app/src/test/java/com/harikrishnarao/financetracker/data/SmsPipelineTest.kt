package com.harikrishnarao.financetracker.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnType
import com.harikrishnarao.financetracker.sms.RawSms
import com.harikrishnarao.financetracker.sms.SmsSource
import com.harikrishnarao.financetracker.ui.incomeOf
import com.harikrishnarao.financetracker.ui.spendByCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Fake inbox: the same messages a phone would return, filtered by date like the real reader. */
class FakeSms(var granted: Boolean = true) : SmsSource {
    val inbox = mutableListOf<RawSms>()
    override fun hasPermission() = granted
    override fun readInbox(sinceMillis: Long) = inbox.filter { it.date > sinceMillis }.sortedBy { it.date }
}

/**
 * Runs bank SMS through the whole pipeline (parse, dedupe, accounts, transfers, log, DB)
 * exactly like the app does, and checks the final result a user would see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmsPipelineTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var sms: FakeSms
    private lateinit var repo: FinanceRepository
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File

    private val min = 60_000L
    private val now = System.currentTimeMillis()
    private val t0 = now - 5 * 60 * min // five hours ago
    private var nextId = 1L

    private fun add(sender: String, body: String, at: Long) { sms.inbox += RawSms(nextId++, sender, body, at) }

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(ctx.cacheDir, "pipeline-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "s.preferences_pb") })
        sms = FakeSms()
        repo = FinanceRepository(db, settings, sms)
        repo.saveSenders(
            listOf(
                DetectedSender("ICICIB", "ICICI Bank", true, 5),
                DetectedSender("UNIONB", "Union Bank of India", true, 2),
                DetectedSender("HDFCBK", "HDFC Bank", true, 1),
            ),
            chosen = setOf("ICICIB", "UNIONB"), // HDFC found but NOT ticked by the user
        )
        settings.setSmsEnabled(true)
        Unit
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        dir.deleteRecursively()
    }

    private suspend fun all() = db.transactions().between(0, Long.MAX_VALUE)
    private suspend fun log() = db.smsLog().observeRecent(500).first()

    private fun icici110(at: Long, ref: String) =
        add("AX-ICICIB-S", "ICICI Bank Acct XX719 debited for Rs 110.00 on 29-Oct-26; CANTEEN SERVICES credited. UPI:$ref. Call 18002662 for dispute. SMS BLOCK 719 to 9215676766.", at)

    @Test fun `same UPI ref twice is one payment`() = runBlocking {
        icici110(t0, "530200000001")
        icici110(t0 + 2 * min, "530200000001")
        val r = repo.syncSms()
        assertEquals(1, all().size)
        assertEquals(1, r.added)
        assertTrue(r.skippedSummary.contains("1 duplicate"))
    }

    @Test fun `late second alert without ref is a possible duplicate and not counted`() = runBlocking {
        icici110(t0, "530200000002")
        add("JD-ICICIB-T", "Rs 110.00 debited from your ICICI Bank Acct XX719 on 29-Oct-26.", t0 + 16 * min)
        repo.syncSms()
        val txns = all()
        assertEquals(2, txns.size)
        assertEquals(1, txns.count { it.possibleDuplicate })
        assertEquals(110_00L, spendByCategory(txns).values.sum()) // counted once

        // The user says "Keep, I paid twice": now both count.
        repo.keepTransaction(txns.single { it.possibleDuplicate })
        assertEquals(220_00L, spendByCategory(all()).values.sum())
    }

    @Test fun `two real lunches with different refs both count`() = runBlocking {
        icici110(t0, "530200000003")
        icici110(t0 + 20 * min, "530200000004")
        repo.syncSms()
        assertEquals(2, all().count { !it.possibleDuplicate })
    }

    @Test fun `OTPs promos untracked banks and personal numbers never become spends`() = runBlocking {
        add("AX-ICICIB", "123456 is the OTP for your txn of Rs 2,500 at AMAZON. Do not share.", t0)
        add("VM-ICICIB", "Congratulations! You are pre-approved for a Personal Loan of up to Rs 5,00,000. Apply now", t0 + min)
        add("AX-HDFCBK", "Sent Rs.340.00 From HDFC Bank A/C *1234 To SWIGGY On 29/10/26 Ref 430012345678", t0 + 2 * min) // not ticked
        add("+919876543210", "Rs 5000 debited from your A/c XX719. Call me", t0 + 3 * min) // a person, not a bank
        val r = repo.syncSms()
        assertTrue(all().isEmpty())
        assertEquals(2, r.read) // only the two ICICI messages were looked at
        val reasons = log().map { it.reason }
        assertTrue(reasons.any { it?.contains("OTP") == true })
        assertTrue(reasons.any { it?.contains("promotion") == true })
    }

    @Test fun `accounts are separated, card bill payment is an own transfer`() = runBlocking {
        add("AX-UNIONB", "A/c *1234 Debited for Rs:500.00 on 29-10-2026 12:46:10 by Mob Bk ref no 123456789012 Avl Bal Rs:1234.56.If not you, Call 1800222243 -Union Bank of India", t0)
        add("AX-ICICIB", "INR 1,234.00 spent using ICICI Bank Card XX5678 on 29-Oct-26 on AMAZON. Avl Limit: INR 50,000.00.", t0 + min)
        add("AX-ICICIB", "ICICI Bank Acct XX719 debited for Rs 5000.00 on 29-Oct-26; ICICI CREDIT CARD credited. UPI:531100000001.", t0 + 10 * min)
        add("AX-ICICIB", "Payment of Rs 5,000.00 has been received on your ICICI Bank Credit Card Account XX5678 on 29-Oct-26.", t0 + 30 * min)
        add("AX-ICICIB", "Dear Customer, Acct XX719 is credited with Rs 40,000.00 on 29-Oct-26 from ACME PVT LTD. NEFT ref NEFT00011122", t0 + 40 * min)
        repo.syncSms()

        val accounts = db.accounts().observeAll().first().associateBy { it.key }
        assertEquals(setOf("Union Bank of India|1234", "ICICI Bank|5678", "ICICI Bank|719"), accounts.keys)
        assertTrue(accounts.getValue("ICICI Bank|5678").isCard)
        assertFalse(accounts.getValue("ICICI Bank|719").isCard)

        val txns = all()
        assertEquals(2, txns.count { it.category == Category.TRANSFER }) // bill payment pair
        assertEquals(500_00L + 1_234_00L, spendByCategory(txns).values.sum()) // Union + Amazon only
        assertEquals(40_000_00L, incomeOf(txns))
        assertEquals(123456L, txns.single { it.accountLast4 == "1234" }.balance)
    }

    @Test fun `syncing again adds nothing`() = runBlocking {
        icici110(t0, "530200000005")
        repo.syncSms()
        assertEquals(0, repo.syncSms().added)
        assertEquals(0, repo.rescan().added)
        assertEquals(1, all().size)
    }

    @Test fun `removed transaction never comes back, even after rebuild`() = runBlocking {
        icici110(t0, "530200000006")
        icici110(t0 + 3 * 60 * min, "530200000007")
        repo.syncSms()
        val wrong = all().first()
        repo.deleteTransaction(wrong)
        repo.rescan()
        assertEquals(1, all().size)
        repo.rebuildFromSms()
        assertEquals(1, all().size)
        assertTrue(all().none { it.smsId == wrong.smsId })
    }

    @Test fun `category choice for a payee is learned for future SMS`() = runBlocking {
        icici110(t0, "530200000008")
        repo.syncSms()
        val first = all().single()
        assertEquals(Category.FOOD, first.category) // "canteen" is a food word
        repo.recategorise(first, Category.OTHER, applyToMerchant = true)

        icici110(t0 + 2 * 60 * min, "530200000009")
        repo.syncSms()
        assertTrue(all().all { it.category == Category.OTHER })
    }

    @Test fun `no permission means no crash and nothing read`() = runBlocking {
        sms.granted = false
        icici110(t0, "530200000010")
        val r = repo.syncSms()
        assertEquals(0, r.read)
        assertTrue(all().isEmpty())
    }

    @Test fun `every bank SMS gets a log entry with the outcome`() = runBlocking {
        icici110(t0, "530200000011")
        icici110(t0 + min, "530200000011")
        add("AX-ICICIB", "Your A/c XX719 has an available balance of Rs 25,000.00", t0 + 2 * min)
        repo.syncSms()
        val verdicts = log().map { it.verdict }.toSet()
        assertEquals(setOf(SmsVerdict.ADDED, SmsVerdict.DUPLICATE, SmsVerdict.SKIPPED), verdicts)
        val added = log().single { it.verdict == SmsVerdict.ADDED }
        assertNotNull(added.transactionId)
        assertEquals(TxnType.DEBIT, db.transactions().byId(added.transactionId!!)!!.type)
    }
}
