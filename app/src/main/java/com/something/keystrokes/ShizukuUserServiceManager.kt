package com.something.keystrokes

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
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
     * 注意：
     *
     * UserService 本身需要一个实现 IBinder 的服务类。
     *
     * 我们下一步会创建：
     *
     *     KeyStrokesUserService
     *
     * 以及对应的 AIDL。
     */
    fun start() {

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
            return
        }


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
                    "com.something.keystrokes.input.KeyStrokesUserService"
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
                        "com.something.keystrokes.input.KeyStrokesUserService"
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
    }
}