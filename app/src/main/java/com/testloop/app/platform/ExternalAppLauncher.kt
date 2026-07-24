package com.testloop.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Boundary for an explicit, user-initiated attempt to launch one commitment package.
 * A production Android implementation must handle absent, disabled, or non-launchable packages.
 */
interface ExternalAppLauncher {
    fun launch(packageName: String): LaunchResult
    fun openUrl(url: String): LaunchResult
}

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data object NotInstalled : LaunchResult
    data object NotLaunchable : LaunchResult
    data object Unavailable : LaunchResult
}

/**
 * Opens only the package the person explicitly selected from a commitment card.
 * This uses an explicit launcher intent and does not query or scan the device's app inventory.
 */
class AndroidExternalAppLauncher(private val context: Context) : ExternalAppLauncher {
    override fun launch(packageName: String): LaunchResult {
        val target = packageName.trim()
        if (!PACKAGE_NAME.matches(target)) return LaunchResult.NotInstalled

        // Do not rebuild a launcher intent with setPackage(). Some Android 15+
        // launchers (including Flipkart on the Pixel 10) expose a launcher
        // activity but do not resolve that package-only intent. Android's own
        // helper returns the exact explicit component the launcher uses.
        val intent = context.packageManager.getLaunchIntentForPackage(target)
            ?: return if (isInstalled(target)) LaunchResult.NotLaunchable else LaunchResult.NotInstalled

        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult.Opened
        } catch (_: android.content.ActivityNotFoundException) {
            LaunchResult.NotLaunchable
        } catch (_: SecurityException) {
            LaunchResult.Unavailable
        }
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        true
    }.getOrDefault(false)

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }

    override fun openUrl(url: String): LaunchResult {
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            LaunchResult.Opened
        } catch (_: android.content.ActivityNotFoundException) {
            LaunchResult.NotLaunchable
        } catch (_: SecurityException) {
            LaunchResult.Unavailable
        }
    }
}
