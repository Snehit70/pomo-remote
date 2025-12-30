package com.pomoremote.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;

import com.pomoremote.network.WebSocketClient;
import com.pomoremote.timer.OfflineTimer;
import com.pomoremote.timer.SyncManager;
import com.pomoremote.timer.TimerState;
import com.pomoremote.util.UtilPreferenceManager;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class PomodoroService extends Service implements WebSocketClient.Listener {
    private static final String TAG = "PomodoroService";
    private final IBinder binder = new LocalBinder();
    private WebSocketClient webSocketClient;
    private NotificationHelper notificationHelper;
    private OfflineTimer offlineTimer;
    private SyncManager syncManager;
    private UtilPreferenceManager prefs;
    private boolean isConnected = false;
    private TimerState currentState;
    private OkHttpClient httpClient;
    private Handler mainHandler;
    private Ringtone currentRingtone;

    public class LocalBinder extends Binder {
        public PomodoroService getService() {
            return PomodoroService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new UtilPreferenceManager(this);
        notificationHelper = new NotificationHelper(this);
        webSocketClient = new WebSocketClient(this);
        offlineTimer = new OfflineTimer(this);
        syncManager = new SyncManager();
        httpClient = new OkHttpClient();
        mainHandler = new Handler(Looper.getMainLooper());
        
        currentState = new TimerState();

        startForeground(NotificationHelper.NOTIFICATION_ID, 
                notificationHelper.buildNotification(currentState, false));
        
        connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent.getAction());
        }
        return START_STICKY;
    }

    private void handleAction(String action) {
        if ("TOGGLE".equals(action)) {
            toggleTimer();
        } else if ("SKIP".equals(action)) {
            skipTimer();
        } else if ("RECONNECT".equals(action)) {
            reconnect();
        }
    }

    public void connect() {
        String ip = prefs.getLaptopIp();
        int port = prefs.getLaptopPort();
        webSocketClient.connect(ip, port);
    }
    
    public void reconnect() {
        webSocketClient.close();
        connect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webSocketClient.close();
    }

    @Override
    public void onConnected() {
        mainHandler.post(() -> {
            isConnected = true;
            Log.d(TAG, "Connected to WebSocket");
            
            TimerState localState = offlineTimer.getState();
            syncManager.syncOnReconnect(prefs.getLaptopIp(), prefs.getLaptopPort(), localState);
            
            updateNotification();
            broadcastStateUpdate();
        });
    }

    @Override
    public void onDisconnected() {
        mainHandler.post(() -> {
            isConnected = false;
            Log.d(TAG, "Disconnected from WebSocket");
            syncManager.setOffline();
            
            if (TimerState.STATUS_RUNNING.equals(currentState.status)) {
                offlineTimer.updateState(currentState);
            }
            
            updateNotification();
            broadcastStateUpdate();
        });
    }

    @Override
    public void onStateReceived(TimerState state) {
        mainHandler.post(() -> {
            this.currentState = state;
            offlineTimer.updateState(state);
            updateNotification();
            broadcastStateUpdate();
        });
    }

    public void onTimerUpdate(TimerState state) {
        this.currentState = state;
        updateNotification();
        broadcastStateUpdate();
    }

    public void onTimerComplete(TimerState state) {
        this.currentState = state;
        updateNotification();
        broadcastStateUpdate();
        vibrate();
        playSound();
    }

    private void broadcastStateUpdate() {
        Intent intent = new Intent("com.pomoremote.STATE_UPDATE");
        sendBroadcast(intent);
    }

    private void updateNotification() {
        notificationHelper.updateNotification(currentState, isConnected);
    }

    public void toggleTimer() {
        if (isConnected) {
            sendApiRequest("toggle");
        } else {
            offlineTimer.toggle();
        }
    }

    public void skipTimer() {
        if (isConnected) {
            sendApiRequest("skip");
        } else {
            offlineTimer.skip();
        }
    }
    
    public void resetTimer() {
        if (isConnected) {
            sendApiRequest("reset");
        } else {
            offlineTimer.reset();
        }
    }

    private void sendApiRequest(String endpoint) {
        new Thread(() -> {
            try {
                String ip = prefs.getLaptopIp();
                int port = prefs.getLaptopPort();
                String url = "http://" + ip + ":" + port + "/api/" + endpoint;
                
                RequestBody body = RequestBody.create("", MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                httpClient.newCall(request).execute().close();
            } catch (IOException e) {
                Log.e(TAG, "API request failed", e);
            }
        }).start();
    }

    private void vibrate() {
        if (!prefs.isVibrateEnabled()) return;

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(500);
        }
    }

    private void playSound() {
        if (!prefs.isSoundEnabled()) return;
        
        try {
            if (currentRingtone != null && currentRingtone.isPlaying()) {
                currentRingtone.stop();
            }

            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            currentRingtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            
            if (currentRingtone != null) {
                currentRingtone.play();
            } else {
                Log.w(TAG, "Could not get ringtone");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play sound", e);
        }
    }
    
    public TimerState getCurrentState() {
        return currentState;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
}