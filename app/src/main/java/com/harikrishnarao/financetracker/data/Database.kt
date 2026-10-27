package com.harikrishnarao.financetracker.data

import android.content.Context
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
import androidx.room.Update
import androidx.room.Upsert
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
)

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

// ---------------- DAOs ----------------

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun between(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to")
    suspend fun near(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE refNo = :ref")
    suspend fun countByRef(ref: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("UPDATE transactions SET category = :category WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, category: Category)

    @Query("SELECT * FROM transactions WHERE merchant IS NOT NULL")
    suspend fun allWithMerchant(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
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

    @Query("SELECT code FROM tracked_senders WHERE tracked = 1")
    suspend fun trackedCodes(): List<String>

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

// ---------------- Database ----------------

class Converters {
    @TypeConverter fun fromType(v: TxnType): String = v.name
    @TypeConverter fun toType(v: String): TxnType = TxnType.valueOf(v)
    @TypeConverter fun fromSource(v: TxnSource): String = v.name
    @TypeConverter fun toSource(v: String): TxnSource = TxnSource.valueOf(v)
    @TypeConverter fun fromCategory(v: Category): String = v.name
    @TypeConverter fun toCategory(v: String): Category =
        Category.entries.firstOrNull { it.name == v } ?: Category.UNCATEGORISED
}

@Database(
    entities = [TransactionEntity::class, MerchantRule::class, TrackedSender::class, Bill::class, BudgetLimit::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rules(): RuleDao
    abstract fun senders(): SenderDao
    abstract fun bills(): BillDao
    abstract fun budgets(): BudgetDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "finance.db").build()
    }
}
