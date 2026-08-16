package com.something.keystrokes.input


data class LinuxInputEvent(

    val timestamp: String,

    val type: Int,

    val code: Int,

    val value: Int
)