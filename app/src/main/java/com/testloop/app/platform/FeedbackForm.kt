package com.testloop.app.platform

import android.net.Uri
import android.os.Build
import com.testloop.app.BuildConfig

/** Builds the Google Forms feedback URL with diagnostic context supplied by the app. */
object FeedbackForm {
    private const val HOST = "docs.google.com"
    private const val PATH = "/forms/d/e/1FAIpQLSeoS6K2eLNRtniYZ-Kcvu9XqfZxMedYnRr3Nx3atx_0pm1w0A/viewform"
    private const val APP_VERSION_ENTRY = "entry.1783446183"
    private const val DEVICE_MODEL_ENTRY = "entry.246033177"
    private const val OS_VERSION_ENTRY = "entry.661572318"

    fun url(): String {
        val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" ")
            .ifBlank { "Unknown" }
        val osVersion = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}"
        return Uri.Builder()
            .scheme("https")
            .authority(HOST)
            .path(PATH)
            .appendQueryParameter("usp", "pp_url")
            .appendQueryParameter(APP_VERSION_ENTRY, appVersion)
            .appendQueryParameter(DEVICE_MODEL_ENTRY, deviceModel)
            .appendQueryParameter(OS_VERSION_ENTRY, osVersion)
            .build()
            .toString()
    }

    fun isAllowedUrl(raw: String): Boolean {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host == HOST &&
            uri.path == PATH &&
            uri.userInfo == null &&
            uri.port == -1
    }
}
