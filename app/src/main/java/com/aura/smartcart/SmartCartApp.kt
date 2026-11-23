package com.aura.smartcart

import android.app.Application
import android.content.Context
import com.aura.smartcart.data.AppPreferences
import com.aura.smartcart.data.WeeklyReminder
import com.aura.smartcart.data.auth.AuthRepository
import com.aura.smartcart.data.db.AppDatabase
import com.aura.smartcart.data.remote.BillCloudSync
import com.aura.smartcart.data.repo.BillRepository
import com.aura.smartcart.data.repo.BudgetRepository
import com.aura.smartcart.data.repo.ListRepository
import com.aura.smartcart.service.location.LocationHelper
import com.aura.smartcart.service.notify.Notifier
import com.aura.smartcart.service.notify.ReminderScheduler
import com.aura.smartcart.data.ScanQuota
import com.aura.smartcart.data.remote.PriceShare
import com.aura.smartcart.data.remote.UserProfileSync
import com.aura.smartcart.data.remote.AdminRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import com.aura.smartcart.service.notify.PriceAlertWorker
import com.aura.smartcart.service.translate.EnglishTranslator
import com.aura.smartcart.ui.common.resolveInputLanguage
import com.aura.smartcart.service.billing.ProBilling
import com.aura.smartcart.service.scan.TesseractOcr
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
    val prices = PriceShare()
    val profiles = UserProfileSync()
    val admin = AdminRepository()
    val translator = EnglishTranslator()
    val bills = BillRepository(
        db = db,
        cloud = BillCloudSync(),
        prices = prices,
        shareEnabled = { prefs.sharePrices.first() },
        toEnglish = { text -> translator.toEnglish(text, resolveInputLanguage(prefs.inputLanguage.first())) }
    )
    val budgets = BudgetRepository(db)
    val scanner = TextScanner(context, TesseractOcr(context))
    val location = LocationHelper(context)
    val auth = AuthRepository(context)
    val pro = ProBilling(context)
    val quota = ScanQuota(appContext) { pro.isPro.value }
}

class SmartCartApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Before any Firebase call, so Gemini (AI Logic) requests carry an App Check token.
        AppCheckSetup.install()
        container = AppContainer(this)
        Notifier.createChannels(this)
        // Refresh Pro status (e.g. a subscription that ended or was bought on another phone).
        container.pro.connect()
        PriceAlertWorker.createChannel(this)
        PriceAlertWorker.scheduleDaily(this)

        // Signed in: save/refresh the user profile in Firestore and follow the admin's isPro switch.
        appScope.launch {
            container.auth.user.collectLatest { user ->
                if (user == null) {
                    container.pro.setAdminPro(false)
                    return@collectLatest
                }
                container.profiles.upsert(user.name, user.email, container.pro.playPro.value)
                container.profiles.adminPro().collect { container.pro.setAdminPro(it) }
            }
        }
        // Subscription started or ended: show it on the profile too.
        appScope.launch {
            container.pro.playPro.drop(1).collect { container.profiles.setPlayPro(it) }
        }
        appScope.launch {
            // Download the translation model for the user's language now, so saving a bill
            // (always stored in English) is quick and works offline later.
            container.translator.prefetch(resolveInputLanguage(container.prefs.inputLanguage.first()))
        }
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
