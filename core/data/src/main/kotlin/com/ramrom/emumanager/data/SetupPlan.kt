package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class PlanSystem(
    val systemId: String,
    val emulatorId: String
)

enum class PlanOrigin {
    NEW,
    ADOPTED,
    IMPORTED,
    RESTORED
}

/**
 * SetupPlan: defines the emulation setup for a device.
 * Exportable as .emuplan.
 */
@Entity(tableName = "setup_plans")
@Serializable
data class SetupPlan(
    @PrimaryKey
    val id: String,
    val origin: PlanOrigin,
    val targetDeviceProfileId: String,
    val frontendId: String?,
    val systems: List<PlanSystem>,
    val biosCheck: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
