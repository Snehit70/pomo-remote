package com.pomoremote.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class UtilPreferenceManager(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val laptopIp: String
        get() = prefs.getString("laptop_ip", "192.168.1.100") ?: "192.168.1.100"

    val laptopPort: Int
        get() {
            val portStr = prefs.getString("laptop_port", "9876")
            return try {
                portStr?.toInt() ?: 9876
            } catch (e: NumberFormatException) {
                9876
            }
        }

    val isVibrateEnabled: Boolean
        get() = prefs.getBoolean("vibrate_enabled", true)

    val isSoundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)

    val pomodoroDuration: Int
        get() {
            val str = prefs.getString("pomodoro_duration", "25")
            return try { str?.toInt() ?: 25 } catch (e: NumberFormatException) { 25 }
        }

    val shortBreakDuration: Int
        get() {
            val str = prefs.getString("short_break_duration", "5")
            return try { str?.toInt() ?: 5 } catch (e: NumberFormatException) { 5 }
        }

    val longBreakDuration: Int
        get() {
            val str = prefs.getString("long_break_duration", "15")
            return try { str?.toInt() ?: 15 } catch (e: NumberFormatException) { 15 }
        }

    var dailyGoal: Int
        get() {
            val str = prefs.getString("daily_goal", "8")
            return try { str?.toInt() ?: 8 } catch (e: NumberFormatException) { 8 }
        }
        set(value) {
            prefs.edit().putString("daily_goal", value.toString()).apply()
        }
}
