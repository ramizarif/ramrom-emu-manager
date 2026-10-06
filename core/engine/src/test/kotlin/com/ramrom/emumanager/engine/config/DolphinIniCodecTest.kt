package com.ramrom.emumanager.engine.config

import org.junit.Assert.*
import org.junit.Test

class DolphinIniCodecTest {
    private val codec = DolphinIniCodec()

    @Test
    fun parseBasicSections() {
        val text = """
            [Core]
            CPUThread=True

            [Graphics]
            Resolution=1
        """.trimIndent() + "\n"

        val config = codec.parse(text)

        assertEquals("True", config.values["Core.CPUThread"])
        assertEquals("1", config.values["Graphics.Resolution"])
    }

    @Test
    fun parseWithComments() {
        val text = """
            ; Global settings
            [Core]
            ; CPU settings
            CPUThread=True
            # Another style comment

            [Graphics]
            Resolution=1
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        assertEquals("True", config.values["Core.CPUThread"])
        assertEquals("1", config.values["Graphics.Resolution"])
    }

    @Test
    fun roundTripPreservesComments() {
        val text = """
            ; Global comment
            [Core]
            ; Core comment
            CPUThread=True

            [Graphics]
            # Graphics comment
            Resolution=1
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        val output = codec.render(config)

        assertTrue(output.contains("; Global comment"))
        assertTrue(output.contains("; Core comment"))
        assertTrue(output.contains("# Graphics comment"))
    }

    @Test
    fun roundTripPreservesOrdering() {
        val text = """
            [Section]
            zzz=last
            aaa=first
            mmm=middle
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        val output = codec.render(config)
        val lines = output.split("\n")

        val zzzIdx = lines.indexOfFirst { it.contains("zzz=") }
        val aaaIdx = lines.indexOfFirst { it.contains("aaa=") }
        val mmmIdx = lines.indexOfFirst { it.contains("mmm=") }

        assertTrue(zzzIdx >= 0 && zzzIdx < aaaIdx)
        assertTrue(aaaIdx >= 0 && aaaIdx < mmmIdx)
    }

    @Test
    fun updateKeyInSection() {
        val text = """
            [Core]
            CPUThread=False

            [Graphics]
            Resolution=0
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        config.values["Core.CPUThread"] = "True"
        config.values["Graphics.Resolution"] = "1"

        val output = codec.render(config)

        assertTrue(output.contains("Core.CPUThread") || output.contains("[Core]"))
        assertTrue(output.contains("CPUThread=True"))
        assertTrue(output.contains("Resolution=1"))
    }

    @Test
    fun roundTripPreservesUnknownKeys() {
        val text = """
            [Known]
            known=old
            unknown_key=preserve_me

            [Other]
            other=value
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        config.values["Known.known"] = "new"

        val output = codec.render(config)

        assertTrue(output.contains("known=new"))
        assertTrue(output.contains("unknown_key=preserve_me"))
        assertTrue(output.contains("other=value"))
    }

    @Test
    fun addNewKeyToExistingSection() {
        val text = """
            [Core]
            CPUThread=True

            [Graphics]
            Resolution=1
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        config.values["Core.NewKey"] = "NewValue"

        val output = codec.render(config)

        assertTrue(output.contains("CPUThread=True"))
        assertTrue(output.contains("NewKey=NewValue"))
    }

    @Test
    fun roundTripMultipleSections() {
        val text = """
            [Section1]
            key1=value1

            [Section2]
            key2=value2

            [Section3]
            key3=value3
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        val output = codec.render(config)

        val sections = output.split(Regex("\\[.*?\\]"))
        assertTrue(sections.size >= 3) // At least the 3 sections

        config.values["Section2.key2"] = "updated"
        val updated = codec.render(config)
        assertTrue(updated.contains("key2=updated"))
    }

    @Test
    fun roundTripLineEndings() {
        val textLf = "[Core]\nCPUThread=True\n"
        val configLf = codec.parse(textLf)
        val outputLf = codec.render(configLf)

        assertEquals("\n", configLf.metadata.lineEnding)
        assertTrue(outputLf.contains("CPUThread=True"))

        val textCrLf = "[Core]\r\nCPUThread=True\r\n"
        val configCrLf = codec.parse(textCrLf)
        val outputCrLf = codec.render(configCrLf)

        assertEquals("\r\n", configCrLf.metadata.lineEnding)
        assertTrue(outputCrLf.contains("CPUThread=True"))
    }

    @Test
    fun roundTripBlankLines() {
        val text = """
            [Core]
            key1=value1


            key2=value2

            [Graphics]
            key3=value3
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        val output = codec.render(config)

        assertEquals("value1", config.values["Core.key1"])
        assertEquals("value2", config.values["Core.key2"])
        assertEquals("value3", config.values["Graphics.key3"])

        assertTrue(output.contains("[Core]"))
        assertTrue(output.contains("key1=value1"))
        assertTrue(output.contains("key2=value2"))
    }

    @Test
    fun roundTripNoTrailingNewline() {
        val text = "[Core]\nCPUThread=True"
        val config = codec.parse(text)
        val output = codec.render(config)

        assertFalse(config.metadata.hasTrailingNewline)
        assertFalse(output.endsWith("\n"))
    }

    @Test
    fun parseKeysWithoutSection() {
        val text = """
            global_key=global_value

            [Section]
            scoped_key=scoped_value
        """.trimIndent() + "\n"

        val config = codec.parse(text)

        // Keys without section are stored without a section prefix
        assertEquals("global_value", config.values["global_key"])
        assertEquals("scoped_value", config.values["Section.scoped_key"])
    }

    @Test
    fun roundTripEmptyFile() {
        val text = ""
        val config = codec.parse(text)
        val output = codec.render(config)

        assertEquals("", output)
    }
}
