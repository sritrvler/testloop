package com.testloop.app.platform

import android.content.Context
import android.content.Intent

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
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            LaunchResult.Opened
        } catch (_: android.content.ActivityNotFoundException) {
            LaunchResult.NotLaunchable
        } catch (_: SecurityException) {
            LaunchResult.Unavailable
        }
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
