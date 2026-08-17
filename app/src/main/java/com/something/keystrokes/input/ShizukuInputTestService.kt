package com.something.keystrokes.input

import android.os.Process
import android.util.Log

import com.something.keystrokes.IShizukuInputTest

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuInputTestService :
    IShizukuInputTest.Stub() {

    companion object {

        private const val TAG =
            "ShizukuInputTest"

        /*
         * Android Linux input_event
         *
         * struct input_event {
         *     struct timeval time; // 16 bytes
         *     __u16 type;          // 2
         *     __u16 code;          // 2
         *     __s32 value;         // 4
         * }
         *
         * 总大小 = 24 bytes
         */
        private const val INPUT_EVENT_SIZE = 24

        private const val EV_KEY = 0x01

        private const val KEY_W = 17
        private const val KEY_A = 30
        private const val KEY_S = 31
        private const val KEY_D = 32
        private const val KEY_SPACE = 57
    }

    /*
     * =========================================================
     * 状态
     * =========================================================
     */

    private val running =
        AtomicBoolean(false)

    @Volatile
    private var status =
        "未启动"

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

        Log.i(TAG, "================================")
        Log.i(
            TAG,
            "Shizuku User Service UID = $uid"
        )
        Log.i(TAG, "================================")

        return uid
    }


    /*
     * =========================================================
     * 开始测试
     * =========================================================
     */

    override fun startInputTest(): Int {

        if (running.get()) {

            Log.w(
                TAG,
                "输入测试已经在运行"
            )

            return 1
        }

        val uid =
            Process.myUid()

        Log.i(TAG, "================================")
        Log.i(
            TAG,
            "开始 Shizuku 输入测试"
        )
        Log.i(
            TAG,
            "UID = $uid"
        )
        Log.i(TAG, "================================")


        /*
         * 必须是 shell UID
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
         * 扫描 event*
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


        eventFiles.forEach {

            Log.i(
                TAG,
                "发现设备：${it.absolutePath}"
            )
        }


        /*
         * =====================================================
         * 尝试打开所有设备
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


        Log.i(TAG, "================================")

        Log.i(
            TAG,
            "成功打开 ${readableDevices.size} 个设备"
        )


        readableDevices.forEach {

            Log.i(
                TAG,
                "OPEN OK: ${it.absolutePath}"
            )
        }


        Log.i(TAG, "================================")


        /*
         * =====================================================
         * 开始监听
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
            "测试运行中：监听 ${readableDevices.size} 个 event 设备"


        Log.i(
            TAG,
            status
        )


        return 0
    }


    /*
     * =========================================================
     * 停止测试
     * =========================================================
     */

    override fun stopInputTest() {

        Log.i(
            TAG,
            "停止输入测试"
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
     * 获取状态
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


        status =
            "服务已销毁"


        /*
         * 不主动 System.exit()
         *
         * Shizuku 会负责 UserService 生命周期。
         */
    }


    /*
     * =========================================================
     * 查找 /dev/input/event*
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

                it.name.startsWith("event")

            }
            ?.sortedBy {

                it.name

            }
            ?: emptyList()
    }


    /*
     * =========================================================
     * 尝试打开设备
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
                        "${e.javaClass.simpleName}: ${e.message}"
            )

            false
        }
    }


    /*
     * =========================================================
     * 读取输入事件
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

                        Log.w(
                            TAG,
                            "${eventFile.name} " +
                                    "读取结束，读取到 $count bytes"
                        )

                        break
                    }


                    val event =
                        parseInputEvent(buffer)


                    /*
                     * 只关心 EV_KEY
                     */

                    if (
                        event.type !=
                        EV_KEY
                    ) {

                        continue
                    }


                    val action =

                        when (event.value) {

                            0 ->
                                "UP"

                            1 ->
                                "DOWN"

                            2 ->
                                "REPEAT"

                            else ->
                                event.value.toString()
                        }


                    val keyName =
                        keyName(event.code)


                    /*
                     * =================================================
                     * 这是现在最重要的 Log
                     * =================================================
                     */

                    Log.i(
                        TAG,
                        "[${eventFile.name}] " +
                                "KEY $keyName " +
                                "$action " +
                                "(code=${event.code})"
                    )
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
     * 完整读取 24 bytes
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
     * 解析 Linux input_event
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

            seconds =
                seconds,

            microseconds =
                microseconds,

            type =
                type,

            code =
                code,

            value =
                value
        )
    }


    /*
     * =========================================================
     * Linux KEY_* 名称
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