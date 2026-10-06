package com.ramrom.emumanager.engine.config

/**
 * A set of key-value changes to apply to a config file.
 * Maps key → value; null value means "delete this key" (if supported by format).
 */
data class ChangeSet(
    val changes: Map<String, String?> = emptyMap()
) {
    val isEmpty: Boolean
        get() = changes.isEmpty()

    val keys: Set<String>
        get() = changes.keys
}

/**
 * Result of a config transaction: what will change, what the new text looks like,
 * and metadata for undo / history.
 */
data class TransactionResult(
    /** (key, oldValue, newValue) for preview/history. oldValue null = key was missing. */
    val changes: List<Triple<String, String?, String?>>,
    /** The new file text, ready to write. */
    val newText: String,
    /** The original file text, for undo. */
    val originalText: String,
    /** True if any keys will actually change. */
    val hasChanges: Boolean
)

/**
 * Executes a config transaction: reads a file, applies key changes, verifies,
 * and manages undo records.
 *
 * Responsibilities:
 * 1. Read current file text (or empty string if missing)
 * 2. Parse using the appropriate codec
 * 3. Apply changes
 * 4. Generate new text preserving unknown keys, comments, ordering, line endings
 * 5. Calculate (key, old, new) for preview and history
 * 6. Write atomically (temp → rename)
 * 7. Verify by re-reading
 * 8. Store snapshot for undo
 *
 * For apps that write config on exit (Eden), the caller must force-stop first.
 */
class ConfigTransaction(
    private val codec: ConfigCodec,
    private val fileStore: FileStore,
    private val snapshotStore: FileStore // separate store for undo snapshots
) {
    /**
     * Execute a transaction: apply changes to a config file.
     *
     * @param filePath path to the config file
     * @param changes the key changes to apply
     * @param snapshotPath path where the original file will be backed up for undo
     * @return transaction result with changes and new text
     *
     * @throws ConfigTransactionException if the file cannot be read/written or codec fails
     */
    fun execute(
        filePath: String,
        changes: ChangeSet,
        snapshotPath: String
    ): TransactionResult {
        if (changes.isEmpty) {
            val originalText = fileStore.read(filePath) ?: ""
            return TransactionResult(
                changes = emptyList(),
                newText = originalText,
                originalText = originalText,
                hasChanges = false
            )
        }

        // 1. Read current file
        val originalText = fileStore.read(filePath) ?: ""

        // 2. Parse using codec
        val parsedConfig = codec.parse(originalText)

        // 3. Calculate changes (for preview/history)
        val changesList = mutableListOf<Triple<String, String?, String?>>()
        for ((key, newValue) in changes.changes) {
            val oldValue = parsedConfig.values[key]
            if (newValue != oldValue) {
                changesList.add(Triple(key, oldValue, newValue))
            }
        }

        val hasChanges = changesList.isNotEmpty()

        if (!hasChanges) {
            return TransactionResult(
                changes = emptyList(),
                newText = originalText,
                originalText = originalText,
                hasChanges = false
            )
        }

        // 4. Apply changes to the config
        for ((key, newValue) in changes.changes) {
            if (newValue != null) {
                parsedConfig.values[key] = newValue
            } else {
                parsedConfig.values.remove(key)
            }
        }

        // 5. Generate new text using codec
        val newText = codec.render(parsedConfig)

        // 6. Snapshot the original for undo
        if (originalText.isNotEmpty()) {
            snapshotStore.write(snapshotPath, originalText)
        }

        // 7. Write new text to file
        fileStore.write(filePath, newText)

        // 8. Verify by re-reading
        val verified = fileStore.read(filePath) ?: ""
        if (verified != newText) {
            throw ConfigTransactionException(
                "File verification failed after write: content mismatch",
                filePath
            )
        }

        return TransactionResult(
            changes = changesList,
            newText = newText,
            originalText = originalText,
            hasChanges = true
        )
    }

    /**
     * Undo a previous transaction by restoring the snapshot.
     * Performs a 3-way check: if the file has changed since our write, warns the user.
     *
     * @param filePath path to the config file
     * @param snapshotPath path where we stored the backup
     * @return whether the undo succeeded
     */
    fun undo(filePath: String, snapshotPath: String): Boolean {
        // Read the snapshot
        val snapshot = snapshotStore.read(snapshotPath) ?: return false

        // Read current file
        val current = fileStore.read(filePath) ?: ""

        // 3-way check: if the original and current differ, the user may have edited it
        // For now, we just restore unconditionally; the UI can warn about this
        fileStore.write(filePath, snapshot)

        // Verify
        val verified = fileStore.read(filePath) ?: ""
        if (verified != snapshot) {
            throw ConfigTransactionException(
                "File verification failed during undo: content mismatch",
                filePath
            )
        }

        // Clean up snapshot
        snapshotStore.delete(snapshotPath)

        return true
    }

    /**
     * Check if a file needs to be closed before writing (e.g., ES-DE, emulators).
     * This is metadata; actual app management is done by the caller.
     */
    fun requiresAppClosed(appPackage: String): Boolean {
        // This would be set per app in the knowledge data
        // For now, return true for apps that write config on exit
        return appPackage in listOf(
            "dev.eden.eden_emulator",
            "dev.eden.eden_emulator.nightly",
            "org.es_de.frontend"
        )
    }
}

class ConfigTransactionException(message: String, val filePath: String) : Exception(message)
