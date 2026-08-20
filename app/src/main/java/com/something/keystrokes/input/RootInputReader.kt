package com.something.keystrokes.input

import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class RootInputReader(
    private val eventPath: String,
    private val onEvent: (KeyEventData) -> Unit,
    private val onError: (String) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var process: Process? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        thread = Thread {
            readLoop()
        }.also {
            it.name = "RootInputReader"
            it.start()
        }
    }

    fun stop() {
        running.set(false)

        try {
            process?.destroy()
        } catch (_: Exception) {
        }

        process = null
        thread?.interrupt()
        thread = null
    }

    private fun readLoop() {
        try {
            val command = arrayOf(
                "su", "-c",
                "cat ${shellQuote(eventPath)}"
            )

            process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val input = BufferedInputStream(process!!.inputStream)

            val eventSize = 24
            val buffer = ByteArray(eventSize)

            while (running.get()) {
                var offset = 0

                while (offset < eventSize && running.get()) {
                    val read = input.read(buffer, offset, eventSize - offset)
                    if (read < 0) {
                        throw IllegalStateException("输入设备读取结束，可能是设备断开或 su/cat 退出。")
                    }
                    offset += read
                }

                if (!running.get()) break

                parseEvent(buffer)
            }
        } catch (e: Exception) {
            if (running.get()) {
                onError("读取 $eventPath 失败：${e.message ?: e.javaClass.simpleName}")
            }
        } finally {
            running.set(false)
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
            process = null
        }
    }

    private fun parseEvent(buffer: ByteArray) {
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        val seconds = bb.long
        bb.long

        val type = bb.short.toInt() and 0xFFFF
        val code = bb.short.toInt() and 0xFFFF
        val value = bb.int

        if (type != 0x01) return

        if (value != 0 && value != 1) return

        val keyName = linuxKeyName(code)

        onEvent(
            KeyEventData(
                timeMillis = seconds * 1000L,
                type = type,
                code = code,
                value = value,
                keyName = keyName,
                down = value == 1
            )
        )
    }

    private fun linuxKeyName(code: Int): String {
        return KEY_NAMES[code] ?: "KEY_CODE_$code"
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    companion object {
        const val BTN_LEFT = 272
        const val BTN_RIGHT = 273

        private val KEY_NAMES = mapOf(
            1 to "KEY_ESC",
            2 to "KEY_1",
            3 to "KEY_2",
            4 to "KEY_3",
            5 to "KEY_4",
            6 to "KEY_5",
            7 to "KEY_6",
            8 to "KEY_7",
            9 to "KEY_8",
            10 to "KEY_9",
            11 to "KEY_0",
            12 to "KEY_MINUS",
            13 to "KEY_EQUAL",
            14 to "KEY_BACKSPACE",
            15 to "KEY_TAB",
            16 to "KEY_Q",
            17 to "KEY_W",
            18 to "KEY_E",
            19 to "KEY_R",
            20 to "KEY_T",
            21 to "KEY_Y",
            22 to "KEY_U",
            23 to "KEY_I",
            24 to "KEY_O",
            25 to "KEY_P",
            26 to "KEY_LEFTBRACE",
            27 to "KEY_RIGHTBRACE",
            28 to "KEY_ENTER",
            29 to "KEY_LEFTCTRL",
            30 to "KEY_A",
            31 to "KEY_S",
            32 to "KEY_D",
            33 to "KEY_F",
            34 to "KEY_G",
            35 to "KEY_H",
            36 to "KEY_J",
            37 to "KEY_K",
            38 to "KEY_L",
            39 to "KEY_SEMICOLON",
            40 to "KEY_APOSTROPHE",
            41 to "KEY_GRAVE",
            42 to "KEY_LEFTSHIFT",
            43 to "KEY_BACKSLASH",
            44 to "KEY_Z",
            45 to "KEY_X",
            46 to "KEY_C",
            47 to "KEY_V",
            48 to "KEY_B",
            49 to "KEY_N",
            50 to "KEY_M",
            51 to "KEY_COMMA",
            52 to "KEY_DOT",
            53 to "KEY_SLASH",
            54 to "KEY_RIGHTSHIFT",
            55 to "KEY_KPASTERISK",
            56 to "KEY_LEFTALT",
            57 to "KEY_SPACE",
            58 to "KEY_CAPSLOCK",
            59 to "KEY_F1",
            60 to "KEY_F2",
            61 to "KEY_F3",
            62 to "KEY_F4",
            63 to "KEY_F5",
            64 to "KEY_F6",
            65 to "KEY_F7",
            66 to "KEY_F8",
            67 to "KEY_F9",
            68 to "KEY_F10",
            69 to "KEY_NUMLOCK",
            70 to "KEY_SCROLLLOCK",
            71 to "KEY_KP7",
            72 to "KEY_KP8",
            73 to "KEY_KP9",
            74 to "KEY_KPMINUS",
            75 to "KEY_KP4",
            76 to "KEY_KP5",
            77 to "KEY_KP6",
            78 to "KEY_KPPLUS",
            79 to "KEY_KP1",
            80 to "KEY_KP2",
            81 to "KEY_KP3",
            82 to "KEY_KP0",
            83 to "KEY_KPDOT",
            87 to "KEY_F11",
            88 to "KEY_F12",
            96 to "KEY_KPENTER",
            97 to "KEY_RIGHTCTRL",
            100 to "KEY_RIGHTALT",
            102 to "KEY_HOME",
            103 to "KEY_UP",
            104 to "KEY_PAGEUP",
            105 to "KEY_LEFT",
            106 to "KEY_RIGHT",
            107 to "KEY_END",
            108 to "KEY_DOWN",
            109 to "KEY_PAGEDOWN",
            110 to "KEY_INSERT",
            111 to "KEY_DELETE",
            272 to "BTN_LEFT",
            273 to "BTN_RIGHT"
        )

        fun getUid(): Int {
            return try {
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        "su",
                        "-c",
                        "id -u"
                    )
                )

                val output = process.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }
                    .trim()

                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    -1
                } else {
                    output.toIntOrNull() ?: -1
                }
            } catch (e: Exception) {
                -1
            }
        }
    }
}