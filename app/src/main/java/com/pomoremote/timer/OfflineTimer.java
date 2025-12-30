package com.pomoremote.timer;

import android.os.CountDownTimer;
import com.pomoremote.service.PomodoroService;

public class OfflineTimer {
    private CountDownTimer timer;
    private final PomodoroService service;
    private TimerState state;

    public OfflineTimer(PomodoroService service) {
        this.service = service;
        this.state = new TimerState();
    }

    public void updateState(TimerState newState) {
        this.state = newState;
        if (TimerState.STATUS_RUNNING.equals(state.status)) {
            startLocalTimer();
        } else {
            stopLocalTimer();
        }
    }

    public TimerState getState() {
        return state;
    }

    private void startLocalTimer() {
        stopLocalTimer();
        
        long remainingMillis = (long) (state.remaining * 1000);
        if (remainingMillis <= 0) return;

        timer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                state.remaining = millisUntilFinished / 1000.0;
                service.onTimerUpdate(state);
            }

            @Override
            public void onFinish() {
                state.remaining = 0;
                state.status = TimerState.STATUS_STOPPED;
                state.completed++;
                service.onTimerComplete(state);
            }
        }.start();
    }

    private void stopLocalTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    public void toggle() {
        if (TimerState.STATUS_RUNNING.equals(state.status)) {
            state.status = TimerState.STATUS_PAUSED;
            state.last_action_time = System.currentTimeMillis() / 1000;
            stopLocalTimer();
            service.onTimerUpdate(state);
        } else {
            state.status = TimerState.STATUS_RUNNING;
            state.last_action_time = System.currentTimeMillis() / 1000;
            if (state.remaining <= 0) {
                state.remaining = state.duration > 0 ? state.duration : 1500;
            }
            startLocalTimer();
            service.onTimerUpdate(state);
        }
    }

    public void skip() {
        state.status = TimerState.STATUS_STOPPED;
        state.remaining = 0;
        state.last_action_time = System.currentTimeMillis() / 1000;
        stopLocalTimer();
        
        if (TimerState.PHASE_WORK.equals(state.phase)) {
            state.phase = TimerState.PHASE_SHORT;
            state.duration = 300;
            state.remaining = 300;
        } else {
            state.phase = TimerState.PHASE_WORK;
            state.duration = 1500;
            state.remaining = 1500;
        }
        service.onTimerUpdate(state);
    }
    
    public void reset() {
        state.status = TimerState.STATUS_STOPPED;
        state.remaining = state.duration;
        state.last_action_time = System.currentTimeMillis() / 1000;
        stopLocalTimer();
        service.onTimerUpdate(state);
    }
}
