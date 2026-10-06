package com.ramrom.emumanager.access

import java.io.File

/**
 * Pure allow-list validator for exec commands and paths.
 * Prevents injection and traversal attacks.
 */
object AllowList {

    /** Validate a command (argv) against the allow-list. */
    fun isCommandAllowed(argv: List<String>): Boolean {
        if (argv.isEmpty()) return false

        val cmd = argv[0]
        if (!cmd.matches(Regex("[a-z0-9_]+"))) return false // Simple cmd names only

        return when (cmd) {
            "pm" -> validatePmSubcommand(argv)
            "am" -> validateAmSubcommand(argv)
            "appops" -> validateAppopsSubcommand(argv)
            "dumpsys" -> validateDumpsysSubcommand(argv)
            "pidof" -> true // pidof <pkg>; safe
            "ls" -> validateFileCommand(argv)
            "stat" -> if (argv.size == 4 && argv[1] == "-c") argv[2] == "%s %Y %F" && isPathAllowed(argv[3]) else validateFileCommand(argv)
            "md5sum" -> argv.size == 2 && validateFileCommand(argv)
            "cmd" -> validateCmdSubcommand(argv)
            else -> false
        }
    }

    private fun validatePmSubcommand(argv: List<String>): Boolean {
        if (argv.size < 2) return false
        return when (argv[1]) {
            "install", "uninstall", "grant", "revoke", "path", "list" -> true
            else -> false
        }
    }

    private fun validateAmSubcommand(argv: List<String>): Boolean {
        if (argv.size < 2) return false
        return when (argv[1]) {
            "force-stop", "start" -> true
            else -> false
        }
    }

    private fun validateAppopsSubcommand(argv: List<String>): Boolean {
        if (argv.size < 2) return false
        return when (argv[1]) {
            "set", "get" -> true
            else -> false
        }
    }

    private fun validateDumpsysSubcommand(argv: List<String>): Boolean {
        if (argv.size < 2) return false
        return when (argv[1]) {
            "activity", "package" -> true
            else -> false
        }
    }

    private fun validateCmdSubcommand(argv: List<String>): Boolean {
        if (argv.size < 2) return false
        return when (argv[1]) {
            "package" -> true
            else -> false
        }
    }

    private fun validateFileCommand(argv: List<String>): Boolean {
        // ls <path>, stat <path>, etc. — just one path argument allowed
        if (argv.size < 2) return false
        return isPathAllowed(argv[1])
    }

    /** Validate that a path is within /storage/emulated/0 and doesn't contain traversal. */
    fun isPathAllowed(path: String): Boolean {
        if (path.contains("..")) return false
        if (path.isEmpty()) return false

        // Canonicalize and check (works for both absolute and relative paths)
        val canonical = try {
            val file = if (path.startsWith("/")) {
                File(path)
            } else {
                File("/storage/emulated/0/$path")
            }
            file.canonicalPath
        } catch (e: Exception) {
            return false
        }

        // Ensure canonicalized path starts with /storage/emulated/0
        return canonical.startsWith("/storage/emulated/0")
    }
}
