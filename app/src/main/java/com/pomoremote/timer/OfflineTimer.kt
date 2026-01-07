package com.pomoremote.timer

import android.os.CountDownTimer
import com.pomoremote.service.PomodoroService

class OfflineTimer(private val service: PomodoroService) {
    private var timer: CountDownTimer? = null
    var state: TimerState = TimerState()
        private set

    fun updateState(newState: TimerState) {
        this.state = newState
        if (TimerState.STATUS_RUNNING == state.status) {
            startLocalTimer()
        } else {
            stopLocalTimer()
        }
    }

    private fun startLocalTimer() {
        stopLocalTimer()

        val remainingMillis = (state.remaining * 1000).toLong()
        if (remainingMillis <= 0) return

        timer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                state.remaining = millisUntilFinished / 1000.0
                service.onTimerUpdate(state)
            }

            override fun onFinish() {
                state.remaining = 0.0
                state.status = TimerState.STATUS_STOPPED
                state.completed++
                service.onTimerComplete(state)
            }
        }.start()
    }

    private fun stopLocalTimer() {
        timer?.cancel()
        timer = null
    }

    fun toggle() {
        if (TimerState.STATUS_RUNNING == state.status) {
            state.status = TimerState.STATUS_PAUSED
            state.last_action_time = System.currentTimeMillis() / 1000
            stopLocalTimer()
            service.onTimerUpdate(state)
        } else {
            state.status = TimerState.STATUS_RUNNING
            state.last_action_time = System.currentTimeMillis() / 1000
            if (state.remaining <= 0) {
                state.remaining = if (state.duration > 0) state.duration else 1500.0
            }
            startLocalTimer()
            service.onTimerUpdate(state)
        }
    }

    fun skip() {
        state.status = TimerState.STATUS_STOPPED
        state.remaining = 0.0
        state.last_action_time = System.currentTimeMillis() / 1000
        stopLocalTimer()

        if (TimerState.PHASE_WORK == state.phase) {
            state.phase = TimerState.PHASE_SHORT
            state.duration = 300.0
            state.remaining = 300.0
        } else {
            state.phase = TimerState.PHASE_WORK
            state.duration = 1500.0
            state.remaining = 1500.0
        }
        service.onTimerUpdate(state)
    }

    fun reset() {
        state.status = TimerState.STATUS_STOPPED
        state.remaining = state.duration
        state.last_action_time = System.currentTimeMillis() / 1000
        stopLocalTimer()
        service.onTimerUpdate(state)
    }
}
