package com.acer.batteryinsight.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.acer.batteryinsight.model.BatteryInsightStats
import java.util.Locale
import kotlin.math.abs

/**
 * Renders the notification small icon for the selected style. Status bar icons are drawn as alpha
 * masks by SystemUI, so everything is painted opaque white on a transparent background.
 */
class StatusIconRenderer {

    companion object {
        const val STYLE_BATTERY = 0
        const val STYLE_PERCENT = 1
        const val STYLE_CIRCLE_PERCENT = 2
        const val STYLE_TEMP = 3
        const val STYLE_PERCENT_TEMP = 4
        const val STYLE_CURRENT = 5
        const val STYLE_VOLTAGE = 6
        const val STYLE_POWER = 7
        const val STYLE_COUNT = 8

        private const val SIZE = 96
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var cachedKey: String? = null
    private var cachedIcon: IconCompat? = null

    /** Returns null for [STYLE_BATTERY] (use the static drawable) or on rendering failure. */
    fun render(style: Int, stats: BatteryInsightStats): IconCompat? {
        if (style !in 1 until STYLE_COUNT) return null
        val lines = labelFor(style, stats)
        val key = "$style|${lines.joinToString("|")}"
        if (key == cachedKey) return cachedIcon
        return try {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (style == STYLE_CIRCLE_PERCENT) {
                drawRing(canvas, stats.level)
                drawLines(canvas, lines, SIZE * 0.62f)
            } else {
                drawLines(canvas, lines, SIZE.toFloat())
            }
            IconCompat.createWithBitmap(bitmap).also {
                cachedKey = key
                cachedIcon = it
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Exposed for change detection without rendering. */
    fun labelFor(style: Int, stats: BatteryInsightStats): List<String> {
        val level = stats.level.coerceIn(0, 100)
        val tempC = Math.round(stats.temp / 10f)
        return when (style) {
            STYLE_PERCENT, STYLE_CIRCLE_PERCENT -> listOf("$level")
            STYLE_TEMP -> listOf("$tempC°")
            STYLE_PERCENT_TEMP -> listOf("$level%", "$tempC°")
            STYLE_CURRENT -> {
                val ma = abs(stats.currentNow)
                if (ma >= 1000) listOf(String.format(Locale.US, "%.1f", ma / 1000f), "A") else listOf("$ma", "mA")
            }
            STYLE_VOLTAGE -> listOf(String.format(Locale.US, "%.2f", stats.voltage / 1000f), "V")
            STYLE_POWER -> listOf(String.format(Locale.US, "%.1f", stats.powerWatts), "W")
            else -> emptyList()
        }
    }

    private fun drawRing(canvas: Canvas, level: Int) {
        val stroke = SIZE * 0.09f
        strokePaint.strokeWidth = stroke
        val inset = stroke / 2f + 1f
        val rect = RectF(inset, inset, SIZE - inset, SIZE - inset)
        strokePaint.alpha = 90
        canvas.drawArc(rect, 0f, 360f, false, strokePaint)
        strokePaint.alpha = 255
        canvas.drawArc(rect, -90f, 360f * level.coerceIn(0, 100) / 100f, false, strokePaint)
    }

    /** Fits up to two lines of text into a centered box of [box] pixels. */
    private fun drawLines(canvas: Canvas, lines: List<String>, box: Float) {
        if (lines.isEmpty()) return
        val lineHeight = box / lines.size
        var textSize = lineHeight * 0.95f
        textPaint.textSize = textSize
        val widest = lines.maxOf { textPaint.measureText(it) }
        if (widest > box) {
            textSize *= box / widest
            textPaint.textSize = textSize
        }
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val totalHeight = textHeight * lines.size
        var baseline = (SIZE - totalHeight) / 2f - metrics.ascent
        for (line in lines) {
            canvas.drawText(line, SIZE / 2f, baseline, textPaint)
            baseline += textHeight
        }
    }
}
