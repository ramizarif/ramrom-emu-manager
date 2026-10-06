package com.ramrom.emumanager.entitlements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the one-time Unlock adds (PLAN §4.3). Everything for setup stays free. */
enum class Feature { Customize, OnlineCatalog, BulkApply, Backups, Sync }

sealed interface EntitlementState {
    data object Locked : EntitlementState
    /** A key was accepted but activation still needs the network once (S34 "waiting for internet"). */
    data object Pending : EntitlementState
    data class Unlocked(val activatedOn: Long, val devicesLeft: Int?) : EntitlementState
    /** Only an explicit refund/disable response re-locks; a network failure never does (P16). */
    data object Disabled : EntitlementState
}

sealed interface ActivationResult {
    data class Activated(val devicesLeft: Int?) : ActivationResult
    data object Queued : ActivationResult
    data object NotRecognised : ActivationResult
    data class DeviceLimit(val devices: List<ActivatedDevice>) : ActivationResult
    data class Failed(val message: String) : ActivationResult
}

data class ActivatedDevice(val instanceId: String, val name: String, val lastSeen: Long)

/** P16. The real Lemon Squeezy client lands in M6; until then [FakeEntitlements] backs debug builds. */
interface Entitlements {
    val state: StateFlow<EntitlementState>
    fun has(feature: Feature): Boolean = state.value is EntitlementState.Unlocked
    /** Checkout URL opened in a Custom Tab. */
    val checkoutUrl: String
    suspend fun activate(key: String): ActivationResult
    suspend fun deactivate(instanceId: String): Boolean
}

/**
 * Lemon Squeezy licence keys are UUID-shaped. Used to offer "Use the key you just bought?" when the
 * user returns from checkout with the key on the clipboard (P16 return path).
 */
fun looksLikeLicenseKey(text: String?): Boolean =
    text != null && KEY.matches(text.trim())

private val KEY = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

/** Debug / test implementation: any key-shaped string unlocks, "0000…" is refused. */
class FakeEntitlements(unlocked: Boolean = false) : Entitlements {
    private val _state = MutableStateFlow<EntitlementState>(
        if (unlocked) EntitlementState.Unlocked(System.currentTimeMillis(), 2) else EntitlementState.Locked,
    )
    override val state: StateFlow<EntitlementState> = _state.asStateFlow()
    override val checkoutUrl = "https://example.invalid/checkout"

    override suspend fun activate(key: String): ActivationResult = when {
        !looksLikeLicenseKey(key) || key.startsWith("00000000") -> ActivationResult.NotRecognised
        else -> ActivationResult.Activated(2).also { _state.value = EntitlementState.Unlocked(System.currentTimeMillis(), 2) }
    }

    override suspend fun deactivate(instanceId: String) = true
}
