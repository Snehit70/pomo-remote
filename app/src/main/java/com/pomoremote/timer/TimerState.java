package com.pomoremote.timer;

public class TimerState {
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_PAUSED = "paused";

    public static final String PHASE_WORK = "work";
    public static final String PHASE_SHORT = "short";
    public static final String PHASE_LONG = "long";

    public String status;
    public String phase;
    public String next_phase;
    public double start_time;
    public double duration;
    public double remaining;
    public int completed;
    public String date;
    public long last_action_time;
    public int version;

    public TimerState() {
        this.status = STATUS_STOPPED;
        this.phase = PHASE_WORK;
    }
}
