package com.testloop.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant

data class PendingSetupReminder(
    val commitmentId: String,
    val appName: String,
    val deadlineAt: Instant
)

/** Keeps short setup-window reminders aligned with the currently pending commitments. */
class PendingSetupReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun sync(reminders: List<PendingSetupReminder>) {
        val desired = reminders.flatMap { reminder ->
            stages.mapNotNull { stage ->
                val trigger = triggerAt(reminder.deadlineAt, stage.offsetSeconds)
                if (trigger.isAfter(Instant.now())) ScheduledReminder(reminder, stage, trigger) else null
            }
        }
        val desiredKeys = desired.mapTo(mutableSetOf()) { it.key }
        val existingKeys = preferences.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        existingKeys.filterNot { it in desiredKeys }.forEach { key -> cancel(key) }
        desired.forEach { scheduled ->
            if (scheduled.key !in existingKeys) schedule(scheduled)
        }
        preferences.edit().putStringSet(KEY_SCHEDULED, desiredKeys).apply()
    }

    private fun schedule(reminder: ScheduledReminder) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt.toEpochMilli(),
            pendingIntent(reminder.key, reminder.reminder.commitmentId, reminder.reminder.appName, reminder.stage.label)
        )
    }

    private fun cancel(key: String) {
        alarmManager.cancel(pendingIntent(key, "", "", ""))
    }

    private fun pendingIntent(key: String, commitmentId: String, appName: String, stage: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            key.hashCode(),
            Intent(appContext, PendingSetupReminderReceiver::class.java)
                .setAction(ACTION_SETUP_REMINDER)
                .putExtra(EXTRA_COMMITMENT_ID, commitmentId)
                .putExtra(EXTRA_APP_NAME, appName)
                .putExtra(EXTRA_STAGE, stage),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun triggerAt(deadlineAt: Instant, offsetSeconds: Long): Instant = when {
        offsetSeconds < 0L -> Instant.now().plusSeconds(-offsetSeconds)
        offsetSeconds == 0L -> deadlineAt
        else -> deadlineAt.minusSeconds(offsetSeconds)
    }

    private data class ScheduledReminder(
        val reminder: PendingSetupReminder,
        val stage: Stage,
        val triggerAt: Instant
    ) {
        val key: String get() = "${reminder.commitmentId}:${stage.label}"
    }

    internal data class Stage(val label: String, val offsetSeconds: Long)

    internal companion object {
        const val PREFERENCES = "pending_setup_reminders"
        const val KEY_SCHEDULED = "scheduled_keys"
        const val ACTION_SETUP_REMINDER = "com.testloop.app.action.PENDING_SETUP_REMINDER"
        const val EXTRA_COMMITMENT_ID = "commitment_id"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_STAGE = "stage"
        val stages = listOf(
            Stage("started", -1),
            Stage("halfway", 30 * 60),
            Stage("final", 10 * 60),
            Stage("expired", 0)
        )
    }
}

class PendingSetupReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PendingSetupReminderScheduler.ACTION_SETUP_REMINDER) return
        val appName = intent.getStringExtra(PendingSetupReminderScheduler.EXTRA_APP_NAME) ?: "the app"
        val stage = intent.getStringExtra(PendingSetupReminderScheduler.EXTRA_STAGE) ?: return
        val message = when (stage) {
            "started" -> "You have 1 hour to install $appName and keep your reserved tester slot."
            "halfway" -> "You have about 30 minutes left to install $appName before your tester slot is released."
            "final" -> "You have about 10 minutes left to install $appName before your tester slot is released."
            "expired" -> "The installation window for $appName ended. Your reserved tester slot was released."
            else -> return
        }
        show(context, message)
    }

    private fun show(context: Context, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = "pending_setup_deadlines"
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId,
                    "Tester slot deadlines",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Install reminders for reserved tester slots" }
            )
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                8301,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TestLoop setup reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
        NotificationManagerCompat.from(context).notify(message.hashCode(), notification)
    }
}
