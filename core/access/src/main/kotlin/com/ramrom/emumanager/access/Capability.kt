package com.ramrom.emumanager.access

/**
 * Capabilities the Access implementation can provide.
 */
enum class Capability {
    WRITE_APP_DATA,           // Write to Android/data/<pkg>/files
    INSTALL_SILENT,           // Install APKs without user prompt
    FORCE_STOP,               // Force-stop other apps
    GRANT_RUNTIME_PERMISSION, // Grant runtime permissions
    SET_APPOP,                // Set app operation modes
    READ_URI_GRANTS,          // Read SAF grants via dumpsys
    READ_SHARED_STORAGE,      // Read files in /storage/emulated/0
    WRITE_SHARED_STORAGE,     // Write files in /storage/emulated/0
    LAUNCH_APP,               // Launch other apps
}
