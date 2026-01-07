// app/src/main/java/com/pomoremote/ui/LineGraphView.kt
package com.pomoremote.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.pomoremote.R
import java.util.Locale

class LineGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<Pair<String, Int>> = emptyList()
    private var dailyGoal: Int = 8

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val primaryColor = ContextCompat.getColor(context, R.color.md_theme_primary)
    private val goldColor = ContextCompat.getColor(context, R.color.gold)
    private val surfaceColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)

    fun setData(data: List<Pair<String, Int>>, goal: Int) {
        dataPoints = data
        dailyGoal = goal
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) return

        val padding = 24f * resources.displayMetrics.density
        val bottomPadding = 28f * resources.displayMetrics.density
        val topPadding = 20f * resources.displayMetrics.density

        val graphWidth = width - 2 * padding
        val graphHeight = height - bottomPadding - topPadding

        val maxMinutes = (dataPoints.maxOfOrNull { it.second } ?: 60).coerceAtLeast(60)
        val goalMinutes = dailyGoal * 25

        val pointSpacing = graphWidth / (dataPoints.size - 1).coerceAtLeast(1)

        // Calculate points
        val points = dataPoints.mapIndexed { index, (_, minutes) ->
            val x = padding + index * pointSpacing
            val y = topPadding + graphHeight - (minutes.toFloat() / maxMinutes * graphHeight)
            PointF(x, y)
        }

        // Draw gradient fill under the line
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, topPadding + graphHeight)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
                lineTo(points.last().x, topPadding + graphHeight)
                close()
            }

            val gradient = LinearGradient(
                0f, topPadding,
                0f, topPadding + graphHeight,
                Color.argb(60, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Color.argb(10, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = gradient
            canvas.drawPath(path, fillPaint)
        }

        // Draw the line
        if (points.size >= 2) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            linePaint.color = primaryColor
            canvas.drawPath(linePath, linePaint)
        }

        // Draw dots and labels
        dataPoints.forEachIndexed { index, (label, minutes) ->
            val point = points[index]
            val isGoalMet = minutes >= goalMinutes

            // Dot
            dotPaint.color = if (isGoalMet) goldColor else primaryColor
            val dotRadius = if (isGoalMet) 6f else 5f
            canvas.drawCircle(point.x, point.y, dotRadius * resources.displayMetrics.density, dotPaint)

            // Value above dot
            valuePaint.color = if (isGoalMet) goldColor else surfaceColor
            valuePaint.alpha = 180
            val valueText = if (minutes >= 60) {
                String.format(Locale.US, "%.1fh", minutes / 60f)
            } else {
                "${minutes}m"
            }
            canvas.drawText(valueText, point.x, point.y - 10 * resources.displayMetrics.density, valuePaint)

            // Day label below
            textPaint.color = surfaceColor
            textPaint.alpha = 150
            canvas.drawText(label, point.x, height - 8f * resources.displayMetrics.density, textPaint)
        }
    }
}
