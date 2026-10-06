package com.ramrom.emumanager.access

/**
 * Access mode (which implementation is active).
 */
enum class AccessMode {
    NotSetUp,
    Elevated,
    Limited,
    Disconnected,
}

/**
 * Shizuku permission state.
 */
enum class ShizukuPermission {
    NotAsked,
    Granted,
    Denied,
}

/**
 * Shizuku setup state (read by refresh()).
 */
data class ShizukuSetup(
    val installed: Boolean,
    val devOptionsOn: Boolean,
    val running: Boolean,
    val permission: ShizukuPermission,
)

/**
 * Result of an access operation.
 */
sealed class AccessResult<out T> {
    data class Ok<T>(val value: T) : AccessResult<T>()
    data class Denied(val reason: String) : AccessResult<Nothing>()
    data class Failed(val message: String) : AccessResult<Nothing>()
}
