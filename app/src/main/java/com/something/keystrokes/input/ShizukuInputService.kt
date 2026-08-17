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

    override fun start(): Int {

        if (running.get()) {

            Log.w(
                TAG,
                "输入监听已经在运行"
            )

            return 1
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
         * 查找 /dev/input/event*
         * =====================================================
         */

        val eventFiles =
            findEventFiles()

        if (eventFiles.isEmpty()) {

            status =
                "没有找到 /dev/input/event*"

            Log.e(
                TAG,
                status
            )

            return 3
        }


        Log.i(
            TAG,
            "发现 ${eventFiles.size} 个 event 设备"
        )


        /*
         * =====================================================
         * 尝试打开所有 event
         * =====================================================
         */

        val readableDevices =
            eventFiles.filter { file ->
                tryOpen(file)
            }


        if (readableDevices.isEmpty()) {

            status =
                "找到 event*，但全部无法打开"

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

        running.set(false)


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


        synchronized(inputThreads) {

            inputThreads.forEach {

                try {
                    it.interrupt()
                } catch (_: Exception) {
                }
            }

            inputThreads.clear()
        }


        listener =
            null


        status =
            "服务已销毁"
    }


    /*
     * =========================================================
     * 查找 event*
     * =========================================================
     */

    private fun findEventFiles(): List<File> {

        val inputDirectory =
            File("/dev/input")


        if (!inputDirectory.exists()) {

            Log.e(
                TAG,
                "/dev/input 不存在"
            )

            return emptyList()
        }


        if (!inputDirectory.isDirectory) {

            Log.e(
                TAG,
                "/dev/input 不是目录"
            )

            return emptyList()
        }


        return inputDirectory
            .listFiles()
            ?.filter {

                it.name.matches(
                    Regex("event\\d+")
                )

            }
            ?.sortedBy {

                it.name
            }
            ?: emptyList()
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


        try {

            BufferedInputStream(
                FileInputStream(eventFile),
                8192
            ).use { input ->

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


                    /*
                     * Linux input_event:
                     *
                     * 0 = UP
                     * 1 = DOWN
                     * 2 = REPEAT
                     *
                     * 这里暂时全部传出去。
                     */

                    val keyName =
                        keyName(event.code)


                    val down =
                        event.value == 1


                    Log.i(
                        TAG,
                        "[${eventFile.name}] " +
                                "KEY $keyName " +
                                "value=${event.value}"
                    )


                    /*
                     * =================================================
                     * Binder -> 主 App
                     * =================================================
                     */

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
                            "发送 KeyEvent 失败",
                            e
                        )
                    }
                }
            }


        } catch (e: Exception) {

            if (running.get()) {

                Log.e(
                    TAG,
                    "${eventFile.name} 读取失败："
                            + "${e.javaClass.simpleName}: "
                            + e.message,
                    e
                )
            }

        } finally {

            Log.i(
                TAG,
                "读取线程结束：${eventFile.name}"
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