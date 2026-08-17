package com.something.keystrokes

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.something.keystrokes.input.ShizukuInputTestService

import java.io.BufferedReader
import java.io.InputStreamReader

import rikka.shizuku.Shizuku


/*
 * =============================================================
 * Shizuku 测试
 * =============================================================
 */

private const val TAG_SHIZUKU = "ShizukuInputTest"

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001


@Composable
fun SetupScreen(
    onEnter: () -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    /*
     * =========================================================
     * 权限状态
     * =========================================================
     */

    var overlayGranted by remember {
        mutableStateOf(
            Settings.canDrawOverlays(context)
        )
    }


    var rootStatus by remember {
        mutableStateOf(
            checkRootAccess()
        )
    }


    /*
     * =========================================================
     * Shizuku 状态
     * =========================================================
     */

    var shizukuStatus by remember {

        mutableStateOf(
            getShizukuStatus()
        )

    }


    /*
     * 当前 Shizuku User Service
     */

    var shizukuInputTestService by remember {

        mutableStateOf<IShizukuInputTest?>(null)

    }


    /*
     * =========================================================
     * Shizuku User Service 参数
     * =========================================================
     */

    val shizukuUserServiceArgs = remember {

        Shizuku.UserServiceArgs(
            ComponentName(
                context,
                ShizukuInputTestService::class.java
            )
        )
            .daemon(false)
            .processNameSuffix("input_test")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    }


    /*
     * =========================================================
     * Shizuku User Service 连接
     * =========================================================
     */

    val shizukuServiceConnection =
        remember(context) {

            object : ServiceConnection {

                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?
                ) {

                    Log.i(
                        TAG_SHIZUKU,
                        "User Service 已连接"
                    )


                    if (
                        service == null ||
                        !service.pingBinder()
                    ) {

                        Log.e(
                            TAG_SHIZUKU,
                            "User Service Binder 无效"
                        )

                        shizukuStatus =
                            "连接失败"

                        Toast.makeText(
                            context,
                            "Shizuku User Service 连接失败",
                            Toast.LENGTH_SHORT
                        ).show()

                        return

                    }


                    shizukuInputTestService =
                        IShizukuInputTest.Stub
                            .asInterface(service)


                    try {

                        val uid =
                            shizukuInputTestService
                                ?.getUid()
                                ?: -1


                        Log.i(
                            TAG_SHIZUKU,
                            "User Service UID = $uid"
                        )


                        if (uid == 2000) {

                            shizukuStatus =
                                "已连接 UID 2000"

                        } else {

                            shizukuStatus =
                                "UID $uid"

                        }


                        val result =
                            shizukuInputTestService
                                ?.startInputTest()
                                ?: -1


                        Log.i(
                            TAG_SHIZUKU,
                            "startInputTest result = $result"
                        )


                        when (result) {

                            0 -> {

                                shizukuStatus =
                                    "测试运行中"

                                Toast.makeText(
                                    context,
                                    "程序已废弃",
                                    Toast.LENGTH_SHORT
                                ).show()

                            }

                            1 -> {

                                shizukuStatus =
                                    "测试已运行"

                                Toast.makeText(
                                    context,
                                    "输入测试已经在运行",
                                    Toast.LENGTH_SHORT
                                ).show()

                            }

                            2 -> {

                                shizukuStatus =
                                    "UID 错误"

                                Toast.makeText(
                                    context,
                                    "Shizuku User Service UID 不是 2000",
                                    Toast.LENGTH_LONG
                                ).show()

                            }

                            3 -> {

                                shizukuStatus =
                                    "未找到 event"

                                Toast.makeText(
                                    context,
                                    "没有找到 /dev/input/event*",
                                    Toast.LENGTH_LONG
                                ).show()

                            }

                            4 -> {

                                shizukuStatus =
                                    "无法读取 event"

                                Toast.makeText(
                                    context,
                                    "找到 event，但无法打开",
                                    Toast.LENGTH_LONG
                                ).show()

                            }

                            else -> {

                                shizukuStatus =
                                    "测试失败"

                                Toast.makeText(
                                    context,
                                    "Shizuku 输入测试失败：$result",
                                    Toast.LENGTH_LONG
                                ).show()

                            }

                        }

                    } catch (e: Exception) {

                        Log.e(
                            TAG_SHIZUKU,
                            "启动输入测试失败",
                            e
                        )

                        shizukuStatus =
                            "测试失败"

                        Toast.makeText(
                            context,
                            "Shizuku 输入测试启动失败",
                            Toast.LENGTH_LONG
                        ).show()

                    }

                }


                override fun onServiceDisconnected(
                    name: ComponentName?
                ) {

                    Log.i(
                        TAG_SHIZUKU,
                        "User Service 已断开"
                    )

                    shizukuInputTestService =
                        null

                    shizukuStatus =
                        getShizukuStatus()

                }

            }

        }


    /*
     * =========================================================
     * Shizuku 权限回调
     * =========================================================
     */

    DisposableEffect(
        lifecycleOwner,
        shizukuServiceConnection
    ) {

        val permissionListener =
            Shizuku.OnRequestPermissionResultListener {
                    requestCode,
                    grantResult ->


                if (
                    requestCode !=
                    SHIZUKU_PERMISSION_REQUEST_CODE
                ) {

                    return@OnRequestPermissionResultListener

                }


                if (
                    grantResult ==
                    PackageManager.PERMISSION_GRANTED
                ) {

                    Log.i(
                        TAG_SHIZUKU,
                        "Shizuku 权限授权成功"
                    )

                    shizukuStatus =
                        "已授权"


                    Toast.makeText(
                        context,
                        "Shizuku 授权成功",
                        Toast.LENGTH_SHORT
                    ).show()


                    bindShizukuService(
                        context = context,
                        args = shizukuUserServiceArgs,
                        connection = shizukuServiceConnection,
                        onError = {
                            shizukuStatus =
                                "连接失败"
                        }
                    )

                } else {

                    Log.w(
                        TAG_SHIZUKU,
                        "Shizuku 权限被拒绝"
                    )

                    shizukuStatus =
                        "未授权"


                    Toast.makeText(
                        context,
                        "Shizuku 权限未授权",
                        Toast.LENGTH_SHORT
                    ).show()

                }

            }


        Shizuku.addRequestPermissionResultListener(
            permissionListener
        )


        val observer =
            LifecycleEventObserver { _, event ->

                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {

                    overlayGranted =
                        Settings.canDrawOverlays(context)


                    rootStatus =
                        checkRootAccess()


                    shizukuStatus =
                        getShizukuStatus()

                }

            }


        lifecycleOwner.lifecycle.addObserver(
            observer
        )


        onDispose {

            lifecycleOwner.lifecycle.removeObserver(
                observer
            )


            Shizuku.removeRequestPermissionResultListener(
                permissionListener
            )


            try {

                shizukuInputTestService
                    ?.stopInputTest()

            } catch (e: Exception) {

                Log.w(
                    TAG_SHIZUKU,
                    "停止输入测试失败",
                    e
                )

            }


            try {

                Shizuku.unbindUserService(
                    shizukuUserServiceArgs,
                    shizukuServiceConnection,
                    true
                )

            } catch (e: Exception) {

                Log.w(
                    TAG_SHIZUKU,
                    "解除 User Service 失败",
                    e
                )

            }

        }

    }


    /*
     * =========================================================
     * 是否允许进入软件
     *
     * 当前版本仍然：
     *
     * 悬浮窗 + Root
     *
     * Shizuku 现在只是实验功能。
     * =========================================================
     */

    val canEnter =
        overlayGranted &&
                rootStatus == "已授权"


    /*
     * =========================================================
     * UI
     * =========================================================
     */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {


        Text(
            text = "KeyStrokes",
            style = MaterialTheme.typography.headlineLarge
        )


        Text(
            text = "请先完成授权，否则软件无法正常工作",
            style = MaterialTheme.typography.bodyLarge
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        /*
         * =====================================================
         * 悬浮窗
         * =====================================================
         */

        PermissionCard(
            title = "悬浮窗权限",

            description =
                "用于显示按键悬浮窗",

            status =
                if (overlayGranted) {
                    "已授权"
                } else {
                    "未授权"
                },

            required = true,

            buttonText =
                if (overlayGranted) {
                    "已授权"
                } else {
                    "授权"
                },

            onClick = {

                if (
                    !Settings.canDrawOverlays(
                        context
                    )
                ) {

                    val intent =
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                "package:${context.packageName}"
                            )
                        )

                    context.startActivity(
                        intent
                    )

                }

            }
        )


        /*
         * =====================================================
         * Root
         * =====================================================
         */

        PermissionCard(
            title = "Root 权限",

            description =
                "使用 Root 读取系统输入设备",

            status =
                rootStatus,

            required = true,

            buttonText =
                if (rootStatus == "已授权") {
                    "已授权"
                } else {
                    "请授予软件 Root 权限"
                },

            onClick = {

                rootStatus =
                    checkRootAccess()

            }
        )


        /*
         * =====================================================
         * Shizuku
         * =====================================================
         */

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp),

                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {


                Text(
                    text = "Shizuku",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )


                Text(
                    text =
                        "免 Root 输入读取测试",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )


                Text(
                    text =
                        "状态：$shizukuStatus",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )


                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = {

                            startShizukuInputTest(
                                context = context,

                                args =
                                    shizukuUserServiceArgs,

                                connection =
                                    shizukuServiceConnection,

                                onStatusChange = {
                                    shizukuStatus =
                                        it
                                }

                            )

                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text =
                                when {

                                    shizukuStatus ==
                                            "测试运行中" ->
                                        "测试运行中"

                                    shizukuStatus ==
                                            "已连接 UID 2000" ->
                                        "重新测试"

                                    else ->
                                        "开始 Shizuku 测试"

                                }
                        )

                    }

                }

            }

        }


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        /*
         * =====================================================
         * 进入软件
         * =====================================================
         */

        Button(
            onClick =
                onEnter,

            enabled =
                canEnter,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
        ) {

            Text(
                text = "进入软件"
            )

        }

    }

}


/*
 * =============================================================
 * 启动 Shizuku 输入测试
 * =============================================================
 */

private fun startShizukuInputTest(
    context: android.content.Context,
    args: Shizuku.UserServiceArgs,
    connection: ServiceConnection,
    onStatusChange: (String) -> Unit
) {

    /*
     * ---------------------------------------------------------
     * 1. Shizuku 是否运行
     * ---------------------------------------------------------
     */

    if (!Shizuku.pingBinder()) {

        Log.e(
            TAG_SHIZUKU,
            "Shizuku Binder 不可用"
        )

        onStatusChange(
            "Shizuku 未运行"
        )

        Toast.makeText(
            context,
            "请先启动 Shizuku",
            Toast.LENGTH_SHORT
        ).show()

        return

    }


    /*
     * ---------------------------------------------------------
     * 2. 检查权限
     * ---------------------------------------------------------
     */

    val permission =
        Shizuku.checkSelfPermission()


    if (
        permission !=
        PackageManager.PERMISSION_GRANTED
    ) {

        Log.i(
            TAG_SHIZUKU,
            "尚未获得 Shizuku 权限，请求授权"
        )

        onStatusChange(
            "等待授权"
        )


        try {

            Shizuku.requestPermission(
                SHIZUKU_PERMISSION_REQUEST_CODE
            )

        } catch (e: Exception) {

            Log.e(
                TAG_SHIZUKU,
                "请求 Shizuku 权限失败",
                e
            )

            onStatusChange(
                "授权失败"
            )

        }

        return

    }


    /*
     * ---------------------------------------------------------
     * 3. 已经有权限
     * ---------------------------------------------------------
     */

    Log.i(
        TAG_SHIZUKU,
        "Shizuku 权限已获得"
    )


    onStatusChange(
        "正在连接"
    )


    bindShizukuService(
        context = context,
        args = args,
        connection = connection,
        onError = {
            onStatusChange(
                "连接失败"
            )
        }
    )

}


/*
 * =============================================================
 * 绑定 Shizuku User Service
 * =============================================================
 */

private fun bindShizukuService(
    context: android.content.Context,
    args: Shizuku.UserServiceArgs,
    connection: ServiceConnection,
    onError: () -> Unit
) {

    try {

        Log.i(
            TAG_SHIZUKU,
            "开始绑定 Shizuku User Service"
        )


        Shizuku.bindUserService(
            args,
            connection
        )


    } catch (e: Exception) {

        Log.e(
            TAG_SHIZUKU,
            "bindUserService 失败",
            e
        )


        Toast.makeText(
            context,
            "Shizuku User Service 启动失败",
            Toast.LENGTH_LONG
        ).show()


        onError()

    }

}


/*
 * =============================================================
 * Shizuku 当前状态
 * =============================================================
 */

private fun getShizukuStatus(): String {

    return try {

        if (!Shizuku.pingBinder()) {

            "未运行"

        } else {

            when (
                Shizuku.checkSelfPermission()
            ) {

                PackageManager.PERMISSION_GRANTED ->
                    "已授权"

                else ->
                    "未授权"

            }

        }

    } catch (
        _: Exception
    ) {

        "未授权"

    }

}


/*
 * =============================================================
 * Root 权限检测
 * =============================================================
 *
 * 原理：
 *
 *     su -c id
 *
 * 正常 Root 环境下通常会返回：
 *
 *     uid=0(root) gid=0(root) ...
 *
 * 只有确认 UID = 0 才认为 Root 已授权。
 *
 * =============================================================
 */

private fun checkRootAccess(): String {

    return try {

        val process =
            Runtime.getRuntime().exec(
                arrayOf(
                    "su",
                    "-c",
                    "id"
                )
            )


        val output =
            BufferedReader(
                InputStreamReader(
                    process.inputStream
                )
            ).use {
                it.readText()
            }


        val error =
            BufferedReader(
                InputStreamReader(
                    process.errorStream
                )
            ).use {
                it.readText()
            }


        val exitCode =
            process.waitFor()


        /*
         * 必须同时满足：
         *
         * 1. su 执行成功
         * 2. 输出中明确存在 uid=0
         */

        if (
            exitCode == 0 &&
            Regex("""uid=0(?:\(|\s|$)""")
                .containsMatchIn(output)
        ) {

            "已授权"

        } else {

            "未授权"

        }

    } catch (
        _: Exception
    ) {

        "未授权"

    }

}


/*
 * =============================================================
 * 权限卡片
 * =============================================================
 */

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    status: String,
    required: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = title,

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )


                Text(
                    text =
                        if (required) {
                            "必须"
                        } else {
                            "可选"
                        },

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

            }


            Text(
                text = description,

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )


            Text(
                text = "状态：$status",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )


            Button(
                onClick =
                    onClick,

                enabled =
                    status != "已授权",

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text = buttonText
                )

            }

        }

    }

}