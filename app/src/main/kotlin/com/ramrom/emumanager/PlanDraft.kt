package com.ramrom.emumanager

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ramrom.emumanager.knowledge.DeviceDetector
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import com.ramrom.emumanager.screens.EmulatorOption
import com.ramrom.emumanager.screens.SystemChoice

/**
 * The plan the user is building on S06–S10. Holds only choices; the engine turns it into steps.
 * Lives as long as the plan flow; a saved plan is persisted through PlanFile.
 */
class PlanDraft(
    private val bundle: KnowledgeBundle,
    /** Chip family to read recommendations for, e.g. "sd8gen2". "generic" when unknown. */
    val familyId: String,
    private val installed: (String) -> Boolean,
    /** The handheld this plan is for, when it's this one; device-only defaults key on it. */
    val deviceId: String? = null,
) {
    val esdeInstalled: Boolean = bundle.frontends.any { f -> f.packages.any(installed) }
    var useEsde by mutableStateOf(true)
    var systems by mutableStateOf(initialSystems())
        private set
    /** The folder picked on S09; kept so a changed plan can be re-checked without asking again. */
    var biosFolder by mutableStateOf<android.net.Uri?>(null)
    /** A folder S09 found itself and the user confirmed (read with all-files access, not a tree grant). */
    var biosPath by mutableStateOf<String?>(null)
    /** S09's own search, best first; null until it has run for the current systems. */
    var biosGuesses by mutableStateOf<List<com.ramrom.emumanager.engine.BiosFolder>?>(null)
    /** S09's last scan; null until the user picks a BIOS folder. */
    var bios by mutableStateOf<List<com.ramrom.emumanager.engine.BiosCheckResult>?>(null)
    /** Systems in the plan that need a BIOS, before any scan. */
    val biosSystems: List<SystemChoice> get() = chosen.filter { it.biosNeeded }

    /** The system whose emulator sheet is open on S07. */
    var changing by mutableStateOf<String?>(null)

    fun toggle(systemId: String) {
        systems = systems.map { if (it.systemId == systemId) it.copy(on = !it.on) else it }
        bios = null; biosGuesses = null
    }

    /** S08 "Add to plan": turn a system on. */
    fun addSystem(systemId: String) {
        systems = systems.map { if (it.systemId == systemId) it.copy(on = true) else it }
        bios = null; biosGuesses = null
    }

    fun systemName(systemId: String) = bundle.systems.firstOrNull { it.id == systemId }?.name ?: systemId

    fun pickEmulator(systemId: String, emulatorId: String) {
        val e = bundle.emulators.firstOrNull { it.id == emulatorId } ?: return
        systems = systems.map {
            if (it.systemId != systemId) it
            else it.copy(
                emulatorId = e.id, emulatorName = e.name, tileLabel = Detection.shortLabel(e.id, e.name),
                installed = e.packages.any(installed), reason = reasonFor(systemId, e.id, e.packages.any(installed)),
            )
        }
        changing = null
    }

    val chosen: List<SystemChoice> get() = systems.filter { it.on }

    /** Loads an imported plan: exactly its systems on, with its emulator choices. */
    fun apply(import: PlanImport) {
        useEsde = import.useEsde
        val wanted = import.systems.associate { it.systemId to it.emulatorId }
        systems = systems.map { it.copy(on = it.systemId in wanted) }
        wanted.forEach { (sys, emu) -> if (systems.first { it.systemId == sys }.emulatorId != emu) pickEmulator(sys, emu) }
    }

    /** What the engine compiles. ES-DE is the only frontend in v1. */
    fun toPlan(targetDeviceId: String? = deviceId) = com.ramrom.emumanager.engine.Plan(
        targetDeviceId = targetDeviceId,
        chipFamily = familyId,
        frontendId = if (useEsde) bundle.frontends.firstOrNull()?.id else null,
        systems = chosen.map { com.ramrom.emumanager.engine.Plan.PlanSystem(it.systemId, it.emulatorId) },
        bios = bios?.let { com.ramrom.emumanager.engine.BiosIdentifier(bundle).toSummary(it) },
    )

    fun checkBios(candidates: List<com.ramrom.emumanager.engine.CandidateFile>) {
        bios = com.ramrom.emumanager.engine.BiosIdentifier(bundle).checkBios(candidates, chosen.map { it.systemId to it.emulatorId })
    }

    /** What S09 found, as copies for the run: system id → source file and destination. */
    fun biosCopies(): Map<String, com.ramrom.emumanager.run.BiosCopy> = bios.orEmpty().mapNotNull { r ->
        val found = r.status as? com.ramrom.emumanager.engine.BiosCheckStatus.Found ?: return@mapNotNull null
        val target = found.entry.targets.firstOrNull { it.emulatorId == r.emulatorId } ?: return@mapNotNull null
        if (found.file.uri.isEmpty()) return@mapNotNull null
        r.system.id to com.ramrom.emumanager.run.BiosCopy(android.net.Uri.parse(found.file.uri), r.emulatorId, target.path,
            target.renameTo.ifEmpty { found.entry.fileName })
    }.toMap()

    /** Sizes worth hashing: only files the size of a known BIOS. */
    val biosSizes: Set<Long> get() = bundle.bios.map { it.size }.filter { it > 0 }.toSet()

    /** Expected file names for a system, for the Missing row. Names only; we never say where to get them. */
    fun biosFileNames(systemId: String): List<String> = bundle.bios.filter { it.system == systemId }.map { it.fileName }.distinct()

    /** Packages from the bundle that are on this device; the engine skips installing them. */
    fun inventory() = com.ramrom.emumanager.engine.Inventory(
        installedPackages = (bundle.emulators.flatMap { it.packages } + bundle.frontends.flatMap { it.packages }).filter(installed).toSet(),
    )

    private fun recommendedFor(systemId: String): String? {
        val s = bundle.systems.firstOrNull { it.id == systemId } ?: return null
        return s.recommended[familyId] ?: s.recommended["generic"]
    }

    private fun reasonFor(systemId: String, emulatorId: String, isInstalled: Boolean) = when {
        isInstalled -> "already on this device"
        emulatorId == recommendedFor(systemId) -> "recommended"
        else -> "your pick"
    }

    private fun initialSystems(): List<SystemChoice> {
        val anyInstalled = bundle.emulators.any { e -> e.packages.any(installed) }
        return bundle.systems.mapNotNull { s ->
            val emuId = recommendedFor(s.id) ?: return@mapNotNull null
            val emu = bundle.emulators.firstOrNull { it.id == emuId } ?: return@mapNotNull null
            val emuInstalled = emu.packages.any(installed)
            // Offer the recommendation, the system's listed alternatives, and any emulator that lists this system.
            val others = bundle.emulators.filter { it.id != emu.id && (it.id in s.alternatives || s.id in it.systems) }
            val options = (listOf(emu) + others)
                .map { e -> EmulatorOption(e.id, e.name, descriptionFor(e.id), e.id == emuId, e.caveats.firstOrNull()) }
            SystemChoice(
                systemId = s.id, systemName = s.name, maker = s.maker,
                emulatorId = emu.id, emulatorName = emu.name, tileLabel = Detection.shortLabel(emu.id, emu.name),
                reason = reasonFor(s.id, emu.id, emuInstalled),
                // Adopting: start from what's already here. Fresh: start from the common set.
                on = if (anyInstalled) emuInstalled else s.id in DEFAULT_ON,
                biosNeeded = s.biosRequired, installed = emuInstalled, alternatives = options,
            )
        }
    }

    private fun descriptionFor(id: String) = when (id) {
        "retroarch" -> "One app for many systems, using cores."
        else -> "Made just for this system."
    }

    companion object {
        /** What a fresh setup turns on before the user touches anything. Everything else is one tap away. */
        val DEFAULT_ON = setOf("switch", "n3ds", "nds", "ps1", "ps2", "psp", "gc", "wii", "gba", "snes")

        fun create(context: Context, bundle: KnowledgeBundle, familyOverride: String? = null): PlanDraft {
            val here = DeviceDetector.match(DeviceDetector.readDevice(context), bundle)
            val family = familyOverride ?: here.chipFamily?.id ?: "generic"
            val pm = context.packageManager
            return PlanDraft(bundle, family, { pkg ->
                try { pm.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
            }, deviceId = if (familyOverride == null) here.profile?.id else null)
        }
    }
}
