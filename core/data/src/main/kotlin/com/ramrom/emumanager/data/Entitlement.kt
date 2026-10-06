package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntitlementState {
    NONE,
    PENDING,
    ACTIVE,
    DISABLED
}

/**
 * Entitlement: license state.
 * Stores only licenseKeyHash, never the key itself.
 */
@Entity(tableName = "entitlements")
data class Entitlement(
    @PrimaryKey
    val licenseKeyHash: String,
    val instanceId: String = "",
    val activatedAt: Long = 0,
    val lastValidatedAt: Long = 0,
    val state: EntitlementState = EntitlementState.NONE
)
