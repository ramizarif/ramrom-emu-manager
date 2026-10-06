package com.ramrom.emumanager.engine

import com.ramrom.emumanager.knowledge.BiosEntry
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import com.ramrom.emumanager.knowledge.SystemDef

/**
 * A candidate file for BIOS identification.
 * Size check is eager; hash is lazy (only computed if size matches a known entry).
 */
data class CandidateFile(
    val path: String,
    val size: Long,
    val md5: () -> String  // Lazy: only called when size matches
)

/**
 * Result of identifying a candidate file against the BIOS table.
 */
sealed class BiosCheckStatus {
    data class Found(
        val file: CandidateFile,
        val entry: BiosEntry
    ) : BiosCheckStatus()

    data class WrongVersion(
        val file: CandidateFile,
        val note: String
    ) : BiosCheckStatus()

    object Missing : BiosCheckStatus()
    object GuidedOnly : BiosCheckStatus()
}

/**
 * BIOS check result per system that needs BIOS.
 */
data class BiosCheckResult(
    val system: SystemDef,
    val emulatorId: String,
    val status: BiosCheckStatus
) {
    val needsBios: Boolean
        get() = when (status) {
            is BiosCheckStatus.Found -> true
            is BiosCheckStatus.WrongVersion -> true
            BiosCheckStatus.Missing -> true
            BiosCheckStatus.GuidedOnly -> false
        }
}

/**
 * Staging plan for a found BIOS file: destination and final name.
 */
data class BiosStagePlan(
    val file: CandidateFile,
    val entry: BiosEntry,
    val emulatorId: String,
    val destPath: String,  // {appData} left as token for executor to resolve
    val finalFileName: String
)

/**
 * Identifies BIOS files by hash; handles lazy hashing and wrong-version detection.
 * P11 per spec: never by filename alone; hash only when size matches.
 */
class BiosIdentifier(
    private val bundle: KnowledgeBundle
) {
    /**
     * Check user files against all systems that need BIOS for the chosen emulators.
     * Returns per chosen system that needs BIOS (from SystemDef.biosRequired or
     * any entry exists): Found, WrongVersion, Missing, or GuidedOnly (never automated).
     *
     * Matching is by hash, never filename. A system counts as Found if ANY required
     * entry for that system matches for the chosen emulator (entries whose targets
     * include that emulator; entries with no target for the chosen emulator don't count).
     *
     * Systems that need BIOS but have no entries (n3ds, psvita, switch) report GuidedOnly.
     */
    fun checkBios(
        candidates: List<CandidateFile>,
        chosenSystems: List<Pair<String, String>>  // (systemId, emulatorId)
    ): List<BiosCheckResult> {
        // Map system id → emulator id for quick lookup
        val systemDefs = bundle.systems.associateBy { it.id }

        // Group BIOS entries by system
        val biosBySystem = bundle.bios.groupBy { it.system }

        // Hash each file at most once: PS1, Saturn and others share sizes, so one file meets many entries.
        val hashed = candidates.map { c -> val h by lazy(c.md5); c.copy(md5 = { h }) }

        val results = mutableListOf<BiosCheckResult>()

        for ((systemId, emulatorId) in chosenSystems) {
            val sysDef = systemDefs[systemId] ?: continue

            // Only process systems that need BIOS OR have entries in the table
            if (!sysDef.biosRequired && biosBySystem[systemId].isNullOrEmpty()) {
                continue
            }

            val status = checkSystem(systemId, emulatorId, hashed, biosBySystem)
            results.add(BiosCheckResult(sysDef, emulatorId, status))
        }

        return results
    }

    /**
     * Check a single system against candidates; return its status.
     */
    private fun checkSystem(
        systemId: String,
        emulatorId: String,
        candidates: List<CandidateFile>,
        biosBySystem: Map<String, List<BiosEntry>>
    ): BiosCheckStatus {
        val entries = biosBySystem[systemId].orEmpty()

        // Guided-only: system needs BIOS but has no entries in the table
        if (entries.isEmpty()) {
            return BiosCheckStatus.GuidedOnly
        }

        // Try to match candidates against entries for this emulator
        var foundMatch: Pair<CandidateFile, BiosEntry>? = null
        var wrongVersionMatch: Pair<CandidateFile, String>? = null

        for (entry in entries) {
            // Skip entries that don't target this emulator (or if emulator is empty, skip)
            if (emulatorId.isNotEmpty()) {
                val targets = entry.targets.filter { it.emulatorId == emulatorId }
                if (targets.isEmpty()) continue
            }

            // Find candidate by size (lazy hash)
            for (candidate in candidates) {
                if (candidate.size != entry.size) continue

                // Size matched; compute hash
                val hash = candidate.md5()
                if (hash.equals(entry.md5, ignoreCase = true)) {
                    // Found a match
                    foundMatch = candidate to entry
                    break
                } else {
                    // Filename suggests it's this BIOS, but hash is wrong
                    // Only set if we haven't found a match yet and this is the first wrong hash
                    if (wrongVersionMatch == null) {
                        wrongVersionMatch = candidate to "Size ${candidate.size} matches, but MD5 is ${hash.take(8)}…, expected ${entry.md5.take(8)}…"
                    }
                }
            }

            if (foundMatch != null) break
        }

        return when {
            foundMatch != null -> {
                val (candidate, entry) = foundMatch
                BiosCheckStatus.Found(candidate, entry)
            }
            wrongVersionMatch != null -> {
                val (candidate, note) = wrongVersionMatch
                BiosCheckStatus.WrongVersion(candidate, note)
            }
            else -> BiosCheckStatus.Missing
        }
    }

    /**
     * Build a staging plan for all found BIOS files.
     * Precondition: all results must be BiosCheckStatus.Found.
     */
    fun buildStagePlan(results: List<BiosCheckResult>): List<BiosStagePlan> {
        return results.mapNotNull { result ->
            val status = result.status as? BiosCheckStatus.Found ?: return@mapNotNull null
            val target = status.entry.targets.firstOrNull { it.emulatorId == result.emulatorId }
                ?: status.entry.targets.firstOrNull() // Fallback to first target
                ?: return@mapNotNull null

            BiosStagePlan(
                file = status.file,
                entry = status.entry,
                emulatorId = result.emulatorId,
                destPath = target.path,
                finalFileName = target.renameTo
            )
        }
    }

    /**
     * Convert check results to a BiosSummary for the plan compiler. Optional BIOS (GameCube IPL, DS,
     * GBA) is staged when found but never counted as missing: those systems play without it.
     */
    fun toSummary(results: List<BiosCheckResult>): BiosSummary {
        val found = mutableSetOf<String>()
        val missing = mutableSetOf<String>()
        val wrongVersion = mutableSetOf<String>()

        for (result in results) {
            when (result.status) {
                is BiosCheckStatus.Found -> found.add(result.system.id)
                is BiosCheckStatus.WrongVersion -> if (result.system.biosRequired) wrongVersion.add(result.system.id)
                BiosCheckStatus.Missing -> if (result.system.biosRequired) missing.add(result.system.id)
                BiosCheckStatus.GuidedOnly -> {} // Not counted in summary
            }
        }

        return BiosSummary(found = found, missing = missing, wrongVersion = wrongVersion)
    }
}
