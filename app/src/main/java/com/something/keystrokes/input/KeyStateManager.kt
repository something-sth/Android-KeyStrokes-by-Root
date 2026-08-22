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

        const val KEY_SPACE = 57

        // Shift 按键
        const val KEY_LEFT_SHIFT = 42
        const val KEY_RIGHT_SHIFT = 54

        // 鼠标按键
        const val KEY_LMB = 272   // BTN_LEFT
        const val KEY_RMB = 273   // BTN_RIGHT
    }
}