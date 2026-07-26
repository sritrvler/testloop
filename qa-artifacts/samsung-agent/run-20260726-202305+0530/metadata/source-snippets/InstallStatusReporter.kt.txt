package com.testloop.app.tracking

import android.content.Context
import android.content.pm.PackageManager

interface InstallStatusReporter {
    fun isInstalled(packageName: String): Boolean
}

/** Checks one package chosen by the member. It never enumerates installed packages. */
class AndroidInstallStatusReporter(context: Context) : InstallStatusReporter {
    private val packageManager = context.packageManager

    override fun isInstalled(packageName: String): Boolean {
        val target = packageName.trim()
        if (!PACKAGE_NAME.matches(target)) return false

        // Use the same Android-resolved launcher path as the Open action. A
        // package can be installed even when it does not expose a launcher;
        // getLaunchIntentForPackage also avoids the Android 15 package-only
        // intent mismatch seen with apps such as Flipkart.
        val launchIntent = runCatching { packageManager.getLaunchIntentForPackage(target) }.getOrNull()
        if (launchIntent != null) return true

        return runCatching {
            packageManager.getApplicationInfo(target, PackageManager.ApplicationInfoFlags.of(0))
            true
        }.getOrDefault(false)
    }

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
