package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.FrontendProfile
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * Generates ES-DE custom systems and find-rules entries for emulators that
 * ES-DE doesn't bundle out-of-the-box.
 *
 * Responsibility: ensure that emulators chosen in the setup plan can be launched from ES-DE
 * by writing appropriate entries to es_systems.xml and es_find_rules.xml.
 *
 * No-op when ES-DE's defaults already cover an emulator (e.g., Dolphin is built-in).
 */
class EsdeIntegrator(private val bundle: KnowledgeBundle) {
    /**
     * Integration requirement: which files need to be written, and whether ES-DE must be closed.
     */
    data class IntegrationPlan(
        val requiresSystemsXml: Boolean = false,
        val requiresFindRulesXml: Boolean = false,
        val mustCloseEsde: Boolean = true,
        val systemsXmlEntries: List<SystemXmlEntry> = emptyList(),
        val findRulesXmlEntries: List<FindRulesXmlEntry> = emptyList()
    )

    data class SystemXmlEntry(
        val name: String, // system short id (e.g. "switch", "ps2")
        val fullName: String,
        val command: String
    )

    data class FindRulesXmlEntry(
        val name: String, // emulator id
        val packageName: String,
        val activity: String
    )

    /**
     * Plan which ES-DE entries are needed for the chosen emulators.
     *
     * @param chosenEmulators emulator IDs from the setup plan
     * @param systemsPerEmulator map of emulator ID → list of system IDs it emulates
     * @param currentSystemsXml current es_systems.xml content (for change detection)
     * @param currentFindRulesXml current es_find_rules.xml content (for change detection)
     * @return integration plan with required changes
     */
    fun plan(
        chosenEmulators: List<String>,
        systemsPerEmulator: Map<String, List<String>>,
        currentSystemsXml: String = "",
        currentFindRulesXml: String = ""
    ): IntegrationPlan {
        // Look up emulator profiles
        val emuProfiles = mutableMapOf<String, EmulatorProfile>()
        for (emuId in chosenEmulators) {
            val profile = bundle.emulators.find { it.id == emuId }
            if (profile != null) {
                emuProfiles[emuId] = profile
            }
        }

        val esdeProfile = bundle.frontends.find { it.id == "es-de" }
            ?: return IntegrationPlan() // ES-DE not in bundle, nothing to do

        // Determine which emulators need ES-DE launch configuration
        val needsConfig = mutableListOf<Pair<EmulatorProfile, List<String>>>() // (profile, systems)

        for ((emuId, profile) in emuProfiles) {
            val systems = systemsPerEmulator[emuId] ?: profile.systems
            if (profile.esdeLaunch != null) {
                // This emulator has custom launch config
                needsConfig.add(profile to systems)
            }
        }

        if (needsConfig.isEmpty()) {
            return IntegrationPlan(mustCloseEsde = false)
        }

        // Generate required entries
        val systemsXmlEntries = mutableListOf<SystemXmlEntry>()
        val findRulesXmlEntries = mutableListOf<FindRulesXmlEntry>()

        for ((profile, systems) in needsConfig) {
            // Add find rule entry for this emulator
            val esdeConfig = profile.esdeLaunch ?: continue
            if (esdeConfig.packageActivity.isNotEmpty()) {
                val parts = esdeConfig.packageActivity.split("/")
                if (parts.size == 2) {
                    findRulesXmlEntries.add(
                        FindRulesXmlEntry(
                            name = esdeConfig.findRuleName.ifEmpty { profile.id },
                            packageName = parts[0],
                            activity = parts[1]
                        )
                    )
                }
            }

            // Add system entries mapping each system to this emulator
            for (systemId in systems) {
                val systemDef = bundle.systems.find { it.id == systemId } ?: continue
                systemsXmlEntries.add(
                    SystemXmlEntry(
                        name = systemId,
                        fullName = systemDef.name,
                        command = esdeConfig.command
                    )
                )
            }
        }

        // Detect changes by parsing current files
        val hasSystemsChanges = detectChangesInSystemsXml(currentSystemsXml, systemsXmlEntries)
        val hasFindRulesChanges = detectChangesInFindRulesXml(currentFindRulesXml, findRulesXmlEntries)

        return IntegrationPlan(
            requiresSystemsXml = hasSystemsChanges,
            requiresFindRulesXml = hasFindRulesChanges,
            mustCloseEsde = true,
            systemsXmlEntries = systemsXmlEntries,
            findRulesXmlEntries = findRulesXmlEntries
        )
    }

    /**
     * Generate the full es_systems.xml content with custom entries merged.
     * Preserves existing entries and adds/updates custom ones.
     */
    fun generateSystemsXml(
        currentXml: String,
        entries: List<SystemXmlEntry>
    ): String {
        if (entries.isEmpty()) {
            return currentXml
        }

        var result = currentXml
        val codec = EsdeXmlCodec("system")

        for (entry in entries) {
            val innerXml = buildSystemEntryInnerXml(entry)
            result = codec.addOrUpdateElement(
                result,
                name = entry.name,
                attributes = mapOf("name" to entry.name),
                innerXml = innerXml
            )
        }

        return result
    }

    /**
     * Generate the full es_find_rules.xml content with custom entries merged.
     */
    fun generateFindRulesXml(
        currentXml: String,
        entries: List<FindRulesXmlEntry>
    ): String {
        if (entries.isEmpty()) {
            return currentXml
        }

        var result = currentXml
        val codec = EsdeXmlCodec("emulator")

        for (entry in entries) {
            result = codec.addOrUpdateElement(
                result,
                name = entry.name,
                attributes = mapOf("name" to entry.name),
                innerXml = buildFindRuleEntryInnerXml(entry)
            )
        }

        return result
    }

    private fun detectChangesInSystemsXml(currentXml: String, required: List<SystemXmlEntry>): Boolean {
        if (currentXml.isEmpty()) {
            return required.isNotEmpty()
        }

        try {
            val codec = EsdeXmlCodec("system")
            val parsed = codec.parse(currentXml)

            for (entry in required) {
                if (entry.name !in parsed.values) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            // If parsing fails, assume changes are needed
            return true
        }
    }

    private fun detectChangesInFindRulesXml(currentXml: String, required: List<FindRulesXmlEntry>): Boolean {
        if (currentXml.isEmpty()) {
            return required.isNotEmpty()
        }

        try {
            val codec = EsdeXmlCodec("emulator")
            val parsed = codec.parse(currentXml)

            for (entry in required) {
                if (entry.name !in parsed.values) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            return true
        }
    }

    private fun buildSystemEntryInnerXml(entry: SystemXmlEntry): String {
        // Escape XML
        val name = entry.fullName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val cmd = entry.command.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        return """
            <fullname>$name</fullname>
            <command>$cmd</command>
        """.trimIndent()
    }

    private fun buildFindRuleEntryInnerXml(entry: FindRulesXmlEntry): String {
        val pkg = entry.packageName.replace("&", "&amp;")
        val act = entry.activity.replace("&", "&amp;")

        return """
            <rule type="androidpackage">
                <entry package="$pkg" activity="$act"/>
            </rule>
        """.trimIndent()
    }
}
