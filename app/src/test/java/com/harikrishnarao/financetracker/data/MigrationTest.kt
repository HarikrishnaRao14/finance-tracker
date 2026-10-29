package com.harikrishnarao.financetracker.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.harikrishnarao.financetracker.domain.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Updating the app must never lose data. This builds a version-1 database exactly as the
 * first prototype created it, then opens it with the current code. Room checks every
 * table and column after the migration, so any mistake fails here instead of on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`type` TEXT NOT NULL, `category` TEXT NOT NULL, `merchant` TEXT, `accountLast4` TEXT, `bankName` TEXT, " +
            "`senderCode` TEXT, `refNo` TEXT, `timestamp` INTEGER NOT NULL, `source` TEXT NOT NULL, `isRefund` INTEGER NOT NULL, " +
            "`note` TEXT, `smsId` INTEGER)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_smsId` ON `transactions` (`smsId`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_refNo` ON `transactions` (`refNo`)",
        "CREATE TABLE IF NOT EXISTS `merchant_rules` (`merchantKey` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`merchantKey`))",
        "CREATE TABLE IF NOT EXISTS `tracked_senders` (`code` TEXT NOT NULL, `bankName` TEXT NOT NULL, `tracked` INTEGER NOT NULL, PRIMARY KEY(`code`))",
        "CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`dueDay` INTEGER NOT NULL, `isAutopay` INTEGER NOT NULL, `reminderOn` INTEGER NOT NULL, `remindDaysBefore` INTEGER NOT NULL, " +
            "`lastPaidMonth` TEXT)",
        "CREATE TABLE IF NOT EXISTS `budget_limits` (`category` TEXT NOT NULL, `limit` INTEGER NOT NULL, PRIMARY KEY(`category`))",
    )

    @Test
    fun `version 1 data survives the update to version 2`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val file = ctx.getDatabasePath(name)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1Schema.forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO transactions (amount, type, category, merchant, accountLast4, bankName, senderCode, refNo, timestamp, source, isRefund, note, smsId) " +
                    "VALUES (11000, 'DEBIT', 'FOOD', 'Canteen', '719', 'ICICI Bank', 'ICICIB', '529812345678', 1761700000000, 'SMS', 0, NULL, 42)",
            )
            db.execSQL("INSERT INTO bills (name, amount, dueDay, isAutopay, reminderOn, remindDaysBefore, lastPaidMonth) VALUES ('Internet', 99900, 8, 1, 1, 3, NULL)")
            db.execSQL("INSERT INTO merchant_rules (merchantKey, category) VALUES ('canteen', 'FOOD')")
            db.execSQL("INSERT INTO budget_limits (category, `limit`) VALUES ('FOOD', 450000)")
            db.execSQL("INSERT INTO tracked_senders (code, bankName, tracked) VALUES ('ICICIB', 'ICICI Bank', 1)")
            db.version = 1
        }

        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val t = room.transactions().byId(1)!!
                assertEquals(11000L, t.amount)
                assertEquals(Category.FOOD, t.category)
                assertEquals("529812345678", t.refNo)
                assertFalse(t.isCard)
                assertFalse(t.possibleDuplicate)
                assertNull(t.rawText)

                assertEquals(1, room.bills().all().size)
                assertEquals(Category.FOOD, room.rules().all().single().category)
                assertEquals(1, room.senders().all().size)

                // New v2 tables work.
                room.accounts().insertIfNew(AccountEntity(AccountEntity.key("ICICI Bank", "719"), "ICICI Bank", "719", false))
                room.accounts().rename(AccountEntity.key("ICICI Bank", "719"), "ICICI Salary")
                room.payees().upsert(PayeeName("canteen", "Office canteen lunch"))
                room.smsLog().upsert(SmsLogEntry(42, 1761700000000, "ICICIB", "ICICI Bank", "text", SmsVerdict.ADDED, null, 1))
                assertEquals(listOf(42L), room.transactions().importedSmsIds())
            }
        } finally {
            room.close()
            ctx.deleteDatabase(name)
        }
    }
}
