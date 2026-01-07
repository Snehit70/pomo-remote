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

class StatsFragment : Fragment() {

    private lateinit var tvTotalFocus: TextView
    private lateinit var tvTotalSessions: TextView
    private lateinit var tvBestStreak: TextView
    private lateinit var tvDailyAvg: TextView
    private lateinit var weekGrid: LinearLayout
    private lateinit var recyclerView: RecyclerView

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
        recyclerView = view.findViewById(R.id.statsRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.isNestedScrollingEnabled = false

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
        val surfaceColor = ContextCompat.getColor(ctx, R.color.md_theme_surfaceVariant)

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(calendar.time)
            val dayLabel = dayFormat.format(calendar.time)
            val entry = history[dateStr]
            val hasActivity = (entry?.completed ?: 0) > 0

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

            // Activity dot
            val dot = View(ctx).apply {
                val size = (24 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (hasActivity) primaryColor else surfaceColor)
                }
            }

            // Minutes label (if any)
            val minutesLabel = TextView(ctx).apply {
                text = if (hasActivity) "${entry?.work_minutes}m" else ""
                setTextColor(primaryColor)
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
