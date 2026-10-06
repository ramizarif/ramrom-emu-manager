package com.ramrom.emumanager.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EmuLog: structured local logger writing JSON lines.
 * Rotates at ~5MB keeping 2 files. P18.
 */
class EmuLog(context: Context) {
    private val logsDir = File(context.filesDir, "logs").also { it.mkdirs() }
    private val logFile = File(logsDir, "app.log")
    private val json = Json

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    @Serializable
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: String? = null
    )

    fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level.name,
            tag = tag,
            message = message,
            throwable = throwable?.stackTraceToString()
        )

        try {
            val line = json.encodeToString(entry) + "\n"
            synchronized(this) {
                logFile.appendText(line)
                rotate()
            }

            // Also log to Android's system log
            val priority = when (level) {
                Level.DEBUG -> Log.DEBUG
                Level.INFO -> Log.INFO
                Level.WARN -> Log.WARN
                Level.ERROR -> Log.ERROR
            }
            if (throwable != null) {
                Log.println(priority, tag, "$message: ${Log.getStackTraceString(throwable)}")
            } else {
                Log.println(priority, tag, message)
            }
        } catch (e: Exception) {
            Log.e("EmuLog", "Failed to write log: ${e.message}", e)
        }
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)

    /**
     * Create a per-run log file for wizard runs.
     */
    fun createRunLog(runId: String): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        val fileName = "run-$runId-${dateFormat.format(Date())}.log"
        return File(logsDir, fileName)
    }

    /**
     * Rotate logs: if logFile > 5MB, move to .1 and start new.
     */
    private fun rotate() {
        if (logFile.length() > 5 * 1024 * 1024) {
            val backup = File(logsDir, "app.log.1")
            val old2 = File(logsDir, "app.log.2")
            if (old2.exists()) old2.delete()
            if (backup.exists()) backup.renameTo(old2)
            logFile.renameTo(backup)
        }
    }

    fun getLogFile(): File = logFile
    fun getLogsDir(): File = logsDir
}
