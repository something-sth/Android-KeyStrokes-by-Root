package com.something.keystrokes

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

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

import rikka.shizuku.Shizuku

import java.io.BufferedReader
import java.io.InputStreamReader


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


    var shizukuStatus by remember {
        mutableStateOf(
            getShizukuStatus()
        )
    }


    /*
     * =========================================================
     * Shizuku 权限回调
     *
     * 这里只处理“是否授权”
     * 不再启动 User Service
     * =========================================================
     */

    DisposableEffect(lifecycleOwner) {

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

                    shizukuStatus = "已授权"

                    Toast.makeText(
                        context,
                        "Shizuku 授权成功",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    shizukuStatus = "未授权"

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


        /*
         * =====================================================
         * 返回 SetupScreen 时重新检查所有权限
         * =====================================================
         */

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
        }
    }


    /*
     * =========================================================
     * 是否允许进入软件
     *
     * 三种情况：
     *
     * 1. Root + 悬浮窗
     * 2. Shizuku + 悬浮窗
     *
     * 任意一种满足即可进入。
     * =========================================================
     */

    val rootAvailable =
        rootStatus == "已授权"


    val shizukuAvailable =
        shizukuStatus == "已授权"


    val canEnter =
        overlayGranted &&
                (
                        rootAvailable ||
                                shizukuAvailable
                        )


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
            style =
                MaterialTheme
                    .typography
                    .headlineLarge
        )


        Text(
            text =
                "请先完成授权，否则软件无法正常工作",

            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        /*
         * =====================================================
         * 悬浮窗权限
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
         * Root 权限
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

                if (rootStatus != "已授权") {

                    Toast.makeText(
                        context,
                        "请在 Root 管理器中授予 KeyStrokes Root 权限",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )


        /*
         * =====================================================
         * Shizuku 权限
         *
         * 这里只负责授权。
         *
         * 不启动 User Service。
         * 不读取输入设备。
         * 不扫描 event。
         * =====================================================
         */

        PermissionCard(
            title = "Shizuku",

            description =
                "使用 Shizuku 作为 Root 的替代授权方式",

            status =
                shizukuStatus,

            required = true,

            buttonText =
                when (shizukuStatus) {

                    "已授权" ->
                        "已授权"

                    "未运行" ->
                        "请先启动 Shizuku"

                    else ->
                        "授权 Shizuku"
                },

            onClick = {

                requestShizukuPermission(
                    context = context,
                    onStatusChange = {
                        shizukuStatus = it
                    }
                )
            }
        )


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
            onClick = onEnter,

            enabled = canEnter,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
        ) {

            Text(
                text =
                    if (canEnter) {
                        "进入软件"
                    } else {
                        "请完成授权"
                    }
            )
        }
    }
}


/*
 * =============================================================
 * 请求 Shizuku 权限
 * =============================================================
 */

private fun requestShizukuPermission(
    context: android.content.Context,
    onStatusChange: (String) -> Unit
) {

    /*
     * ---------------------------------------------------------
     * Shizuku 是否运行
     * ---------------------------------------------------------
     */

    if (!Shizuku.pingBinder()) {

        onStatusChange(
            "未运行"
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
     * 检查当前权限
     * ---------------------------------------------------------
     */

    if (
        Shizuku.checkSelfPermission() ==
        PackageManager.PERMISSION_GRANTED
    ) {

        onStatusChange(
            "已授权"
        )

        Toast.makeText(
            context,
            "Shizuku 已授权",
            Toast.LENGTH_SHORT
        ).show()

        return
    }


    /*
     * ---------------------------------------------------------
     * 请求 Shizuku 授权
     * ---------------------------------------------------------
     */

    onStatusChange(
        "等待授权"
    )


    try {

        Shizuku.requestPermission(
            SHIZUKU_PERMISSION_REQUEST_CODE
        )

    } catch (e: Exception) {

        onStatusChange(
            "未授权"
        )

        Toast.makeText(
            context,
            "请求 Shizuku 权限失败",
            Toast.LENGTH_SHORT
        ).show()
    }
}


/*
 * =============================================================
 * 获取 Shizuku 当前状态
 * =============================================================
 */

private fun getShizukuStatus(): String {

    return try {

        /*
         * Shizuku 没有运行
         */

        if (!Shizuku.pingBinder()) {

            "未运行"

        } else {

            /*
             * Shizuku 正在运行
             */

            if (
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
            ) {

                "已授权"

            } else {

                "未授权"
            }
        }

    } catch (e: Exception) {

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
 * 正常 Root 环境下：
 *
 *     uid=0(root)
 *
 * 只有明确检测到 UID 0 才认为 Root 已授权。
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


        /*
         * 错误输出读取掉，避免进程异常阻塞。
         */

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
         * 2. uid = 0
         */

        if (
            exitCode == 0 &&
            Regex(
                """uid=0(?:\(|\s|$)"""
            ).containsMatchIn(output)
        ) {

            "已授权"

        } else {

            "未授权"
        }

    } catch (e: Exception) {

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
                onClick = onClick,

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