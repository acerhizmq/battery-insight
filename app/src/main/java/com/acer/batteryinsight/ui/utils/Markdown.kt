package com.acer.batteryinsight.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Minimal GitHub-flavored Markdown support for release notes: headings, emphasis, inline code,
 * strikethrough, links, (nested) lists, block quotes incl. GitHub alerts, rules and code blocks.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val text: String, val depth: Int, val marker: String) : MdBlock
    /** [alert] is NOTE, TIP, IMPORTANT, WARNING or CAUTION for GitHub alert blocks. */
    data class Quote(val blocks: List<MdBlock>, val alert: String?) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {

    const val LINK_TAG = "URL"

    private val headingRe = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val bulletRe = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val orderedRe = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
    private val ruleRe = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val alertRe = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*$""", RegexOption.IGNORE_CASE)
    private val tableSeparatorRe = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")

    fun parse(markdown: String): List<MdBlock> {
        val lines = markdown.replace("\r\n", "\n").replace('\t', ' ').split('\n')
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flush()

                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    flush()
                    val fence = trimmed.take(3)
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                        code += lines[i]
                        i++
                    }
                    blocks += MdBlock.Code(code.joinToString("\n"))
                }

                trimmed.startsWith(">") -> {
                    flush()
                    val inner = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        inner += lines[i].trim().removePrefix(">").removePrefix(" ")
                        i++
                    }
                    i--
                    val alert = inner.firstOrNull()?.let { alertRe.find(it.trim())?.groupValues?.get(1)?.uppercase() }
                    val body = if (alert != null) inner.drop(1) else inner
                    blocks += MdBlock.Quote(parse(body.joinToString("\n")), alert)
                }

                ruleRe.matches(line) -> {
                    flush()
                    blocks += MdBlock.Rule
                }

                headingRe.matches(trimmed) -> {
                    flush()
                    val m = headingRe.find(trimmed)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
                }

                bulletRe.matches(line) -> {
                    flush()
                    val m = bulletRe.find(line)!!
                    blocks += MdBlock.ListItem(m.groupValues[2], depthOf(m.groupValues[1]), "•")
                }

                orderedRe.matches(line) -> {
                    flush()
                    val m = orderedRe.find(line)!!
                    blocks += MdBlock.ListItem(m.groupValues[3], depthOf(m.groupValues[1]), "${m.groupValues[2]}.")
                }

                trimmed.startsWith("|") -> {
                    flush()
                    if (!tableSeparatorRe.matches(trimmed)) {
                        val cells = trimmed.trim('|').split('|').map { it.trim() }
                        blocks += MdBlock.Paragraph(cells.joinToString("  •  "))
                    }
                }

                // Indented continuation of the previous list item.
                line.startsWith("  ") && paragraph.isEmpty() && blocks.lastOrNull() is MdBlock.ListItem -> {
                    val last = blocks.removeAt(blocks.lastIndex) as MdBlock.ListItem
                    blocks += last.copy(text = last.text + " " + trimmed)
                }

                else -> paragraph += trimmed
            }
            i++
        }
        flush()
        return blocks
    }

    private fun depthOf(indent: String): Int = (indent.length / 2).coerceAtMost(4)

    /** Builds styled text for inline Markdown; links carry a [LINK_TAG] string annotation. */
    fun inline(text: String, linkColor: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
        appendInline(this, decodeEntities(text), linkColor, codeBackground)
    }

    private fun appendInline(b: AnnotatedString.Builder, s: String, linkColor: Color, codeBg: Color) {
        var i = 0
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                b.append(plain.toString())
                plain.clear()
            }
        }
        while (i < s.length) {
            val c = s[i]
            val rest = s.substring(i)
            when {
                c == '\\' && i + 1 < s.length && !s[i + 1].isLetterOrDigit() -> {
                    plain.append(s[i + 1])
                    i += 2
                    continue
                }
                c == '`' -> {
                    val end = s.indexOf('`', i + 1)
                    if (end > i) {
                        flushPlain()
                        b.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                            append(s.substring(i + 1, end))
                        }
                        i = end + 1
                        continue
                    }
                }
                rest.startsWith("**") || rest.startsWith("__") -> {
                    val delim = rest.take(2)
                    val end = s.indexOf(delim, i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        b.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendInline(this, s.substring(i + 2, end), linkColor, codeBg)
                        }
                        i = end + 2
                        continue
                    }
                }
                rest.startsWith("~~") -> {
                    val end = s.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        flushPlain()
                        b.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            appendInline(this, s.substring(i + 2, end), linkColor, codeBg)
                        }
                        i = end + 2
                        continue
                    }
                }
                (c == '*' || c == '_') && i + 1 < s.length && !s[i + 1].isWhitespace() &&
                    (c == '*' || i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    val end = findClosingEmphasis(s, i + 1, c)
                    if (end > i + 1) {
                        flushPlain()
                        b.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendInline(this, s.substring(i + 1, end), linkColor, codeBg)
                        }
                        i = end + 1
                        continue
                    }
                }
                c == '[' -> {
                    val close = s.indexOf("](", i + 1)
                    val urlEnd = if (close > i) s.indexOf(')', close + 2) else -1
                    if (close > i && urlEnd > close) {
                        flushPlain()
                        val url = s.substring(close + 2, urlEnd).trim()
                        b.pushStringAnnotation(LINK_TAG, url)
                        b.withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            appendInline(this, s.substring(i + 1, close), linkColor, codeBg)
                        }
                        b.pop()
                        i = urlEnd + 1
                        continue
                    }
                }
            }
            plain.append(c)
            i++
        }
        flushPlain()
    }

    private fun findClosingEmphasis(s: String, from: Int, delim: Char): Int {
        var j = from
        while (j < s.length) {
            if (s[j] == delim && !s[j - 1].isWhitespace() &&
                (delim == '*' || j + 1 >= s.length || !s[j + 1].isLetterOrDigit()) &&
                !(j + 1 < s.length && s[j + 1] == delim)
            ) return j
            j++
        }
        return -1
    }

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    /** Markdown reduced to readable plain text, e.g. for notifications. */
    fun toPlainText(markdown: String): String {
        val out = StringBuilder()
        fun render(blocks: List<MdBlock>) {
            for (block in blocks) {
                val line = when (block) {
                    is MdBlock.Heading -> plainInline(block.text)
                    is MdBlock.Paragraph -> plainInline(block.text)
                    is MdBlock.ListItem -> "  ".repeat(block.depth) + block.marker + " " + plainInline(block.text)
                    is MdBlock.Code -> block.text
                    is MdBlock.Quote -> {
                        block.alert?.let { out.append(it.lowercase().replaceFirstChar(Char::uppercase)).append(":\n") }
                        render(block.blocks)
                        null
                    }
                    MdBlock.Rule -> null
                }
                if (line != null) out.append(line).append('\n')
            }
        }
        render(parse(markdown))
        return out.toString().trim()
    }

    fun plainInline(text: String): String = inline(text, Color.Unspecified, Color.Unspecified).text
}
