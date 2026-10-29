package com.harikrishnarao.financetracker.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.Paise
import com.harikrishnarao.financetracker.domain.TxnSource
import com.harikrishnarao.financetracker.domain.TxnType
import kotlinx.coroutines.flow.Flow

// ---------------- Entities ----------------

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsId"], unique = true), Index("timestamp"), Index("refNo")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Paise,
    val type: TxnType,
    val category: Category,
    val merchant: String?,
    val accountLast4: String?,
    val bankName: String?,
    val senderCode: String?,
    val refNo: String?,
    val timestamp: Long,
    val source: TxnSource,
    val isRefund: Boolean = false,
    val note: String? = null,
    /** Android SMS row id; unique, so the same SMS can never be imported twice. */
    val smsId: Long? = null,
    // ---- added in v2 ----
    @ColumnInfo(defaultValue = "0") val isCard: Boolean = false,
    val channel: String? = null,
    /** Account balance after this transaction, when the SMS mentions it. */
    val balance: Paise? = null,
    /** Looks like a late second alert of another payment. Not counted until the user keeps it. */
    @ColumnInfo(defaultValue = "0") val possibleDuplicate: Boolean = false,
    val rawText: String? = null,
)

/** Counts towards totals: not a suspected duplicate. */
val TransactionEntity.counts: Boolean get() = !possibleDuplicate

val TransactionEntity.accountKey: String?
    get() = if (bankName != null && accountLast4 != null) AccountEntity.key(bankName, accountLast4) else null

/** A category the user chose for a merchant; applied to all future SMS from it. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val category: Category,
)

/** A bank sender the user agreed to track. */
@Entity(tableName = "tracked_senders")
data class TrackedSender(
    @PrimaryKey val code: String,
    val bankName: String,
    val tracked: Boolean,
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Paise,
    /** Day of month the bill is due (1..31; clamped to the month's last day). */
    val dueDay: Int,
    val isAutopay: Boolean,
    val reminderOn: Boolean = true,
    val remindDaysBefore: Int = 3,
    /** "2026-10" when paid for that month; null if never paid. */
    val lastPaidMonth: String? = null,
)

@Entity(tableName = "budget_limits")
data class BudgetLimit(
    @PrimaryKey val category: Category,
    val limit: Paise,
)

/** A bank account or credit card found in SMS (bank + last digits). */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val key: String,
    val bankName: String,
    val last4: String,
    val isCard: Boolean,
    val nickname: String? = null,
) {
    companion object {
        fun key(bankName: String, last4: String) = "$bankName|$last4"
    }
}

val AccountEntity.label: String
    get() = nickname?.takeIf { it.isNotBlank() } ?: "$bankName ${if (isCard) "Card" else "A/c"} ··$last4"

/** A friendly name the user gave a payee, e.g. "Office canteen lunch". */
@Entity(tableName = "payee_names")
data class PayeeName(
    @PrimaryKey val merchantKey: String,
    val displayName: String,
)

enum class SmsVerdict(val label: String) {
    ADDED("Added"),
    DUPLICATE("Skipped: same payment already added"),
    POSSIBLE_DUPLICATE("Added as possible duplicate"),
    SKIPPED("Skipped"),
    REMOVED("Removed by you"),
}

/** What the app did with each bank SMS, so the user can always see why. */
@Entity(tableName = "sms_log", indices = [Index("timestamp")])
data class SmsLogEntry(
    @PrimaryKey val smsId: Long,
    val timestamp: Long,
    val senderCode: String,
    val bankName: String,
    val body: String,
    val verdict: SmsVerdict,
    val reason: String?,
    val transactionId: Long?,
)

// ---------------- DAOs ----------------

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun between(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun observeSince(from: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to")
    suspend fun near(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT smsId FROM transactions WHERE smsId IS NOT NULL")
    suspend fun importedSmsIds(): List<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE refNo = :ref")
    suspend fun countByRef(ref: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Query("UPDATE transactions SET category = :category WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, category: Category)

    @Query("UPDATE transactions SET possibleDuplicate = 0 WHERE id = :id")
    suspend fun keep(id: Long)

    @Query("SELECT * FROM transactions WHERE merchant IS NOT NULL")
    suspend fun allWithMerchant(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions WHERE source = 'SMS'")
    suspend fun deleteAllSms()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRule>

    @Upsert
    suspend fun upsert(rule: MerchantRule)
}

@Dao
interface SenderDao {
    @Query("SELECT * FROM tracked_senders ORDER BY bankName")
    fun observeAll(): Flow<List<TrackedSender>>

    @Query("SELECT * FROM tracked_senders")
    suspend fun all(): List<TrackedSender>

    @Upsert
    suspend fun upsertAll(senders: List<TrackedSender>)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY dueDay")
    fun observeAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    suspend fun all(): List<Bill>

    @Upsert
    suspend fun upsert(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_limits")
    fun observeAll(): Flow<List<BudgetLimit>>

    @Upsert
    suspend fun upsertAll(limits: List<BudgetLimit>)

    @Query("DELETE FROM budget_limits")
    suspend fun clear()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isCard, bankName, last4")
    fun observeAll(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(a: AccountEntity)

    @Query("UPDATE accounts SET nickname = :nickname WHERE `key` = :key")
    suspend fun rename(key: String, nickname: String?)

    @Query("UPDATE accounts SET isCard = :isCard WHERE `key` = :key")
    suspend fun setCard(key: String, isCard: Boolean)
}

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payee_names")
    fun observeAll(): Flow<List<PayeeName>>

    @Upsert
    suspend fun upsert(p: PayeeName)

    @Query("DELETE FROM payee_names WHERE merchantKey = :key")
    suspend fun delete(key: String)
}

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SmsLogEntry>>

    @Query("SELECT smsId FROM sms_log WHERE verdict = 'REMOVED'")
    suspend fun removedIds(): List<Long>

    @Upsert
    suspend fun upsert(e: SmsLogEntry)

    @Query("UPDATE sms_log SET verdict = :verdict WHERE transactionId = :txnId")
    suspend fun setVerdictForTxn(txnId: Long, verdict: SmsVerdict)

    @Query("DELETE FROM sms_log WHERE verdict != 'REMOVED'")
    suspend fun clearExceptRemoved()

    @Query("DELETE FROM sms_log WHERE timestamp < :before AND verdict != 'REMOVED'")
    suspend fun trim(before: Long)
}

// ---------------- Database ----------------

class Converters {
    @TypeConverter fun fromType(v: TxnType): String = v.name
    @TypeConverter fun toType(v: String): TxnType = TxnType.valueOf(v)
    @TypeConverter fun fromSource(v: TxnSource): String = v.name
    @TypeConverter fun toSource(v: String): TxnSource = TxnSource.valueOf(v)
    @TypeConverter fun fromCategory(v: Category): String = v.name
    @TypeConverter fun toCategory(v: String): Category =
        Category.entries.firstOrNull { it.name == v } ?: Category.UNCATEGORISED
    @TypeConverter fun fromVerdict(v: SmsVerdict): String = v.name
    @TypeConverter fun toVerdict(v: String): SmsVerdict =
        SmsVerdict.entries.firstOrNull { it.name == v } ?: SmsVerdict.SKIPPED
}

/** v1 -> v2: card/channel/balance/duplicate/raw text on transactions; accounts, payee names, SMS log. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isCard` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `channel` TEXT")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `balance` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `possibleDuplicate` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `rawText` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`key` TEXT NOT NULL, `bankName` TEXT NOT NULL, " +
                "`last4` TEXT NOT NULL, `isCard` INTEGER NOT NULL, `nickname` TEXT, PRIMARY KEY(`key`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `payee_names` (`merchantKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "PRIMARY KEY(`merchantKey`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sms_log` (`smsId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`senderCode` TEXT NOT NULL, `bankName` TEXT NOT NULL, `body` TEXT NOT NULL, `verdict` TEXT NOT NULL, " +
                "`reason` TEXT, `transactionId` INTEGER, PRIMARY KEY(`smsId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_log_timestamp` ON `sms_log` (`timestamp`)")
    }
}

@Database(
    entities = [
        TransactionEntity::class, MerchantRule::class, TrackedSender::class, Bill::class, BudgetLimit::class,
        AccountEntity::class, PayeeName::class, SmsLogEntry::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rules(): RuleDao
    abstract fun senders(): SenderDao
    abstract fun bills(): BillDao
    abstract fun budgets(): BudgetDao
    abstract fun accounts(): AccountDao
    abstract fun payees(): PayeeDao
    abstract fun smsLog(): SmsLogDao

    companion object {
        const val NAME = "finance.db"

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
