package com.ramrom.emumanager

import com.ramrom.emumanager.data.PlanFile
import com.ramrom.emumanager.data.PlanSystem
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/** One line of the S11 diff. */
data class PlanChange(val systemName: String, val from: String?, val to: String?, val why: String)

/** A plan file read against this device: what will be used, and what changed on the way. */
data class PlanImport(
    val madeFor: String,
    val systems: List<PlanSystem>,
    val useEsde: Boolean,
    val changes: List<PlanChange>,
)

object PlanFiles {
    const val EXTENSION = "emuplan"
    /** Not application/json: the system picker would append ".json" to the name. */
    const val MIME = "application/octet-stream"

    fun export(draft: PlanDraft, targetDeviceId: String?, appVersion: String): String = PlanFile.encode(
        PlanFile(
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            targetDeviceProfileId = targetDeviceId ?: "generic-android",
            frontendId = if (draft.useEsde) "es-de" else null,
            systems = draft.chosen.map { PlanSystem(it.systemId, it.emulatorId) },
        )
    )

    /**
     * Reads a plan for this device. A choice that was just the other device's recommendation follows
     * this device's recommendation instead; a choice the user made on purpose is kept.
     */
    fun read(file: PlanFile, bundle: KnowledgeBundle, hereFamily: String): PlanImport {
        val target = bundle.devices.firstOrNull { it.id == file.targetDeviceProfileId }
        val thereFamily = target?.chipFamily ?: "generic"
        val emuName = { id: String -> bundle.emulators.firstOrNull { it.id == id }?.name ?: id }
        val changes = mutableListOf<PlanChange>()
        val systems = file.systems.mapNotNull { ps ->
            val sys = bundle.systems.firstOrNull { it.id == ps.systemId }
            if (sys == null) {
                changes += PlanChange(ps.systemId, emuName(ps.emulatorId), null, "not supported yet, left out")
                return@mapNotNull null
            }
            val known = bundle.emulators.any { it.id == ps.emulatorId }
            val there = sys.recommended[thereFamily] ?: sys.recommended["generic"]
            val here = sys.recommended[hereFamily] ?: sys.recommended["generic"]
            when {
                !known && here != null -> {
                    changes += PlanChange(sys.name, ps.emulatorId, emuName(here), "unknown emulator, using ours")
                    PlanSystem(sys.id, here)
                }
                !known -> null.also { changes += PlanChange(sys.name, ps.emulatorId, null, "no emulator for it, left out") }
                ps.emulatorId == there && here != null && here != there -> {
                    changes += PlanChange(sys.name, emuName(there), emuName(here), "better fit for this chip")
                    PlanSystem(sys.id, here)
                }
                else -> ps
            }
        }
        return PlanImport(target?.names?.firstOrNull() ?: "another device", systems, file.frontendId != null, changes)
    }
}
