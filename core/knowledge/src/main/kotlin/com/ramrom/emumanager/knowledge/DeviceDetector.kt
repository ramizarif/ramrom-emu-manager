package com.ramrom.emumanager.knowledge

import android.content.Context
import android.os.Build

/**
 * Device detection (P01): matches device to a profile, or falls back by SoC / generic.
 */
object DeviceDetector {
    /**
     * Pure function: match device info to a profile.
     * Tries exact model/device match, then SoC family, then generic.
     * Phone heuristic: no gamepad and no handheld match → phone.
     */
    fun match(info: DeviceInfo, bundle: KnowledgeBundle): DeviceMatchResult {
        // Only the marketing model identifies a device. Build.DEVICE is often the Qualcomm platform
        // codename ("kalama" on every 8 Gen 2 handheld), so it must never pick a profile on its own.
        val byModel = info.model?.let { m -> bundle.devices.find { d -> d.match.models.any { it.equals(m, ignoreCase = true) } } }
        val family = info.socModel?.let { soc -> bundle.chipFamilies.find { f -> f.socModels.any { it.equals(soc, ignoreCase = true) } } }
        if (byModel != null) return DeviceMatchResult(byModel, info, chipFamily = family ?: bundle.chipFamilies.find { it.id == byModel.chipFamily }, matchKind = "model")
        val generic = bundle.devices.find { it.id == "generic-android" }
        return DeviceMatchResult(
            generic, info,
            isPhoneDetected = !info.hasGamepad,
            chipFamily = family,
            matchKind = if (family != null) "family" else "none",
        )
    }

    /**
     * Android reader: build DeviceInfo from Build properties and system services.
     * Runs on the main thread; safe for startup.
     */
    fun readDevice(context: Context): DeviceInfo {
        val model = Build.MODEL
        val device = Build.DEVICE
        val socModel = try {
            Build::class.java.getField("SOC_MODEL").get(null) as? String
        } catch (e: Exception) {
            null
        }

        val displayCount = try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE)
                as? android.hardware.display.DisplayManager
            dm?.displays?.size ?: 0
        } catch (e: Exception) {
            0
        }

        val hasGamepad = detectGamepad(context)

        val runtime = Runtime.getRuntime()
        val ramMb = runtime.totalMemory() / (1024 * 1024)
        val storageGb = try {
            val stat = android.os.StatFs("/storage/emulated/0")
            stat.blockSize * stat.blockCount / (1024L * 1024 * 1024)
        } catch (e: Exception) {
            0L
        }

        return DeviceInfo(
            model = model,
            device = device,
            socModel = socModel,
            manufacturer = Build.MANUFACTURER,
            displayCount = displayCount,
            hasGamepad = hasGamepad,
            ramMb = ramMb,
            storageGb = storageGb,
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    private fun detectGamepad(context: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val inputManager = Class.forName("android.hardware.input.InputManager")
                .getMethod("getInstance")
                .invoke(null) ?: return false
            val listInputDevices = inputManager.javaClass.getMethod("getInputDevices")
            @Suppress("UNCHECKED_CAST")
            val devices = listInputDevices.invoke(inputManager) as? Array<Any> ?: return false

            devices.any { dev ->
                try {
                    val sources = dev.javaClass.getMethod("getSources").invoke(dev) as? Int ?: return@any false
                    // Check for JOYSTICK (0x01000000) or DPAD (0x00000010)
                    (sources and 0x01000000) != 0 || (sources and 0x00000010) != 0
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Result of device matching: the matched profile (or null if generic fails),
 * and metadata about the match.
 */
data class DeviceMatchResult(
    val profile: DeviceProfile?,
    val info: DeviceInfo,
    val isPhoneDetected: Boolean = false,
    /** Chip family by SoC; drives best-guess recommendations when the model is unknown. */
    val chipFamily: ChipFamily? = null,
    /** "model" (exact device), "family" (best guess by chip) or "none". */
    val matchKind: String = "none",
) {
    val deviceId: String? = profile?.id
    val isVerified: Boolean = matchKind == "model" && profile?.verification == "verified"
}
