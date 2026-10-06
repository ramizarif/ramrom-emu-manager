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
 * Qt INI format (used by Eden, Citron, Azahar).
 * Supports both regular keys and the Qt-style key\default / key\use_global variants.
 * Example:
 * ```
 * [General]
 * key=value
 * key\default=true
 * key\use_global=false
 * ```
 */
class QtIniCodec : ConfigCodec {
    override fun parse(text: String): ParsedConfig {
        val lineEnding = detectLineEnding(text)
        val hasTrailing = text.isNotEmpty() && (text.endsWith("\n") || text.endsWith("\r\n"))
        val lines = text.split(Regex("\r\n|\n"))
        val values = mutableMapOf<String, String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                continue
            }
            val eqIdx = line.indexOf("=")
            if (eqIdx > 0) {
                val key = line.substring(0, eqIdx).trim()
                val value = line.substring(eqIdx + 1)
                values[key] = value
            }
        }

        return ParsedConfig(
            originalText = text,
            values = values,
            metadata = ConfigMetadata(
                lineEnding = lineEnding,
                hasTrailingNewline = hasTrailing,
                codec = "qt_ini"
            )
        )
    }

    override fun render(config: ParsedConfig): String {
        val originalLines = config.originalText.split(Regex("\r\n|\n"))
        val result = mutableListOf<String>()
        val processed = mutableSetOf<String>()

        for (line in originalLines) {
            val trimmed = line.trim()

            // Preserve comments, blank lines, and section headers
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                result.add(line)
                continue
            }

            // Check if this is a known key
            val eqIdx = line.indexOf("=")
            if (eqIdx > 0) {
                val key = line.substring(0, eqIdx).trim()
                if (key in config.values) {
                    // Replace with new value, preserving indentation
                    val indent = line.takeWhile { it.isWhitespace() }
                    result.add("$indent$key=${config.values[key]}")
                    processed.add(key)
                } else {
                    // Unknown key, preserve it
                    result.add(line)
                }
            } else {
                result.add(line)
            }
        }

        // Append any new keys that weren't in the original
        for ((key, value) in config.values) {
            if (key !in processed) {
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
