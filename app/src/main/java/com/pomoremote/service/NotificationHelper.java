package com.pomoremote.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.pomoremote.MainActivity;
import com.pomoremote.R;
import com.pomoremote.timer.TimerState;

import java.util.Locale;

public class NotificationHelper {
    public static final String CHANNEL_ID = "pomodoro_channel";
    public static final int NOTIFICATION_ID = 1;

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pomodoro Timer",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active timer status");
            notificationManager.createNotificationChannel(channel);
        }
    }

    public Notification buildNotification(TimerState state, boolean isConnected) {
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingOpenApp = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Pomo Remote";
        if (!isConnected) {
            title += " (Offline)";
        } else if (state.status.equals(TimerState.STATUS_RUNNING)) {
            title += " (Running)";
        } else {
             title += " (Paused)";
        }

        int minutes = (int) state.remaining / 60;
        int seconds = (int) state.remaining % 60;
        String timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds);
        
        String phaseName = state.phase;
        if (TimerState.PHASE_WORK.equals(state.phase)) phaseName = "Focus";
        else if (TimerState.PHASE_SHORT.equals(state.phase)) phaseName = "Short Break";
        else if (TimerState.PHASE_LONG.equals(state.phase)) phaseName = "Long Break";

        String contentText = String.format("%s - %s", timeStr, phaseName);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(contentText)
                .setOngoing(true)
                .setContentIntent(pendingOpenApp)
                .setOnlyAlertOnce(true);

        Intent toggleIntent = new Intent(context, NotificationActionReceiver.class);
        toggleIntent.setAction("TOGGLE");
        PendingIntent pendingToggle = PendingIntent.getBroadcast(
                context, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent skipIntent = new Intent(context, NotificationActionReceiver.class);
        skipIntent.setAction("SKIP");
        PendingIntent pendingSkip = PendingIntent.getBroadcast(
                context, 2, skipIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String toggleLabel = TimerState.STATUS_RUNNING.equals(state.status) ? "Pause" : "Start";
        builder.addAction(android.R.drawable.ic_media_play, toggleLabel, pendingToggle);
        builder.addAction(android.R.drawable.ic_media_next, "Skip", pendingSkip);

        return builder.build();
    }

    public void updateNotification(TimerState state, boolean isConnected) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, isConnected));
    }
}
