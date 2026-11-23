package com.aura.smartcart.service.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aura.smartcart.MainActivity
import com.aura.smartcart.R
import com.aura.smartcart.SmartCartApp
import com.aura.smartcart.data.remote.PricePoint
import com.aura.smartcart.util.Money
import com.aura.smartcart.util.PriceMatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/** Opens the SmartCart Pro screen (free users' cheaper-price teaser). */
const val EXTRA_OPEN_PRO = "open_pro"

/**
 * Cheaper-price alerts. Once a day (and right after saving a bill) the user's items from the
 * last 30 days are compared with prices other people paid within 5 km in the last 7 days.
 * The biggest saving not shown before becomes one notification:
 *  - Pro: item, price, shop, distance and how many days ago.
 *  - Free: only "someone bought your item Rs X cheaper" (no shop, place or date); tap opens Pro.
 */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private class Paid(val name: String, val quantity: String, val price: Double, val currency: String)

    override suspend fun doWork(): Result {
        val container = (applicationContext as SmartCartApp).container
        val myUid = container.prices.myUid() ?: return Result.success()
        val now = System.currentTimeMillis()
        val mine = container.bills.recent(now - 30 * DAY).filter { it.bill.lat != null && it.bill.lng != null }
        val home = mine.firstOrNull()?.bill ?: return Result.success()

        val points = container.prices.nearby(home.lat!!, home.lng!!, RADIUS_KM, days = 7).filter { it.uid != myUid }
        if (points.isEmpty()) return Result.success()

        // Latest price the user paid for each item.
        val paid = LinkedHashMap<String, Paid>()
        mine.forEach { b ->
            b.items.filter { it.price > 0 }.forEach { item ->
                val key = PriceMatch.key(item.name)
                if (key.isNotEmpty() && key !in paid) paid[key] = Paid(item.name, item.quantity, item.price, b.bill.currency)
            }
        }

        val store = applicationContext.getSharedPreferences("price_alerts", Context.MODE_PRIVATE)
        val shown = store.getStringSet(KEY_SHOWN, emptySet()).orEmpty()

        var best: Triple<Paid, PricePoint, Double>? = null
        for (p in paid.values) for (pt in points) {
            if (pt.id in shown) continue
            if (!PriceMatch.sameItem(p.name, pt.name) || !PriceMatch.sameQuantity(p.quantity, pt.quantity)) continue
            val saving = p.price - pt.price
            if (saving >= max(MIN_SAVING, p.price * MIN_SAVING_SHARE) && saving > (best?.third ?: 0.0)) best = Triple(p, pt, saving)
        }
        val (p, pt, saving) = best ?: return Result.success()

        store.edit().putStringSet(KEY_SHOWN, (shown + pt.id).toList().takeLast(MAX_SHOWN).toSet()).apply()
        val km = PriceMatch.distanceKm(home.lat, home.lng, pt.lat, pt.lng)
        val daysAgo = ((now - pt.at) / DAY).toInt().coerceAtLeast(0)
        showCheaper(applicationContext, container.pro.isPro.value, p.name, saving, pt, p.currency, km, daysAgo)
        return Result.success()
    }

    private fun showCheaper(
        context: Context, isPro: Boolean, item: String, saving: Double, pt: PricePoint,
        currency: String, km: Double, daysAgo: Int
    ) {
        if (!Notifier.canNotify(context)) return
        createChannel(context)
        val savingText = Money.format(saving, currency)
        val title = context.getString(R.string.notif_cheaper_title, item)
        val body = if (isPro) {
            val where = pt.store.ifBlank { context.getString(R.string.notif_cheaper_unknown_store) }
            val distance = if (km < 1) context.getString(R.string.distance_m, (km * 1000).roundToInt())
            else context.getString(R.string.distance_km, "%.1f".format(km))
            val days = context.resources.getQuantityString(R.plurals.days_ago, daysAgo, daysAgo)
            context.getString(R.string.notif_cheaper_pro, Money.format(pt.price, currency), where, distance, days, savingText)
        } else {
            context.getString(R.string.notif_cheaper_free, savingText)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!isPro) putExtra(EXTRA_OPEN_PRO, true)
        }
        val tap = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF15803D.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "price_alerts"
        private const val NOTIFICATION_ID = 900_101
        private const val KEY_SHOWN = "shown_ids"
        private const val MAX_SHOWN = 300
        private const val RADIUS_KM = 5.0
        private const val MIN_SAVING = 5.0
        private const val MIN_SAVING_SHARE = 0.03
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val DAILY = "price_alerts_daily"
        private const val NOW = "price_alerts_now"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_price), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        /** Daily check; safe to call on every app start. */
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.DAYS).setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Check soon (after a bill is saved). */
        fun runSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<PriceAlertWorker>()
                .setConstraints(online)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
