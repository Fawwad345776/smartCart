package com.aura.smartcart.service.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aura.smartcart.MainActivity
import com.aura.smartcart.R
import com.aura.smartcart.SmartCartApp
import com.aura.smartcart.data.WeeklyReminder
import com.aura.smartcart.data.db.ListStatus
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

const val EXTRA_OPEN_LIST_ID = "open_list_id"

object Notifier {
    private const val CHANNEL_TRIP = "shopping_trip"
    private const val CHANNEL_WEEKLY = "weekly_market"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIP, context.getString(R.string.channel_trip), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WEEKLY, context.getString(R.string.channel_weekly), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, listId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            listId?.let { putExtra(EXTRA_OPEN_LIST_ID, it) }
        }
        return PendingIntent.getActivity(
            context, (listId ?: 0L).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showTrip(context: Context, listId: Long, listName: String, itemCount: Int) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF15803D.toInt())
            .setContentTitle(context.getString(R.string.notif_trip_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_trip_body, itemCount, listName, itemCount))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, listId))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(listId.toInt(), n)
    }

    fun showWeekly(context: Context) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF15803D.toInt())
            .setContentTitle(context.getString(R.string.notif_weekly_title))
            .setContentText(context.getString(R.string.notif_weekly_body))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(WEEKLY_NOTIFICATION_ID, n)
    }

    private const val WEEKLY_NOTIFICATION_ID = 900_001
}

/** Schedules the "go shopping" reminder (1 hour before) and the weekly market-day reminders. */
object ReminderScheduler {
    private const val HOUR = 60 * 60 * 1000L

    private fun listWork(listId: Long) = "trip_reminder_$listId"
    private fun weeklyWork(day: DayOfWeek) = "weekly_reminder_${day.name}"

    fun scheduleTrip(context: Context, listId: Long, shoppingAt: Long?) {
        val wm = WorkManager.getInstance(context)
        if (shoppingAt == null) {
            wm.cancelUniqueWork(listWork(listId)); return
        }
        val fireAt = shoppingAt - HOUR
        val delay = fireAt - System.currentTimeMillis()
        // Too late for the 1-hour warning but the trip is still ahead: remind right away.
        if (delay < 0 && shoppingAt < System.currentTimeMillis()) {
            wm.cancelUniqueWork(listWork(listId)); return
        }
        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TripReminderWorker.KEY_LIST_ID to listId))
            .build()
        wm.enqueueUniqueWork(listWork(listId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelTrip(context: Context, listId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(listWork(listId))
    }

    fun scheduleWeekly(context: Context, reminder: WeeklyReminder?) {
        val wm = WorkManager.getInstance(context)
        DayOfWeek.entries.forEach { day ->
            if (reminder == null || !reminder.enabled || day !in reminder.days) {
                wm.cancelUniqueWork(weeklyWork(day))
            } else {
                scheduleWeeklyDay(context, day, reminder.minuteOfDay, ExistingWorkPolicy.REPLACE)
            }
        }
    }

    /** Used by the worker to queue next week's reminder after the current one finishes. */
    fun scheduleWeeklyDay(context: Context, day: DayOfWeek, minuteOfDay: Int, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<WeeklyReminderWorker>()
            .setInitialDelay(millisUntilNext(day, minuteOfDay), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(WeeklyReminderWorker.KEY_DAY to day.value))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(weeklyWork(day), policy, request)
    }

    private fun millisUntilNext(day: DayOfWeek, minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(day))
            .withHour(minuteOfDay / 60).withMinute(minuteOfDay % 60).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return java.time.Duration.between(now, next).toMillis()
    }
}

class TripReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val listId = inputData.getLong(KEY_LIST_ID, -1)
        if (listId < 0) return Result.success()
        val container = (applicationContext as SmartCartApp).container
        val list = container.lists.getList(listId) ?: return Result.success()
        if (list.list.status == ListStatus.DONE) return Result.success()
        Notifier.showTrip(applicationContext, listId, list.list.name, list.items.count { !it.checked })
        return Result.success()
    }

    companion object {
        const val KEY_LIST_ID = "list_id"
    }
}

class WeeklyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SmartCartApp).container
        val reminder = container.prefs.weeklyReminder.first()
        val day = DayOfWeek.of(inputData.getInt(KEY_DAY, 1))
        if (reminder != null && reminder.enabled && day in reminder.days) {
            Notifier.showWeekly(applicationContext)
            // Queue next week's reminder; it starts after this one finishes.
            ReminderScheduler.scheduleWeeklyDay(
                applicationContext, day, reminder.minuteOfDay, ExistingWorkPolicy.APPEND_OR_REPLACE
            )
        }
        return Result.success()
    }

    companion object {
        const val KEY_DAY = "day"
    }
}
