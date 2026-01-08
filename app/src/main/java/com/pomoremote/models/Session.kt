package com.pomoremote.models

data class Session(
    val type: String,
    val start: Long,
    val duration: Int,
    val completed: Boolean
)
