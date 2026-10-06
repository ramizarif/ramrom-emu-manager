package com.ramrom.emumanager.knowledge

import android.content.Context
import android.content.res.AssetManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Offline game title index for P20 (Game catalog / search).
 *
 * Sourced from:
 *   - Wikidata SPARQL (CC0): all gaming platforms
 *   - blawar/titledb (MIT): Switch title IDs and names (facts only)
 *
 * Data structure: gzipped JSON bundle with ~7000+ entries across platforms.
 * Loaded lazily on first access; cached in memory.
 *
 * Usage:
 *   val index = TitleIndex.load(context)
 *   val matcher = TitleMatcher(index)
 *   val result = matcher.match("Super Mario Galaxy (USA).rvz", "gc")
 */

@Serializable
data class TitleIndexBundle(
    val schemaVersion: String = "1",
    val dataVersion: String = "",
    val entries: List<TitleIndexEntry> = emptyList(),
    val count: Int = 0
)

/**
 * In-memory title index with lookup by system.
 */
class TitleIndex(private val bundle: TitleIndexBundle) {
    // Build indices by system for fast lookup
    private val bySystem: Map<String, List<TitleIndexEntry>> =
        bundle.entries.groupBy { it.system }

    fun getEntriesForSystem(systemId: String): List<TitleIndexEntry> =
        bySystem[systemId] ?: emptyList()

    fun getAllEntries(): List<TitleIndexEntry> = bundle.entries

    fun getDataVersion(): String = bundle.dataVersion

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private var instance: TitleIndex? = null

        /**
         * Load title index from bundled gzipped asset (lazy singleton).
         *
         * @param context Android context for asset access
         * @param assetPath Path to gzipped JSON (default: "knowledge/title-index-v1.bin")
         * @return TitleIndex instance
         * @throws Exception if asset cannot be read or JSON is invalid
         */
        fun load(context: Context, assetPath: String = "knowledge/title-index-v1.bin"): TitleIndex {
            instance?.let { return it }

            val bundle = loadBundleFromAsset(context, assetPath)
            val index = TitleIndex(bundle)
            instance = index
            return index
        }

        /**
         * Load and parse gzipped JSON from assets.
         */
        private fun loadBundleFromAsset(
            context: Context,
            assetPath: String
        ): TitleIndexBundle {
            return try {
                context.assets.open(assetPath).use { inputStream ->
                    GZIPInputStream(inputStream).use { gzipStream ->
                        BufferedReader(InputStreamReader(gzipStream, Charsets.UTF_8)).use { reader ->
                            val jsonText = reader.readText()
                            json.decodeFromString<TitleIndexBundle>(jsonText)
                        }
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("Failed to load title index from $assetPath", e)
            }
        }

        /**
         * Reset singleton (for testing).
         */
        fun reset() {
            instance = null
        }
    }
}
