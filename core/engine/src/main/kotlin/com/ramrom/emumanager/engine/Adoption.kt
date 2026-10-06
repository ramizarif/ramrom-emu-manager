package com.ramrom.emumanager.engine

import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * F23: the plan a device is already following, read from what's on it. Nothing is changed; S16
 * shows this and the user keeps it, fixes it, or adds to it.
 */
object Adoption {
    fun infer(bundle: KnowledgeBundle, inventory: Inventory, chipFamily: String): Plan {
        val installed = bundle.emulators.filter { e -> e.packages.any { it in inventory.installedPackages } }
        val withGames = inventory.romFolders.filter { it.files.isNotEmpty() || it.gameCount > 0 }.map { it.systemId }.toSet()
        // A dedicated emulator (one or two systems, like Eden or Dolphin) says the user wants that
        // system. RetroArch being installed says nothing about which of its 17 they play.
        val dedicated = installed.filter { it.systems.size <= 2 }.flatMap { it.systems }.toSet()
        val systems = bundle.systems.filter { it.id in withGames || it.id in dedicated }.mapNotNull { s ->
            val candidates = installed.filter { s.id in it.systems }
            val recommended = s.recommended[chipFamily] ?: s.recommended["generic"]
            val emu = candidates.firstOrNull { it.id == recommended }
                ?: candidates.minByOrNull { it.systems.size }
                ?: recommended?.let { r -> bundle.emulators.firstOrNull { it.id == r } }
                ?: return@mapNotNull null
            Plan.PlanSystem(s.id, emu.id)
        }
        val frontend = bundle.frontends.firstOrNull { f -> f.packages.any { it in inventory.installedPackages } || inventory.esdeConfig.installed }
        return Plan(null, chipFamily, frontend?.id, systems, null)
    }
}
