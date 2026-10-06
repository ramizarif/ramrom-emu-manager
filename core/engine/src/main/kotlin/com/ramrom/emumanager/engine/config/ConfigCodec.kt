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
 * Qt INI format (used by Eden, Citron, Azahar). Keys inside a section are `Section/key`
 * (`Renderer/resolution_setup`, `Renderer/resolution_setup\use_global`); keys before any section
 * stay bare. New keys go at the end of their own section, which is created when missing, and a
 * removed key's line is dropped. Everything else is kept byte for byte.
 * ```
 * [Renderer]
 * resolution_setup\use_global=false
 * resolution_setup\default=false
 * resolution_setup=3
 * ```
 */
class QtIniCodec : ConfigCodec {
    private fun sectionOf(line: String) = line.trim().takeIf { it.startsWith("[") && it.endsWith("]") }?.removeSurrounding("[", "]")
    private fun qualify(section: String?, key: String) = if (section.isNullOrEmpty()) key else "$section/$key"

    override fun parse(text: String): ParsedConfig {
        val values = linkedMapOf<String, String>()
        var section: String? = null
        for (line in text.split(Regex("\r\n|\n"))) {
            val trimmed = line.trim()
            val sec = sectionOf(line)
            if (sec != null) { section = sec; continue }
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq > 0) values[qualify(section, line.substring(0, eq).trim())] = line.substring(eq + 1)
        }
        return ParsedConfig(text, values, ConfigMetadata(detectLineEnding(text), text.isNotEmpty() && text.endsWith("\n"), "qt_ini"))
    }

    override fun render(config: ParsedConfig): String {
        val lines = if (config.originalText.isEmpty()) emptyList() else config.originalText.split(Regex("\r\n|\n")).let {
            // split leaves a trailing empty element for a final newline; it's restored below
            if (config.metadata.hasTrailingNewline) it.dropLast(1) else it
        }
        // Keys not yet in the file, grouped by section, in the order they were given.
        val seen = mutableSetOf<String>()
        var section: String? = null
        for (line in lines) {
            val sec = sectionOf(line)
            if (sec != null) { section = sec; continue }
            val eq = line.indexOf('=')
            if (eq > 0 && !line.trim().startsWith(";") && !line.trim().startsWith("#")) seen += qualify(section, line.substring(0, eq).trim())
        }
        val pending = config.values.keys.filter { it !in seen }.groupBy({ if ('/' in it) it.substringBefore('/') else "" }, { it }).toMutableMap()
        fun newLines(sec: String?) = pending.remove(sec ?: "").orEmpty().map { k -> "${k.substringAfter('/', k)}=${config.values[k]}" }

        val out = mutableListOf<String>()
        section = null
        var sectionEnd = 0 // where the current section's new keys go: after its last key
        fun closeSection() { out.addAll(sectionEnd, newLines(section)) }
        for (line in lines) {
            val sec = sectionOf(line)
            if (sec != null) { closeSection(); section = sec; out += line; sectionEnd = out.size; continue }
            val trimmed = line.trim()
            val eq = line.indexOf('=')
            if (eq > 0 && !trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                val key = qualify(section, line.substring(0, eq).trim())
                when (val v = config.values[key]) {
                    null -> continue // removed
                    line.substring(eq + 1) -> out += line // unchanged: keep it exactly
                    else -> { val indent = line.takeWhile { it.isWhitespace() }; out += "$indent${line.substring(0, eq).trim()}=$v" }
                }
                sectionEnd = out.size
            } else {
                out += line
                if (trimmed.isNotEmpty()) sectionEnd = out.size
            }
        }
        closeSection()
        // Sections the file doesn't have yet.
        for ((sec, keys) in pending.toList()) {
            if (out.isNotEmpty() && out.last().isNotBlank()) out += ""
            if (sec.isNotEmpty()) out += "[$sec]"
            keys.forEach { k -> out += "${k.substringAfter('/', k)}=${config.values[k]}" }
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
 * Dolphin INI format (plain INI with sections).
 * Example:
 * ```
 * [Core]
 * CPUThread=True
 * [Graphics]
 * Resolution=1
 * ```
 */
class DolphinIniCodec : ConfigCodec {
    override fun parse(text: String): ParsedConfig {
        val lineEnding = detectLineEnding(text)
        val hasTrailing = text.isNotEmpty() && (text.endsWith("\n") || text.endsWith("\r\n"))
        val lines = text.split(Regex("\r\n|\n"))
        val values = mutableMapOf<String, String>()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()

            // Section header
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1)
                continue
            }

            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                continue
            }

            val eqIdx = line.indexOf("=")
            if (eqIdx > 0) {
                val key = line.substring(0, eqIdx).trim()
                val value = line.substring(eqIdx + 1)
                val fullKey = if (currentSection.isNotEmpty()) "$currentSection.$key" else key
                values[fullKey] = value
            }
        }

        return ParsedConfig(
            originalText = text,
            values = values,
            metadata = ConfigMetadata(
                lineEnding = lineEnding,
                hasTrailingNewline = hasTrailing,
                codec = "dolphin_ini"
            )
        )
    }

    override fun render(config: ParsedConfig): String {
        val originalLines = config.originalText.split(Regex("\r\n|\n"))
        val result = mutableListOf<String>()
        val processed = mutableSetOf<String>()
        var currentSection = ""

        for (line in originalLines) {
            val trimmed = line.trim()

            // Section header
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1)
                result.add(line)
                continue
            }

            // Preserve comments and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                result.add(line)
                continue
            }

            val eqIdx = line.indexOf("=")
            if (eqIdx > 0) {
                val key = line.substring(0, eqIdx).trim()
                val fullKey = if (currentSection.isNotEmpty()) "$currentSection.$key" else key

                if (fullKey in config.values) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    result.add("$indent$key=${config.values[fullKey]}")
                    processed.add(fullKey)
                } else {
                    result.add(line)
                }
            } else {
                result.add(line)
            }
        }

        // Append new keys grouped by section
        val newBySection = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for ((key, value) in config.values) {
            if (key !in processed) {
                val (section, shortKey) = if ("." in key) {
                    val parts = key.split(".", limit = 2)
                    parts[0] to parts[1]
                } else {
                    "" to key
                }
                newBySection.getOrPut(section) { mutableListOf() }.add(shortKey to value)
            }
        }

        for ((section, entries) in newBySection) {
            if (section.isNotEmpty()) {
                result.add("[$section]")
            }
            for ((key, value) in entries) {
                result.add("$key=$value")
            }
        }

        val text = result.joinToString(config.metadata.lineEnding)
        return if (config.metadata.hasTrailingNewline) text + config.metadata.lineEnding else text
    }

    private fun detectLineEnding(text: String): String =
        when {
            text.contains("\r\n") -> "\r\n"
            text.contains("\n") -> "\n"
            else -> "\n"
        }
}
