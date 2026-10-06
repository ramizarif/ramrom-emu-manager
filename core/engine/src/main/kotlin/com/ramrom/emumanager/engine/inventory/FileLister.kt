package com.ramrom.emumanager.engine.inventory

/**
 * Interface for reading files and folders from storage.
 * The Android implementation plugs in actual file system access;
 * tests provide mock implementations.
 */
interface FileLister {
    /**
     * List all files in a folder recursively (depth-first).
     * @param folderPath absolute path like "/storage/emulated/0/ROMs"
     * @return list of file paths relative to folderPath, or empty if folder doesn't exist
     */
    fun listFiles(folderPath: String): List<String>

    /**
     * Check if a path exists (file or folder).
     */
    fun exists(path: String): Boolean

    /**
     * Check if a path is a directory.
     */
    fun isDirectory(path: String): Boolean

    /** MD5 of a file, lowercase hex; null when it can't be read. Only BIOS candidates are hashed. */
    fun md5(path: String): String? = null

    /** Size in bytes; null when it can't be read. */
    fun size(path: String): Long? = null
}
