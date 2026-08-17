package com.something.keystrokes

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

import java.io.BufferedReader
import java.io.InputStreamReader


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
     * Shizuku 暂时只做占位。
     *
     * 现在不会真正检测 Shizuku。
     * 点击后只提示「暂未开放」。
     */

    val shizukuStatus = "未授权"


    /*
     * =========================================================
     * 当用户从系统设置返回软件时重新检查权限
     * =========================================================
     */

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {

                overlayGranted =
                    Settings.canDrawOverlays(context)

                rootStatus =
                    checkRootAccess()

            }

        }


        lifecycleOwner.lifecycle.addObserver(observer)


        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    /*
     * =========================================================
     * 是否允许进入软件
     *
     * 当前版本：
     *
     * 悬浮窗 + Root
     *
     * 后续接入 Shizuku 后再改成：
     *
     * 悬浮窗 + (Root || Shizuku)
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

                if (!Settings.canDrawOverlays(context)) {

                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse(
                            "package:${context.packageName}"
                        )
                    )

                    context.startActivity(intent)

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
                    text = "免 Root 方案(暂未开放)",

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

                            Toast.makeText(
                                context,
                                "暂未开放",
                                Toast.LENGTH_SHORT
                            ).show()

                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text =
                                "Shizuku：$shizukuStatus"
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
 * Root 权限检测
 * =============================================================
 *
 * 原理：
 *
 * 执行：
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