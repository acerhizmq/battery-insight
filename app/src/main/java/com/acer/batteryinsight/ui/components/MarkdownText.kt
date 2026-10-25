package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.acer.batteryinsight.R
import com.acer.batteryinsight.ui.utils.MarkdownParser
import com.acer.batteryinsight.ui.utils.MdBlock

/** Renders GitHub release-note Markdown (see [MarkdownParser]). */
@Composable
fun MarkdownText(
    markdown: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MarkdownBlocks(blocks, style.copy(color = color))
    }
}

@Composable
private fun MarkdownBlocks(blocks: List<MdBlock>, style: TextStyle) {
    for (block in blocks) {
        when (block) {
            is MdBlock.Heading -> InlineText(
                block.text,
                style.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = style.fontSize.scaled(
                        when (block.level) {
                            1 -> 1.35f
                            2 -> 1.2f
                            else -> 1.08f
                        }
                    ),
                    lineHeight = style.lineHeight.scaled(1.15f),
                ),
                Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp),
            )

            is MdBlock.Paragraph -> InlineText(block.text, style)

            is MdBlock.ListItem -> Row(Modifier.padding(start = (block.depth * 14).dp)) {
                Text(
                    text = block.marker,
                    style = style.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.width(if (block.marker == "•") 12.dp else 20.dp),
                )
                InlineText(block.text, style, Modifier.weight(1f))
            }

            is MdBlock.Quote -> QuoteBlock(block, style)

            is MdBlock.Code -> Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                Text(block.text, style = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize.scaled(0.92f)))
            }

            MdBlock.Rule -> HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** Scales a text unit; Unspecified stays Unspecified (multiplying it would throw). */
private fun TextUnit.scaled(factor: Float): TextUnit = if (isSpecified) this * factor else this

@Composable
private fun QuoteBlock(block: MdBlock.Quote, style: TextStyle) {
    val scheme = MaterialTheme.colorScheme
    val (accent, title) = when (block.alert) {
        "NOTE" -> scheme.primary to stringResource(R.string.battery_insight_md_note)
        "TIP" -> Color(0xFF2E9E5B) to stringResource(R.string.battery_insight_md_tip)
        "IMPORTANT" -> scheme.tertiary to stringResource(R.string.battery_insight_md_important)
        "WARNING" -> Color(0xFFD19A1F) to stringResource(R.string.battery_insight_md_warning)
        "CAUTION" -> scheme.error to stringResource(R.string.battery_insight_md_caution)
        else -> scheme.outline to null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.08f))
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null) {
                Text(title, style = style.copy(color = accent, fontWeight = FontWeight.Bold))
            }
            MarkdownBlocks(block.blocks, if (block.alert == null) style.copy(color = scheme.onSurfaceVariant) else style)
        }
    }
}

@Composable
private fun InlineText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(text, linkColor, codeBg) { MarkdownParser.inline(text, linkColor, codeBg) }
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(MarkdownParser.LINK_TAG, offset, offset).firstOrNull()?.let {
                try {
                    uriHandler.openUri(it.item)
                } catch (_: Exception) {}
            }
        },
    )
}
