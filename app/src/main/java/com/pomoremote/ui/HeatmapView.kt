package com.pomoremote.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.pomoremote.R
import java.text.SimpleDateFormat
import java.util.*

class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var historyData: Map<String, DayEntry> = emptyMap()
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Config
    private val cellSize = 12f * resources.displayMetrics.density
    private val cellSpacing = 4f * resources.displayMetrics.density
    private val cornerRadius = 2f * resources.displayMetrics.density
    private val weeksToShow = 20 // Show last ~5 months by default to fit screen width roughly

    // Paints
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_theme_surfaceVariant)
        alpha = 100 // Slightly transparent
    }

    private val level1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val level2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val level3Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val level4Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        val primary = ContextCompat.getColor(context, R.color.md_theme_primary)

        level1Paint.color = primary
        level1Paint.alpha = 60  // 25% intensity

        level2Paint.color = primary
        level2Paint.alpha = 120 // 50% intensity

        level3Paint.color = primary
        level3Paint.alpha = 190 // 75% intensity

        level4Paint.color = primary // 100% intensity
    }

    fun setData(data: Map<String, DayEntry>) {
        historyData = data
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Calculate desired size
        val desiredWidth = (weeksToShow * (cellSize + cellSpacing) + cellSpacing).toInt()
        val desiredHeight = (7 * (cellSize + cellSpacing) + cellSpacing).toInt() // 7 days a week

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Reset calendar to end date (today)
        calendar.time = Date()

        // Align to the end of the current week (Saturday) so columns line up
        // Or align so today is the last cell.
        // GitHub usually puts today at the bottom right.

        // Let's iterate backwards from today
        // We need to determine the (row, col) for today.

        val today = calendar.time
        val todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun, 6=Sat

        // Calculate starting position (bottom-right)
        // We render from right to left, or we calculate start date and render left to right.
        // Left to right is easier for coordinates.

        // Calculate start date: (weeksToShow - 1) weeks ago, starting from Sunday
        calendar.add(Calendar.WEEK_OF_YEAR, -(weeksToShow - 1))
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val rect = RectF()

        for (w in 0 until weeksToShow) {
            for (d in 0 until 7) {
                val dateStr = dateFormat.format(calendar.time)
                val entry = historyData[dateStr]
                val sessions = entry?.completed ?: 0
                val minutes = entry?.work_minutes ?: 0

                // Determine paint based on activity
                val paint = when {
                    sessions == 0 -> emptyPaint
                    minutes < 30 -> level1Paint
                    minutes < 60 -> level2Paint
                    minutes < 120 -> level3Paint
                    else -> level4Paint
                }

                val left = paddingLeft + w * (cellSize + cellSpacing)
                val top = paddingTop + d * (cellSize + cellSpacing)

                rect.set(left, top, left + cellSize, top + cellSize)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

                // Next day
                calendar.add(Calendar.DAY_OF_YEAR, 1)

                // Don't render future days
                if (calendar.time.after(Date())) {
                    return
                }
            }
        }
    }
}
