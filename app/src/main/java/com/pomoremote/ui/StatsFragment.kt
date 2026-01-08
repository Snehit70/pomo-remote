package com.pomoremote.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomoremote.MainActivity
import com.pomoremote.R
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import android.widget.FrameLayout

class StatsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    private lateinit var tvTotalFocus: TextView
    private lateinit var tvTotalSessions: TextView
    private lateinit var tvBestStreak: TextView
    private lateinit var tvDailyAvg: TextView
    private lateinit var weekGrid: LinearLayout
    private lateinit var barGraph: LinearLayout
    private lateinit var barGraphCard: MaterialCardView
    private lateinit var lineGraphCard: MaterialCardView
    private lateinit var lineGraphContainer: FrameLayout
    private lateinit var graphToggleGroup: MaterialButtonToggleGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnToggleBreakdown: MaterialButton
    private var isBreakdownVisible = false
    private var lineGraphView: LineGraphView? = null
    private var weekData: List<Pair<String, Int>> = emptyList()

    private val client = OkHttpClient()
    private val gson = Gson()

    private val mainActivity: MainActivity?
        get() = activity as? MainActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTotalFocus = view.findViewById(R.id.tvTotalFocus)
        tvTotalSessions = view.findViewById(R.id.tvTotalSessions)
        tvBestStreak = view.findViewById(R.id.tvBestStreak)
        tvDailyAvg = view.findViewById(R.id.tvDailyAvg)
        weekGrid = view.findViewById(R.id.weekGrid)
        barGraph = view.findViewById(R.id.barGraph)
        barGraphCard = view.findViewById(R.id.barGraphCard)
        lineGraphCard = view.findViewById(R.id.lineGraphCard)
        lineGraphContainer = view.findViewById(R.id.lineGraphContainer)
        graphToggleGroup = view.findViewById(R.id.graphToggleGroup)
        recyclerView = view.findViewById(R.id.statsRecyclerView)
        btnToggleBreakdown = view.findViewById(R.id.btnToggleBreakdown)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.visibility = View.GONE
        btnToggleBreakdown.text = "Show"

        // Set default selection to bar graph
        graphToggleGroup.check(R.id.btnBarGraph)

        graphToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnBarGraph -> {
                        barGraphCard.visibility = View.VISIBLE
                        lineGraphCard.visibility = View.GONE
                    }
                    R.id.btnLineGraph -> {
                        barGraphCard.visibility = View.GONE
                        lineGraphCard.visibility = View.VISIBLE
                        updateLineGraph()
                    }
                }
            }
        }

        btnToggleBreakdown.setOnClickListener {
            isBreakdownVisible = !isBreakdownVisible
            recyclerView.visibility = if (isBreakdownVisible) View.VISIBLE else View.GONE
            btnToggleBreakdown.text = if (isBreakdownVisible) "Hide" else "Show"
        }

        fetchStats()
    }

    private fun fetchStats() {
        val activity = mainActivity ?: return
        val ip = activity.prefs.laptopIp
        val port = activity.prefs.laptopPort
        val url = "http://$ip:$port/api/history"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Show error state
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val type = object : TypeToken<Map<String, DayEntry>>() {}.type
                    val historyMap: Map<String, DayEntry> = gson.fromJson(json, type) ?: emptyMap()

                    Handler(Looper.getMainLooper()).post {
                        if (!isAdded) return@post
                        populateStats(historyMap)
                    }
                }
            }
        })
    }

    private fun populateStats(history: Map<String, DayEntry>) {
        val ctx = context ?: return

        // Total calculations
        var totalMinutes = 0
        var totalSessions = 0
        history.values.forEach {
            totalMinutes += it.work_minutes
            totalSessions += it.completed
        }

        // Format total focus
        val totalHours = totalMinutes / 60
        val totalMins = totalMinutes % 60
        tvTotalFocus.text = if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins}m"
        tvTotalSessions.text = "$totalSessions"

        // Daily average
        val daysWithActivity = history.values.count { it.completed > 0 }
        val avgMinutes = if (daysWithActivity > 0) totalMinutes / daysWithActivity else 0
        val avgHours = avgMinutes / 60
        val avgMins = avgMinutes % 60
        tvDailyAvg.text = if (avgHours > 0) "${avgHours}h ${avgMins}m" else "${avgMins}m"

        // Best streak
        val bestStreak = calculateBestStreak(history)
        tvBestStreak.text = "$bestStreak days"

        // Week grid
        populateWeekGrid(history)

        // Bar graph
        populateBarGraph(history)

        // History list (last 14 days)
        val historyList = history.entries
            .map { HistoryItem(it.key, it.value) }
            .sortedByDescending { it.date }
            .take(14)
        recyclerView.adapter = StatsAdapter(historyList)
    }

    private fun calculateBestStreak(history: Map<String, DayEntry>): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sortedDates = history.keys
            .filter { (history[it]?.completed ?: 0) > 0 }
            .map { dateFormat.parse(it) }
            .filterNotNull()
            .sortedDescending()

        if (sortedDates.isEmpty()) return 0

        var bestStreak = 1
        var currentStreak = 1

        for (i in 0 until sortedDates.size - 1) {
            val diff = (sortedDates[i].time - sortedDates[i + 1].time) / (1000 * 60 * 60 * 24)
            if (diff == 1L) {
                currentStreak++
                bestStreak = maxOf(bestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        return bestStreak
    }

    private fun populateWeekGrid(history: Map<String, DayEntry>) {
        val ctx = context ?: return
        weekGrid.removeAllViews()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val calendar = Calendar.getInstance()

        // Start from 6 days ago
        calendar.add(Calendar.DAY_OF_YEAR, -6)

        val primaryColor = ContextCompat.getColor(ctx, R.color.md_theme_primary)
        val goldColor = ContextCompat.getColor(ctx, R.color.gold)
        val surfaceColor = ContextCompat.getColor(ctx, R.color.md_theme_surfaceVariant)

        // Get daily goal from preferences
        val dailyGoal = mainActivity?.prefs?.dailyGoal ?: 8

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(calendar.time)
            val dayLabel = dayFormat.format(calendar.time)
            val entry = history[dateStr]
            val sessionsCompleted = entry?.completed ?: 0
            val hasActivity = sessionsCompleted > 0
            val goalMet = sessionsCompleted >= dailyGoal

            val dayView = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Day label
            val label = TextView(ctx).apply {
                text = dayLabel.uppercase().take(1)
                setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onSurface))
                textSize = 10f
                gravity = Gravity.CENTER
                alpha = 0.6f
            }

            // Activity dot - gold if goal met, primary if active, surface if inactive
            val dotColor = when {
                goalMet -> goldColor
                hasActivity -> primaryColor
                else -> surfaceColor
            }
            val dot = View(ctx).apply {
                val size = (24 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColor)
                    if (goalMet) {
                        setStroke((2 * resources.displayMetrics.density).toInt(), goldColor)
                    }
                }
            }

            // Minutes label (if any)
            val minutesLabel = TextView(ctx).apply {
                text = if (hasActivity) "${entry?.work_minutes}m" else ""
                setTextColor(if (goalMet) goldColor else primaryColor)
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (4 * resources.displayMetrics.density).toInt()
                }
            }

            dayView.addView(label)
            dayView.addView(dot)
            dayView.addView(minutesLabel)
            weekGrid.addView(dayView)

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun populateBarGraph(history: Map<String, DayEntry>) {
        val ctx = context ?: return
        barGraph.removeAllViews()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        val calendar = Calendar.getInstance()

        // Start from 6 days ago
        calendar.add(Calendar.DAY_OF_YEAR, -6)

        val primaryColor = ContextCompat.getColor(ctx, R.color.md_theme_primary)
        val goldColor = ContextCompat.getColor(ctx, R.color.gold)

        // Get daily goal and max height
        val dailyGoal = mainActivity?.prefs?.dailyGoal ?: 8
        val maxBarHeight = 80 * resources.displayMetrics.density

        // Collect week data and store for line graph
        weekData = mutableListOf<Pair<String, Int>>().apply {
            for (i in 0 until 7) {
                val dateStr = dateFormat.format(calendar.time)
                val dayLabel = dayFormat.format(calendar.time)
                val entry = history[dateStr]
                val minutes = entry?.work_minutes ?: 0
                add(Pair(dayLabel.take(1).uppercase(), minutes))
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Find max for scaling
        val maxMinutes = weekData.maxOfOrNull { it.second } ?: 60
        val scaleFactor = if (maxMinutes > 0) maxBarHeight / maxMinutes else 1f

        for ((dayLabel, minutes) in weekData) {
            val barContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }

            // Hours label on top
            val hoursLabel = TextView(ctx).apply {
                val hours = minutes / 60f
                text = if (minutes >= 60) String.format(Locale.US, "%.1fh", hours) else "${minutes}m"
                setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onSurface))
                textSize = 9f
                gravity = Gravity.CENTER
                alpha = 0.7f
            }

            // The bar itself
            val barHeight = (minutes * scaleFactor).toInt().coerceAtLeast(4)
            val goalMinutes = dailyGoal * 25 // Assume 25 min sessions
            val barColor = if (minutes >= goalMinutes) goldColor else primaryColor

            val bar = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (16 * resources.displayMetrics.density).toInt(),
                    barHeight
                ).apply {
                    topMargin = (4 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = 8 * resources.displayMetrics.density
                    setColor(barColor)
                }
            }

            // Day label below
            val dayLabelView = TextView(ctx).apply {
                text = dayLabel
                setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_onSurface))
                textSize = 10f
                gravity = Gravity.CENTER
                alpha = 0.6f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (4 * resources.displayMetrics.density).toInt()
                }
            }

            barContainer.addView(hoursLabel)
            barContainer.addView(bar)
            barContainer.addView(dayLabelView)
            barGraph.addView(barContainer)
        }
    }

    private fun updateLineGraph() {
        val ctx = context ?: return
        if (weekData.isEmpty()) return

        if (lineGraphView == null) {
            lineGraphView = LineGraphView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            lineGraphContainer.addView(lineGraphView)
        }

        val dailyGoal = mainActivity?.prefs?.dailyGoal ?: 8
        lineGraphView?.setData(weekData, dailyGoal)
    }
}

// Adapter for stats history
class StatsAdapter(private val list: List<HistoryItem>) : RecyclerView.Adapter<StatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvFocus: TextView = view.findViewById(R.id.tvFocusTime)
        val tvBreak: TextView = view.findViewById(R.id.tvBreakTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        // Format date nicely
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMM d", Locale.US)
            val date = inputFormat.parse(item.date)
            holder.tvDate.text = date?.let { outputFormat.format(it) } ?: item.date
        } catch (e: Exception) {
            holder.tvDate.text = item.date
        }

        val hours = item.entry.work_minutes / 60
        val mins = item.entry.work_minutes % 60
        holder.tvFocus.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        holder.tvBreak.text = "${item.entry.completed} sessions"
    }

    override fun getItemCount() = list.size
}
