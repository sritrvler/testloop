package com.testloop.app.tracking

import android.content.Context
import android.content.pm.PackageManager

interface InstallStatusReporter {
    fun isInstalled(packageName: String): Boolean
}

/** Checks one package chosen by the member. It never enumerates installed packages. */
class AndroidInstallStatusReporter(context: Context) : InstallStatusReporter {
    private val packageManager = context.packageManager

    override fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        true
    }.getOrDefault(false)
}
