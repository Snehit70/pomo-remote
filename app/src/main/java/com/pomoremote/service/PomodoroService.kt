package com.pomoremote.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.util.Log
import com.pomoremote.network.WebSocketClient
import com.pomoremote.storage.HistoryRepository
import com.pomoremote.timer.OfflineTimer
import com.pomoremote.timer.SyncManager
import com.pomoremote.timer.TimerState
import com.pomoremote.util.UtilPreferenceManager
import com.pomoremote.widget.TimerWidgetProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class PomodoroService : Service(), WebSocketClient.Listener {

    private val binder = LocalBinder()
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var offlineTimer: OfflineTimer
    private lateinit var syncManager: SyncManager
    private lateinit var historyRepository: HistoryRepository
    private lateinit var prefs: UtilPreferenceManager
    var isConnected: Boolean = false
        private set
    var currentState: TimerState = TimerState()
        private set
    private lateinit var httpClient: OkHttpClient
    private lateinit var mainHandler: Handler
    private var currentRingtone: Ringtone? = null

    @Volatile
    private var syncCompleted = false
    @Volatile
    private var readySent = false
    private var syncTimeoutRunnable: Runnable? = null

    inner class LocalBinder : Binder() {
        val service: PomodoroService
            get() = this@PomodoroService
    }

    override fun onCreate() {
        super.onCreate()
        prefs = UtilPreferenceManager(this)
        currentState.goal = prefs.dailyGoal
        notificationHelper = NotificationHelper(this)
        webSocketClient = WebSocketClient(this)
        historyRepository = HistoryRepository(this)
        offlineTimer = OfflineTimer(this, prefs, historyRepository)
        syncManager = SyncManager()
        httpClient = OkHttpClient()
        mainHandler = Handler(Looper.getMainLooper())

        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notificationHelper.buildNotification(currentState, false)
        )

        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action != null) {
            handleAction(intent.action!!)
        }
        return START_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            "TOGGLE" -> toggleTimer()
            "SKIP" -> skipTimer()
            "RECONNECT" -> reconnect()
        }
    }

    fun connect() {
        val ip = prefs.laptopIp
        val port = prefs.laptopPort
        webSocketClient.connect(ip, port)
    }

    fun reconnect() {
        webSocketClient.close()
        connect()
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        syncTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        webSocketClient.close()
    }

    override fun onConnected() {
        mainHandler.post {
            isConnected = true
            syncCompleted = false
            readySent = false
            Log.d(TAG, "Connected to WebSocket - starting sync")

            syncTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            syncTimeoutRunnable = Runnable {
                if (!syncCompleted) {
                    Log.w(TAG, "Sync timeout - accepting WS messages anyway")
                    syncCompleted = true
                    readySent = true
                    webSocketClient.send("{\"type\":\"ready\"}")
                }
            }
            mainHandler.postDelayed(syncTimeoutRunnable!!, SYNC_TIMEOUT_MS)

            val localState = offlineTimer.state

            // Sync offline history first
            val offlineSessions = historyRepository.loadSessions()
            if (offlineSessions.isNotEmpty()) {
                Log.d(TAG, "Found ${offlineSessions.size} offline sessions to sync")
                syncManager.syncHistory(prefs.laptopIp, prefs.laptopPort, offlineSessions,
                    object : SyncManager.SyncCallback {
                        override fun onSyncSuccess(mergedState: TimerState) {
                            Log.d(TAG, "History sync successful - clearing local history")
                            historyRepository.clearSessions()
                        }

                        override fun onSyncFailed(e: Exception) {
                            Log.e(TAG, "History sync failed - keeping local history", e)
                        }
                    })
            }

            syncManager.syncNow(prefs.laptopIp, prefs.laptopPort, localState,
                object : SyncManager.SyncCallback {
                    override fun onSyncSuccess(mergedState: TimerState) {
                        mainHandler.post {
                            syncTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

                            Log.d(TAG, "Sync successful - applying merged state")
                            sanitizeState(mergedState)
                            currentState = mergedState
                            prefs.dailyGoal = mergedState.goal
                            offlineTimer.updateState(mergedState)
                            syncCompleted = true

                            // Fetch latest config from server
                            syncManager.fetchConfig(prefs.laptopIp, prefs.laptopPort) { config ->
                                if (config != null) {
                                    mainHandler.post {
                                        prefs.pomodoroDuration = config.durations.work
                                        prefs.shortBreakDuration = config.durations.short_break
                                        prefs.longBreakDuration = config.durations.long_break
                                        prefs.longBreakAfter = config.long_break_after
                                        prefs.dailyGoal = config.daily_goal
                                        Log.d(TAG, "Updated local preferences from server config")
                                    }
                                }
                            }

                            webSocketClient.send("{\"type\":\"ready\"}")
                            readySent = true
                            Log.d(TAG, "Sent ready message to server")

                            updateNotification()
                            broadcastStateUpdate()
                        }
                    }

                    override fun onSyncFailed(e: Exception) {
                        mainHandler.post {
                            Log.e(TAG, "Sync failed - will accept WS messages on timeout", e)
                        }
                    }
                })

            updateNotification()
            broadcastStateUpdate()
        }
    }

    override fun onDisconnected() {
        mainHandler.post {
            isConnected = false
            syncCompleted = false
            readySent = false

            syncTimeoutRunnable?.let {
                mainHandler.removeCallbacks(it)
                syncTimeoutRunnable = null
            }

            Log.d(TAG, "Disconnected from WebSocket")
            syncManager.setOffline()

            if (TimerState.STATUS_RUNNING == currentState.status) {
                offlineTimer.updateState(currentState)
            }

            updateNotification()
            broadcastStateUpdate()
        }
    }

    override fun onStateReceived(state: TimerState) {
        mainHandler.post {
            if (!syncCompleted || !readySent) {
                Log.d(TAG, "Ignoring WS state - sync not completed yet")
                return@post
            }

            sanitizeState(state)
            this.currentState = state
            prefs.dailyGoal = state.goal
            offlineTimer.updateState(state)
            updateNotification()
            broadcastStateUpdate()
        }
    }

    private fun sanitizeState(state: TimerState) {
        if (state.duration <= 0) {
            when (state.phase) {
                TimerState.PHASE_WORK -> state.duration = 1500.0 // 25 min
                TimerState.PHASE_SHORT -> state.duration = 300.0 // 5 min
                TimerState.PHASE_LONG -> state.duration = 900.0  // 15 min
                else -> state.duration = 1500.0
            }
        }
        // Ensure remaining doesn't exceed duration
        if (state.remaining > state.duration) {
            state.remaining = state.duration
        }
    }

    fun onTimerUpdate(state: TimerState) {
        this.currentState = state
        updateNotification()
        broadcastStateUpdate()
    }

    fun onTimerComplete(state: TimerState) {
        this.currentState = state
        updateNotification()
        broadcastStateUpdate()
        vibrate()
        playSound()
    }

    private fun broadcastStateUpdate() {
        val intent = Intent("com.pomoremote.STATE_UPDATE")
        sendBroadcast(intent)
        TimerWidgetProvider.updateAllWidgets(this, currentState)
    }

    private fun updateNotification() {
        notificationHelper.updateNotification(currentState, isConnected)
    }

    fun toggleTimer() {
        if (isConnected) {
            sendApiRequest("toggle")
        } else {
            offlineTimer.toggle()
        }
    }

    fun skipTimer() {
        if (isConnected) {
            sendApiRequest("skip")
        } else {
            offlineTimer.skip()
        }
    }

    fun resetTimer() {
        if (isConnected) {
            sendApiRequest("reset")
        } else {
            offlineTimer.reset()
        }
    }

    fun updateDailyGoal() {
        val newGoal = prefs.dailyGoal
        if (currentState.goal != newGoal) {
            currentState.goal = newGoal
            updateNotification()
            broadcastStateUpdate()
        }
    }

    fun syncConfig() {
        if (!isConnected) return

        val durations = SyncManager.Durations(
            work = prefs.pomodoroDuration,
            short_break = prefs.shortBreakDuration,
            long_break = prefs.longBreakDuration
        )

        val config = SyncManager.ConfigPayload(
            durations = durations,
            long_break_after = prefs.longBreakAfter,
            daily_goal = prefs.dailyGoal
        )

        syncManager.syncConfig(prefs.laptopIp, prefs.laptopPort, config)
    }

    private fun sendApiRequest(endpoint: String) {
        Thread {
            try {
                val ip = prefs.laptopIp
                val port = prefs.laptopPort
                val url = "http://$ip:$port/api/$endpoint"

                val body = "".toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().close()
            } catch (e: IOException) {
                Log.e(TAG, "API request failed", e)
            }
        }.start()
    }

    private fun vibrate() {
        if (!prefs.isVibrateEnabled) return

        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(500)
        }
    }

    private fun playSound() {
        if (!prefs.isSoundEnabled) return

        try {
            if (currentRingtone != null && currentRingtone!!.isPlaying) {
                currentRingtone!!.stop()
            }

            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            currentRingtone = RingtoneManager.getRingtone(applicationContext, notification)

            if (currentRingtone != null) {
                currentRingtone!!.play()
            } else {
                Log.w(TAG, "Could not get ringtone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound", e)
        }
    }

    companion object {
        private const val TAG = "PomodoroService"
        private const val SYNC_TIMEOUT_MS = 10000L
    }
}
