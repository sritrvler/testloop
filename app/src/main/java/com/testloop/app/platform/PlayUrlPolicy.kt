package com.testloop.app.platform

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * The only URLs TestLoop may open from app listings or commitment flows.
 * Play links must point to the exact package they describe. Group links must
 * be canonical Google Groups URLs. Arbitrary web URLs are never accepted.
 */
object PlayUrlPolicy {
    private const val TrustedHost = "play.google.com"
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun packageFromInstallLink(raw: String): String? = parse(raw)?.let { parsed ->
        if (parsed.path != "/store/apps/details" || parsed.query.keys != setOf("id")) return@let null
        parsed.query["id"]?.singleOrNull()?.takeIf(PACKAGE_NAME::matches)
    }

    fun isInstallLink(raw: String, packageName: String): Boolean =
        packageFromInstallLink(raw) == packageName

    fun isTestingLink(raw: String, packageName: String): Boolean =
        parse(raw)?.let { parsed ->
            parsed.path == "/apps/testing/$packageName" && parsed.query.isEmpty()
        } == true && PACKAGE_NAME.matches(packageName)

    fun isGroupLink(raw: String): Boolean =
        parse(raw)?.let { parsed ->
            parsed.host == "groups.google.com" && parsed.query.isEmpty() && GROUP_PATH.matches(parsed.path)
        } == true

    fun isAllowedUrl(raw: String): Boolean =
        packageFromInstallLink(raw) != null ||
            isGroupLink(raw) ||
            parse(raw)?.let { parsed ->
                parsed.path.removePrefix("/apps/testing/")
                    .takeIf { parsed.path.startsWith("/apps/testing/") }
                    ?.takeIf(PACKAGE_NAME::matches)
                    ?.let { parsed.query.isEmpty() }
            } == true

    private data class Parsed(val host: String, val path: String, val query: Map<String, List<String>>)

    private val GROUP_PATH = Regex("^/g/[A-Za-z0-9][A-Za-z0-9_-]*$")

    private fun parse(raw: String): Parsed? {
        val normalized = raw.trim()
        if (normalized.length !in 1..2_048 || normalized.any(::unsafe)) return null
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host !in setOf(TrustedHost, "groups.google.com") || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null || uri.rawPath != uri.path) return null
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
        return Parsed(uri.host, uri.path, map)
    }

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()?.takeIf { decoded -> decoded.none(::unsafe) }

    private fun unsafe(character: Char): Boolean =
        character.isISOControl() || character in '\u202A'..'\u202E' || character in '\u2066'..'\u2069'
}
