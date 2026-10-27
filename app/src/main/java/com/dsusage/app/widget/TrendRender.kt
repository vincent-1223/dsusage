package com.dsusage.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.dsusage.app.data.UsageSnapshot
import kotlin.math.max

object TrendRender {
    fun renderBars(
        snapshot: UsageSnapshot,
        width: Int,
        height: Int,
        dark: Boolean,
        line: Boolean = false
    ): Bitmap {
        val safeWidth = max(1, width)
        val safeHeight = max(1, height)
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val values = snapshot.trend.map { it.cost.toFloat() }
        if (values.isEmpty()) return bitmap

        val accent = Color.rgb(77, 107, 254)
        val track = if (dark) Color.argb(30, 255, 255, 255) else Color.argb(18, 32, 36, 43)
        val maxValue = values.maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (line) {
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = track
                strokeWidth = 1f
            }
            repeat(3) { index ->
                val y = safeHeight * (index + 1) / 4f
                canvas.drawLine(0f, y, safeWidth.toFloat(), y, gridPaint)
            }
            val path = Path()
            val fillPath = Path()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) safeWidth / 2f else {
                    safeWidth * index / (values.size - 1f)
                }
                val y = safeHeight - 4f - (safeHeight - 8f) * (value / maxValue)
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, safeHeight.toFloat())
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(safeWidth.toFloat(), safeHeight.toFloat())
            fillPath.close()
            canvas.drawPath(
                fillPath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(36, 77, 107, 254)
                }
            )
            canvas.drawPath(
                path,
                paint.apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeWidth = 2.2f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            )
            return bitmap
        }

        val gap = max(2f, safeWidth * 0.018f)
        val barWidth = (safeWidth - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
            val barHeight = max(3f, (safeHeight - 4f) * (value / maxValue))
            val left = index * (barWidth + gap)
            val rect = RectF(
                left,
                safeHeight - barHeight,
                left + barWidth,
                safeHeight.toFloat()
            )
            paint.color = if (index == values.lastIndex) accent else {
                Color.argb(92, 77, 107, 254)
            }
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, barWidth / 3f, barWidth / 3f, paint)
        }
        return bitmap
    }
}

