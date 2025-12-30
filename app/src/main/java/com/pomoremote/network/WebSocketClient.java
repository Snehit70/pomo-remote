package com.pomoremote.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.pomoremote.timer.TimerState;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class WebSocketClient {
    private static final String TAG = "WebSocketClient";
    private static final long RECONNECT_DELAY_MS = 5000;
    
    private final OkHttpClient client;
    private WebSocket webSocket;
    private final Gson gson;
    private final WebSocketListener listener;
    private Listener callback;
    
    private String currentIp;
    private int currentPort;
    private volatile boolean shouldReconnect = true;
    private volatile boolean isConnected = false;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldReconnect && !isConnected && currentIp != null) {
                Log.d(TAG, "Attempting reconnect...");
                doConnect();
            }
        }
    };

    public interface Listener {
        void onStateReceived(TimerState state);
        void onConnected();
        void onDisconnected();
    }

    public WebSocketClient(Listener callback) {
        this.callback = callback;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        
        this.listener = new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                Log.d(TAG, "Connected");
                isConnected = true;
                reconnectHandler.removeCallbacks(reconnectRunnable);
                if (WebSocketClient.this.callback != null) {
                    WebSocketClient.this.callback.onConnected();
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                try {
                    Message msg = gson.fromJson(text, Message.class);
                    if ("state".equals(msg.type) && msg.data != null) {
                        if (WebSocketClient.this.callback != null) {
                            WebSocketClient.this.callback.onStateReceived(msg.data);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing message", e);
                }
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                Log.d(TAG, "Closed: " + reason);
                isConnected = false;
                if (WebSocketClient.this.callback != null) {
                    WebSocketClient.this.callback.onDisconnected();
                }
                scheduleReconnect();
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "Failure: " + t.getMessage());
                isConnected = false;
                if (WebSocketClient.this.callback != null) {
                    WebSocketClient.this.callback.onDisconnected();
                }
                scheduleReconnect();
            }
        };
    }

    public void connect(String ip, int port) {
        this.currentIp = ip;
        this.currentPort = port;
        this.shouldReconnect = true;
        doConnect();
    }
    
    private void doConnect() {
        if (webSocket != null) {
            webSocket.cancel();
        }
        String url = "ws://" + currentIp + ":" + currentPort + "/ws";
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, listener);
    }
    
    private void scheduleReconnect() {
        if (shouldReconnect) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        }
    }

    public void close() {
        shouldReconnect = false;
        reconnectHandler.removeCallbacks(reconnectRunnable);
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
        }
    }
    
    public boolean isConnected() {
        return isConnected;
    }

    private static class Message {
        String type;
        TimerState data;
    }
}
