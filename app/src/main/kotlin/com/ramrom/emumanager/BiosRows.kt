package com.ramrom.emumanager

import com.ramrom.emumanager.engine.BiosCheckStatus
import com.ramrom.emumanager.screens.BiosRow
import com.ramrom.emumanager.screens.BiosRowState

/** S09's matrix: one row per planned system that has anything to do with BIOS. */
object BiosRows {
    fun rows(d: PlanDraft): List<BiosRow> {
        val results = d.bios?.associateBy { it.system.id }
        return d.chosen.mapNotNull { s ->
            val names = d.biosFileNames(s.systemId)
            if (!s.biosNeeded && names.isEmpty()) return@mapNotNull null
            val r = results?.get(s.systemId)
            // Short canonical names (scph1001.bin) help; a long dump name would wrongly suggest only that one works.
            val expected = names.filter { it.length <= 16 }.take(3).joinToString(", ").ifEmpty { "a ${s.systemName} BIOS file" }
            when (val st = r?.status) {
                is BiosCheckStatus.Found -> {
                    val finalName = st.entry.targets.firstOrNull { it.emulatorId == s.emulatorId }?.renameTo ?: st.entry.fileName
                    BiosRow(s.systemName, BiosRowState.Found, "${st.file.path.removePrefix("/storage/emulated/0/")}  →  $finalName for ${s.emulatorName}")
                }
                is BiosCheckStatus.WrongVersion -> BiosRow(s.systemName, BiosRowState.WrongVersion, "${st.file.path.removePrefix("/storage/emulated/0/")} isn't a version we know. Expected: $expected")
                BiosCheckStatus.GuidedOnly -> BiosRow(s.systemName, BiosRowState.InApp, "${s.emulatorName} sets this up itself; we'll walk you through it")
                null, BiosCheckStatus.Missing -> when {
                    names.isEmpty() -> BiosRow(s.systemName, BiosRowState.InApp, "${s.emulatorName} sets this up itself; we'll walk you through it")
                    !s.biosNeeded -> BiosRow(s.systemName, BiosRowState.Optional, "Plays without it. If you have $expected, it'll be used")
                    r == null -> BiosRow(s.systemName, BiosRowState.NotChecked, "Expected: $expected")
                    else -> BiosRow(s.systemName, BiosRowState.Missing, "Expected: $expected")
                }
            }
        }
    }
}
