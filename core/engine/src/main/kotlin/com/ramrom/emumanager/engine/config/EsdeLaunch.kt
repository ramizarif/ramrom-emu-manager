package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.FrontendProfile

/**
 * Which emulator ES-DE opens a game in, from its gamelist: the game's own `<altemulator>`, else the
 * system's `<alternativeEmulator>`. ES-DE reads the latter only as a top-level element before
 * `<gameList>`; a copy inside `<gameList>` is ignored, as ES-DE ignores it. Null means ES-DE's
 * default for the system.
 */
object EsdeLaunch {
    private val SYSTEM = Regex("""<alternativeEmulator>\s*<label>([^<]+)</label>""")
    private val GAME = Regex("""<game>(.*?)</game>""", RegexOption.DOT_MATCHES_ALL)
    private val PATH = Regex("""<path>\./([^<]+)</path>""")
    private val ALT = Regex("""<altemulator>([^<]+)</altemulator>""")

    fun label(gamelist: String, fileName: String): String? {
        val game = GAME.findAll(gamelist).map { it.groupValues[1] }
            .firstOrNull { g -> PATH.find(g)?.groupValues?.get(1)?.let(::unescape) == fileName }
        game?.let { ALT.find(it)?.groupValues?.get(1)?.trim() }?.let { return unescape(it) }
        val top = gamelist.substringBefore("<gameList>")
        return SYSTEM.find(top)?.groupValues?.get(1)?.trim()?.let(::unescape)
    }

    /**
     * The build of [emu] a label names ("Eden Nightly (Standalone)" → the nightly package), null when
     * the label is another emulator altogether. Without a label, the emulator's first package.
     */
    fun packageFor(emu: EmulatorProfile, label: String?, installed: List<String>): Pick {
        if (label == null) return Pick.Package(installed.firstOrNull() ?: return Pick.NotInstalled)
        if (!label.startsWith(emu.name, ignoreCase = true)) return Pick.OtherEmulator(label.substringBefore(" (").trim())
        val nightly = label.contains("nightly", ignoreCase = true)
        val pkg = installed.firstOrNull { it.endsWith(".nightly") == nightly } ?: return Pick.NotInstalled
        return Pick.Package(pkg)
    }

    /** The system-wide choice only: the top-level block before `<gameList>`. */
    fun systemLabel(gamelist: String): String? =
        SYSTEM.find(gamelist.substringBefore("<gameList"))?.groupValues?.get(1)?.trim()?.let(::unescape)

    /**
     * The label ES-DE needs for [systemId] to open games in [emu], or null when its default already
     * does. [pkg] is the installed build (Eden Nightly has its own label).
     */
    fun wanted(frontend: FrontendProfile, systemId: String, emu: EmulatorProfile, pkg: String?): String? {
        val label = pkg?.let { frontend.packageLabels[it] } ?: frontend.emulatorLabels[emu.id] ?: return null
        return label.takeIf { it != frontend.defaultLabels[systemId] }
    }

    /** True when [current] already opens [emu]: "Eden Nightly (Standalone)" is still Eden. */
    fun opens(current: String?, emu: EmulatorProfile, frontend: FrontendProfile): Boolean {
        if (current == null) return false
        val labels = listOfNotNull(frontend.emulatorLabels[emu.id]) + emu.packages.mapNotNull { frontend.packageLabels[it] }
        return current in labels
    }

    /**
     * [gamelist] with the system-wide choice set to [label]: the top-level block is replaced or
     * added before `<gameList>`. Games' own choices and a stray block inside `<gameList>` are left
     * alone. An empty file becomes a minimal gamelist ES-DE reads.
     */
    fun withSystemLabel(gamelist: String, label: String): String {
        val block = "<alternativeEmulator>\n\t<label>${escape(label)}</label>\n</alternativeEmulator>\n"
        if (gamelist.isBlank()) return "<?xml version=\"1.0\"?>\n$block<gameList>\n</gameList>\n"
        val cut = gamelist.indexOf("<gameList").let { if (it < 0) gamelist.length else it }
        val head = gamelist.substring(0, cut); val rest = gamelist.substring(cut)
        val existing = Regex("""<alternativeEmulator>.*?</alternativeEmulator>\s*""", RegexOption.DOT_MATCHES_ALL).find(head)
        return if (existing != null) head.replaceRange(existing.range, block) + rest else head + block + rest
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    sealed interface Pick {
        data class Package(val pkg: String) : Pick
        data class OtherEmulator(val name: String) : Pick
        data object NotInstalled : Pick
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
}
