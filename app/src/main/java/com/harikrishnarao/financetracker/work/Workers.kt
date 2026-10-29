package com.harikrishnarao.financetracker.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.harikrishnarao.financetracker.FinanceApp
import com.harikrishnarao.financetracker.R
import com.harikrishnarao.financetracker.data.Bill
import com.harikrishnarao.financetracker.ui.Format
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** Reads new bank SMS. Runs every 3 hours; WorkManager keeps the schedule across reboots. */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as FinanceApp).container.repository
        return runCatching { repo.ensureParserUpToDate(); repo.syncSms() }.fold({ Result.success() }, { Result.retry() })
    }
}

/** Once a day: notify about bills that are due soon and not yet paid this month. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as FinanceApp).container.repository
        val today = LocalDate.now()
        repo.allBills()
            .filter { it.reminderOn }
            .mapNotNull { bill -> BillSchedule.reminderDue(bill, today)?.let { bill to it } }
            .forEach { (bill, daysLeft) -> Notifications.billReminder(applicationContext, bill, daysLeft) }
        return Result.success()
    }
}

/** Pure date logic for bills, kept separate so it can be unit tested. */
object BillSchedule {
    /** The next due date on or after [today]. A due day of 31 means the month's last day. */
    fun nextDueDate(dueDay: Int, today: LocalDate, paidThisMonth: Boolean): LocalDate {
        fun dueIn(m: YearMonth) = m.atDay(minOf(dueDay, m.lengthOfMonth()))
        val thisMonth = YearMonth.from(today)
        val thisDue = dueIn(thisMonth)
        return if (paidThisMonth || thisDue.isBefore(today)) dueIn(thisMonth.plusMonths(1)) else thisDue
    }

    fun isPaidThisMonth(bill: Bill, today: LocalDate) = bill.lastPaidMonth == YearMonth.from(today).toString()

    /** Days left until the due date if a reminder should go out today, else null. */
    fun reminderDue(bill: Bill, today: LocalDate): Long? {
        if (isPaidThisMonth(bill, today)) return null
        val due = nextDueDate(bill.dueDay, today, paidThisMonth = false)
        val days = ChronoUnit.DAYS.between(today, due)
        return days.takeIf { it in 0..bill.remindDaysBefore.toLong() }
    }
}

object Notifications {
    private const val CHANNEL_BILLS = "bills"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Reminders a few days before a bill is due"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun billReminder(context: Context, bill: Bill, daysLeft: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val whenText = when (daysLeft) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $daysLeft days"
        }
        val text = if (bill.isAutopay) {
            "Autopay of ${Format.money(bill.amount)} runs $whenText. Keep enough balance."
        } else {
            "${Format.money(bill.amount)} is due $whenText."
        }
        val n = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(bill.name)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(("bill" + bill.id).hashCode(), n)
    }
}

object WorkScheduler {
    private const val SMS_PERIODIC = "sms-sync-periodic"
    private const val SMS_NOW = "sms-sync-now"
    private const val BILLS_DAILY = "bill-reminders-daily"

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SMS_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SmsSyncWorker>(3, TimeUnit.HOURS).build(),
        )
        wm.enqueueUniquePeriodicWork(
            BILLS_DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SMS_NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SmsSyncWorker>().build(),
        )
    }
}
