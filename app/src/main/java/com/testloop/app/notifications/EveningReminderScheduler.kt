package com.testloop.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.testloop.app.MainActivity
import java.time.ZonedDateTime

/** Schedules one low-pressure local reminder at 8 PM in the tester's timezone. */
class EveningReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun schedule(pendingCount: Int, singleAppName: String?) {
        if (pendingCount <= 0) {
            cancel()
            return
        }
        preferences.edit()
            .putInt(KEY_PENDING_COUNT, pendingCount)
            .putString(KEY_SINGLE_APP, singleAppName)
            .apply()
        scheduleStoredReminder()
    }

    fun cancel() {
        alarmManager.cancel(alarmIntent())
        preferences.edit().clear().apply()
    }

    internal fun scheduleStoredReminder() {
        if (preferences.getInt(KEY_PENDING_COUNT, 0) <= 0) return
        val triggerAt = nextEightPm().toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        Intent(appContext, EveningReminderReceiver::class.java).setAction(ACTION_EVENING_REMINDER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextEightPm(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val todayAtEight = now.withHour(20).withMinute(0).withSecond(0).withNano(0)
        return if (todayAtEight.isAfter(now)) todayAtEight else todayAtEight.plusDays(1)
    }

    internal companion object {
        const val PREFERENCES = "evening_reminder"
        const val KEY_PENDING_COUNT = "pending_count"
        const val KEY_SINGLE_APP = "single_app"
        const val ACTION_EVENING_REMINDER = "com.testloop.app.action.EVENING_REMINDER"
        const val REQUEST_CODE = 8200

        fun messageFor(pendingCount: Int, singleAppName: String?): String = if (pendingCount > 1) {
            "Quick check-in: open TestLoop when you have a moment so we can update your testing status for your test commitments. It helps keep the shared test on track."
        } else {
            "Quick check-in: open TestLoop when you have a moment so we can update your testing status for ${singleAppName ?: "your test commitment"}. It helps keep the shared test on track."
        }
    }
}

class EveningReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            EveningReminderScheduler(context).scheduleStoredReminder()
            return
        }
        if (intent?.action != EveningReminderScheduler.ACTION_EVENING_REMINDER) return
        val preferences = context.getSharedPreferences(EveningReminderScheduler.PREFERENCES, Context.MODE_PRIVATE)
        val pendingCount = preferences.getInt(EveningReminderScheduler.KEY_PENDING_COUNT, 0)
        if (pendingCount <= 0) return
        val singleAppName = preferences.getString(EveningReminderScheduler.KEY_SINGLE_APP, null)
        TestLoopNotification.show(context, EveningReminderScheduler.messageFor(pendingCount, singleAppName))
        EveningReminderScheduler(context).scheduleStoredReminder()
    }
}

private object TestLoopNotification {
    private const val CHANNEL_ID = "testing_reminders"
    private const val NOTIFICATION_ID = 8201

    fun show(context: Context, message: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Testing reminders",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Helpful reminders to open TestLoop and update testing status"
                }
            )
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A quick TestLoop check-in")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
