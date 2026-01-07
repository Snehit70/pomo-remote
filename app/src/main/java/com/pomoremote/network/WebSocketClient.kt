package com.pomoremote.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.pomoremote.timer.TimerState
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketClient(private val callback: Listener?) {

    interface Listener {
        fun onStateReceived(state: TimerState)
        fun onConnected()
        fun onDisconnected()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private var currentIp: String? = null
    private var currentPort: Int = 0
    @Volatile private var shouldReconnect: Boolean = true
    @Volatile var isConnected: Boolean = false
        private set

    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (shouldReconnect && !isConnected && currentIp != null) {
                Log.d(TAG, "Attempting reconnect...")
                doConnect()
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Connected")
            isConnected = true
            reconnectHandler.removeCallbacks(reconnectRunnable)
            callback?.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val msg = gson.fromJson(text, Message::class.java)
                if ("state" == msg.type && msg.data != null) {
                    callback?.onStateReceived(msg.data!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message", e)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closed: $reason")
            isConnected = false
            callback?.onDisconnected()
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Failure: ${t.message}")
            isConnected = false
            callback?.onDisconnected()
            scheduleReconnect()
        }
    }

    fun connect(ip: String, port: Int) {
        this.currentIp = ip
        this.currentPort = port
        this.shouldReconnect = true
        doConnect()
    }

    private fun doConnect() {
        webSocket?.cancel()
        val url = "ws://$currentIp:$currentPort/ws"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    private fun scheduleReconnect() {
        if (shouldReconnect) {
            reconnectHandler.removeCallbacks(reconnectRunnable)
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
        }
    }

    fun close() {
        shouldReconnect = false
        reconnectHandler.removeCallbacks(reconnectRunnable)
        webSocket?.close(1000, "App closed")
    }

    fun send(message: String) {
        if (webSocket != null && isConnected) {
            webSocket?.send(message)
        }
    }

    private class Message {
        var type: String? = null
        var data: TimerState? = null
    }

    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY_MS = 5000L
    }
}
