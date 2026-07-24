package com.testloop.app.ui

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Direct Google Play URL validator. Rejects spoofed hosts, userinfo, ports, fragments, duplicate parameters, and package mismatches. */
object PlayLinkValidator {
    private const val TrustedHost = "play.google.com"

    fun packageFromInstallLink(raw: String): String? = parse(raw)?.takeIf { it.path == "/store/apps/details" }?.query?.get("id")?.singleOrNull()

    fun isInstallLink(raw: String, packageName: String): Boolean = packageFromInstallLink(raw) == packageName

    fun isTestingLink(raw: String, packageName: String): Boolean = parse(raw)?.path == "/apps/testing/$packageName"

    private data class Parsed(val path: String, val query: Map<String, List<String>>)

    private fun parse(raw: String): Parsed? {
        if (raw.length !in 1..2_048 || raw != raw.trim() || raw.any(::unsafe)) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != TrustedHost || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null || uri.rawPath != uri.path) return null
        val map = linkedMapOf<String, MutableList<String>>()
        val query = uri.rawQuery ?: ""
        if (query.isNotEmpty()) query.split('&').forEach { part ->
            val index = part.indexOf('=')
            if (index <= 0 || index == part.lastIndex) return null
            val key = decode(part.substring(0, index)) ?: return null
            val value = decode(part.substring(index + 1)) ?: return null
            if (key.isEmpty() || value.isEmpty() || key in map) return null
            map[key] = mutableListOf(value)
        }
        return Parsed(uri.path, map)
    }

    private fun decode(value: String): String? = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull()?.takeIf { decoded -> decoded.none(::unsafe) }
    private fun unsafe(character: Char): Boolean = character.isISOControl() || character in '\u202A'..'\u202E' || character in '\u2066'..'\u2069'
}
