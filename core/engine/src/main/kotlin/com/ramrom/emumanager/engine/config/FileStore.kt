package com.ramrom.emumanager.engine.config

/**
 * Abstraction for file I/O, enabling the config transaction logic to work with
 * different access mechanisms (Shizuku, limited, mock for tests).
 */
interface FileStore {
    /**
     * Read a file's entire contents as a string.
     * @return the file's text, or null if it does not exist
     */
    fun read(path: String): String?

    /**
     * Write text to a file, creating it if it doesn't exist.
     * Implementation should be atomic: write to temp, then rename.
     */
    fun write(path: String, text: String)

    /**
     * Check if a file exists.
     */
    fun exists(path: String): Boolean

    /**
     * Delete a file.
     */
    fun delete(path: String)
}

/**
 * In-memory file store for tests.
 */
class InMemoryFileStore : FileStore {
    private val files = mutableMapOf<String, String>()

    override fun read(path: String): String? = files[path]

    override fun write(path: String, text: String) {
        files[path] = text
    }

    override fun exists(path: String): Boolean = path in files

    override fun delete(path: String) {
        files.remove(path)
    }

    fun getFiles(): Map<String, String> = files.toMap()

    fun clear() {
        files.clear()
    }
}
