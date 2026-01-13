// app/src/main/java/com/pomoremote/db/HistoryCacheRepository.kt
package com.pomoremote.db

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Repository that provides offline-first access to history data.
 *
 * Strategy:
 * 1. Return cached data immediately from Room
 * 2. Fetch from server in background
 * 3. Merge server data into Room (server is source of truth)
 * 4. UI observes Room via Flow for automatic updates
 */
class HistoryCacheRepository(context: Context) {

    companion object {
        private const val TAG = "HistoryCacheRepo"
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val dao: HistoryDao = AppDatabase.getInstance(context).historyDao()
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Observable flow of all day stats. UI should collect this.
     */
    fun observeDayStats(): Flow<List<DayStatsEntity>> = dao.getAllDayStats()

    /**
     * Get cached day stats immediately (non-blocking snapshot).
     */
    suspend fun getCachedDayStats(): List<DayStatsEntity> = dao.getAllDayStatsSnapshot()

    /**
     * Get sessions for a specific date.
     */
    suspend fun getSessionsForDate(date: String): List<SessionEntity> =
        dao.getSessionsForDate(date)

    /**
     * Check if we have any cached data.
     */
    suspend fun hasCachedData(): Boolean = dao.getAllDayStatsSnapshot().isNotEmpty()

    /**
     * Get the last sync timestamp.
     */
    suspend fun getLastSyncTime(): Long? = dao.getLastSyncTime()

    /**
     * Check if sync is needed based on time elapsed.
     */
    suspend fun needsSync(): Boolean {
        val lastSync = getLastSyncTime() ?: return true
        return System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS
    }

    // ─── Sync Logic ──────────────────────────────────────────────────────────────

    /**
     * Sync history from server. Returns true if sync succeeded.
     */
    suspend fun syncFromServer(ip: String, port: Int): SyncResult = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/api/history"
        Log.d(TAG, "Syncing from $url")

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Server returned ${response.code}")
                return@withContext SyncResult.Error("Server error: ${response.code}")
            }

            val json = response.body?.string()
            if (json.isNullOrBlank()) {
                Log.w(TAG, "Empty response from server")
                return@withContext SyncResult.Error("Empty response")
            }

            val serverData = parseServerResponse(json)
            if (serverData.isEmpty()) {
                Log.d(TAG, "No history data from server")
                return@withContext SyncResult.Success(0)
            }

            // Convert to entities
            val now = System.currentTimeMillis()
            val dayStatsEntities = mutableListOf<DayStatsEntity>()
            val sessionEntities = mutableListOf<SessionEntity>()

            for ((date, dayData) in serverData) {
                // Recalculate stats from sessions to ensure accuracy (server aggregation might be off)
                val calculatedCompleted = dayData.sessions.count { it.type == "work" && it.completed }
                val calculatedWorkMinutes = dayData.sessions
                    .filter { it.type == "work" && it.completed }
                    .sumOf { it.duration } / 60
                val calculatedBreakMinutes = dayData.sessions
                    .filter { (it.type == "short" || it.type == "long") && it.completed }
                    .sumOf { it.duration } / 60

                dayStatsEntities.add(
                    DayStatsEntity(
                        date = date,
                        completed = calculatedCompleted,
                        workMinutes = calculatedWorkMinutes,
                        breakMinutes = calculatedBreakMinutes,
                        lastUpdated = now
                    )
                )

                dayData.sessions.forEach { session ->
                    sessionEntities.add(
                        SessionEntity(
                            date = date,
                            type = session.type,
                            start = session.start,
                            duration = session.duration,
                            completed = session.completed
                        )
                    )
                }
            }

            // Replace all data (server is source of truth)
            dao.replaceAllHistory(dayStatsEntities, sessionEntities)
            Log.d(TAG, "Synced ${dayStatsEntities.size} days, ${sessionEntities.size} sessions")

            SyncResult.Success(dayStatsEntities.size)

        } catch (e: IOException) {
            Log.w(TAG, "Network error during sync", e)
            SyncResult.NetworkError(e.message ?: "Network error")
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parse error", e)
            SyncResult.Error("Invalid data format")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun parseServerResponse(json: String): Map<String, ServerDayEntry> {
        val type = object : TypeToken<Map<String, ServerDayEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    /**
     * Clear all cached history.
     */
    suspend fun clearCache() {
        dao.replaceAllHistory(emptyList(), emptyList())
    }

    // ─── Data Classes for Server Response ────────────────────────────────────────

    data class ServerDayEntry(
        val completed: Int = 0,
        val work_minutes: Int = 0,
        val break_minutes: Int = 0,
        val sessions: List<ServerSession> = emptyList()
    )

    data class ServerSession(
        val type: String = "",
        val start: Long = 0,
        val duration: Int = 0,
        val completed: Boolean = false
    )

    // ─── Sync Result ─────────────────────────────────────────────────────────────

    sealed class SyncResult {
        data class Success(val daysUpdated: Int) : SyncResult()
        data class NetworkError(val message: String) : SyncResult()
        data class Error(val message: String) : SyncResult()

        val isSuccess: Boolean get() = this is Success
    }
}
