package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.knowledge.ConfigFormat
import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.GameSettingsProfile
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/** One profile, ready to write: which file, and the exact keys that go in it. */
data class ProfileWrite(val profile: GameSettingsProfile, val filePath: String, val changes: ChangeSet)

/**
 * Picks settings profiles (L2 device defaults, L3 per-game) and turns them into config writes.
 * Paths keep `{appData}` for the caller to resolve, as BIOS staging does.
 */
object SettingsProfiles {
    /** The emulator's defaults for this device: device-specific profiles first, then the chip family's. */
    fun defaults(bundle: KnowledgeBundle, emulatorId: String, deviceId: String?, chipFamily: String): List<GameSettingsProfile> =
        bundle.settingsProfiles
            .filter { it.emulatorId == emulatorId && it.gameKey.titleId.isEmpty() && it.chipFamily == chipFamily && (it.device.isEmpty() || it.device == deviceId) }
            .sortedBy { it.device.isEmpty() }

    /** Per-game profiles for one title, most specific device first. */
    fun forGame(bundle: KnowledgeBundle, emulatorId: String, titleId: String, deviceId: String?, chipFamily: String): List<GameSettingsProfile> =
        bundle.settingsProfiles
            .filter { it.emulatorId == emulatorId && it.gameKey.titleId.equals(titleId, ignoreCase = true) && it.chipFamily == chipFamily && (it.device.isEmpty() || it.device == deviceId) }
            .sortedBy { it.device.isEmpty() }

    /** Null when the emulator has no file to write this profile to. */
    fun write(emulator: EmulatorProfile, profile: GameSettingsProfile): ProfileWrite? {
        if (emulator.configFormat == ConfigFormat.NONE || profile.settings.isEmpty()) return null
        val titleId = profile.gameKey.titleId
        val path = if (titleId.isEmpty()) emulator.paths.configFile.ifEmpty { return null }
        else {
            if (emulator.paths.perGameDir.isEmpty() || emulator.paths.perGameFileName.isEmpty()) return null
            emulator.paths.perGameDir.trimEnd('/') + "/" + emulator.paths.perGameFileName.replace("{titleId}", titleId.uppercase())
        }
        return ProfileWrite(profile, path, ChangeSet(changes(emulator, profile.settings.map { it.key to it.value }, perGame = titleId.isNotEmpty())))
    }

    /** Values plus the marker lines an Eden-style INI needs for them to take effect. */
    fun changes(emulator: EmulatorProfile, settings: List<Pair<String, String>>, perGame: Boolean): Map<String, String?> {
        val out = linkedMapOf<String, String?>()
        for ((key, value) in settings) {
            if (emulator.marksNonDefault) {
                if (perGame) out["$key\\use_global"] = "false"
                out["$key\\default"] = "false"
            }
            out[key] = value
        }
        return out
    }

    /** The marker lines are bookkeeping; previews and history show the setting itself. */
    fun isMarker(key: String) = key.endsWith("\\use_global") || key.endsWith("\\default")
}
