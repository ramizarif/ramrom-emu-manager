package com.ramrom.emumanager.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Room type converters.
 */
class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun testAppModeConversion() {
        assertEquals("ELEVATED", converters.appModeToString(AppMode.ELEVATED))
        assertEquals(AppMode.LIMITED, converters.stringToAppMode("LIMITED"))
    }

    @Test
    fun testPlanSystemListConversion() {
        val systems = listOf(
            PlanSystem("nes", "nestopia"),
            PlanSystem("snes", "snes9x")
        )

        val json = converters.planSystemListToJson(systems)
        assertTrue(json.contains("nes"))
        assertTrue(json.contains("nestopia"))

        val decoded = converters.jsonToPlanSystemList(json)
        assertEquals(2, decoded.size)
        assertEquals("nes", decoded[0].systemId)
    }

    @Test
    fun testWizardStepStatusConversion() {
        assertEquals("DONE", converters.wizardStepStatusToString(WizardStepStatus.DONE))
        assertEquals(WizardStepStatus.RUNNING, converters.stringToWizardStepStatus("RUNNING"))
    }

    @Test
    fun testStringListConversion() {
        val list = listOf("tag1", "tag2", "tag3")

        val json = converters.stringListToJson(list)
        assertTrue(json.contains("tag1"))

        val decoded = converters.jsonToStringList(json)
        assertEquals(3, decoded.size)
        assertEquals("tag2", decoded[1])
    }

    @Test
    fun testEmptyListConversion() {
        val empty = emptyList<String>()
        val json = converters.stringListToJson(empty)
        val decoded = converters.jsonToStringList(json)
        assertEquals(0, decoded.size)
    }

    @Test
    fun testConfigChangeTargetConversion() {
        assertEquals("FILE", converters.configChangeTargetToString(ConfigChangeTarget.FILE))
        assertEquals(ConfigChangeTarget.APP, converters.stringToConfigChangeTarget("APP"))
    }

    @Test
    fun testHealthIssueSeverityConversion() {
        assertEquals("BLOCKING", converters.healthIssueSeverityToString(HealthIssueSeverity.BLOCKING))
        assertEquals(HealthIssueSeverity.TIP, converters.stringToHealthIssueSeverity("TIP"))
    }
}
