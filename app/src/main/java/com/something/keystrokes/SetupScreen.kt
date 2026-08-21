package com.something.keystrokes

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.withStyle

import androidx.compose.foundation.text.ClickableText

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilledTonalButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import kotlinx.coroutines.delay

import rikka.shizuku.Shizuku

import java.io.BufferedReader
import java.io.InputStreamReader


private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
private const val ANNOUNCEMENT_VERSION = "1.5"
private const val ANNOUNCEMENT_KEY = "announcement_version"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onEnter: () -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    /*
     * =========================================================
     * 公告状态
     * =========================================================
     */

    var showAnnouncement by remember {
        mutableStateOf(false)
    }

    var announcementCountdown by remember {
        mutableStateOf(5)
    }


    /*
     * =========================================================
     * 检查是否显示公告
     * =========================================================
     */

    LaunchedEffect(Unit) {

        val prefs =
            context.getSharedPreferences(
                "app_settings",
                Context.MODE_PRIVATE
            )

        val lastVersion =
            prefs.getString(
                ANNOUNCEMENT_KEY,
                ""
            )

        if (
            lastVersion != ANNOUNCEMENT_VERSION
        ) {

            showAnnouncement = true

            announcementCountdown = 5

            while (
                announcementCountdown > 0
            ) {

                delay(1000)

                announcementCountdown--
            }
        }
    }


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

    var batteryOptimizationIgnored by remember {
        mutableStateOf(
            isIgnoringBatteryOptimization(context)
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
     * 这里只处理"是否授权"
     * 不再启动 User Service
     * =========================================================
     */

    DisposableEffect(lifecycleOwner) {

        val permissionListener =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->

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

                    batteryOptimizationIgnored =
                        isIgnoringBatteryOptimization(context)

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
     * 1. Root + 悬浮窗 + 电池优化
     * 2. Shizuku + 悬浮窗 + 电池优化
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
                batteryOptimizationIgnored &&
                (
                        rootAvailable ||
                                shizukuAvailable
                        )


    /*
     * =========================================================
     * UI
     * =========================================================
     */

    Scaffold(
        topBar = {

            TopAppBar(
                title = {

                    Text(
                        "KeyStrokes V${ANNOUNCEMENT_VERSION}"
                    )
                }
            )
        }

    ) { padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {

            Text(
                text =
                    "请先完成授权，否则软件无法正常工作\n悬浮窗与忽略电池优化必须授权，Root 与 Shizuku 授权二选一即可",

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )


            /*
             * =====================================================
             * 第一行：悬浮窗 + 电池优化
             * =====================================================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    PermissionCard(
                        title = "悬浮窗",

                        description =
                            "显示按键悬浮窗",

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
                }

                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    PermissionCard(
                        title = "电池优化",

                        description =
                            "后台持续运行",

                        status =
                            if (batteryOptimizationIgnored) {
                                "已授权"
                            } else {
                                "未授权"
                            },

                        required = true,

                        buttonText =
                            if (batteryOptimizationIgnored) {
                                "已授权"
                            } else {
                                "授权"
                            },

                        onClick = {

                            try {

                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse(
                                        "package:${context.packageName}"
                                    )
                                )

                                context.startActivity(intent)

                            } catch (e: Exception) {

                                Toast.makeText(
                                    context,
                                    "请在系统设置中手动开启忽略电池优化",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }


            /*
             * =====================================================
             * 第二行：Root + Shizuku
             * =====================================================
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    PermissionCard(
                        title = "Root",

                        description =
                            "Root读取输入设备",

                        status =
                            rootStatus,

                        required = true,

                        buttonText =
                            if (rootStatus == "已授权") {
                                "已授权"
                            } else {
                                "授权"
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
                }

                Box(
                    modifier = Modifier.weight(1f)
                ) {

                    PermissionCard(
                        title = "Shizuku",

                        description =
                            "Root替代方案",

                        status =
                            shizukuStatus,

                        required = true,

                        buttonText =
                            when (shizukuStatus) {

                                "已授权" ->
                                    "已授权"

                                "未运行" ->
                                    "授权"

                                else ->
                                    "授权"
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
                onClick = onEnter,

                enabled = canEnter,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                shape =
                    MaterialTheme.shapes.large

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
     * =========================================================
     * 公告弹窗
     * =========================================================
     */

    if (showAnnouncement) {

        AlertDialog(
            onDismissRequest = {},

            title = {
                Text(
                    text =
                        "KeyStrokes V${ANNOUNCEMENT_VERSION} 更新公告"
                )
            },

            text = {

                Column(
                    modifier =
                        Modifier
                            .verticalScroll(
                                rememberScrollState()
                            )
                            .padding(8.dp)
                ) {

                    val annotatedText = buildAnnotatedString {

                        append(
                            "本版本更新：\n\n" +
                                    "• 正式加入鼠标监听，删除 SHIFT 按键监听与显示\n" +
                                    "• 提升了 Shizuku 模式的稳定性\n" +
                                    "• 修复了一些零散的 bug\n\n" +
                                    "使用说明：\n" +
                                    "最好先连接设备再进入软件，否则可能会出现一些不稳定 bug\n" +
                                    "Root 模式稳定性最强，不容易报错，Shizuku 模式经过持续优化稳定性也提升了许多\n" +
                                    "自动选择逻辑会固定排除 event0 到 event6，若发现监听没反应，请手动选择 event6 等更靠前的设备\n\n" +
                                    "若发现软件存在 bug 请第一时间联系我们，我们会尽快处理\n\n"
                        )

                        pushStringAnnotation(
                            tag = "github",
                            annotation = "https://github.com/something-sth/Sth-Android-KeyStrokes"
                        )

                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(
                                "GitHub开源仓库：\n" +
                                        "https://github.com/something-sth/Sth-Android-KeyStrokes"
                            )
                        }

                        pop()

                        append("\n\n")

                        pushStringAnnotation(
                            tag = "qq",
                            annotation = "https://qun.qq.com/universal-share/share?ac=1&authKey=TAcvzxnpxvtKzwgk%2Ba%2Br7WtZ5Mj63H3jNtzCLY9oy352oBj2mu5EFu2UYrGG2MbR&busi_data=eyJncm91cENvZGUiOiI5MDg4ODc0NzQiLCJ0b2tlbiI6IlVXOWloN3l2eGpQVksrTSsySnZiWi84MXFsN2xhMXVxVUZ4K0xLd3hnRU5yanRpd29rMzB6MmtIeER2L1lwZk4iLCJ1aW4iOiIyNzUxODA5MjM3In0%3D&data=Xt1S3wTDGgqTCNJq8LaH9gg5UE1zg87Uw3a0VawgciuMnwuReiG1Hx-z_UX7X9i2MFP4w7OyNlwf2rVKURr7Zw&svctype=4&tempid=h5_group_info"
                        )

                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(
                                "QQ交流反馈群：\n" +
                                        "908887474"
                            )
                        }

                        pop()

                        append(
                            "\n\n" +
                                    "本项目免费开源。\n" +
                                    "请勿进行未经授权的收费售卖。"
                        )
                    }

                    ClickableText(
                        text = annotatedText,
                        onClick = { offset ->

                            annotatedText.getStringAnnotations(
                                tag = "github",
                                start = offset,
                                end = offset
                            ).firstOrNull()?.let {

                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(it.item)
                                )

                                context.startActivity(intent)
                            }

                            annotatedText.getStringAnnotations(
                                tag = "qq",
                                start = offset,
                                end = offset
                            ).firstOrNull()?.let {

                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(it.item)
                                )

                                context.startActivity(intent)
                            }
                        }
                    )
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        if (
                            announcementCountdown == 0
                        ) {

                            context
                                .getSharedPreferences(
                                    "app_settings",
                                    Context.MODE_PRIVATE
                                )
                                .edit()
                                .putString(
                                    ANNOUNCEMENT_KEY,
                                    ANNOUNCEMENT_VERSION
                                )
                                .apply()

                            showAnnouncement = false
                        }
                    },

                    enabled =
                        announcementCountdown == 0
                ) {

                    Text(
                        if (
                            announcementCountdown > 0
                        ) {

                            "确定(${announcementCountdown})"

                        } else {

                            "确定"
                        }
                    )
                }
            }
        )
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
 * 检测是否忽略电池优化
 * =============================================================
 */

private fun isIgnoringBatteryOptimization(
    context: Context
): Boolean {

    return try {

        val powerManager =
            context.getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

        powerManager.isIgnoringBatteryOptimizations(
            context.packageName
        )

    } catch (e: Exception) {

        false
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
            Modifier.fillMaxWidth(),

        shape =
            MaterialTheme.shapes.large
    ) {

        Column(
            modifier =
                Modifier.padding(12.dp),

            verticalArrangement =
                Arrangement.spacedBy(4.dp)
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
                            .titleSmall
                )


            }


            Text(
                text = description,

                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )


            Text(
                text = "状态：$status",

                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )


            if (status == "已授权") {

                FilledTonalButton(
                    onClick = {},

                    enabled = false,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                ) {

                    Text(
                        text = buttonText
                    )
                }

            } else {

                Button(
                    onClick = onClick,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                ) {

                    Text(
                        text = buttonText
                    )
                }
            }
        }
    }
}