package com.pomoremote.network;

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
    private final OkHttpClient client;
    private WebSocket webSocket;
    private final Gson gson;
    private final WebSocketListener listener;
    private Listener callback;

    public interface Listener {
        void onStateReceived(TimerState state);
        void onConnected();
        void onDisconnected();
    }

    public WebSocketClient(Listener callback) {
        this.callback = callback;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.gson = new Gson();
        
        this.listener = new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                Log.d(TAG, "Connected");
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
                if (WebSocketClient.this.callback != null) {
                    WebSocketClient.this.callback.onDisconnected();
                }
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "Failure", t);
                if (WebSocketClient.this.callback != null) {
                    WebSocketClient.this.callback.onDisconnected();
                }
            }
        };
    }

    public void connect(String ip, int port) {
        String url = "ws://" + ip + ":" + port + "/ws";
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, listener);
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
        }
    }

    private static class Message {
        String type;
        TimerState data;
    }
}
