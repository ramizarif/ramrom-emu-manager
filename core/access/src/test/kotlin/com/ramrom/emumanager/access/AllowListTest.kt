package com.ramrom.emumanager.access

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM unit tests for AllowList validation.
 */
class AllowListTest {

    @Test
    fun pmInstallAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("pm", "install", "/path/to/app.apk")))
    }

    @Test
    fun pmUninstallAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("pm", "uninstall", "com.example.app")))
    }

    @Test
    fun pmGrantAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("pm", "grant", "com.example", "android.permission.CAMERA")))
    }

    @Test
    fun pmRevokeAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("pm", "revoke", "com.example", "android.permission.CAMERA")))
    }

    @Test
    fun pmUnknownSubcommandDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("pm", "clear", "com.example")))
    }

    @Test
    fun amForceStopAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("am", "force-stop", "com.example")))
    }

    @Test
    fun amStartAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("am", "start", "com.example")))
    }

    @Test
    fun amUnknownSubcommandDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("am", "kill", "com.example")))
    }

    @Test
    fun appopsSetAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("appops", "set", "--uid", "10123", "com.example", "SYSTEM_ALERT_WINDOW", "allow")))
    }

    @Test
    fun appopsGetAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("appops", "get", "com.example")))
    }

    @Test
    fun dumpsysActivityAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("dumpsys", "activity", "permissions")))
    }

    @Test
    fun dumpsysPackageAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("dumpsys", "package")))
    }

    @Test
    fun dumpsysUnknownDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("dumpsys", "meminfo")))
    }

    @Test
    fun pidofAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("pidof", "com.example")))
    }

    @Test
    fun lsAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("ls", "ROMs/switch")))
    }

    @Test
    fun shDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("sh", "-c", "rm -rf /")))
    }

    @Test
    fun rmDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("rm", "-rf", "/data")))
    }

    @Test
    fun emptyCommandDenied() {
        assertFalse(AllowList.isCommandAllowed(emptyList()))
    }

    @Test
    fun pathTraversalAllowed() {
        assertFalse(AllowList.isPathAllowed("../../../data/data"))
    }

    @Test
    fun pathWithDotsAllowed() {
        assertFalse(AllowList.isPathAllowed("ROMs/../../../etc/passwd"))
    }

    @Test
    fun absolutePathAllowed() {
        assertTrue(AllowList.isPathAllowed("/storage/emulated/0/ROMs"))
    }

    @Test
    fun absoluteNestedPathAllowed() {
        assertTrue(AllowList.isPathAllowed("/storage/emulated/0/Android/data/com.example/files"))
    }

    @Test
    fun relativePathAllowed() {
        assertTrue(AllowList.isPathAllowed("ROMs/switch"))
    }

    @Test
    fun nestedPathAllowed() {
        assertTrue(AllowList.isPathAllowed("Android/data/com.example/files/config"))
    }

    @Test
    fun injectionWithSemicolonDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("pm; rm -rf /", "install")))
    }

    @Test
    fun injectionWithPipeDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("pm | rm -rf /", "install")))
    }

    @Test
    fun pmNoSubcommandDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("pm")))
    }

    @Test
    fun statCommandAllowed() {
        assertTrue(AllowList.isCommandAllowed(listOf("stat", "ROMs/n64/game.z64")))
    }

    @Test
    fun commandWithInvalidCharsDenied() {
        assertFalse(AllowList.isCommandAllowed(listOf("p\nm", "install")))
    }

    @Test
    fun md5sumTakesOnePathOnly() {
        assertTrue(AllowList.isCommandAllowed(listOf("md5sum", "/storage/emulated/0/Android/data/xyz.aethersx2.tturnip/files/bios/7EBD68DE.bin")))
        assertFalse(AllowList.isCommandAllowed(listOf("md5sum", "/data/system/packages.xml")))
        assertFalse(AllowList.isCommandAllowed(listOf("md5sum", "ROMs/a.bin", "ROMs/b.bin")))
    }

    @Test
    fun statTakesOnlyTheSizeFormat() {
        assertTrue(AllowList.isCommandAllowed(listOf("stat", "-c", "%s %Y %F", "ROMs/n64/game.z64")))
        assertFalse(AllowList.isCommandAllowed(listOf("stat", "-c", "%n", "/data/system/packages.xml")))
        assertFalse(AllowList.isCommandAllowed(listOf("stat", "-c", "%s %Y %F", "/data/system/packages.xml")))
    }
}
