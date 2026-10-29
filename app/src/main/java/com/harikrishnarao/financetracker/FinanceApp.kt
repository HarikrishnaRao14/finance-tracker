package com.harikrishnarao.financetracker

import android.app.Application
import android.content.Context
import com.harikrishnarao.financetracker.data.AppDatabase
import com.harikrishnarao.financetracker.data.FinanceRepository
import com.harikrishnarao.financetracker.data.SettingsStore
import com.harikrishnarao.financetracker.sms.SmsReader
import com.harikrishnarao.financetracker.update.Updater
import com.harikrishnarao.financetracker.work.Notifications
import com.harikrishnarao.financetracker.work.WorkScheduler

/** Simple manual dependency container: one database, one settings store, one repository. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val database = AppDatabase.create(context)
    val smsReader = SmsReader(context)
    val repository = FinanceRepository(database, settings, smsReader)
    val updater = Updater(context)
}

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        WorkScheduler.schedule(this)
    }
}
