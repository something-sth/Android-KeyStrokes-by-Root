package com.something.keystrokes.input


object LinuxKeyMapper {


    fun name(code: Int): String {

        return when(code) {

            17 -> "W"
            30 -> "A"
            31 -> "S"
            32 -> "D"

            42 -> "SHIFT"
            54 -> "SHIFT"

            57 -> "SPACE"

            else -> code.toString()
        }

    }

}