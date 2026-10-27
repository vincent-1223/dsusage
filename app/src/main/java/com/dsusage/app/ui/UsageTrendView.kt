package com.dsusage.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.dsusage.app.R
import com.dsusage.app.data.UsageSnapshot
import kotlin.math.max

class UsageTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var snapshot: UsageSnapshot = UsageSnapshot()
    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.density * resources.configuration.fontScale
        textAlign = Paint.Align.CENTER
    }

    fun submit(snapshot: UsageSnapshot) {
        this.snapshot = snapshot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val accent = ContextCompat.getColor(context, R.color.deepseek_blue)
        gridPaint.color = ContextCompat.getColor(context, R.color.line)
        textPaint.color = ContextCompat.getColor(context, R.color.ink_tertiary)
        linePaint.color = accent
        fillPaint.color = Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent))

        val left = 0f
        val right = width.toFloat()
        val top = 4f * density
        val bottom = height - 22f * density
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        repeat(3) { index ->
            val y = top + chartHeight * (index + 1) / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        val points = snapshot.trend
        if (points.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("同步后显示最近 7 天趋势", width / 2f, height / 2f, textPaint)
            return
        }

        val maxValue = points.maxOf { it.cost.toFloat() }.coerceAtLeast(0.01f)
        val gap = 10f * density
        val usableWidth = max(1f, right - left - gap * (points.size - 1))
        val step = if (points.size == 1) 0f else usableWidth / (points.size - 1)
        val rounded = RectF()
        val path = Path()
        val fillPath = Path().apply {
            moveTo(left, bottom)
        }
        val xPositions = mutableListOf<Float>()

        points.forEachIndexed { index, day ->
            val x = left + index * (step + gap)
            val y = bottom - chartHeight * (day.cost.toFloat() / maxValue)
            xPositions += x
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            fillPath.lineTo(x, y)
            rounded.set(x - 2.2f * density, y - 2.2f * density, x + 2.2f * density, y + 2.2f * density)
            if (index == points.lastIndex) {
                fillPaint.alpha = 210
                canvas.drawOval(rounded, fillPaint)
            }
        }
        fillPath.lineTo(xPositions.lastOrNull() ?: right, bottom)
        fillPath.close()
        fillPaint.alpha = 30
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        textPaint.textAlign = Paint.Align.CENTER
        points.forEachIndexed { index, day ->
            val x = xPositions[index].coerceIn(16f * density, width - 16f * density)
            canvas.drawText("${day.date.monthValue}/${day.date.dayOfMonth}", x, height - 4f * density, textPaint)
        }
    }
}
