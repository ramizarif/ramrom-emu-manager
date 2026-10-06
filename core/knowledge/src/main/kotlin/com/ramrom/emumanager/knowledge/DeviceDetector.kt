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
        if (byModel != null) return DeviceMatchResult(byModel, info, chipFamily = family ?: bundle.chipFamilies.find { it.id == byModel.chipFamily }, matchKind = MatchKind.MODEL)
        val generic = bundle.devices.find { it.id == "generic-android" }
        return DeviceMatchResult(
            generic, info,
            isPhoneDetected = !info.hasGamepad,
            chipFamily = family,
            matchKind = if (family != null) MatchKind.FAMILY else MatchKind.NONE,
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

        val displays = try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE)
                as? android.hardware.display.DisplayManager
            val metrics = android.util.DisplayMetrics()
            dm?.displays?.map { display ->
                display.getMetrics(metrics)
                Display(
                    role = if (display.displayId == 0) "main" else "secondary",
                    widthPx = display.width,
                    heightPx = display.height,
                    dpi = metrics.densityDpi
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val hasGamepad = detectGamepad(context)

        val ramMb = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }

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
            displays = displays,
            hasGamepad = hasGamepad,
            ramMb = ramMb,
            storageGb = storageGb,
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    private fun detectGamepad(context: Context): Boolean {
        return try {
            val im = context.getSystemService(Context.INPUT_SERVICE) as? android.hardware.input.InputManager
            im?.let {
                it.inputDeviceIds.any { id ->
                    val device = android.view.InputDevice.getDevice(id)
                    if (device != null) {
                        val sources = device.sources
                        (sources and 0x00000401) != 0 // GAMEPAD | DPAD
                    } else {
                        false
                    }
                }
            } ?: false
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
    /** model (exact device), family (best guess by chip) or none. */
    val matchKind: MatchKind = MatchKind.NONE,
) {
    val deviceId: String? = profile?.id
    val isVerified: Boolean = matchKind == MatchKind.MODEL && profile?.verification == DeviceVerification.VERIFIED
}
