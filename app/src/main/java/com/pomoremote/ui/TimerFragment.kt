package com.pomoremote.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomoremote.MainActivity
import com.pomoremote.R
import com.pomoremote.timer.TimerState
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.material.transition.MaterialFadeThrough

class TimerFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    private lateinit var tvTimer: TextView
    private lateinit var tvPhase: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: FloatingActionButton
    private lateinit var btnSkip: Button
    private lateinit var btnReset: Button
    private lateinit var progressIndicator: CircularProgressIndicator

    // Stats card views
    private lateinit var tvTodayFocus: TextView
    private lateinit var tvSessions: TextView
    private lateinit var tvStreak: TextView

    private val client = OkHttpClient()
    private val gson = Gson()

    // We access the service through MainActivity which holds the connection
    private val mainActivity: MainActivity?
        get() = activity as? MainActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTimer = view.findViewById(R.id.tvTimer)
        tvPhase = view.findViewById(R.id.tvPhase)
        tvStatus = view.findViewById(R.id.tvConnectionStatus)
        btnToggle = view.findViewById(R.id.btnToggle)
        btnSkip = view.findViewById(R.id.btnSkip)
        btnReset = view.findViewById(R.id.btnReset)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        // Stats card
        tvTodayFocus = view.findViewById(R.id.tvTodayFocus)
        tvSessions = view.findViewById(R.id.tvSessions)
        tvStreak = view.findViewById(R.id.tvStreak)

        btnToggle.setOnClickListener { mainActivity?.toggleTimer() }
        btnSkip.setOnClickListener { mainActivity?.skipTimer() }
        btnReset.setOnClickListener { mainActivity?.resetTimer() }

        // Initial UI update if service is already bound
        mainActivity?.service?.currentState?.let { updateUI(it) }

        // Fetch stats
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
                // Silently fail - stats will show defaults
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val type = object : TypeToken<Map<String, DayEntry>>() {}.type
                    val historyMap: Map<String, DayEntry> = gson.fromJson(json, type) ?: emptyMap()

                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val todayEntry = historyMap[today]

                    // Calculate streak (consecutive days with sessions)
                    val streak = calculateStreak(historyMap)

                    Handler(Looper.getMainLooper()).post {
                        if (!isAdded) return@post

                        // Today's focus time
                        val minutes = todayEntry?.work_minutes ?: 0
                        val hours = minutes / 60
                        val mins = minutes % 60
                        tvTodayFocus.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                        // Today's sessions
                        tvSessions.text = "${todayEntry?.completed ?: 0}"

                        // Streak
                        tvStreak.text = "$streak\uD83D\uDD25"
                    }
                }
            }
        })
    }

    private fun calculateStreak(history: Map<String, DayEntry>): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sortedDates = history.keys
            .filter { (history[it]?.completed ?: 0) > 0 }
            .sortedDescending()

        if (sortedDates.isEmpty()) return 0

        var streak = 0
        val calendar = java.util.Calendar.getInstance()
        calendar.time = Date()

        for (i in sortedDates.indices) {
            val expectedDate = dateFormat.format(calendar.time)
            if (sortedDates.contains(expectedDate)) {
                streak++
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else if (i == 0) {
                // Today might not have sessions yet, check yesterday
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val yesterday = dateFormat.format(calendar.time)
                if (sortedDates.contains(yesterday)) {
                    streak++
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            } else {
                break
            }
        }
        return streak
    }

    // Called by MainActivity when state updates
    fun updateUI(state: TimerState) {
        val context = context ?: return
        if (!isAdded) return

        val minutes = state.remaining.toInt() / 60
        val seconds = state.remaining.toInt() % 60
        tvTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)

        var phaseName = state.phase
        var colorRes = R.color.md_theme_primary

        if (TimerState.PHASE_WORK == state.phase) {
            phaseName = "Focus"
            colorRes = R.color.md_theme_primary
        } else if (TimerState.PHASE_SHORT == state.phase) {
            phaseName = "Short Break"
            colorRes = R.color.md_theme_secondary
        } else if (TimerState.PHASE_LONG == state.phase) {
            phaseName = "Long Break"
            colorRes = R.color.md_theme_secondary
        }

        tvPhase.text = phaseName
        val color = ContextCompat.getColor(context, colorRes)
        tvPhase.setTextColor(color)
        progressIndicator.setIndicatorColor(color)
        btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

        // Progress Calculation
        var total = state.duration
        if (total <= 0) {
             // Fallback default durations if missing
             total = when (state.phase) {
                 TimerState.PHASE_WORK -> 1500.0
                 TimerState.PHASE_SHORT -> 300.0
                 TimerState.PHASE_LONG -> 900.0
                 else -> 1500.0
             }
        }
        val progress = ((state.remaining / total) * 100).toInt()
        progressIndicator.setProgressCompat(progress, true)

        // Button Icon
        if (TimerState.STATUS_RUNNING == state.status) {
            btnToggle.setImageResource(R.drawable.ic_pause)
        } else {
            btnToggle.setImageResource(R.drawable.ic_play)
        }

        // Connection Status
        val isConnected = mainActivity?.service?.isConnected == true
        if (isConnected) {
            tvStatus.text = "Connected"
            tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_connected))
        } else {
            tvStatus.text = "Offline"
            tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_offline))
        }
    }
}