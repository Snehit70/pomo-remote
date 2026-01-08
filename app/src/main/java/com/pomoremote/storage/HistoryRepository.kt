package com.pomoremote.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomoremote.models.Session
import com.pomoremote.timer.TimerState
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryRepository(private val context: Context) {
    private val gson = Gson()
    private val filename = "offline_history.json"

    @Synchronized
    fun saveSession(session: Session) {
        val sessions = loadSessions().toMutableList()
        sessions.add(session)
        saveSessionsInternal(sessions)
    }

    @Synchronized
    fun loadSessions(): List<Session> {
        val file = File(context.filesDir, filename)
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            FileReader(file).use { reader ->
                val type = object : TypeToken<List<Session>>() {}.type
                gson.fromJson(reader, type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clearSessions() {
        val file = File(context.filesDir, filename)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Counts completed work sessions for today from local offline history.
     * This is the source of truth for offline completed count.
     */
    @Synchronized
    fun countTodayCompletedSessions(dayStartHour: Int): Int {
        val sessions = loadSessions()
        if (sessions.isEmpty()) return 0

        val todayStart = getTodayStartTimestamp(dayStartHour)
        return sessions.count { session ->
            session.type == TimerState.PHASE_WORK &&
                session.completed &&
                session.start >= todayStart
        }
    }

    fun getTodayStartTimestamp(dayStartHour: Int): Long {
        val calendar = Calendar.getInstance()
        if (calendar.get(Calendar.HOUR_OF_DAY) < dayStartHour) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, dayStartHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000
    }

    fun getEffectiveDateString(dayStartHour: Int): String {
        val calendar = Calendar.getInstance()
        if (calendar.get(Calendar.HOUR_OF_DAY) < dayStartHour) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(calendar.time)
    }

    @Synchronized
    private fun saveSessionsInternal(sessions: List<Session>) {
        val file = File(context.filesDir, filename)
        FileWriter(file).use { writer ->
            gson.toJson(sessions, writer)
        }
    }
}
