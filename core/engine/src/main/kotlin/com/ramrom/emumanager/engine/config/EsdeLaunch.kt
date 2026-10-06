package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.knowledge.EmulatorProfile

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

    sealed interface Pick {
        data class Package(val pkg: String) : Pick
        data class OtherEmulator(val name: String) : Pick
        data object NotInstalled : Pick
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
}
