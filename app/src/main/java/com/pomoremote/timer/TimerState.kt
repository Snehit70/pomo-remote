package com.pomoremote.timer

class TimerState {
    companion object {
        const val STATUS_STOPPED = "stopped"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"

        const val PHASE_WORK = "work"
        const val PHASE_SHORT = "short"
        const val PHASE_LONG = "long"
    }

    @JvmField var status: String = STATUS_STOPPED
    @JvmField var phase: String = PHASE_WORK
    @JvmField var next_phase: String? = null
    @JvmField var start_time: Double = 0.0
    @JvmField var duration: Double = 0.0
    @JvmField var remaining: Double = 0.0
    @JvmField var completed: Int = 0
    @JvmField var goal: Int = 8
    @JvmField var date: String? = null
    @JvmField var last_action_time: Long = 0
    @JvmField var version: Int = 2
}
