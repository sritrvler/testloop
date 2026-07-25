package com.testloop.app.ui

import com.testloop.app.platform.PlayUrlPolicy

/** Direct Google Play URL validator. Rejects spoofed hosts, userinfo, ports, fragments, duplicate parameters, and package mismatches. */
object PlayLinkValidator {
    fun packageFromInstallLink(raw: String): String? = PlayUrlPolicy.packageFromInstallLink(raw)
    fun isInstallLink(raw: String, packageName: String): Boolean = PlayUrlPolicy.isInstallLink(raw, packageName)
    fun isTestingLink(raw: String, packageName: String): Boolean = PlayUrlPolicy.isTestingLink(raw, packageName)
    fun isGroupLink(raw: String): Boolean = PlayUrlPolicy.isGroupLink(raw)
}
