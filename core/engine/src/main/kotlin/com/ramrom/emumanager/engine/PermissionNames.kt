package com.ramrom.emumanager.engine

/** "android.permission.POST_NOTIFICATIONS" → "Notifications": the word Android's settings show. */
object PermissionNames {
    fun of(permission: String) = when (permission.substringAfterLast('.')) {
        "POST_NOTIFICATIONS" -> "Notifications"
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO" -> "Files and media"
        "RECORD_AUDIO" -> "Microphone"
        "CAMERA" -> "Camera"
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> "Location"
        "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN" -> "Nearby devices"
        else -> permission.substringAfterLast('.').lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
