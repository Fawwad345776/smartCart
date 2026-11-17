package com.aura.smartcart

import android.app.Application
import android.content.Context
import com.aura.smartcart.data.AppPreferences
import com.aura.smartcart.data.WeeklyReminder
import com.aura.smartcart.data.db.AppDatabase
import com.aura.smartcart.data.remote.BillCloudSync
import com.aura.smartcart.data.repo.BillRepository
import com.aura.smartcart.data.repo.BudgetRepository
import com.aura.smartcart.data.repo.ListRepository
import com.aura.smartcart.service.location.LocationHelper
import com.aura.smartcart.service.notify.Notifier
import com.aura.smartcart.service.notify.ReminderScheduler
import com.aura.smartcart.service.scan.GeminiVision
import com.aura.smartcart.service.scan.TextScanner
import com.aura.smartcart.util.Country
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Simple manual dependency container shared by the whole app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val prefs = AppPreferences(context)
    private val db = AppDatabase.build(context)
    val lists = ListRepository(db)
    val bills = BillRepository(db, BillCloudSync())
    val budgets = BudgetRepository(db)
    val scanner = TextScanner(context, GeminiVision())
    val location = LocationHelper(context)
}

class SmartCartApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)

        appScope.launch {
            val prefs = container.prefs
            // First launch: remember the country and pre-fill the weekly market reminder for it.
            val country = prefs.countryOnce() ?: Country.detect(this@SmartCartApp).also { prefs.setCountry(it) }
            var weekly = prefs.weeklyReminder.first()
            if (weekly == null) {
                val (days, minute) = Country.defaultMarketSchedule(country)
                weekly = WeeklyReminder(enabled = false, days = days, minuteOfDay = minute)
                prefs.setWeeklyReminder(weekly)
            }
            ReminderScheduler.scheduleWeekly(this@SmartCartApp, weekly)
            // Upload any bills saved while offline.
            container.bills.syncPending()
        }
    }
}
