package com.something.keystrokes.input

data class KeyEventData(
    val timeMillis: Long,
    val type: Int,
    val code: Int,
    val value: Int,
    val keyName: String,
    val down: Boolean
)