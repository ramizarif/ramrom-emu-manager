package com.ramrom.emumanager.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * PlanFile: versioned export format for .emuplan files.
 * Supports formatVersion 1 only; newer versions rejected with a clear error.
 */
@Serializable
data class PlanFile(
    val formatVersion: Int = 1,
    val appVersion: String,
    val createdAt: Long,
    val targetDeviceProfileId: String,
    val frontendId: String? = null,
    val systems: List<PlanSystem>,
    val biosCheck: Boolean = false
) {
    companion object {
        private const val CURRENT_FORMAT_VERSION = 1
        private val json = Json { prettyPrint = false }

        fun encode(plan: PlanFile): String {
            require(plan.formatVersion == CURRENT_FORMAT_VERSION) {
                "Cannot encode plan with unsupported formatVersion ${plan.formatVersion}"
            }
            return json.encodeToString(plan)
        }

        fun decode(content: String): Result<PlanFile> = runCatching {
            val file = json.decodeFromString<PlanFile>(content)
            if (file.formatVersion > CURRENT_FORMAT_VERSION) {
                throw PlanFileVersionError(
                    "This plan needs a newer version of RamROM Emu Manager " +
                        "(formatVersion ${file.formatVersion}, appVersion ${file.appVersion})"
                )
            }
            file
        }
    }
}

/**
 * Error type for plan file version mismatches (S11 state).
 */
class PlanFileVersionError(message: String) : Exception(message)
