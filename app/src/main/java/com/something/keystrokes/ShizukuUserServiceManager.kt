package com.something.keystrokes

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs


/**
 * ============================================================
 * Shizuku UserService 管理器
 * ============================================================
 *
 * 负责：
 *
 * 1. 检查 Shizuku
 * 2. 请求 / 检查权限
 * 3. 启动 UserService
 * 4. 保存 UserService Binder
 * 5. 停止 UserService
 *
 * 后续真正读取输入设备的代码，
 * 会逐渐移动到 UserService 进程中。
 *
 * ============================================================
 */
object ShizukuUserServiceManager {

    private const val TAG = "KeyStrokes-Shizuku"

    private var serviceBinder: IBinder? = null

    private var serviceConnection: ServiceConnection? = null

    private var onConnected: ((IBinder) -> Unit)? = null


    /**
     * 当前是否已经连接 UserService
     */
    fun isConnected(): Boolean {
        return serviceBinder?.isBinderAlive == true
    }


    /**
     * 获取当前 Binder
     *
     * 后续 AIDL 通信会使用。
     */
    fun getBinder(): IBinder? {
        return serviceBinder
    }


    /**
     * 获取 Shizuku InputService 的 AIDL 接口
     */
    fun getService(): IShizukuInputService? {
        val binder = serviceBinder ?: return null
        if (!binder.isBinderAlive) {
            return null
        }
        return IShizukuInputService.Stub.asInterface(binder)
    }


    /**
     * 检查 Shizuku 是否正在运行。
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }


    /**
     * 检查当前应用是否拥有 Shizuku 权限。
     */
    fun hasPermission(): Boolean {

        if (!isShizukuRunning()) {
            return false
        }

        return try {

            Shizuku.checkSelfPermission() ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED

        } catch (e: Exception) {

            Log.e(
                TAG,
                "检查 Shizuku 权限失败",
                e
            )

            false
        }
    }


    /**
     * 请求 Shizuku 权限。
     *
     * 如果已经拥有权限，则不会重复请求。
     */
    fun requestPermission(
        requestCode: Int = 1000
    ) {

        if (!isShizukuRunning()) {
            throw IllegalStateException(
                "Shizuku 未运行"
            )
        }

        if (hasPermission()) {
            return
        }

        Shizuku.requestPermission(requestCode)
    }


    /**
     * 启动 UserService。
     *
     * @param eventPaths 要监听的 event 路径列表，例如 arrayOf("/dev/input/event1", "/dev/input/event4")
     * @param onConnected 连接成功回调，参数为 IBinder
     */
    fun start(
        eventPaths: Array<String> = emptyArray(),
        onConnected: ((IBinder) -> Unit)? = null
    ) {

        if (!isShizukuRunning()) {
            throw IllegalStateException(
                "Shizuku 未运行"
            )
        }

        if (!hasPermission()) {
            throw SecurityException(
                "Shizuku 未授权"
            )
        }


        /*
         * 已经连接就不用重复启动。
         */
        if (isConnected()) {
            this.onConnected?.invoke(serviceBinder!!)
            return
        }

        this.onConnected = onConnected


        /*
         * UserService 参数。
         *
         * className：
         *     UserService 的完整类名。
         *
         * tag：
         *     用来区分 UserService。
         *
         * version：
         *     UserService 版本。
         */
        val args =
            UserServiceArgs(
                ComponentName(
                    "com.something.keystrokes",
                    "com.something.keystrokes.input.ShizukuInputService"
                )
            )
                .daemon(false)
                .processNameSuffix("input")
                .version(1)


        /*
         * 标准 Android ServiceConnection。
         */
        val connection =
            object : ServiceConnection {

                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?
                ) {

                    Log.d(
                        TAG,
                        "UserService 已连接"
                    )

                    serviceBinder = service

                    if (service != null) {
                        // 启动后调用 start 传入 eventPaths
                        try {
                            val inputService = IShizukuInputService.Stub.asInterface(service)
                            inputService?.start(eventPaths)
                        } catch (e: Exception) {
                            Log.e(TAG, "调用 UserService.start() 失败", e)
                        }
                        onConnected?.invoke(service)
                    }
                }


                override fun onServiceDisconnected(
                    name: ComponentName?
                ) {

                    Log.d(
                        TAG,
                        "UserService 已断开"
                    )

                    serviceBinder = null
                }
            }


        serviceConnection = connection


        /*
         * 启动 UserService。
         */
        Shizuku.bindUserService(
            args,
            connection
        )
    }


    /**
     * 停止 UserService。
     */
    fun stop() {

        val connection =
            serviceConnection
                ?: return


        try {

            Shizuku.unbindUserService(
                UserServiceArgs(
                    ComponentName(
                        "com.something.keystrokes",
                        "com.something.keystrokes.input.ShizukuInputService"
                    )
                )
                    .daemon(false)
                    .processNameSuffix("input")
                    .version(1),
                connection,
                true
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "停止 UserService 失败",
                e
            )
        }


        serviceBinder = null
        serviceConnection = null
        onConnected = null
    }
}