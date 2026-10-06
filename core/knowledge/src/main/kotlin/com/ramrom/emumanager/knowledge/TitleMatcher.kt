package com.ramrom.emumanager.knowledge

import java.io.File
import kotlin.math.max

/**
 * Match ROM file names to entries in the offline title index.
 *
 * Matching strategy (in order of priority):
 *   1. Exact Switch title ID from brackets: "Game [0100F2C0115B6000].nsp" → titleId match
 *   2. Name match with region/revision stripping: "Super Mario Galaxy (USA).rvz" → "Super Mario Galaxy"
 *   3. Fuzzy match on normalized name (case-insensitive, punctuation-aware)
 *
 * Confidence scoring:
 *   - 1.0: exact Switch titleId match
 *   - 0.95–0.99: exact normalized name match
 *   - 0.60–0.94: fuzzy match with high similarity
 *   - 0.0: no match
 *
 * Region heuristics (extracted from filename):
 *   - "(USA)", "(US)" → region = "US"
 *   - "(JAP)", "(JP)" → region = "JP"
 *   - "(EUR)", "(EU)", "(EUROPE)" → region = "EU"
 *   - "(KOR)", "(KR)" → region = "KR"
 *   - (etc. for other region codes)
 *
 * Usage:
 *   val matcher = TitleMatcher(titleIndex)
 *   val result = matcher.match("Zelda - TOTK [0100F2C0115B6000].nsp", "switch")
 *   println("${result.entry?.name} (confidence: ${result.confidence})")
 */

data class MatchResult(
    val entry: TitleIndexEntry? = null,
    val confidence: Double = 0.0,
    val reason: String = "no match"
)

class TitleMatcher(private val index: TitleIndex) {

    // Region code mappings for extraction
    private val regionPatterns = mapOf(
        Regex("""\(USA?\)|\[USA?\]""", RegexOption.IGNORE_CASE) to "US",
        Regex("""\(JAP|JP\)|\[JAP|JP\]""", RegexOption.IGNORE_CASE) to "JP",
        Regex("""\(EUR|EU|EUROPE\)|\[EUR|EU|EUROPE\]""", RegexOption.IGNORE_CASE) to "EU",
        Regex("""\(KOR|KR\)|\[KOR|KR\]""", RegexOption.IGNORE_CASE) to "KR",
        Regex("""\(CHN|CN|CHINA\)|\[CHN|CN|CHINA\]""", RegexOption.IGNORE_CASE) to "CN",
        Regex("""\(AUS\)|\[AUS\]""", RegexOption.IGNORE_CASE) to "AU",
    )

    /**
     * Match a ROM file name to an entry in the title index.
     *
     * @param fileName ROM file name (e.g., "Super Mario Galaxy (USA) (En,Fr,Es).rvz")
     * @param systemId System ID (e.g., "switch", "gc", "ps2")
     * @return MatchResult with entry and confidence score
     */
    fun match(fileName: String, systemId: String): MatchResult {
        val candidates = index.getEntriesForSystem(systemId)
        if (candidates.isEmpty()) {
            return MatchResult(reason = "no entries for system: $systemId")
        }

        val baseName = File(fileName).nameWithoutExtension

        // Try exact Switch titleId match first (highest priority)
        if (systemId == "switch") {
            val titleIdMatch = matchSwitchTitleId(baseName, candidates)
            if (titleIdMatch != null) {
                return MatchResult(
                    entry = titleIdMatch,
                    confidence = 1.0,
                    reason = "exact Switch titleId match"
                )
            }
        }

        // Extract normalized name and region from filename
        val extractedName = extractGameName(baseName)
        val extractedRegion = extractRegion(baseName)

        // Try exact normalized name match
        for (entry in candidates) {
            if (normalizeForComparison(entry.name) == normalizeForComparison(extractedName)) {
                // Bonus if region matches
                val confidence = if (extractedRegion != null && entry.region == extractedRegion) {
                    0.99
                } else {
                    0.95
                }
                return MatchResult(
                    entry = entry,
                    confidence = confidence,
                    reason = "exact normalized name match"
                )
            }
        }

        // Fuzzy match: find best similarity
        var bestEntry: TitleIndexEntry? = null
        var bestScore = 0.0

        for (entry in candidates) {
            val similarity = calculateSimilarity(extractedName, entry.name)
            if (similarity > bestScore && similarity >= 0.60) {
                bestScore = similarity
                bestEntry = entry
            }
        }

        return if (bestEntry != null) {
            MatchResult(
                entry = bestEntry,
                confidence = bestScore,
                reason = "fuzzy match"
            )
        } else {
            MatchResult(reason = "no match above threshold (0.60)")
        }
    }

    /**
     * Match Switch titleId from filename brackets.
     * Examples: "[0100F2C0115B6000]", "name [0100...]"
     */
    private fun matchSwitchTitleId(fileName: String, candidates: List<TitleIndexEntry>): TitleIndexEntry? {
        // Look for 16-hex-digit titleId in brackets
        val titleIdRegex = Regex("""\[([0-9A-Fa-f]{16})\]""")
        val match = titleIdRegex.find(fileName)
        if (match != null) {
            val titleId = match.groupValues[1].uppercase()
            return candidates.find { it.ids["titleId"]?.uppercase() == titleId }
        }
        return null
    }

    /**
     * Extract game name from filename by removing region, language, and version tags.
     *
     * Examples:
     *   "Super Mario Galaxy (USA) (En,Fr,Es)" → "Super Mario Galaxy"
     *   "Zelda - TOTK [v0]" → "Zelda - TOTK"
     *   "Game (JP) [revision 2]" → "Game"
     */
    private fun extractGameName(fileName: String): String {
        var name = fileName

        // Remove region/language tags in parentheses or brackets
        name = name.replace(Regex("""\s*\([^)]*\)\s*"""), " ")
        name = name.replace(Regex("""\s*\[[^\]]*\]\s*"""), " ")

        // Collapse multiple spaces
        name = name.replace(Regex("""\s+"""), " ").trim()

        return name
    }

    /**
     * Extract region code from filename.
     * Examples: "(USA)" → "US", "[JP]" → "JP"
     */
    private fun extractRegion(fileName: String): String? {
        for ((pattern, region) in regionPatterns) {
            if (pattern.containsMatchIn(fileName)) {
                return region
            }
        }
        return null
    }

    /**
     * Normalize a name for case-insensitive comparison.
     * Removes punctuation, colons, hyphens; collapses spaces.
     */
    private fun normalizeForComparison(name: String): String {
        return name
            .lowercase()
            .replace(Regex("""[:\-–—]"""), "")  // Remove punctuation
            .replace(Regex("""[^\w\s]"""), "")   // Remove special chars
            .replace(Regex("""\s+"""), " ")      // Collapse spaces
            .trim()
    }

    /**
     * Calculate string similarity using Levenshtein distance.
     * Returns 0.0–1.0; 1.0 is identical.
     */
    private fun calculateSimilarity(a: String, b: String): Double {
        val norm_a = normalizeForComparison(a)
        val norm_b = normalizeForComparison(b)

        // If one is empty, return 0 unless both are empty
        if (norm_a.isEmpty() || norm_b.isEmpty()) {
            return if (norm_a == norm_b) 1.0 else 0.0
        }

        // Avoid matching very short strings; too noisy
        if (norm_a.length < 3 || norm_b.length < 3) {
            return if (norm_a == norm_b) 1.0 else 0.0
        }

        val distance = levenshteinDistance(norm_a, norm_b)
        val maxLen = max(norm_a.length, norm_b.length)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Levenshtein distance: minimum edits (insert, delete, replace) to transform a to b.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // delete
                    dp[i][j - 1] + 1,     // insert
                    dp[i - 1][j - 1] + cost  // replace
                )
            }
        }

        return dp[a.length][b.length]
    }
}
