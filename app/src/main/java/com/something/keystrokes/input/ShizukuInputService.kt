package com.something.keystrokes.input

import android.os.Process
import android.util.Log

import com.something.keystrokes.IShizukuInputListener
import com.something.keystrokes.IShizukuInputService

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuInputService :
    IShizukuInputService.Stub() {

    companion object {

        private const val TAG =
            "ShizukuInputService"

        /*
         * Android/Linux input_event
         *
         * 64 位设备：
         *
         * timeval = 16 bytes
         * type     = 2
         * code     = 2
         * value    = 4
         *
         * 总计 24 bytes
         */
        private const val INPUT_EVENT_SIZE = 24

        private const val EV_KEY = 0x01

        private const val KEY_W = 17
        private const val KEY_A = 30
        private const val KEY_S = 31
        private const val KEY_D = 32
        private const val KEY_SPACE = 57
    }

    private val running =
        AtomicBoolean(false)

    @Volatile
    private var status =
        "未启动"

    /*
     * 主 App 注册的 Binder Listener
     */
    @Volatile
    private var listener:
            IShizukuInputListener? = null

    /*
     * 一个 event 一个线程
     */
    private val inputThreads =
        Collections.synchronizedList(
            mutableListOf<Thread>()
        )

    /*
     * =========================================================
     * 输入流生命周期管理
     * =========================================================
     */
    private val inputStreams =
        Collections.synchronizedList(
            mutableListOf<BufferedInputStream>()
        )


    /*
     * =========================================================
     * UID
     * =========================================================
     */

    override fun getUid(): Int {

        val uid =
            Process.myUid()

        Log.i(
            TAG,
            "Shizuku User Service UID = $uid"
        )

        return uid
    }


    /*
     * =========================================================
     * 扫描输入设备
     *
     * 只枚举 /dev/input/event*，不读取设备名称。
     * =========================================================
     */

    override fun scanDevices(): Array<String> {

        val uid =
            Process.myUid()

        Log.i(
            TAG,
            "开始由 Shizuku UserService 扫描输入设备，UID = $uid"
        )

        if (uid != 2000) {

            Log.e(
                TAG,
                "扫描失败：UserService UID 不正确：$uid"
            )

            return emptyArray()
        }

        return try {

            val process =
                ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    """
                    for f in /dev/input/event*; do
                        [ -e "${'$'}f" ] || continue
                        printf '%s\n' "${'$'}f"
                    done
                    """.trimIndent()
                )
                    .redirectErrorStream(true)
                    .start()

            val result =
                process.inputStream
                    .bufferedReader()
                    .readLines()

            process.waitFor()

            result
                .map {
                    it.trim()
                }
                .filter {
                    it.matches(
                        Regex("/dev/input/event\\d+")
                    )
                }
                .forEach {

                    Log.i(
                        TAG,
                        "发现 event：$it"
                    )
                }

            result
                .filter {
                    it.matches(
                        Regex("/dev/input/event\\d+")
                    )
                }
                .toTypedArray()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Shizuku 扫描输入设备失败",
                e
            )

            emptyArray()
        }
    }


    /*
     * =========================================================
     * 设置事件监听器
     * =========================================================
     */

    override fun setListener(
        listener: IShizukuInputListener?
    ) {

        this.listener =
            listener

        Log.i(
            TAG,
            if (listener != null) {
                "已注册输入事件 Listener"
            } else {
                "已解除输入事件 Listener"
            }
        )
    }


    /*
     * =========================================================
     * 开始读取
     * =========================================================
     */

    override fun start(eventPaths: Array<String>): Int {

        if (running.get()) {

            Log.i(
                TAG,
                "检测到已有输入监听，先停止旧监听，再启动新的监听"
            )

            stop()
        }


        val uid =
            Process.myUid()

        Log.i(
            TAG,
            "================================"
        )

        Log.i(
            TAG,
            "开始 Shizuku 输入监听"
        )

        Log.i(
            TAG,
            "UID = $uid"
        )

        Log.i(
            TAG,
            "收到 eventPaths: ${eventPaths.joinToString()}"
        )

        Log.i(
            TAG,
            "================================"
        )


        /*
         * Shizuku User Service 正常情况下应该是 shell UID。
         */

        if (uid != 2000) {

            status =
                "UID 不正确：$uid"

            Log.e(
                TAG,
                status
            )

            return 2
        }


        /*
         * =====================================================
         * 使用传入的 eventPaths
         * =====================================================
         */

        if (eventPaths.isEmpty()) {

            status =
                "传入的 event 列表为空，不启动任何监听线程"

            Log.w(
                TAG,
                status
            )

            return 0
        }


        Log.i(
            TAG,
            "收到 ${eventPaths.size} 个 event 路径"
        )


        /*
         * =====================================================
         * 尝试打开指定的 event
         * =====================================================
         */

        Log.i(
            TAG,
            "准备测试打开 ${eventPaths.size} 个 event"
        )

        val readableDevices =
            eventPaths.mapNotNull { path ->

                Log.i(
                    TAG,
                    "进入 tryOpen：$path"
                )

                val file = File(path)

                val result =
                    tryOpen(file)

                Log.i(
                    TAG,
                    "tryOpen 返回：$path -> $result"
                )

                if (result) {
                    file
                } else {
                    null
                }
            }

        Log.i(
            TAG,
            "tryOpen 完成，成功打开 ${readableDevices.size} 个设备"
        )


        if (readableDevices.isEmpty()) {

            status =
                "所有指定的 event 都无法打开"

            Log.e(
                TAG,
                status
            )

            return 4
        }


        Log.i(
            TAG,
            "成功打开 ${readableDevices.size} 个 event 设备"
        )


        /*
         * =====================================================
         * 启动读取线程
         * =====================================================
         */

        running.set(true)

        inputThreads.clear()
        inputStreams.clear()


        readableDevices.forEach { file ->

            val thread =
                Thread {

                    readEvents(file)

                }.apply {

                    name =
                        "ShizukuInput-${file.name}"

                    start()
                }


            inputThreads.add(thread)
        }


        status =
            "运行中：监听 ${readableDevices.size} 个 event 设备"


        Log.i(
            TAG,
            status
        )


        return 0
    }


    /*
     * =========================================================
     * 停止
     * =========================================================
     */

    override fun stop() {

        Log.i(
            TAG,
            "停止 Shizuku 输入监听"
        )

        /*
         * 先通知所有读取线程退出
         */
        running.set(false)

        /*
         * 关闭正在阻塞读取的输入流。
         *
         * 仅 interrupt() 对 FileInputStream /
         * BufferedInputStream 不够可靠。
         */
        synchronized(inputStreams) {

            inputStreams.forEach {

                try {
                    it.close()
                } catch (_: Exception) {
                }

            }

            inputStreams.clear()
        }

        /*
         * 再中断线程
         */
        synchronized(inputThreads) {

            inputThreads.forEach {

                try {
                    it.interrupt()
                } catch (_: Exception) {
                }

            }

            inputThreads.clear()
        }

        status =
            "已停止"

        Log.i(
            TAG,
            "Shizuku 输入监听已停止"
        )
    }


    /*
     * =========================================================
     * 状态
     * =========================================================
     */

    override fun getStatus(): String {

        return status
    }


    /*
     * =========================================================
     * Service 销毁
     * =========================================================
     */

    override fun destroy() {

        Log.i(
            TAG,
            "Shizuku User Service destroy()"
        )

        running.set(false)

        synchronized(inputStreams) {

            inputStreams.forEach {

                try {
                    it.close()
                } catch (_: Exception) {
                }

            }

            inputStreams.clear()
        }

        synchronized(inputThreads) {

            inputThreads.forEach {

                try {
                    it.interrupt()
                } catch (_: Exception) {
                }

            }

            inputThreads.clear()
        }

        listener = null

        status =
            "服务已销毁"
    }


    /*
     * =========================================================
     * 测试打开
     * =========================================================
     */

    private fun tryOpen(
        file: File
    ): Boolean {

        return try {

            Log.i(
                TAG,
                "尝试打开：${file.absolutePath}"
            )


            FileInputStream(file).use {

                Log.i(
                    TAG,
                    "OPEN OK：${file.absolutePath}"
                )
            }


            true

        } catch (e: Exception) {

            Log.w(
                TAG,
                "OPEN FAILED：${file.absolutePath} " +
                        "${e.javaClass.simpleName}: " +
                        e.message
            )

            false
        }
    }


    /*
     * =========================================================
     * 读取事件
     * =========================================================
     */

    private fun readEvents(
        eventFile: File
    ) {

        Log.i(
            TAG,
            "开始读取：${eventFile.absolutePath}"
        )

        var input: BufferedInputStream? = null

        try {

            input =
                BufferedInputStream(
                    FileInputStream(eventFile),
                    8192
                )

            inputStreams.add(input)

            val buffer =
                ByteArray(
                    INPUT_EVENT_SIZE
                )


            while (
                running.get() &&
                !Thread.currentThread().isInterrupted
            ) {

                val count =
                    readFully(
                        input,
                        buffer
                    )


                if (
                    count <
                    INPUT_EVENT_SIZE
                ) {

                    break
                }


                val event =
                    parseInputEvent(buffer)


                if (
                    event.type !=
                    EV_KEY
                ) {

                    continue
                }


                val keyName =
                    keyName(event.code)


                val down =
                    event.value == 1


                Log.d(
                    TAG,
                    "[${eventFile.name}] " +
                            "KEY $keyName " +
                            "value=${event.value}"
                )


                try {

                    listener?.onKeyEvent(
                        event.type,
                        event.code,
                        event.value,
                        keyName,
                        down
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "发送按键事件失败",
                        e
                    )
                }
            }


        } catch (e: Exception) {

            /*
             * stop() 主动 close() 流时，
             * 这里出现 IOException 属于正常情况。
             */
            if (running.get()) {

                Log.e(
                    TAG,
                    "读取 ${eventFile.absolutePath} 失败",
                    e
                )
            }

        } finally {

            if (input != null) {

                inputStreams.remove(input)

                try {
                    input.close()
                } catch (_: Exception) {
                }
            }

            Log.i(
                TAG,
                "读取线程结束：${eventFile.absolutePath}"
            )
        }
    }


    /*
     * =========================================================
     * 完整读取
     * =========================================================
     */

    private fun readFully(
        input: BufferedInputStream,
        buffer: ByteArray
    ): Int {

        var offset = 0


        while (
            offset <
            buffer.size
        ) {

            val count =
                input.read(
                    buffer,
                    offset,
                    buffer.size - offset
                )


            if (count < 0) {
                break
            }


            if (count == 0) {
                continue
            }


            offset += count
        }


        return offset
    }


    /*
     * =========================================================
     * 解析 input_event
     * =========================================================
     */

    private fun parseInputEvent(
        buffer: ByteArray
    ): LinuxEvent {

        val byteBuffer =
            ByteBuffer
                .wrap(buffer)
                .order(
                    ByteOrder.LITTLE_ENDIAN
                )


        val seconds =
            byteBuffer.long

        val microseconds =
            byteBuffer.long

        val type =
            byteBuffer
                .short
                .toInt() and 0xFFFF


        val code =
            byteBuffer
                .short
                .toInt() and 0xFFFF


        val value =
            byteBuffer.int


        return LinuxEvent(
            seconds,
            microseconds,
            type,
            code,
            value
        )
    }


    /*
     * =========================================================
     * KEY 名称
     * =========================================================
     */

    private fun keyName(
        code: Int
    ): String {

        return when (code) {

            KEY_W ->
                "KEY_W"

            KEY_A ->
                "KEY_A"

            KEY_S ->
                "KEY_S"

            KEY_D ->
                "KEY_D"

            KEY_SPACE ->
                "KEY_SPACE"

            1 ->
                "KEY_ESC"

            28 ->
                "KEY_ENTER"

            29 ->
                "KEY_LEFTCTRL"

            42 ->
                "KEY_LEFTSHIFT"

            54 ->
                "KEY_RIGHTSHIFT"

            56 ->
                "KEY_LEFTALT"

            100 ->
                "KEY_RIGHTALT"

            14 ->
                "KEY_BACKSPACE"

            15 ->
                "KEY_TAB"

            103 ->
                "KEY_UP"

            108 ->
                "KEY_DOWN"

            105 ->
                "KEY_LEFT"

            106 ->
                "KEY_RIGHT"

            111 ->
                "KEY_DELETE"

            110 ->
                "KEY_INSERT"

            102 ->
                "KEY_HOME"

            107 ->
                "KEY_END"

            else ->
                "KEY_$code"
        }
    }


    /*
     * =========================================================
     * Linux Event
     * =========================================================
     */

    private data class LinuxEvent(

        val seconds: Long,

        val microseconds: Long,

        val type: Int,

        val code: Int,

        val value: Int
    )
}