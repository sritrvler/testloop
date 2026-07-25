package com.testloop.app.tracking

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

interface UsageAccessReporter {
    fun isGranted(): Boolean
    fun openSettings()
    fun openedOn(packageName: String, day: LocalDate = LocalDate.now()): Boolean?
}
/**
 * Reads Android's daily aggregate only after the person has enabled Usage Access.
 * Callers pass one explicit commitment package. No package inventory or duration
 * leaves the device.
 */
class AndroidUsageAccessReporter(private val context: Context) : UsageAccessReporter {
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    override fun isGranted(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun openedOn(packageName: String, day: LocalDate): Boolean? {
        if (!isGranted()) return null
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return usageStats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .firstOrNull { it.packageName == packageName }
            ?.totalTimeInForeground
            ?.let { foregroundMs -> foregroundMs > 0L }
            ?: false
    }
}
