package com.ramrom.emumanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.engine.Adoption
import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.engine.health.FixAction
import com.ramrom.emumanager.engine.health.HealthChecker
import com.ramrom.emumanager.engine.health.IssueSeverity
import com.ramrom.emumanager.knowledge.DeviceDetector
import com.ramrom.emumanager.run.DeviceInventory
import com.ramrom.emumanager.run.HealthReport
import com.ramrom.emumanager.run.StepText
import com.ramrom.emumanager.screens.AdoptScreen
import com.ramrom.emumanager.screens.FoundSetup
import com.ramrom.emumanager.screens.FoundSystem
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.HandheldTile

private class Found(val plan: Plan, val screen: FoundSetup, val tiles: List<HandheldTile>)

/** S16 bound to a fresh scan. [onAddSystems] gets the found plan to start S07 from. */
@Composable
fun AdoptRoute(app: EmuApplication, onKeep: (Plan) -> Unit, onFix: (Plan) -> Unit, onAddSystems: (Plan) -> Unit, onSetUpInstead: () -> Unit) {
    val found by produceState<Found?>(null) {
        val bundle = app.knowledge.getBundle() ?: return@produceState
        val family = DeviceDetector.match(DeviceDetector.readDevice(app), bundle).chipFamily?.id ?: "generic"
        val inventory = DeviceInventory(app, bundle, app.access).scan()
        val plan = Adoption.infer(bundle, inventory, family)
        val issues = HealthChecker(bundle).check(plan, inventory, accessLost = app.access.mode.value == AccessMode.Disconnected)
        val report = HealthReport.of(plan, issues, bundle)
        val emus = bundle.emulators.associateBy { it.id }
        val systems = report.systems.map { s ->
            val emu = emus[plan.systems.first { it.systemId == s.systemId }.emulatorId]
            val version = emu?.packages?.firstNotNullOfOrNull { p -> inventory.installedApps.firstOrNull { it.packageId == p } }?.versionName
            val folder = inventory.romFolders.firstOrNull { it.systemId == s.systemId }
            val games = folder?.gameCount ?: 0
            FoundSystem(s.name, listOfNotNull(
                listOfNotNull(s.emulatorName, version?.takeIf { it.isNotBlank() }).joinToString(" "),
                if (folder == null) "no folder" else "$games game${if (games == 1) "" else "s"}",
            ).joinToString("  ·  "), s.ready, s.top?.takeIf { !s.ready }?.title)
        }
        val fixable = issues.any { it.severity != IssueSeverity.INFO && (it.fix is FixAction.InstallApp || it.fix is FixAction.GrantPermission || it.fix is FixAction.GrantAllFilesAccess || it.fix is FixAction.CreateFolder) }
        val tiles = plan.systems.map { it.emulatorId }.distinct().mapIndexed { i, id ->
            HandheldTile(id, (emus[id]?.name ?: id).filter { it.isLetterOrDigit() }.take(2).replaceFirstChar { it.uppercase() }, Detection.tileColor(i))
        }
        val fe = plan.frontendId?.let { id -> bundle.frontends.firstOrNull { it.id == id } }?.let(StepText::frontendName)
        value = Found(plan, FoundSetup(systems, fe, (report.blocking + report.warnings).size, fixable), tiles)
    }
    AdoptScreen(
        found?.screen, HandheldState(awake = true, tiles = found?.tiles.orEmpty()),
        onKeep = { found?.let { onKeep(it.plan) } },
        onFix = { found?.let { onFix(it.plan) } },
        onAddSystems = { found?.let { onAddSystems(it.plan) } },
        onSetUpInstead = onSetUpInstead,
    )
}
