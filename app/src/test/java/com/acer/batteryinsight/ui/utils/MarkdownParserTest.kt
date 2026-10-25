package com.acer.batteryinsight.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun inline(s: String) = MarkdownParser.inline(s, Color.Blue, Color.Gray)

    @Test
    fun `bold italic code and strike are rendered without markers`() {
        val a = inline("**Bold** and *italic*, `code`, ~~old~~")
        assertEquals("Bold and italic, code, old", a.text)
        assertTrue(a.spanStyles.any { it.item.fontWeight == FontWeight.Bold && a.text.substring(it.start, it.end) == "Bold" })
        assertTrue(a.spanStyles.any { it.item.fontStyle == FontStyle.Italic && a.text.substring(it.start, it.end) == "italic" })
    }

    @Test
    fun `nested emphasis and links`() {
        val a = inline("- **Telegram:** [@acerhizm](https://t.me/acerhizm)")
        assertEquals("- Telegram: @acerhizm", a.text)
        val link = a.getStringAnnotations(MarkdownParser.LINK_TAG, 0, a.length).single()
        assertEquals("https://t.me/acerhizm", link.item)
        assertEquals("@acerhizm", a.text.substring(link.start, link.end))
    }

    @Test
    fun `snake_case and lone markers stay literal`() {
        assertEquals("MILLET_NO_RESTRICT_APP and 5 * 3", inline("MILLET_NO_RESTRICT_APP and 5 * 3").text)
        assertEquals("use *", inline("use *").text)
        assertEquals("a*b", inline("a\\*b").text)
    }

    @Test
    fun `parses block structure`() {
        val blocks = MarkdownParser.parse(
            """
            ## Title
            ### Section
            - **Item:**
              - Child one
            1. First
            ---
            > [!IMPORTANT]
            > Line
            > 1. Step
            """.trimIndent()
        )
        assertEquals(MdBlock.Heading(2, "Title"), blocks[0])
        assertEquals(MdBlock.Heading(3, "Section"), blocks[1])
        assertEquals(MdBlock.ListItem("**Item:**", 0, "•"), blocks[2])
        assertEquals(MdBlock.ListItem("Child one", 1, "•"), blocks[3])
        assertEquals(MdBlock.ListItem("First", 0, "1."), blocks[4])
        assertEquals(MdBlock.Rule, blocks[5])
        val quote = blocks[6] as MdBlock.Quote
        assertEquals("IMPORTANT", quote.alert)
        assertEquals(listOf(MdBlock.Paragraph("Line"), MdBlock.ListItem("Step", 0, "1.")), quote.blocks)
    }

    @Test
    fun `real release notes leave no markdown syntax behind`() {
        val md = javaClass.classLoader!!.getResource("release_notes_v1.3.37.md")!!.readText()
        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks.first() is MdBlock.Heading)
        assertTrue(blocks.any { it is MdBlock.Quote && it.alert == "IMPORTANT" })
        assertTrue(blocks.any { it is MdBlock.ListItem && it.depth == 1 })

        fun texts(bs: List<MdBlock>): List<String> = bs.flatMap {
            when (it) {
                is MdBlock.Heading -> listOf(it.text)
                is MdBlock.Paragraph -> listOf(it.text)
                is MdBlock.ListItem -> listOf(it.text)
                is MdBlock.Quote -> texts(it.blocks)
                else -> emptyList()
            }
        }
        for (t in texts(blocks)) {
            val rendered = inline(t).text
            assertFalse("leftover ** in: $rendered", rendered.contains("**"))
            assertFalse("leftover backtick in: $rendered", rendered.contains("`"))
            assertFalse("leftover link syntax in: $rendered", rendered.contains("]("))
        }

        val plain = MarkdownParser.toPlainText(md)
        assertFalse(plain.contains("**") || plain.contains("###") || plain.contains("[!IMPORTANT]"))
        assertTrue(plain.startsWith("BatteryInsight v1.3.37 Release"))
        assertTrue(plain.contains("Important:"))
    }
}
