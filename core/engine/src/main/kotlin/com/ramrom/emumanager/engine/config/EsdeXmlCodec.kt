package com.ramrom.emumanager.engine.config

/**
 * ES-DE XML codec for custom systems and find rules.
 * Uses regex-based parsing to preserve byte-identical formatting.
 * Can add or update entries by name attribute while preserving all other content.
 *
 * Supports:
 * - `es_systems.xml`: system entries with launch commands
 * - `es_find_rules.xml`: emulator discovery rules
 *
 * Note: Uses regex instead of DOM to achieve true byte-identity preservation
 * (DOM parsing/serialization always re-formats and loses byte-identity).
 */
class EsdeXmlCodec(private val elementName: String = "system") : ConfigCodec {
    override fun parse(text: String): ParsedConfig {
        val values = extractElementsByName(text, elementName)
        return ParsedConfig(
            originalText = text,
            values = values,
            metadata = ConfigMetadata(
                lineEnding = if (text.contains("\r\n")) "\r\n" else "\n",
                hasTrailingNewline = text.isNotEmpty() && (text.endsWith("\n") || text.endsWith("\r\n")),
                codec = "es-de-xml"
            )
        )
    }

    override fun render(config: ParsedConfig): String {
        // ES-DE XML: no in-place rendering without DOM.
        // The addOrUpdateElement method is the main API for modifications.
        return config.originalText
    }

    /**
     * Add or update a child element by name.
     * Preserves all other content byte-identically.
     *
     * @param text the current XML text
     * @param name the value of the name attribute
     * @param attributes map of attributes to set (name will be added automatically)
     * @param innerXml the inner XML content (child elements)
     * @return updated XML with the element added or replaced
     */
    fun addOrUpdateElement(text: String, name: String, attributes: Map<String, String>, innerXml: String): String {
        // Find if element with this name exists
        val pattern = Regex("""<$elementName\s+name\s*=\s*["']$name["'][^>]*>.*?</$elementName>""", RegexOption.DOT_MATCHES_ALL)
        val existingMatch = pattern.find(text)

        // Build the new element
        val attrs = buildString {
            append("""name="$name"""")
            for ((key, value) in attributes) {
                if (key != "name") {
                    append(""" $key="$value"""")
                }
            }
        }

        val newElement = if (innerXml.trim().isEmpty()) {
            """<$elementName $attrs />"""
        } else {
            """<$elementName $attrs>
$innerXml
    </$elementName>"""
        }

        return if (existingMatch != null) {
            // Replace existing element, preserving position
            text.replaceRange(existingMatch.range, newElement)
        } else {
            // Append new element before closing root tag
            val closingRootPattern = Regex("""</\w+>\s*$""")
            val closingMatch = closingRootPattern.find(text)
            if (closingMatch != null) {
                text.replaceRange(closingMatch.range.first, closingMatch.range.first, newElement + "\n    ")
            } else {
                text + "\n    " + newElement
            }
        }
    }

    /**
     * Extract all elements of a given name, indexed by their name attribute value.
     */
    private fun extractElementsByName(text: String, elementName: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val pattern = Regex(
            """<$elementName\s+name\s*=\s*["']([^"']*?)["'][^>]*>.*?</$elementName>""",
            RegexOption.DOT_MATCHES_ALL
        )

        pattern.findAll(text).forEach { match ->
            val nameAttrValue = match.groupValues[1]
            if (nameAttrValue.isNotEmpty()) {
                map[nameAttrValue] = match.value
            }
        }

        return map
    }
}
