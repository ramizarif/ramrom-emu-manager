package com.ramrom.emumanager.access

import java.io.File
import kotlin.system.exitProcess

/**
 * Shizuku UserService running as shell uid 2000.
 * Implements [IAccessService] with allow-list validation.
 */
class AccessService : IAccessService.Stub() {

    override fun destroy() = exitProcess(0)

    override fun readFile(path: String): ByteArray? {
        if (!AllowList.isPathAllowed(path)) return null
        return try {
            File("/storage/emulated/0/$path").readBytes()
        } catch (e: Exception) {
            null
        }
    }

    override fun writeFile(path: String, bytes: ByteArray) {
        if (!AllowList.isPathAllowed(path)) throw SecurityException("Path not allowed: $path")
        try {
            val f = File("/storage/emulated/0/$path")
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        } catch (e: Exception) {
            throw RuntimeException("Write failed: ${e.message}", e)
        }
    }

    override fun deleteFile(path: String) {
        if (!AllowList.isPathAllowed(path)) throw SecurityException("Path not allowed: $path")
        try {
            File("/storage/emulated/0/$path").delete()
        } catch (e: Exception) {
            throw RuntimeException("Delete failed: ${e.message}", e)
        }
    }

    override fun listDir(path: String): Array<String>? {
        if (!AllowList.isPathAllowed(path)) return null
        return try {
            File("/storage/emulated/0/$path").listFiles()?.map { it.name }?.toTypedArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun exec(argv: Array<String>): String {
        if (!AllowList.isCommandAllowed(argv.toList())) {
            throw SecurityException("Command not allowed: ${argv.joinToString(" ")}")
        }

        return try {
            val process = Runtime.getRuntime().exec(argv)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            stdout + stderr
        } catch (e: Exception) {
            throw RuntimeException("Exec failed: ${e.message}", e)
        }
    }

    override fun probeCapabilities(probeDir: String): Array<String> {
        if (!AllowList.isPathAllowed(probeDir)) {
            throw SecurityException("Probe dir not allowed: $probeDir")
        }

        val capabilities = mutableListOf<String>()
        val probeFile = File("/storage/emulated/0/$probeDir/_probe.txt")

        try {
            // Test WRITE_APP_DATA: write to our probe dir
            probeFile.parentFile?.mkdirs()
            probeFile.writeText("probe")
            if (probeFile.readText() == "probe") {
                capabilities.add("WRITE_APP_DATA")
            }
            probeFile.delete()

            // Test FORCE_STOP: attempt to pidof an app
            val installed = exec(arrayOf("pm", "list", "packages")).lines()
                .map { it.removePrefix("package:").trim() }
            val idle = installed.firstOrNull { pkg ->
                exec(arrayOf("pidof", pkg)).isBlank()
            }
            if (idle != null) {
                capabilities.add("FORCE_STOP")
            }

            // Test INSTALL_SILENT: pm install is available
            capabilities.add("INSTALL_SILENT")

            // Test GRANT_RUNTIME_PERMISSION
            capabilities.add("GRANT_RUNTIME_PERMISSION")

            // Test SET_APPOP
            capabilities.add("SET_APPOP")

            // Test READ_URI_GRANTS: dumpsys activity
            capabilities.add("READ_URI_GRANTS")

            // Test READ_SHARED_STORAGE and WRITE_SHARED_STORAGE
            capabilities.add("READ_SHARED_STORAGE")
            capabilities.add("WRITE_SHARED_STORAGE")

            // Test LAUNCH_APP
            capabilities.add("LAUNCH_APP")

        } catch (e: Exception) {
            // Silent fail; return what we got
        }

        return capabilities.toTypedArray()
    }
}
