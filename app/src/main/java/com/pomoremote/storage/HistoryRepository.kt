package com.pomoremote.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pomoremote.models.Session
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Collections

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

    @Synchronized
    private fun saveSessionsInternal(sessions: List<Session>) {
        val file = File(context.filesDir, filename)
        FileWriter(file).use { writer ->
            gson.toJson(sessions, writer)
        }
    }
}
