package com.ramrom.emumanager.engine.install

import com.ramrom.emumanager.knowledge.InstallSource
import com.ramrom.emumanager.knowledge.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {
    private fun rel(tag: String, pre: Boolean, vararg assets: String) =
        """{"tag_name":"$tag","prerelease":$pre,"assets":[${assets.joinToString(",") { """{"name":"$it","browser_download_url":"https://example.org/$it","size":10}""" }}]}"""

    // Shape of git.eden-emu.dev's real feed (four builds per release).
    private val eden = "[" + listOf(
        rel("v0.2.1", false, "Eden-Android-v0.2.1-chromeos.apk", "Eden-Android-v0.2.1-legacy.apk", "Eden-Android-v0.2.1-optimized.apk", "Eden-Android-v0.2.1-standard.apk", "Eden-Windows.zip"),
        rel("v0.2.0", false, "Eden-Android-v0.2.0-standard.apk"),
    ).joinToString(",") + "]"

    @Test fun edenStandardBuild() = assertEquals("Eden-Android-v0.2.1-standard.apk", Releases.pick(eden, ".*-standard\\.apk$", false)!!.fileName)

    @Test fun skipsPrereleaseWhenStableExists() {
        val feed = "[" + rel("v2-rc", true, "app.apk") + "," + rel("v1", false, "app.apk") + "]"
        assertEquals("v1", Releases.pick(feed, "", true)!!.version)
    }

    @Test fun prereleaseOnlyProjectsNeedPermission() {
        val feed = "[" + rel("nightly", true, "GameNative-release.apk") + "]"
        assertNull(Releases.pick(feed, ".*release.*\\.apk$", false))
        assertEquals("nightly", Releases.pick(feed, ".*release.*\\.apk$", true)!!.version)
    }

    @Test fun prefersArm64() {
        val feed = "[" + rel("v1", false, "app-x86_64.apk", "app-arm64-v8a.apk") + "]"
        assertEquals("app-arm64-v8a.apk", Releases.pick(feed, ".*\\.apk$", false)!!.fileName)
    }

    @Test fun releaseWithoutApkIsSkipped() {
        val feed = "[" + rel("v2", false, "source.zip") + "," + rel("v1", false, "app.apk") + "]"
        assertEquals("v1", Releases.pick(feed, "", false)!!.version)
    }

    @Test fun forgejoUrlNeedsHttps() {
        assertEquals("https://git.eden-emu.dev/api/v1/repos/eden-emu/eden/releases?limit=10",
            Releases.releasesUrl(InstallSource(SourceType.FORGEJO_RELEASE, "eden-emu/eden", "", "https://git.eden-emu.dev")))
        assertNull(Releases.releasesUrl(InstallSource(SourceType.FORGEJO_RELEASE, "x/y", "", "http://insecure.example")))
    }

    /** Asset names from each project's real releases (checked 2026-10-05) against the bundle's regexes. */
    @Test fun bundleRegexesMatchRealAssets() {
        val bundle = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(
            com.ramrom.emumanager.knowledge.KnowledgeBundle.serializer(),
            java.io.File("../knowledge/src/main/assets/knowledge/bundle-v1.json").readText())
        val real = mapOf(
            "azahar" to listOf("azahar-android-googleplay-2126.1.2.apk", "azahar-android-vanilla-2126.1.2.apk") to "azahar-android-vanilla-2126.1.2.apk",
            "melonds" to listOf("app-gitHub-prod-release.apk") to "app-gitHub-prod-release.apk",
            "vita3k" to listOf("android-latest.apk") to "android-latest.apk",
            "cemu" to listOf("Cemu-0.5.apk") to "Cemu-0.5.apk",
            "gamenative" to listOf("gamenative-v1.2.1-legacy-xr.apk", "gamenative-v1.2.1.apk") to "gamenative-v1.2.1.apk",
            "winlator" to listOf("Winlator_11.2.apk") to "Winlator_11.2.apk",
            "eden" to listOf("Eden-Android-v0.2.1-chromeos.apk", "Eden-Android-v0.2.1-legacy.apk", "Eden-Android-v0.2.1-optimized.apk", "Eden-Android-v0.2.1-standard.apk") to "Eden-Android-v0.2.1-standard.apk",
        )
        for ((key, expected) in real) {
            val (id, assets) = key
            val regex = bundle.emulators.single { it.id == id }.source.assetRegex
            val feed = "[" + rel("v1", false, *assets.toTypedArray()) + "]"
            assertEquals(id, expected, Releases.pick(feed, regex, false)?.fileName)
        }
    }
}
