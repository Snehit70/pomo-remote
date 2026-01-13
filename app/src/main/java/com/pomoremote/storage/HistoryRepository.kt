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
    private val diskExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    @Volatile
    private var cache: List<Session>? = null
    private val cacheLock = Any()

    init {
        // Preload cache asynchronously
        diskExecutor.execute {
            loadFromDisk()
        }
    }

    fun saveSession(session: Session) {
        synchronized(cacheLock) {
            val current = getCacheInternal().toMutableList()
            current.add(session)
            cache = current
        }

        diskExecutor.execute {
            saveSessionsToDisk()
        }
    }

    fun loadSessions(): List<Session> {
        return getCacheInternal()
    }

    private fun getCacheInternal(): List<Session> {
        synchronized(cacheLock) {
            if (cache == null) {
                // Fallback to synchronous load if accessed before async init (should be rare)
                cache = loadFromDisk()
            }
            return cache!!
        }
    }

    fun clearSessions() {
        synchronized(cacheLock) {
            cache = emptyList()
        }
        diskExecutor.execute {
            val file = File(context.filesDir, filename)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * Counts completed work sessions for today from local offline history.
     * This is the source of truth for offline completed count.
     */
    fun countTodayCompletedSessions(dayStartHour: Int): Int {
        return getTodayCompletedSessions(dayStartHour).size
    }

    fun getTodayCompletedSessions(dayStartHour: Int): List<Session> {
        val sessions = getCacheInternal()
        if (sessions.isEmpty()) return emptyList()

        val todayStart = getTodayStartTimestamp(dayStartHour)
        return sessions.filter { session ->
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

    private fun loadFromDisk(): List<Session> {
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

    private fun saveSessionsToDisk() {
        val snapshot = synchronized(cacheLock) { cache } ?: return

        try {
            val file = File(context.filesDir, filename)
            FileWriter(file).use { writer ->
                gson.toJson(snapshot, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shutdown() {
        diskExecutor.shutdown()
    }
}
