package com.ramrom.emumanager.engine.config

import org.junit.Assert.*
import org.junit.Test

class QtIniCodecTest {
    private val codec = QtIniCodec()

    @Test
    fun parseBasicKeyValue() {
        val text = "key1=value1\nkey2=value2\n"
        val config = codec.parse(text)

        assertEquals("value1", config.values["key1"])
        assertEquals("value2", config.values["key2"])
    }

    @Test
    fun parseWithComments() {
        val text = """
            ; This is a comment
            key1=value1
            # Also a comment
            key2=value2
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        assertEquals("value1", config.values["key1"])
        assertEquals("value2", config.values["key2"])
    }

    @Test
    fun parseWithSections() {
        val text = """
            [Section1]
            key1=value1
            [Section2]
            key2=value2
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        assertEquals("value1", config.values["Section1/key1"])
        assertEquals("value2", config.values["Section2/key2"])
    }

    @Test
    fun parseWithQtDefaultKeys() {
        val text = """
            key=value
            key\default=true
            key\use_global=false
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        assertEquals("value", config.values["key"])
        assertEquals("true", config.values["key\\default"])
        assertEquals("false", config.values["key\\use_global"])
    }

    @Test
    fun roundTripPreservesComments() {
        val text = """
            ; Important setting
            key1=value1
            # Another comment
            key2=value2
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        val output = codec.render(config)

        assertTrue(output.contains("; Important setting"))
        assertTrue(output.contains("# Another comment"))
        assertTrue(output.contains("key1=value1"))
        assertTrue(output.contains("key2=value2"))
    }

    @Test
    fun roundTripPreservesOrdering() {
        val text = """
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
    fun roundTripPreservesLineEnding() {
        val textLf = "key1=value1\nkey2=value2\n"
        val configLf = codec.parse(textLf)
        val outputLf = codec.render(configLf)

        assertEquals("\n", configLf.metadata.lineEnding)
        assertTrue(outputLf.endsWith("\n"))
        assertFalse(outputLf.endsWith("\r\n"))

        val textCrLf = "key1=value1\r\nkey2=value2\r\n"
        val configCrLf = codec.parse(textCrLf)
        val outputCrLf = codec.render(configCrLf)

        assertEquals("\r\n", configCrLf.metadata.lineEnding)
        assertTrue(outputCrLf.endsWith("\r\n"))
    }

    @Test
    fun roundTripPreservesTrailingNewline() {
        val textWithTrailing = "key=value\n"
        val configWith = codec.parse(textWithTrailing)
        val outputWith = codec.render(configWith)

        assertTrue(configWith.metadata.hasTrailingNewline)
        assertTrue(outputWith.endsWith("\n"))

        val textNoTrailing = "key=value"
        val configNo = codec.parse(textNoTrailing)
        val outputNo = codec.render(configNo)

        assertFalse(configNo.metadata.hasTrailingNewline)
        assertFalse(outputNo.endsWith("\n"))
    }

    @Test
    fun updatePreservesUnknownKeys() {
        val text = """
            known1=old1
            unknown_key=should_preserve_this
            known2=old2
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        config.values["known1"] = "new1"
        config.values["known2"] = "new2"

        val output = codec.render(config)

        assertTrue(output.contains("known1=new1"))
        assertTrue(output.contains("known2=new2"))
        assertTrue(output.contains("unknown_key=should_preserve_this"))
    }

    @Test
    fun addNewKeys() {
        val text = "key1=value1\n"
        val config = codec.parse(text)

        config.values["key2"] = "value2"
        config.values["key3"] = "value3"

        val output = codec.render(config)

        assertTrue(output.contains("key1=value1"))
        assertTrue(output.contains("key2=value2"))
        assertTrue(output.contains("key3=value3"))
    }

    @Test
    fun roundTripEmptyFile() {
        val text = ""
        val config = codec.parse(text)
        val output = codec.render(config)

        assertEquals("", output)
    }

    @Test
    fun roundTripCommentsOnly() {
        val text = """
            ; Comment 1
            # Comment 2
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        val output = codec.render(config)

        assertTrue(output.contains("; Comment 1"))
        assertTrue(output.contains("# Comment 2"))
    }

    @Test
    fun roundTripIndentationPreserved() {
        val text = """
            [Section]
              indented_key=value
            non_indented=value2
        """.trimIndent() + "\n"

        val config = codec.parse(text)
        config.values["Section/indented_key"] = "new_value"

        val output = codec.render(config)

        assertTrue(output.contains("  indented_key=new_value"))
        assertTrue(output.contains("non_indented=value2"))
    }

    /** Eden per-game overrides: three lines per key, in the right section. */
    @Test
    fun newKeysGoInTheirSection() {
        val text = "[Core]\nuse_multi_core=true\n\n[Renderer]\nbackend=1\n\n[System]\nlanguage_index=1\n"
        val config = codec.parse(text)
        config.values["Renderer/resolution_setup\\use_global"] = "false"
        config.values["Renderer/resolution_setup\\default"] = "false"
        config.values["Renderer/resolution_setup"] = "3"
        config.values["Cpu/cpu_accuracy"] = "0"
        val out = codec.render(config)
        assertEquals("[Core]\nuse_multi_core=true\n\n[Renderer]\nbackend=1\nresolution_setup\\use_global=false\nresolution_setup\\default=false\nresolution_setup=3\n\n[System]\nlanguage_index=1\n\n[Cpu]\ncpu_accuracy=0\n", out)
    }

    @Test
    fun sameKeyInTwoSectionsStaysApart() {
        val config = codec.parse("[A]\nx=1\n[B]\nx=2\n")
        config.values["B/x"] = "9"
        assertEquals("[A]\nx=1\n[B]\nx=9\n", codec.render(config))
    }

    @Test
    fun removedKeysAreDropped() {
        val config = codec.parse("[R]\na=1\nb=2\n")
        config.values.remove("R/a")
        assertEquals("[R]\nb=2\n", codec.render(config))
    }

    @Test
    fun newFileGetsSections() {
        val config = codec.parse("")
        config.values["Renderer/resolution_setup"] = "3"
        assertEquals("[Renderer]\nresolution_setup=3\n", codec.render(config))
    }

    @Test
    fun untouchedFileIsByteForByte() {
        val text = "; c\r\n[A]\r\n  x = 1\r\ny=2\r\n\r\n[B]\r\nz=3"
        assertEquals(text, codec.render(codec.parse(text)))
    }

    @Test
    fun spacedFilesKeepTheirSpacing() {
        // Azahar writes `key = value`, and an empty value means "use the default".
        val config = codec.parse("[Renderer]\nuse_vsync = \nlayout_option = 1\n")
        assertEquals("", config.values["Renderer/use_vsync"])
        assertEquals("1", config.values["Renderer/layout_option"])
        config.values["Renderer/use_vsync"] = "true"
        config.values["Renderer/factor_3d"] = "0"
        assertEquals("[Renderer]\nuse_vsync = true\nlayout_option = 1\nfactor_3d = 0\n", codec.render(config))
    }
}
