package com.pomoremote.timer;

import android.util.Log;
import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private final Gson gson;
    private final OkHttpClient client;
    private long offlineSince = 0;

    public SyncManager() {
        this.gson = new Gson();
        this.client = new OkHttpClient();
    }

    public void setOffline() {
        if (offlineSince == 0) {
            offlineSince = System.currentTimeMillis() / 1000;
        }
    }

    public void syncOnReconnect(String ip, int port, TimerState localState) {
        if (offlineSince == 0) return;

        new Thread(() -> {
            try {
                String url = "http://" + ip + ":" + port + "/api/sync";
                SyncRequest req = new SyncRequest();
                req.state = localState;
                req.offline_since = offlineSince;

                String json = gson.toJson(req);
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        offlineSince = 0;
                        Log.d(TAG, "Sync successful");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
            }
        }).start();
    }

    private static class SyncRequest {
        TimerState state;
        long offline_since;
    }
}
