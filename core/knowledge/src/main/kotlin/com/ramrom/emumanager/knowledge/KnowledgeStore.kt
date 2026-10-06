package com.ramrom.emumanager.knowledge

import android.content.Context
import android.content.res.AssetManager
import kotlinx.serialization.json.Json

/**
 * Loads and exposes the bundled knowledge data (device profiles, emulator configs, etc).
 * Provides lookup helpers for callers. Validates schemaVersion on load.
 */
class KnowledgeStore(private val context: Context) {
    private var bundle: KnowledgeBundle? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load the bundled knowledge data from assets. Called once at app startup.
     * Throws if the schema version is not supported.
     */
    fun load(): Result<Unit> = try {
        val assetMgr = context.assets
        val data = assetMgr.open("knowledge/bundle-v1.json").bufferedReader().use { it.readText() }
        bundle = json.decodeFromString<KnowledgeBundle>(data)
        val loaded = bundle!!
        if (loaded.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            Result.failure(UnsupportedSchemaException(
                "Unsupported schema version: ${loaded.schemaVersion}, expected $SUPPORTED_SCHEMA_VERSION"
            ))
        } else {
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun getBundle(): KnowledgeBundle? = bundle

    fun getDeviceProfile(id: String): DeviceProfile? = bundle?.devices?.find { it.id == id }

    fun getChipFamily(id: String): ChipFamily? = bundle?.chipFamilies?.find { it.id == id }

    fun getEmulator(id: String): EmulatorProfile? = bundle?.emulators?.find { it.id == id }

    fun getEmulatorByPackage(pkg: String): EmulatorProfile? =
        bundle?.emulators?.find { pkg in it.packages }

    fun getFrontend(id: String): FrontendProfile? = bundle?.frontends?.find { it.id == id }

    fun getSystem(id: String): SystemDef? = bundle?.systems?.find { it.id == id }

    fun getTheme(themeId: String): ThemeCapability? = bundle?.themes?.find { it.themeId == themeId }

    fun getGuidedStep(id: String): GuidedStepContent? = bundle?.guidedSteps?.find { it.id == id }

    /**
     * Find device profile by exact match: model/device first, then by SoC, else fallback.
     */
    fun matchDevice(info: DeviceInfo): DeviceProfile? {
        val bundle = bundle ?: return null

        // Try exact model/device match
        info.model?.let { model ->
            bundle.devices.find { it.match.models.contains(model) }?.let { return it }
        }

        info.device?.let { device ->
            bundle.devices.find { it.match.devices.contains(device) }?.let { return it }
        }

        // Try SoC match
        info.socModel?.let { soc ->
            val chipFamily = bundle.chipFamilies.find { soc in it.socModels }
            chipFamily?.let { family ->
                bundle.devices.find { it.chipFamily == family.id }?.let { return it }
            }
        }

        // Fallback to generic
        return bundle.devices.find { it.id == "generic-android" }
    }

    companion object {
        private const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

class UnsupportedSchemaException(msg: String) : Exception(msg)

/**
 * Device information read from Build, DisplayManager, InputManager, etc.
 */
data class DeviceInfo(
    val model: String? = null,
    val device: String? = null,
    val socModel: String? = null,
    val manufacturer: String? = null,
    val displayCount: Int = 0,
    val hasGamepad: Boolean = false,
    val ramMb: Long = 0,
    val storageGb: Long = 0,
    val apiLevel: Int = 0
)
