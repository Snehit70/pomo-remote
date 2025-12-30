package com.pomoremote.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class UtilPreferenceManager {
    private final SharedPreferences prefs;
    private final Context context;

    public UtilPreferenceManager(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String getLaptopIp() {
        return prefs.getString("laptop_ip", "192.168.1.100");
    }

    public int getLaptopPort() {
        String portStr = prefs.getString("laptop_port", "9876");
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return 9876;
        }
    }

    public boolean isVibrateEnabled() {
        return prefs.getBoolean("vibrate_enabled", true);
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean("sound_enabled", true);
    }
}
