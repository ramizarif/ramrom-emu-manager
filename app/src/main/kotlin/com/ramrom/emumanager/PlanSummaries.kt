package com.ramrom.emumanager

import com.ramrom.emumanager.access.Capability
import com.ramrom.emumanager.engine.PlanCompiler
import com.ramrom.emumanager.screens.PlanSummary

/** Turns a draft into the S10 counts by compiling it (P04 dry run). */
object PlanSummaries {
    fun review(app: EmuApplication, draft: PlanDraft, otherDevice: String?, limited: Boolean): PlanSummary {
        val bundle = app.knowledge.getBundle()
        val compiled = bundle?.let {
            runCatching {
                // Planning for another device: nothing here counts as installed, and access is theirs to sort out.
                val inventory = if (otherDevice == null) draft.inventory() else com.ramrom.emumanager.engine.Inventory()
                val caps = if (otherDevice == null) app.access.capabilities.value else Capability.entries.toSet()
                val here = PlanCompiler().compile(draft.toPlan(null), it, inventory, caps).summary()
                // What Shizuku would save: the same plan compiled with full access.
                val connected = PlanCompiler().compile(draft.toPlan(null), it, inventory, Capability.entries.toSet()).summary()
                here.copy(savedMinutesIfConnected = (here.handsOnMinutes - connected.handsOnMinutes).coerceAtLeast(0))
            }.onFailure { e -> app.log.e("plan", "compile failed", e) }.getOrNull()
        }
        val biosSystems = draft.chosen.count { it.biosNeeded }
        if (compiled == null) {
            // Fallback when the compiler fails: counts straight from the draft.
            val apps = draft.chosen.filter { !it.installed }.distinctBy { it.emulatorId }.size
            return PlanSummary(apps, if (limited) 0 else apps, 0, biosSystems, if (biosSystems > 0) "we'll check your files" else null,
                apps + biosSystems, biosSystems, 2 + apps, limited, 0)
        }
        return PlanSummary(
            apps = compiled.appsTotal, appsAuto = compiled.appsAuto,
            biosFound = compiled.biosFound.size, biosNeeded = maxOf(compiled.biosNeeded.size, biosSystems),
            biosNote = if (biosSystems > 0) "we'll check your files" else null,
            steps = compiled.stepsTotal, stepsNeedYou = compiled.stepsNeedYou, minutes = compiled.handsOnMinutes,
            limited = limited && otherDevice == null, savedMinutesIfConnected = compiled.savedMinutesIfConnected,
        )
    }
}
