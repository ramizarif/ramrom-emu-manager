package com.ramrom.emumanager.engine.install

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionsTest {
    @Test fun numericVersions() {
        assertEquals(true, Versions.isNewer("2.0.1 GH", "v2.1.0"))
        assertEquals(false, Versions.isNewer("2125.0", "2125"))
        assertEquals(false, Versions.isNewer("1.3.0", "v1.2.9"))
        assertEquals(true, Versions.isNewer("v0.2.0", "v0.2.1"))
        assertEquals(true, Versions.isNewer("b2.2", "beta-3.07"))
        assertEquals(false, Versions.isNewer("beta-3.07", "b3.07"))
    }

    @Test fun hashesAndBlanksAreUnknown() {
        assertEquals(null, Versions.isNewer("1f6734c", "v0.2.1"))
        assertEquals(null, Versions.isNewer("", "v1"))
        assertEquals(null, Versions.isNewer("1.0", "nightly"))
    }
}
