package com.ramrom.emumanager.engine.config

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfigTransactionTest {
    private lateinit var fileStore: InMemoryFileStore
    private lateinit var snapshotStore: InMemoryFileStore
    private lateinit var transaction: ConfigTransaction

    @Before
    fun setUp() {
        fileStore = InMemoryFileStore()
        snapshotStore = InMemoryFileStore()
        transaction = ConfigTransaction(QtIniCodec(), fileStore, snapshotStore)
    }

    @Test
    fun executeBasicChange() {
        val original = "key1=value1\nkey2=value2\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf("key1" to "updated"))
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        assertTrue(result.hasChanges)
        assertEquals(1, result.changes.size)
        assertEquals("key1", result.changes[0].first)
        assertEquals("value1", result.changes[0].second) // old
        assertEquals("updated", result.changes[0].third) // new

        val written = fileStore.read("/test.ini")
        assertTrue(written!!.contains("key1=updated"))
        assertTrue(written.contains("key2=value2"))
    }

    @Test
    fun executeMultipleChanges() {
        val original = "key1=value1\nkey2=value2\nkey3=value3\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf(
            "key1" to "new1",
            "key3" to "new3"
        ))
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        assertEquals(2, result.changes.size)
        assertTrue(result.hasChanges)

        val written = fileStore.read("/test.ini")!!
        assertTrue(written.contains("key1=new1"))
        assertTrue(written.contains("key2=value2"))
        assertTrue(written.contains("key3=new3"))
    }

    @Test
    fun executeEmptyChangeSet() {
        val original = "key1=value1\nkey2=value2\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(emptyMap())
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        assertFalse(result.hasChanges)
        assertEquals(0, result.changes.size)
        assertEquals(original, fileStore.read("/test.ini"))
    }

    @Test
    fun executeCreatesSnapshot() {
        val original = "key1=value1\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf("key1" to "updated"))
        transaction.execute("/test.ini", changes, "/test.ini.backup")

        val snapshot = snapshotStore.read("/test.ini.backup")
        assertEquals(original, snapshot)
    }

    @Test
    fun executeVerifiesWritten() {
        val original = "key1=value1\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf("key1" to "updated"))
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        val verified = fileStore.read("/test.ini")
        assertEquals(result.newText, verified)
    }

    @Test
    fun undoRestoresSnapshot() {
        val original = "key1=value1\nkey2=value2\n"
        fileStore.write("/test.ini", original)
        snapshotStore.write("/test.ini.backup", original)

        val changes = ChangeSet(mapOf("key1" to "updated"))
        transaction.execute("/test.ini", changes, "/test.ini.backup")

        // Verify change was made
        assertTrue(fileStore.read("/test.ini")!!.contains("key1=updated"))

        // Undo
        val success = transaction.undo("/test.ini", "/test.ini.backup")
        assertTrue(success)

        // Verify restored
        assertEquals(original, fileStore.read("/test.ini"))

        // Verify snapshot was cleaned up
        assertNull(snapshotStore.read("/test.ini.backup"))
    }

    @Test
    fun undoFailsWithoutSnapshot() {
        fileStore.write("/test.ini", "key=value\n")

        val success = transaction.undo("/test.ini", "/nonexistent.backup")
        assertFalse(success)
    }

    @Test
    fun executeChangeWithNewKeys() {
        val original = "key1=value1\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf(
            "key2" to "value2",
            "key3" to "value3"
        ))
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        assertEquals(2, result.changes.size)

        val written = fileStore.read("/test.ini")!!
        assertTrue(written.contains("key2=value2"))
        assertTrue(written.contains("key3=value3"))
    }

    @Test
    fun executeNoChangeIfValueSame() {
        val original = "key1=value1\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf("key1" to "value1")) // Same value
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        assertFalse(result.hasChanges)
        assertEquals(0, result.changes.size)
    }

    @Test
    fun executePreservesComments() {
        val original = "; Comment\nkey1=value1\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf("key1" to "updated"))
        transaction.execute("/test.ini", changes, "/test.ini.backup")

        val written = fileStore.read("/test.ini")!!
        assertTrue(written.contains("; Comment"))
        assertTrue(written.contains("key1=updated"))
    }

    @Test
    fun executePreservesLineEndings() {
        val original = "key1=value1\r\nkey2=value2\r\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(mapOf("key1" to "updated"))
        transaction.execute("/test.ini", changes, "/test.ini.backup")

        val written = fileStore.read("/test.ini")!!
        assertTrue(written.contains("\r\n"))
    }

    @Test
    fun executeWithMissingFile() {
        val changes = ChangeSet(mapOf("key1" to "value1"))
        val result = transaction.execute("/missing.ini", changes, "/missing.ini.backup")

        // Should still work: empty file → add the key
        assertTrue(result.hasChanges)
        assertEquals(1, result.changes.size)
        assertEquals("key1", result.changes[0].first)
        assertNull(result.changes[0].second) // old value was null
        assertEquals("value1", result.changes[0].third) // new value
    }

    @Test
    fun executeDolphinIni() {
        val original = "[Core]\nCPUThread=False\n[Graphics]\nResolution=0\n"
        fileStore.write("/Dolphin.ini", original)

        val dolphinTransaction = ConfigTransaction(DolphinIniCodec(), fileStore, snapshotStore)
        val changes = ChangeSet(mapOf(
            "Core.CPUThread" to "True",
            "Graphics.Resolution" to "1"
        ))
        val result = dolphinTransaction.execute("/Dolphin.ini", changes, "/Dolphin.ini.backup")

        assertEquals(2, result.changes.size)
        assertTrue(result.hasChanges)

        val written = fileStore.read("/Dolphin.ini")!!
        assertTrue(written.contains("CPUThread=True"))
        assertTrue(written.contains("Resolution=1"))
    }

    @Test
    fun executeBytesIdenticalWhenNoChanges() {
        val original = "key1=value1\nkey2=value2\n; Comment\n"
        fileStore.write("/test.ini", original)

        val changes = ChangeSet(emptyMap())
        val result = transaction.execute("/test.ini", changes, "/test.ini.backup")

        assertEquals(original, result.newText)
        assertEquals(original, result.originalText)
        assertFalse(result.hasChanges)
    }

    @Test
    fun previewWritesNothing() {
        fileStore.write("/p.ini", "[R]\na=1\n")
        val r = transaction.preview("/p.ini", ChangeSet(mapOf("R/a" to "2", "R/b" to "3")))
        assertEquals(listOf(Triple("R/a", "1", "2"), Triple("R/b", null, "3")), r.changes)
        assertEquals("[R]\na=1\n", fileStore.read("/p.ini"))
        assertEquals("[R]\na=2\nb=3\n", r.newText)
    }

    @Test
    fun undoRefusesWhenTheFileChangedSinceOurWrite() {
        fileStore.write("/u.ini", "[R]\na=1\n")
        val r = transaction.execute("/u.ini", ChangeSet(mapOf("R/a" to "2")), "/u.snap")
        fileStore.write("/u.ini", "[R]\na=5\n") // the app or the user edited it after us
        val res = transaction.undoChecked("/u.ini", "/u.snap", r.newText, existedBefore = true)
        assertTrue(res is ConfigTransaction.UndoResult.Changed)
        assertEquals("[R]\na=5\n", fileStore.read("/u.ini"))
        assertEquals(ConfigTransaction.UndoResult.Restored, transaction.undoChecked("/u.ini", "/u.snap", r.newText, existedBefore = true, force = true))
        assertEquals("[R]\na=1\n", fileStore.read("/u.ini"))
    }

    @Test
    fun undoOfANewFileRemovesIt() {
        val r = transaction.execute("/n.ini", ChangeSet(mapOf("R/a" to "1")), "/n.snap")
        assertEquals(ConfigTransaction.UndoResult.Restored, transaction.undoChecked("/n.ini", "/n.snap", r.newText, existedBefore = false))
        assertFalse(fileStore.exists("/n.ini"))
    }
}
