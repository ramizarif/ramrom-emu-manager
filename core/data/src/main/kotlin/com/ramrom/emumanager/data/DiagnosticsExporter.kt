package com.ramrom.emumanager.data

import java.io.File

/**
 * DiagnosticsExporter: builds redacted diagnostic bundles.
 * Redacts ROM paths to ROMs/<system>/<n files> unless includeGameFileNames = true.
 * P18.
 */
class DiagnosticsExporter(
    private val logsDir: File,
    private val includeGameFileNames: Boolean = false
) {
    /**
     * Redact a file path: any segment under a ROM folder becomes ROMs/<system>/<n files>.
     */
    fun redactPath(path: String): String {
        val normalized = path.replace("\\", "/")

        // Match any path containing roms/system/file (case-insensitive)
        val romPattern = Regex("(?i)^(.*/)?(?:roms?)/([^/]+)/(.+)$")
        val match = romPattern.find(normalized)

        return if (match != null) {
            val prefix = match.groupValues[1].removeSuffix("/")
            val system = match.groupValues[2]
            val resultPrefix = if (prefix.isEmpty()) "" else "$prefix/"
            if (includeGameFileNames) {
                "${resultPrefix}ROMs/$system/${match.groupValues[3]}"
            } else {
                "${resultPrefix}ROMs/$system/<n files>"
            }
        } else {
            normalized
        }
    }

    /**
     * Export diagnostics: redacted logs + metadata bundle.
     */
    fun exportDiagnostics(): String {
        val sb = StringBuilder()
        sb.append("=== RamROM Emu Manager Diagnostics ===\n")
        sb.append("Exported: ${java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)}\n")
        sb.append("includeGameFileNames: $includeGameFileNames\n\n")

        // Include log files
        sb.append("=== System Logs ===\n")
        if (logsDir.exists()) {
            logsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("app.log")) {
                    sb.append("\n--- ${file.name} ---\n")
                    file.readLines().forEach { line ->
                        sb.append(redactPath(line)).append("\n")
                    }
                }
            }
        }

        return sb.toString()
    }

    /**
     * Build a redacted text bundle.
     */
    fun buildRedactedBundle(additionalData: Map<String, String> = emptyMap()): String {
        val bundle = exportDiagnostics()
        val sb = StringBuilder(bundle)

        additionalData.forEach { (key, value) ->
            sb.append("\n=== $key ===\n")
            sb.append(redactPath(value)).append("\n")
        }

        return sb.toString()
    }
}
