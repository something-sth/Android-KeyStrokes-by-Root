package com.something.keystrokes

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info

import com.something.keystrokes.input.InputDeviceScanner
import com.something.keystrokes.input.KeyEventData
import com.something.keystrokes.input.KeyStateManager
import com.something.keystrokes.input.OverlayState
import com.something.keystrokes.input.RootInputReader

import com.something.keystrokes.service.OverlayService

import com.something.keystrokes.ui.KeyButton
import com.something.keystrokes.ui.theme.KeyStrokesTheme

import java.util.concurrent.Executors
import java.util.concurrent.Future

import rikka.shizuku.Shizuku


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KeyStrokesTheme {

                var showSetupScreen by remember {
                    mutableStateOf(true)
                }

                if (showSetupScreen) {

                    SetupScreen(
                        onEnter = {
                            showSetupScreen = false
                        }
                    )

                } else {

                    KeystrokesTestScreen()

                }
            }
        }
    }
}

/*
 * ============================================================
 * 模式枚举
 * ============================================================
 */

private enum class InputMode {
    ROOT,
    SHIZUKU
}

/*
 * ============================================================
 * 自动选择策略
 *
 * 统一使用 event >= 7 作为外部输入设备的判断标准。
 * ============================================================
 */
private const val AUTO_SELECT_START_EVENT = 7

/*
 * ============================================================
 * Root 权限检测
 * ============================================================
 */

private fun checkRootAuthorized(): Boolean {

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
            process.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        process.errorStream
            .bufferedReader()
            .use {
                it.readText()
            }

        val exitCode =
            process.waitFor()

        exitCode == 0 &&
                Regex(
                    """uid=0(?:\(|\s|$)"""
                ).containsMatchIn(output)

    } catch (
        _: Exception
    ) {

        false

    }
}


/*
 * ============================================================
 * Shizuku 权限检测
 * ============================================================
 */

private fun checkShizukuAuthorized(): Boolean {

    return try {

        Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED

    } catch (
        _: Exception
    ) {

        false

    }
}

/*
 * ============================================================
 * 获取 event 编号（工具函数）
 * ============================================================
 */

private fun getEventNumber(device: InputDeviceScanner.InputDeviceInfo): Int? {
    return device.eventPath
        .substringAfterLast("event")
        .toIntOrNull()
}

/*
 * ============================================================
 * 页面
 * ============================================================
 */

private enum class AppPage {

    MAIN,

    CONFIG,

    ABOUT

}


/*
 * ============================================================
 * 主界面
 * ============================================================
 */

@Composable
private fun KeystrokesTestScreen() {

    val context = LocalContext.current

    var executor = remember {
        Executors.newSingleThreadExecutor()
    }

    val keyStateManager = remember {
        KeyStateManager()
    }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    fun ensureExecutor() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newSingleThreadExecutor()
        }
    }


    /*
     * 当前页面
     */

    var currentPage by remember {
        mutableStateOf(AppPage.MAIN)
    }


    /*
     * 当前状态
     */

    var status by remember {
        mutableStateOf("已停止")
    }


    var isListening by remember {
        mutableStateOf(false)
    }


    /*
     * ============================================================
     * 进入主页时自动判断默认模式
     *
     * Root 优先，Root 未授权则尝试 Shizuku
     * ============================================================
     */
    var selectedMode by remember {
        mutableStateOf(
            when {
                checkRootAuthorized() -> InputMode.ROOT
                checkShizukuAuthorized() -> InputMode.SHIZUKU
                else -> InputMode.ROOT
            }
        )
    }


    var devices by remember {
        mutableStateOf(
            emptyList<InputDeviceScanner.InputDeviceInfo>()
        )
    }


    var events by remember {
        mutableStateOf(
            emptyList<String>()
        )
    }


    var pressedKeys by remember {
        mutableStateOf(
            emptySet<Int>()
        )
    }


    val shizukuListener =
        remember(keyStateManager) {

            object : IShizukuInputListener.Stub() {

                override fun onKeyEvent(
                    type: Int,
                    code: Int,
                    value: Int,
                    keyName: String,
                    down: Boolean
                ) {

                    mainHandler.post {

                        val event =
                            KeyEventData(
                                timeMillis = System.currentTimeMillis(),
                                type = type,
                                code = code,
                                value = value,
                                keyName = keyName,
                                down = down
                            )

                        keyStateManager.update(event)

                        if ((event.code == KeyStateManager.KEY_LMB ||
                                    event.code == KeyStateManager.KEY_RMB) && event.value == 1) {
                            OverlayState.recordMouseClick(event.code)
                        }

                        pressedKeys =
                            keyStateManager.getPressedKeys()

                        OverlayState.update(
                            pressedKeys
                        )

                        val action =
                            when (value) {

                                0 -> "UP"

                                1 -> "DOWN"

                                2 -> "REPEAT"

                                else -> value.toString()
                            }

                        val line =
                            "${event.timeMillis}  ${event.keyName}  $action"

                        events =
                            (
                                    listOf(line) + events
                                    ).take(80)
                    }
                }
            }
        }


    var readerFuture by remember {
        mutableStateOf<Future<*>?>(null)
    }


    /*
     * ============================================================
     * 一、把 rootInputReader 改成 Map 结构
     * ============================================================
     */

    var rootInputReaders by remember {
        mutableStateOf<Map<String, RootInputReader>>(emptyMap())
    }


    /*
     * ============================================================
     * 二、增加设备选择状态
     * ============================================================
     */

    var selectedDevicePaths by remember {
        mutableStateOf(emptySet<String>())
    }

    var autoSelectedDevicePaths by remember {
        mutableStateOf(emptySet<String>())
    }


    /*
     * ============================================================
     * Root 专用扫描函数
     *
     * 只负责 Root 模式扫描设备，保留自动选择。
     * 自动选择策略：event >= 7
     * ============================================================
     */

    fun scanInputDevices(): Boolean {

        return try {

            val result =
                InputDeviceScanner.scan()

            /*
             * 更新设备列表
             */
            devices =
                result.devices

            /*
             * ========================================================
             * 已经存在设备选择
             *
             * 说明用户已经手动选择过设备。
             *
             * 无论自动扫描结果是什么，
             * 都绝对不能覆盖用户选择。
             * ========================================================
             */

            if (selectedDevicePaths.isNotEmpty()) {

                return true
            }

            /*
             * ========================================================
             * 当前没有设备选择
             *
             * 尝试自动选择 event >= 7 的设备。
             * ========================================================
             */

            val autoDevices =
                result.devices.filter { device ->
                    getEventNumber(device)?.let { it >= AUTO_SELECT_START_EVENT } ?: false
                }

            if (autoDevices.isNotEmpty()) {

                selectedDevicePaths =
                    autoDevices
                        .map {
                            it.eventPath
                        }
                        .toSet()

                autoSelectedDevicePaths =
                    selectedDevicePaths

                return true
            }

            /*
             * ========================================================
             * 自动识别失败
             * ========================================================
             */

            status =
                "未自动识别到设备，请手动选择"

            false

        } catch (e: Exception) {

            status =
                "扫描输入设备失败：${
                    e.message
                        ?: e.javaClass.simpleName
                }"

            false
        }
    }


    /*
     * ============================================================
     * Shizuku 自动选择函数
     * ============================================================
     */

    fun applyShizukuAutoSelection(
        scannedDevices: List<InputDeviceScanner.InputDeviceInfo>
    ) {

        /*
         * 已经存在自动选择
         * 不重复执行
         */
        if (
            autoSelectedDevicePaths.isNotEmpty()
        ) {
            return
        }

        val autoDevices =
            scannedDevices.filter { device ->
                getEventNumber(device)?.let { it >= AUTO_SELECT_START_EVENT } ?: false
            }

        autoSelectedDevicePaths =
            autoDevices
                .map {
                    it.eventPath
                }
                .toSet()

        /*
         * 只有当前没有选择时才填入
         */
        if (
            selectedDevicePaths.isEmpty()
        ) {

            selectedDevicePaths =
                autoSelectedDevicePaths
        }

        android.util.Log.i(
            "KeyStrokes-Shizuku",
            "自动选择设备：${selectedDevicePaths.joinToString()}"
        )
    }


    /*
     * ============================================================
     * 设备点击切换逻辑
     * ============================================================
     */

    fun toggleDevice(eventPath: String) {

        if (isListening) {
            android.widget.Toast.makeText(
                context,
                "请先停止监听再修改设备选择",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val newSelection =
            selectedDevicePaths.toMutableSet()

        if (eventPath in newSelection) {

            /*
             * 当前已选，尝试取消
             */

            if (newSelection.size == 1) {

                android.widget.Toast.makeText(
                    context,
                    "至少选择一个设备",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                return
            }

            newSelection.remove(eventPath)

        } else {

            /*
             * 当前未选，加入手动选择
             */

            newSelection.add(eventPath)
        }

        selectedDevicePaths = newSelection
    }


    /*
     * ========================================================
     * 生命周期
     * ========================================================
     */

    DisposableEffect(Unit) {

        onDispose {

            /*
             * 三、生命周期里的 Root 清理 - 改成遍历 Map
             */

            rootInputReaders.values.forEach {
                it.stop()
            }
            rootInputReaders = emptyMap()

            // Shizuku 清理
            try {
                ShizukuUserServiceManager.getService()
                    ?.apply {
                        setListener(null)
                        stop()
                    }
            } catch (_: Exception) {
            }
            ShizukuUserServiceManager.stop()

            readerFuture?.cancel(true)
            readerFuture = null

            executor.shutdownNow()

            keyStateManager.clear()

        }

    }


    /*
     * ========================================================
     * 停止监听
     * ========================================================
     */

    fun stopReader() {

        /*
         * 四、stopReader() 里的 Root 清理 - 改成遍历 Map
         */

        rootInputReaders.values.forEach {
            it.stop()
        }
        rootInputReaders = emptyMap()

        /*
         * ============================================================
         * Shizuku
         * ============================================================
         */

        if (selectedMode == InputMode.SHIZUKU) {

            try {

                ShizukuUserServiceManager
                    .getService()
                    ?.apply {

                        android.util.Log.i(
                            "KeyStrokes-Shizuku",
                            "停止输入监听，但保留 UserService"
                        )

                        setListener(null)
                        stop()

                    }

            } catch (e: Exception) {

                android.util.Log.e(
                    "KeyStrokes-Shizuku",
                    "停止 Shizuku 输入监听失败",
                    e
                )
            }

            // 注意：这里不再调用 ShizukuUserServiceManager.stop()
            // 只停止监听，不销毁 UserService
        }

        /*
         * ============================================================
         * 扫描任务
         * ============================================================
         */

        readerFuture?.cancel(true)
        readerFuture = null

        /*
         * ============================================================
         * 按键状态
         * ============================================================
         */

        keyStateManager.clear()
        pressedKeys = emptySet()

        /*
         * ============================================================
         * UI 状态
         * ============================================================
         */

        status = "已停止"
        isListening = false
    }


    /*
     * ========================================================
     * 开始监听
     * ========================================================
     */

    fun startReader() {

        if (isListening) {
            status = "已经正在监听"
            return
        }

        when (selectedMode) {

            /*
             * ========================================================
             * ROOT
             * ========================================================
             */

            InputMode.ROOT -> {

                status = "正在扫描输入设备..."

                readerFuture =
                    executor.submit {

                        try {

                            /*
                             * ========================================================
                             * 第二处：统一扫描设备
                             *
                             * Root / Shizuku 都使用同一套设备选择逻辑
                             * ========================================================
                             */

                            if (!scanInputDevices()) {

                                isListening = false
                                readerFuture = null

                                return@submit
                            }

                            /*
                             * 当前仍然没有设备
                             */

                            if (selectedDevicePaths.isEmpty()) {

                                status =
                                    "请先选择要监听的输入设备"

                                isListening = false
                                readerFuture = null

                                return@submit
                            }

                            /*
                             * ============================================================
                             * 后面继续保持原来的 RootInputReader 创建逻辑
                             * ============================================================
                             */

                            /*
                             * ============================================================
                             * 创建多个 Reader
                             * ============================================================
                             */

                            val newReaders = mutableMapOf<String, RootInputReader>()

                            /*
                             * onEvent 处理逻辑 - 所有 Reader 共用
                             */

                            val onEvent: (KeyEventData) -> Unit = { event ->

                                /*
                                 * 鼠标事件调试日志
                                 */
                                if (event.code == RootInputReader.BTN_LEFT ||
                                    event.code == RootInputReader.BTN_RIGHT) {
                                    Log.d(
                                        "Mouse",
                                        "${event.keyName} ${if (event.down) "DOWN" else "UP"}"
                                    )
                                }

                                /*
                                 * 更新按键状态
                                 */

                                keyStateManager.update(event)

                                if ((event.code == KeyStateManager.KEY_LMB ||
                                            event.code == KeyStateManager.KEY_RMB) && event.value == 1) {
                                    OverlayState.recordMouseClick(event.code)
                                }

                                pressedKeys =
                                    keyStateManager.getPressedKeys()

                                /*
                                 * 更新悬浮窗
                                 */

                                OverlayState.update(
                                    pressedKeys
                                )

                                /*
                                 * 记录事件
                                 */

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

                                val line =
                                    "${event.timeMillis}  ${event.keyName}  $action"

                                events =
                                    (
                                            listOf(line) +
                                                    events
                                            ).take(80)
                            }

                            /*
                             * onError 处理逻辑 - 所有 Reader 共用
                             */

                            val onError: (String) -> Unit = { error ->

                                status =
                                    "Root 错误：$error"

                                isListening =
                                    false
                            }

                            selectedDevicePaths.forEach { eventPath ->

                                val reader =
                                    RootInputReader(
                                        eventPath,
                                        onEvent = onEvent,
                                        onError = onError
                                    )

                                newReaders[eventPath] = reader
                            }

                            rootInputReaders = newReaders

                            newReaders.values.forEach {
                                it.start()
                            }

                            status =
                                "Root 正常，正在监听 ${selectedDevicePaths.size} 个设备"

                            isListening = true

                        } catch (e: Exception) {

                            rootInputReaders = emptyMap()

                            isListening = false

                            status =
                                "Root 监听失败：${e.message ?: e.javaClass.simpleName}"
                        }
                    }
            }

            /*
             * ========================================================
             * SHIZUKU
             * ========================================================
             */

            InputMode.SHIZUKU -> {

                android.util.Log.i(
                    "KeyStrokes-Shizuku",
                    "进入 Shizuku 开始监听流程"
                )

                status =
                    "正在启动 Shizuku 服务..."

                if (
                    !ShizukuUserServiceManager
                        .isShizukuRunning()
                ) {

                    android.util.Log.e(
                        "KeyStrokes-Shizuku",
                        "Shizuku 未运行"
                    )

                    status =
                        "Shizuku 未运行"

                    isListening = false

                    return
                }

                if (
                    !ShizukuUserServiceManager
                        .hasPermission()
                ) {

                    android.util.Log.e(
                        "KeyStrokes-Shizuku",
                        "Shizuku 未授权"
                    )

                    status =
                        "Shizuku 未授权"

                    isListening = false

                    return
                }

                android.util.Log.i(
                    "KeyStrokes-Shizuku",
                    "Shizuku 正常，准备启动 UserService"
                )

                ShizukuUserServiceManager.start(
                    onConnected = { binder ->

                        android.util.Log.i(
                            "KeyStrokes-Shizuku",
                            "Shizuku UserService 已连接"
                        )

                        ensureExecutor()

                        executor.submit {

                            try {

                                val service =
                                    IShizukuInputService
                                        .Stub
                                        .asInterface(binder)

                                android.util.Log.i(
                                    "KeyStrokes-Shizuku",
                                    "开始请求 UserService 扫描输入设备"
                                )

                                val rawDevices =
                                    service.scanDevices()

                                android.util.Log.i(
                                    "KeyStrokes-Shizuku",
                                    "UserService 扫描完成，共发现 ${rawDevices.size} 个设备"
                                )

                                rawDevices.forEach { device ->

                                    android.util.Log.i(
                                        "KeyStrokes-Shizuku",
                                        "Shizuku 返回 event：[$device]"
                                    )
                                }

                                /*
                                 * ============================================================
                                 * Shizuku 转换 event 列表为 InputDeviceInfo
                                 *
                                 * 对 event7 及以上设备添加（自动选择）标记
                                 * ============================================================
                                 */

                                val scannedDevices =
                                    rawDevices.map { path ->

                                        val eventPath =
                                            path.trim()

                                        val eventName =
                                            eventPath.substringAfterLast("/")

                                        val eventNumber =
                                            eventName
                                                .removePrefix("event")
                                                .toIntOrNull()

                                        InputDeviceScanner.InputDeviceInfo(

                                            eventName =
                                                if (
                                                    eventNumber != null &&
                                                    eventNumber >= AUTO_SELECT_START_EVENT
                                                ) {

                                                    "$eventName（自动选择）"

                                                } else {

                                                    eventName
                                                },

                                            eventPath =
                                                eventPath,

                                            deviceName =
                                                ""
                                        )
                                    }
                                        // 按 event 编号数字排序
                                        .sortedBy {
                                            it.eventPath
                                                .substringAfter("event")
                                                .toIntOrNull() ?: Int.MAX_VALUE
                                        }

                                android.util.Log.i(
                                    "KeyStrokes-Shizuku",
                                    "转换后得到 ${scannedDevices.size} 个 event"
                                )

                                mainHandler.post {

                                    devices = scannedDevices

                                    /*
                                     * Shizuku 自动选择
                                     *
                                     * 默认选择 event7 及以上设备
                                     */
                                    applyShizukuAutoSelection(
                                        scannedDevices
                                    )

                                    if (selectedDevicePaths.isEmpty()) {

                                        status =
                                            if (scannedDevices.isNotEmpty()) {

                                                "Shizuku 服务正常，请手动选择输入设备"

                                            } else {

                                                "Shizuku 服务正常，但没有找到输入设备"
                                            }

                                        isListening = false

                                        return@post
                                    }

                                    status =
                                        "Shizuku 服务正常，使用当前选择的 ${selectedDevicePaths.size} 个设备"

                                    try {

                                        /*
                                         * 先注册 Listener
                                         */
                                        service.setListener(
                                            shizukuListener
                                        )

                                        android.util.Log.i(
                                            "KeyStrokes-Shizuku",
                                            "已注册 Shizuku 输入 Listener"
                                        )

                                        /*
                                         * 再启动真正的输入读取
                                         */
                                        val result =
                                            service.start(
                                                selectedDevicePaths.toTypedArray()
                                            )

                                        android.util.Log.i(
                                            "KeyStrokes-Shizuku",
                                            "UserService.start() 返回：$result"
                                        )

                                        if (result == 0) {

                                            status =
                                                "Shizuku 正常，正在监听 ${selectedDevicePaths.size} 个设备"

                                            isListening = true

                                        } else {

                                            status =
                                                "Shizuku 监听启动失败，错误码：$result"

                                            isListening = false
                                        }

                                    } catch (e: Exception) {

                                        android.util.Log.e(
                                            "KeyStrokes-Shizuku",
                                            "启动 Shizuku 输入监听失败",
                                            e
                                        )

                                        status =
                                            "Shizuku 监听启动失败：${
                                                e.message
                                                    ?: e.javaClass.simpleName
                                            }"

                                        isListening = false
                                    }
                                }

                            } catch (e: Exception) {

                                android.util.Log.e(
                                    "KeyStrokes-Shizuku",
                                    "UserService 扫描设备失败",
                                    e
                                )

                                mainHandler.post {

                                    status =
                                        "Shizuku 扫描失败：${
                                            e.message
                                                ?: e.javaClass.simpleName
                                        }"

                                    isListening = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }


    /*
     * ========================================================
     * 页面切换（带底部导航栏 + AnimatedContent）
     * ========================================================
     */

    Scaffold(
        bottomBar = {

            NavigationBar {

                NavigationBarItem(
                    selected =
                        currentPage == AppPage.MAIN,

                    onClick = {

                        currentPage =
                            AppPage.MAIN
                    },

                    icon = {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = "主页"
                        )
                    },

                    label = {

                        Text("主页")
                    }
                )

                NavigationBarItem(
                    selected =
                        currentPage == AppPage.CONFIG,

                    onClick = {

                        currentPage =
                            AppPage.CONFIG
                    },

                    icon = {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "配置"
                        )
                    },

                    label = {

                        Text("配置")
                    }
                )

                NavigationBarItem(
                    selected =
                        currentPage == AppPage.ABOUT,

                    onClick = {

                        currentPage =
                            AppPage.ABOUT
                    },

                    icon = {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "关于"
                        )
                    },

                    label = {

                        Text("关于")
                    }
                )
            }
        }

    ) { innerPadding ->

        AnimatedContent(
            targetState = currentPage,
            label = "page_animation"
        ) { page ->

            when (page) {

                AppPage.MAIN -> {

                    MainPage(
                        status = status,
                        devices = devices,
                        events = events,
                        pressedKeys = pressedKeys,

                        isListening = isListening,

                        selectedMode = selectedMode,
                        selectedDevicePaths = selectedDevicePaths,
                        autoSelectedDevicePaths = autoSelectedDevicePaths,

                        onDeviceToggle = { eventPath ->
                            toggleDevice(eventPath)
                        },

                        onModeChanged = { mode ->

                            if (isListening) {
                                android.widget.Toast.makeText(
                                    context,
                                    "请先停止监听",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                if (selectedMode != mode) {
                                    selectedMode = mode

                                    /*
                                     * 切换 Root / Shizuku 后，
                                     * 清除之前模式的设备选择。
                                     */
                                    selectedDevicePaths =
                                        emptySet()

                                    autoSelectedDevicePaths =
                                        emptySet()

                                    devices =
                                        emptyList()
                                }
                            }
                        },

                        onStartOverlay = {

                            if (
                                android.provider.Settings.canDrawOverlays(
                                    context
                                )
                            ) {

                                context.startService(
                                    Intent(
                                        context,
                                        OverlayService::class.java
                                    )
                                )

                            } else {

                                android.widget.Toast.makeText(
                                    context,
                                    "请先授予悬浮窗权限",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()

                            }

                        },

                        onStopOverlay = {

                            context.stopService(
                                Intent(
                                    context,
                                    OverlayService::class.java
                                )
                            )

                        },

                        onStartListening = {

                            startReader()

                        },

                        onStopListening = {

                            stopReader()

                        },

                        modifier =
                            Modifier.padding(innerPadding)
                    )

                }

                AppPage.CONFIG -> {

                    ConfigPage(
                        repository = remember {
                            com.something.keystrokes.config.ConfigRepository(context)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )

                }

                AppPage.ABOUT -> {

                    AboutPage(
                        modifier =
                            Modifier.padding(innerPadding)
                    )

                }

            }
        }
    }

}


/*
 * ============================================================
 * 主页面 UI
 * ============================================================
 */

@Composable
private fun MainPage(

    status: String,

    devices:
    List<InputDeviceScanner.InputDeviceInfo>,

    events:
    List<String>,

    pressedKeys:
    Set<Int>,

    isListening:
    Boolean,

    selectedMode: InputMode,

    selectedDevicePaths: Set<String>,

    autoSelectedDevicePaths: Set<String>,

    onDeviceToggle: (String) -> Unit,

    onModeChanged: (InputMode) -> Unit,

    onStartOverlay:
        () -> Unit,

    onStopOverlay:
        () -> Unit,

    onStartListening:
        () -> Unit,

    onStopListening:
        () -> Unit,

    modifier: Modifier = Modifier

) {

    val context = LocalContext.current

    /*
     * 悬浮窗开关状态 - 持久化存储
     */
    val overlayPreferences =
        remember {
            context.getSharedPreferences(
                "keystrokes_overlay",
                Context.MODE_PRIVATE
            )
        }

    var overlayEnabled by remember {
        mutableStateOf(
            overlayPreferences.getBoolean(
                "overlay_enabled",
                false
            )
        )
    }

    Column(

        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),

        verticalArrangement =
            Arrangement.spacedBy(8.dp)

    ) {

        /*
         * =================================================
         * 顶部标题
         * =================================================
         */

        Text(

            text =
                "KeyStrokes V1.6.1",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium

        )

        Text(

            text =
                "通过Root或Shizuku监听外接输入设备",

            style =
                MaterialTheme
                    .typography
                    .titleMedium

        )


        /*
         * =================================================
         * 模式切换 - Root / Shizuku
         * =================================================
         */

        var rootAuthorized by remember {
            mutableStateOf(
                checkRootAuthorized()
            )
        }

        var shizukuAuthorized by remember {
            mutableStateOf(
                checkShizukuAuthorized()
            )
        }

        /*
         * ============================================================
         * 生命周期
         *
         * 回到主页时重新检查授权状态
         * ============================================================
         */

        val lifecycleOwner =
            LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {

            val observer =
                LifecycleEventObserver { _, event ->

                    if (
                        event ==
                        Lifecycle.Event.ON_RESUME
                    ) {

                        rootAuthorized =
                            checkRootAuthorized()

                        shizukuAuthorized =
                            checkShizukuAuthorized()

                        /*
                         * 重新读取悬浮窗保存的状态
                         */
                        overlayEnabled =
                            overlayPreferences.getBoolean(
                                "overlay_enabled",
                                false
                            )
                    }

                }

            lifecycleOwner.lifecycle.addObserver(
                observer
            )

            onDispose {

                lifecycleOwner.lifecycle.removeObserver(
                    observer
                )

            }

        }

        /*
         * 点击模式时再次实时检查授权。
         *
         * 这样即使用户没有离开页面，
         * Root / Shizuku 授权状态发生变化，
         * 点击时也能够立即刷新。
         */
        fun trySelectMode(mode: InputMode) {

            when (mode) {

                InputMode.ROOT -> {

                    val authorized =
                        checkRootAuthorized()

                    rootAuthorized =
                        authorized

                    if (!authorized) {

                        android.widget.Toast.makeText(
                            context,
                            "Root 未授权",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        return
                    }
                }

                InputMode.SHIZUKU -> {

                    val authorized =
                        checkShizukuAuthorized()

                    shizukuAuthorized =
                        authorized

                    if (!authorized) {

                        android.widget.Toast.makeText(
                            context,
                            "Shizuku 未授权",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        return
                    }
                }
            }

            onModeChanged(mode)
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {

            SegmentedButton(
                selected =
                    selectedMode == InputMode.ROOT,

                onClick = {
                    trySelectMode(InputMode.ROOT)
                },

                enabled = true,

                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = 0,
                        count = 2
                    ),

                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor =
                            if (rootAuthorized) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        activeContentColor =
                            if (rootAuthorized) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        inactiveContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        inactiveContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
            ) {
                Text("Root 模式")
            }

            SegmentedButton(
                selected =
                    selectedMode == InputMode.SHIZUKU,

                onClick = {
                    trySelectMode(InputMode.SHIZUKU)
                },

                enabled = true,

                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = 1,
                        count = 2
                    ),

                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor =
                            if (shizukuAuthorized) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        activeContentColor =
                            if (shizukuAuthorized) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        inactiveContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        inactiveContentColor =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
            ) {
                Text("Shizuku 模式")
            }
        }


        /*
         * =================================================
         * 按键显示 UI 悬浮窗（Switch）
         * =================================================
         */

        ListItem(
            headlineContent = {
                Text("按键显示 UI 悬浮窗")
            },

            supportingContent = {
                Text(
                    if (overlayEnabled) {
                        "悬浮窗正在显示"
                    } else {
                        "显示按键状态悬浮窗"
                    }
                )
            },

            trailingContent = {

                Switch(

                    checked = overlayEnabled,

                    onCheckedChange = { enabled ->

                        if (enabled) {

                            if (
                                android.provider.Settings.canDrawOverlays(
                                    context
                                )
                            ) {

                                onStartOverlay()

                                overlayEnabled = true

                                overlayPreferences
                                    .edit()
                                    .putBoolean(
                                        "overlay_enabled",
                                        true
                                    )
                                    .apply()

                            } else {

                                android.widget.Toast.makeText(
                                    context,
                                    "请先授予悬浮窗权限",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()

                                overlayEnabled = false
                            }

                        } else {

                            onStopOverlay()

                            overlayEnabled = false

                            overlayPreferences
                                .edit()
                                .putBoolean(
                                    "overlay_enabled",
                                    false
                                )
                                .apply()
                        }
                    }
                )
            }
        )


        /*
         * =================================================
         * 监听（单个按钮，根据状态切换文字）
         * =================================================
         */

        Button(

            onClick = {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Text(
                if (isListening) {
                    "停止监听输入"
                } else {
                    "开始监听输入"
                }
            )

        }


        /*
         * =================================================
         * 状态
         * =================================================
         */

        Text(

            text =
                "状态：$status",

            style =
                MaterialTheme
                    .typography
                    .titleMedium,

            modifier =
                Modifier.padding(
                    top = 8.dp
                )

        )


        /*
         * =================================================
         * 内容
         * =================================================
         */

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)

        ) {


            /*
             * 输入设备
             */

            item {

                Text(

                    text =
                        "检测到的输入设备",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    modifier =
                        Modifier.padding(
                            top = 8.dp
                        )

                )

            }


            items(
                devices
            ) { device ->

                val isSelected =
                    device.eventPath in selectedDevicePaths

                Row(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDeviceToggle(device.eventPath)
                            }
                            .padding(vertical = 4.dp),

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {

                    /*
                     * RadioButton 外观，但逻辑是多选
                     */

                    RadioButton(

                        selected = isSelected,

                        onClick = {
                            onDeviceToggle(device.eventPath)
                        }

                    )


                    Text(

                        text = buildString {
                            append(device.eventPath)
                            if (device.eventPath in autoSelectedDevicePaths) {
                                append(" (自动选择)")
                            }
                        },

                        style =
                            MaterialTheme.typography.bodyMedium,

                        modifier =
                            Modifier.padding(start = 4.dp)

                    )

                }

            }


            /*
             * =================================================
             * 按键状态（文字外显）
             * =================================================
             */

            item {

                Text(

                    text =
                        "按键状态",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    modifier =
                        Modifier.padding(
                            top = 12.dp
                        )

                )

            }


            item {

                Text(

                    text =
                        "W: ${
                            if (
                                KeyStateManager.KEY_W
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "A: ${
                            if (
                                KeyStateManager.KEY_A
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "S: ${
                            if (
                                KeyStateManager.KEY_S
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "D: ${
                            if (
                                KeyStateManager.KEY_D
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "SPACE: ${
                            if (
                                KeyStateManager.KEY_SPACE
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "LMB: ${
                            if (
                                KeyStateManager.KEY_LMB
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "RMB: ${
                            if (
                                KeyStateManager.KEY_RMB
                                in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )


                Text(

                    text =
                        "SHIFT: ${
                            if (
                                KeyStateManager.KEY_LEFT_SHIFT in pressedKeys
                                || KeyStateManager.KEY_RIGHT_SHIFT in pressedKeys
                            ) {
                                "DOWN"
                            } else {
                                "UP"
                            }
                        }"

                )

            }


            /*
             * 最近按键
             */

            item {

                Text(

                    text =
                        "最近按键",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    modifier =
                        Modifier.padding(
                            top = 12.dp
                        )

                )

            }


            items(
                events
            ) { event ->

                Text(

                    text =
                        event,

                    modifier =
                        Modifier.padding(
                            vertical = 2.dp
                        )

                )

            }

        }

    }

}


/*
 * ============================================================
 * 关于页面
 * ============================================================
 */

@Composable
private fun AboutPage(
    modifier: Modifier = Modifier
) {

    val context =
        LocalContext.current

    fun openUrl(url: String) {

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )

        context.startActivity(intent)
    }

    LazyColumn(

        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)

    ) {

        item {

            Text(
                text = "关于",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,

                modifier =
                    Modifier.padding(
                        top = 16.dp
                    )
            )
        }

        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .surfaceContainer
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "KeyStrokes",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall
                    )

                    Text(
                        text = "V1.6.1",
                        style =
                            MaterialTheme
                                .typography
                                .labelLarge,

                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "一个通过 Root 或 Shizuku 监听外接输入设备，" +
                                    "并提供可自定义悬浮窗按键显示的 Android 应用。",

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }
            }
        }

        item {

            Text(
                text = "项目链接",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,

                modifier =
                    Modifier.padding(
                        top = 8.dp,
                        bottom = 2.dp
                    )
            )
        }

        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column {

                    ListItem(

                        headlineContent = {
                            Text("GitHub 开源仓库")
                        },

                        supportingContent = {
                            Text(
                                "Sth-Android-KeyStrokes"
                            )
                        },

                        trailingContent = {
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },

                        modifier =
                            Modifier.clickable {

                                openUrl(
                                    "https://github.com/something-sth/Sth-Android-KeyStrokes"
                                )
                            }
                    )

                    HorizontalDivider()

                    ListItem(

                        headlineContent = {
                            Text("MIT License")
                        },

                        supportingContent = {
                            Text(
                                "查看项目开源许可证"
                            )
                        },

                        trailingContent = {
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },

                        modifier =
                            Modifier.clickable {

                                openUrl(
                                    "https://github.com/something-sth/Sth-Android-KeyStrokes/blob/main/LICENSE"
                                )
                            }
                    )

                    HorizontalDivider()

                    ListItem(

                        headlineContent = {
                            Text("QQ 交流反馈群")
                        },

                        supportingContent = {
                            Text(
                                "加入交流群进行反馈与交流"
                            )
                        },

                        trailingContent = {
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },

                        modifier =
                            Modifier.clickable {

                                openUrl(
                                    "https://qun.qq.com/universal-share/share?ac=1&authKey=TAcvzxnpxvtKzwgk%2Ba%2Br7WtZ5Mj63H3jNtzCLY9oy352oBj2mu5EFu2UYrGG2MbR&busi_data=eyJncm91cENvZGUiOiI5MDg4ODc0NzQiLCJ0b2tlbiI6IlVXOWloN3l2eGpQVksrTSsyMnZiWi84MXFsN2xhMXVxVUZ4K0xLd3hnRU5yanRpd29rMzB6MmtIeER2L1lwZk4iLCJ1aW4iOiIyNzUxODA5MjM3In0%3D&data=Xt1S3wTDGgqTCNJq8LaH9gg5UE1zg87Uw3a0VawgciuMnwuReiG1Hx-z_UX7X9i2MFP4w7OyNlwf2rVKURr7Zw&svctype=4&tempid=h5_group_info"
                                )
                            }
                    )
                }
            }
        }

        item {

            HorizontalDivider(
                modifier =
                    Modifier.padding(
                        vertical = 8.dp
                    )
            )

            Text(
                text = "开发者：something-sth",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            Text(
                text = "多多支持谢谢喵~",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,

                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,

                modifier =
                    Modifier.padding(
                        top = 4.dp,
                        bottom = 20.dp
                    )
            )
        }
    }
}