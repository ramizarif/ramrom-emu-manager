package com.ramrom.emumanager.engine.install

import com.ramrom.emumanager.knowledge.InstallSource
import com.ramrom.emumanager.knowledge.SourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The APK to download for an app, resolved from its release feed. */
data class ResolvedApk(val version: String, val fileName: String, val url: String, val size: Long)

/**
 * Picks the APK from a GitHub or Forgejo release list (P06). Pure: the caller fetches [releasesUrl]
 * and hands the JSON in, so this is tested against fixtures with no network.
 */
object Releases {
    @Serializable
    private data class Asset(val name: String, val browser_download_url: String, val size: Long = 0)
    @Serializable
    private data class Release(val tag_name: String, val prerelease: Boolean = false, val draft: Boolean = false, val assets: List<Asset> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    /** Where the release list lives. Only https hosts named in knowledge data are ever asked. */
    fun releasesUrl(source: InstallSource): String? = when (source.type) {
        SourceType.GITHUB_RELEASE -> "https://api.github.com/repos/${source.repo}/releases?per_page=10"
        SourceType.FORGEJO_RELEASE -> source.url.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { "$it/api/v1/repos/${source.repo}/releases?limit=10" }
        else -> null
    }

    /**
     * Newest release with a matching APK. Prereleases only when the app ships nothing else (some
     * projects tag every build as a prerelease). Prefers arm64 builds when several assets match.
     */
    fun pick(releasesJson: String, assetRegex: String, allowPrerelease: Boolean): ResolvedApk? {
        val regex = Regex(assetRegex.ifEmpty { ".*\\.apk$" }, RegexOption.IGNORE_CASE)
        val releases = json.decodeFromString<List<Release>>(releasesJson).filter { !it.draft }
        val usable = releases.filter { !it.prerelease }.ifEmpty { if (allowPrerelease) releases else emptyList() }
        for (r in usable) {
            val apks = r.assets.filter { regex.matches(it.name) && it.name.endsWith(".apk", ignoreCase = true) }
            if (apks.isEmpty()) continue
            val best = apks.firstOrNull { "arm64" in it.name.lowercase() || "aarch64" in it.name.lowercase() } ?: apks.first()
            if (!best.browser_download_url.startsWith("https://")) continue
            return ResolvedApk(r.tag_name, best.name, best.browser_download_url, best.size)
        }
        return null
    }
}
