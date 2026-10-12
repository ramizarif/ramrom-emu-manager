package com.ramrom.emumanager.access

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Helper to check Shizuku status and provide intents for user actions.
 * Respects Shizuku API restrictions: never call checkSelfPermission unless pingBinder() is true.
 */
class ShizukuStatus(private val context: Context) {

    /** Is the Shizuku app (moe.shizuku.privileged.api) installed? */
    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Is developer options enabled? */
    fun isDeveloperOptionsOn(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    /** Wireless debugging on? Read where Android allows it; null when it doesn't. */
    fun isWirelessDebuggingOn(): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0
    }.getOrNull()

    /** Is Shizuku server running? */
    fun isServerRunning(): Boolean {
        return Shizuku.pingBinder()
    }

    /**
     * Get Shizuku permission state.
     * Only calls checkSelfPermission if server is running (pingBinder() == true).
     */
    fun getPermissionState(): ShizukuPermission {
        return when {
            !isServerRunning() -> ShizukuPermission.NotAsked
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuPermission.Granted
            else -> ShizukuPermission.Denied
        }
    }

    /** Get current setup state. */
    fun getSetup(): ShizukuSetup {
        return ShizukuSetup(
            installed = isShizukuInstalled(),
            devOptionsOn = isDeveloperOptionsOn(),
            running = isServerRunning(),
            permission = getPermissionState(),
            wirelessDebugging = isWirelessDebuggingOn(),
        )
    }

    /** Request Shizuku permission (only safe if server is running). */
    fun requestPermission() {
        if (isServerRunning()) {
            Shizuku.requestPermission(1)
        }
    }

    /** Intent to open Shizuku app. */
    fun openShizukuIntent(): Intent {
        return context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://shizuku.rikka.app/")
            }
    }

    /** Intent to open About phone / developer options. */
    fun openDeveloperOptionsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    }

    /** Intent to open app info for Shizuku. */
    fun openShizukuAppInfoIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", SHIZUKU_PACKAGE, null)
        }
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
