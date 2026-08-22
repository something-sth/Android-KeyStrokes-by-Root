package com.something.keystrokes.input

object OverlayState {

    private var pressedKeys = emptySet<Int>()

    private val mouseClickTimes = mutableMapOf(
        KeyStateManager.KEY_LMB to ArrayDeque<Long>(),
        KeyStateManager.KEY_RMB to ArrayDeque<Long>()
    )

    private val listeners = mutableListOf<(Set<Int>) -> Unit>()

    @Synchronized
    fun update(keys: Set<Int>) {
        pressedKeys = keys
        listeners.toList().forEach { it(keys) }
    }

    /** 记录一次鼠标左/右键点击，用最近 1 秒的点击次数计算 CPS。 */
    @Synchronized
    fun recordMouseClick(keyCode: Int, now: Long = System.currentTimeMillis()) {
        val times = mouseClickTimes[keyCode] ?: return
        times.addLast(now)
        prune(times, now)
    }

    @Synchronized
    fun getMouseCps(keyCode: Int, now: Long = System.currentTimeMillis()): Int {
        val times = mouseClickTimes[keyCode] ?: return 0
        prune(times, now)
        return times.size
    }

    @Synchronized
    fun clearMouseClicks() {
        mouseClickTimes.values.forEach { it.clear() }
    }

    @Synchronized
    fun getPressedKeys(): Set<Int> = pressedKeys

    fun addListener(listener: (Set<Int>) -> Unit) {
        synchronized(this) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (Set<Int>) -> Unit) {
        synchronized(this) {
            listeners.remove(listener)
        }
    }

    private fun prune(times: ArrayDeque<Long>, now: Long) {
        while (times.isNotEmpty() && now - times.first() >= CPS_WINDOW_MS) {
            times.removeFirst()
        }
    }

    private const val CPS_WINDOW_MS = 1000L
}
