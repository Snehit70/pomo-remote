package com.pomoremote.timer

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SyncManager {
    interface SyncCallback {
        fun onSyncSuccess(mergedState: TimerState)
        fun onSyncFailed(e: Exception)
    }

    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(SYNC_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .readTimeout(SYNC_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .writeTimeout(SYNC_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .build()

    private var offlineSince: Long = 0

    fun setOffline() {
        if (offlineSince == 0L) {
            offlineSince = System.currentTimeMillis() / 1000
        }
    }

    fun clearOffline() {
        offlineSince = 0
    }

    fun syncNow(ip: String, port: Int, localState: TimerState, callback: SyncCallback?) {
        Thread {
            try {
                val url = "http://$ip:$port/api/sync"
                val req = SyncRequest()
                req.state = localState
                req.offline_since = offlineSince

                val json = gson.toJson(req)
                val body = json.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val responseBody = response.body!!.string()
                        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject

                        if (jsonResponse.has("state")) {
                            val mergedState = gson.fromJson(jsonResponse.get("state"), TimerState::class.java)
                            offlineSince = 0
                            Log.d(TAG, "Sync successful")
                            callback?.onSyncSuccess(mergedState)
                        } else {
                            throw Exception("Response missing state field")
                        }
                    } else {
                        throw Exception("Sync request failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                callback?.onSyncFailed(e)
            }
        }.start()
    }

    private class SyncRequest {
        var state: TimerState? = null
        var offline_since: Long = 0
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_TIMEOUT_SECONDS = 10
    }
}
