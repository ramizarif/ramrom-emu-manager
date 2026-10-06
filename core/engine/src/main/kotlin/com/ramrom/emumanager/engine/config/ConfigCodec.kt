package com.ramrom.emumanager.engine.config

/**
 * Abstraction for reading and writing config files in various formats.
 * Codecs must preserve unknown keys, comments, ordering, and line endings.
 */
interface ConfigCodec {
    /**
     * Parse a config file and return a mutable map of keys to values.
     * The codec internally tracks the original text to enable faithful round-tripping.
     */
    fun parse(text: String): ParsedConfig

    /**
     * Generate updated text from a [ParsedConfig] with applied changes.
     * Preserves all unknown keys, comments, ordering, and line endings.
     */
    fun render(config: ParsedConfig): String
}

/**
 * A config file parsed by a codec, tracking both the original text
 * and the decoded key-value map. Applying changes produces a new [ParsedConfig].
 */
data class ParsedConfig(
    val originalText: String,
    val values: MutableMap<String, String>,
    val metadata: ConfigMetadata = ConfigMetadata()
)

data class ConfigMetadata(
    val lineEnding: String = "\n", // \n or \r\n
    val hasTrailingNewline: Boolean = true,
    val codec: String = "" // identifies which codec produced this
)

/**
 * Sectioned INI, shared by every emulator format we write. Keys inside a section are
 * `Section<sep>key` (`Renderer/resolution_setup`, `Core.CPUThread`); keys before any section stay
 * bare. New keys go at the end of their own section, which is created when missing, and a removed
 * key's line is dropped. Everything else is kept byte for byte. Files written as `key = value`
 * (Azahar, Dolphin) keep that spacing: values are read without it and written back with it.
 */
open class IniCodec(private val separator: Char, private val name: String) : ConfigCodec {
    private fun sectionOf(line: String) = line.trim().takeIf { it.startsWith("[") && it.endsWith("]") }?.removeSurrounding("[", "]")
    private fun qualify(section: String?, key: String) = if (section.isNullOrEmpty()) key else "$section$separator$key"
    private fun isComment(trimmed: String) = trimmed.startsWith(";") || trimmed.startsWith("#")
    /** Index of the `=` on a key line, or -1. */
    private fun eqOf(line: String) = line.indexOf('=').takeIf { it > 0 && !isComment(line.trim()) } ?: -1
    private fun valueOf(line: String, eq: Int) = line.substring(eq + 1).let { if (line.getOrNull(eq - 1) == ' ') it.removePrefix(" ") else it }

    override fun parse(text: String): ParsedConfig {
        val values = linkedMapOf<String, String>()
        var section: String? = null
        for (line in text.split(Regex("\r\n|\n"))) {
            val sec = sectionOf(line)
            if (sec != null) { section = sec; continue }
            val eq = eqOf(line)
            if (eq > 0) values[qualify(section, line.substring(0, eq).trim())] = valueOf(line, eq)
        }
        return ParsedConfig(text, values, ConfigMetadata(detectLineEnding(text), text.isNotEmpty() && text.endsWith("\n"), name))
    }

    override fun render(config: ParsedConfig): String {
        val lines = if (config.originalText.isEmpty()) emptyList() else config.originalText.split(Regex("\r\n|\n")).let {
            // split leaves a trailing empty element for a final newline; it's restored below
            if (config.metadata.hasTrailingNewline) it.dropLast(1) else it
        }
        // Keys not yet in the file, grouped by section, in the order they were given.
        val seen = mutableSetOf<String>()
        var section: String? = null
        var spaced = false
        for (line in lines) {
            val sec = sectionOf(line)
            if (sec != null) { section = sec; continue }
            val eq = eqOf(line)
            if (eq > 0) { seen += qualify(section, line.substring(0, eq).trim()); if (line.getOrNull(eq - 1) == ' ') spaced = true }
        }
        val join = if (spaced) " = " else "="
        val pending = config.values.keys.filter { it !in seen }.groupBy({ if (separator in it) it.substringBefore(separator) else "" }, { it }).toMutableMap()
        fun newLine(k: String) = "${k.substringAfter(separator, k)}$join${config.values[k]}"
        fun newLines(sec: String?) = pending.remove(sec ?: "").orEmpty().map(::newLine)

        val out = mutableListOf<String>()
        section = null
        var sectionEnd = 0 // where the current section's new keys go: after its last key
        fun closeSection() { out.addAll(sectionEnd, newLines(section)) }
        for (line in lines) {
            val sec = sectionOf(line)
            if (sec != null) { closeSection(); section = sec; out += line; sectionEnd = out.size; continue }
            val eq = eqOf(line)
            if (eq > 0) {
                val key = qualify(section, line.substring(0, eq).trim())
                when (val v = config.values[key]) {
                    null -> continue // removed
                    valueOf(line, eq) -> out += line // unchanged: keep it exactly
                    else -> out += line.substring(0, eq + 1) + (if (line.getOrNull(eq - 1) == ' ') " " else "") + v
                }
                sectionEnd = out.size
            } else {
                out += line
                if (line.isNotBlank()) sectionEnd = out.size
            }
        }
        closeSection()
        // Sections the file doesn't have yet.
        for ((sec, keys) in pending.toList()) {
            if (out.isNotEmpty() && out.last().isNotBlank()) out += ""
            if (sec.isNotEmpty()) out += "[$sec]"
            keys.forEach { k -> out += newLine(k) }
        }
        if (out.isEmpty()) return ""
        val text = out.joinToString(config.metadata.lineEnding)
        return if (config.metadata.hasTrailingNewline || config.originalText.isEmpty()) text + config.metadata.lineEnding else text
    }

    private fun detectLineEnding(text: String): String =
        when {
            text.contains("\r\n") -> "\r\n"
            text.contains("\n") -> "\n"
            else -> "\n"
        }
}

/**
 * Qt INI (Eden, Citron, Azahar): `Section/key`, including Eden's `key\use_global` and `key\default`.
 * ```
 * [Renderer]
 * resolution_setup\use_global=false
 * resolution_setup\default=false
 * resolution_setup=3
 * ```
 */
class QtIniCodec : IniCodec('/', "qt_ini")

/** Dolphin's INI: `Section.key`, written `Key = Value`. */
class DolphinIniCodec : IniCodec('.', "dolphin_ini")
