package com.mqunibi.mqplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

private const val RELEASES_URL =
    "https://api.github.com/repos/MohmmadQunibi/MQ-player-AA/releases"

// Returns (tagName, downloadUrl) of the latest stable release, or null on failure.
// downloadUrl points directly to the APK asset when one is present, otherwise the release page.
suspend fun fetchLatestRelease(): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val json = JSONArray(URL(RELEASES_URL).readText())
        for (i in 0 until json.length()) {
            val r = json.getJSONObject(i)
            if (r.getBoolean("draft") || r.getBoolean("prerelease")) continue

            val tag = r.getString("tag_name")
            val assets = r.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url")

            return@withContext tag to (apkUrl ?: r.getString("html_url"))
        }
        null
    } catch (_: Exception) {
        null
    }
}

// Returns true if latestTag (e.g. "v1.2.0") is newer than currentVersion (e.g. "1.0").
fun isNewerVersion(currentVersion: String, latestTag: String): Boolean {
    val c = currentVersion.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    val l = latestTag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(c.size, l.size)) {
        if (l.getOrElse(i) { 0 } > c.getOrElse(i) { 0 }) return true
        if (l.getOrElse(i) { 0 } < c.getOrElse(i) { 0 }) return false
    }
    return false
}

