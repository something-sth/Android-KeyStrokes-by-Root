package com.something.keystrokes.input

class KeyStateManager {

    private val pressedKeys = mutableSetOf<Int>()

    @Synchronized
    fun update(event: KeyEventData) {

        when (event.value) {

            1 -> {
                // DOWN
                pressedKeys.add(event.code)
            }

            0 -> {
                // UP
                pressedKeys.remove(event.code)
            }

            2 -> {
                // REPEAT
                // 已经按下，不处理
            }
        }
    }

    @Synchronized
    fun getPressedKeys(): Set<Int> {
        return pressedKeys.toSet()
    }

    @Synchronized
    fun clear() {
        pressedKeys.clear()
    }

    companion object {

        const val KEY_W = 17
        const val KEY_A = 30
        const val KEY_S = 31
        const val KEY_D = 32

        const val KEY_LEFTSHIFT = 42
        const val KEY_RIGHTSHIFT = 54

        const val KEY_SPACE = 57
    }
}