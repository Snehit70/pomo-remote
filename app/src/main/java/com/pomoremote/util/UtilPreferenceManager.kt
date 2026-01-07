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
}
