package com.something.keystrokes

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

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
 * 页面
 * ============================================================
 */

private enum class AppPage {

    MAIN,

    SETTINGS,

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

    val executor = remember {
        Executors.newSingleThreadExecutor()
    }

    val keyStateManager = remember {
        KeyStateManager()
    }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
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


    var selectedMode by remember {
        mutableStateOf(InputMode.ROOT)
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


    var autoSelectedDevicePath by remember {
        mutableStateOf<String?>(null)
    }


    /*
     * ============================================================
     * 第一处：统一扫描函数
     *
     * Root / Shizuku 共用
     *
     * 负责：
     * 1. 扫描所有输入设备
     * 2. 更新设备列表
     * 3. 当前没有设备选择时执行自动选择
     * 4. 已经存在手动选择时绝不覆盖
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

                /*
                 * 如果设备仍然存在，就继续使用。
                 *
                 * 这里不修改 selectedDevicePaths。
                 */

                autoSelectedDevicePath = null

                return true
            }

            /*
             * ========================================================
             * 当前没有设备选择
             *
             * 尝试自动选择键盘。
             * ========================================================
             */

            val keyboard =
                result.keyboard

            if (keyboard != null) {

                selectedDevicePaths =
                    setOf(
                        keyboard.eventPath
                    )

                autoSelectedDevicePath =
                    keyboard.eventPath

                return true

            }

            /*
             * ========================================================
             * 自动识别失败
             * ========================================================
             */

            autoSelectedDevicePath =
                null

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

            /*
             * 如果取消的是自动选择的设备，
             * 那么它就不再属于自动选择状态。
             */

            if (eventPath == autoSelectedDevicePath) {
                autoSelectedDevicePath = null
            }

        } else {

            /*
             * 当前未选，加入手动选择
             */

            newSelection.add(eventPath)

            /*
             * 用户开始手动修改选择，
             * 自动选择标记不再作为当前选择依据。
             */

            if (eventPath != autoSelectedDevicePath) {
                autoSelectedDevicePath = null
            }
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

            ShizukuUserServiceManager.stop()
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
                                 * 更新按键状态
                                 */

                                keyStateManager.update(event)

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

                status =
                    "正在扫描输入设备..."

                /*
                 * ========================================================
                 * 第三处：Shizuku 同样使用统一设备扫描逻辑
                 * ========================================================
                 */

                if (!scanInputDevices()) {

                    isListening = false

                    return
                }

                if (selectedDevicePaths.isEmpty()) {

                    status =
                        "请先选择要监听的输入设备"

                    isListening = false

                    return
                }

                if (
                    !ShizukuUserServiceManager
                        .isShizukuRunning()
                ) {

                    status =
                        "Shizuku 未运行"

                    isListening =
                        false

                    return
                }

                if (
                    !ShizukuUserServiceManager
                        .hasPermission()
                ) {

                    status =
                        "Shizuku 未授权"

                    isListening =
                        false

                    return
                }

                /*
                 * =====================================================
                 * 传入 selectedDevicePaths
                 * =====================================================
                 */

                ShizukuUserServiceManager.start(
                    eventPaths = selectedDevicePaths.toTypedArray(),
                    onConnected = { binder ->

                        try {

                            val service =
                                IShizukuInputService
                                    .Stub
                                    .asInterface(
                                        binder
                                    )

                            /*
                             * 注册 Listener
                             */

                            service.setListener(
                                shizukuListener
                            )

                            /*
                             * =================================================
                             * 启动输入监听
                             *
                             * 传入 selectedDevicePaths
                             * =================================================
                             */

                            val result =
                                service.start(selectedDevicePaths.toTypedArray())

                            mainHandler.post {

                                when (result) {

                                    0 -> {

                                        status =
                                            "Shizuku 模式：正在监听"

                                        isListening = true
                                    }

                                    1 -> {

                                        status =
                                            "Shizuku 输入监听已经在运行"

                                        isListening = true
                                    }

                                    2 -> {

                                        status =
                                            "Shizuku UID 不正确"

                                        isListening = false
                                    }

                                    3 -> {

                                        status =
                                            "Shizuku：没有找到输入设备"

                                        isListening = false
                                    }

                                    4 -> {

                                        status =
                                            "Shizuku：输入设备无法打开"

                                        isListening = false
                                    }

                                    else -> {

                                        status =
                                            "Shizuku 启动失败：$result"

                                        isListening = false
                                    }
                                }
                            }

                        } catch (e: Exception) {

                            android.util.Log.e(
                                "KeyStrokes-Shizuku",
                                "Shizuku 输入监听启动失败",
                                e
                            )

                            mainHandler.post {

                                status =
                                    "Shizuku 启动失败：${
                                        e.message
                                            ?: e.javaClass.simpleName
                                    }"

                                isListening = false
                            }
                        }
                    }
                )
            }
        }
    }


    /*
     * ========================================================
     * 页面切换
     * ========================================================
     */

    when (currentPage) {

        AppPage.MAIN -> {

            MainPage(
                status = status,
                devices = devices,
                events = events,
                pressedKeys = pressedKeys,

                selectedMode = selectedMode,
                selectedDevicePaths = selectedDevicePaths,
                autoSelectedDevicePath = autoSelectedDevicePath,

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
                        selectedMode = mode
                    }
                },

                onOpenSettings = {

                    currentPage =
                        AppPage.SETTINGS

                },

                onOpenAbout = {

                    currentPage =
                        AppPage.ABOUT

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

                }

            )

        }


        AppPage.SETTINGS -> {

            SettingsPage(
                onBack = {

                    currentPage =
                        AppPage.MAIN

                }

            )

        }


        AppPage.ABOUT -> {

            AboutPage(
                onBack = {

                    currentPage =
                        AppPage.MAIN

                }

            )

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

    selectedMode: InputMode,

    selectedDevicePaths: Set<String>,

    autoSelectedDevicePath: String?,

    onDeviceToggle: (String) -> Unit,

    onModeChanged: (InputMode) -> Unit,

    onOpenSettings:
        () -> Unit,

    onOpenAbout:
        () -> Unit,

    onStartOverlay:
        () -> Unit,

    onStopOverlay:
        () -> Unit,

    onStartListening:
        () -> Unit,

    onStopListening:
        () -> Unit

) {

    Scaffold(

        modifier =
            Modifier.fillMaxSize()

    ) { innerPadding ->


        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),

            verticalArrangement =
                Arrangement.spacedBy(8.dp)

        ) {


            /*
             * =================================================
             * 顶部标题
             * =================================================
             */

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),

                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.SpaceBetween

            ) {


                Column {

                    Text(

                        text =
                            "Keystrokes V1.4.4",

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

                }


                Row {

                    TextButton(

                        onClick =
                            onOpenSettings

                    ) {

                        Text(
                            "设置"
                        )

                    }


                    TextButton(

                        onClick =
                            onOpenAbout

                    ) {

                        Text(
                            "关于"
                        )

                    }

                }

            }


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

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {

                    Button(
                        onClick = {
                            onModeChanged(InputMode.ROOT)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text("Root 模式")
                    }

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {
                            onModeChanged(InputMode.SHIZUKU)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text("Shizuku 模式")
                    }
                }

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    /*
                     * Root
                     */

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = if (selectedMode == InputMode.ROOT) {
                                "已选择"
                            } else {
                                "未选择"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = if (rootAuthorized) {
                                "已授权"
                            } else {
                                "未授权"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                    }

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    /*
                     * Shizuku
                     */

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = if (selectedMode == InputMode.SHIZUKU) {
                                "已选择"
                            } else {
                                "未选择"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = if (shizukuAuthorized) {
                                "已授权"
                            } else {
                                "未授权"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                    }

                }

            }


            /*
             * =================================================
             * 悬浮窗
             * =================================================
             */

            Button(

                onClick =
                    onStartOverlay,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    "开启按键显示UI"
                )

            }


            Button(

                onClick =
                    onStopOverlay,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    "关闭按键显示UI"
                )

            }


            /*
             * =================================================
             * 监听
             * =================================================
             */

            Button(

                onClick =
                    onStartListening,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    "开始监听输入"
                )

            }


            Button(

                onClick =
                    onStopListening,

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    "停止监听输入"
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

                    val isAutoSelected =
                        device.eventPath == autoSelectedDevicePath

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

                            text =
                                "${device.eventPath} | " +
                                        "${device.deviceName}" +
                                        if (device.isKeyboard) {
                                            " | 键盘"
                                        } else {
                                            " | 非键盘"
                                        } +
                                        if (isAutoSelected) {
                                            " (自动选择)"
                                        } else {
                                            ""
                                        },

                            style =
                                MaterialTheme.typography.bodyMedium,

                            modifier =
                                Modifier.padding(start = 4.dp)

                        )

                    }

                }


                /*
                 * 按键显示
                 */

                item {

                    Column(

                        horizontalAlignment =
                            Alignment.CenterHorizontally,

                        modifier =
                            Modifier.fillMaxWidth()

                    ) {


                        KeyButton(

                            text =
                                "W",

                            pressed =
                                KeyStateManager.KEY_W
                                        in pressedKeys

                        )


                        Row(

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )

                        ) {

                            KeyButton(

                                text =
                                    "A",

                                pressed =
                                    KeyStateManager.KEY_A
                                            in pressedKeys

                            )


                            KeyButton(

                                text =
                                    "S",

                                pressed =
                                    KeyStateManager.KEY_S
                                            in pressedKeys

                            )


                            KeyButton(

                                text =
                                    "D",

                                pressed =
                                    KeyStateManager.KEY_D
                                            in pressedKeys

                            )

                        }


                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp
                                )
                        )


                        KeyButton(

                            text =
                                "SPACE",

                            pressed =
                                KeyStateManager.KEY_SPACE
                                        in pressedKeys

                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )


                        KeyButton(

                            text =
                                "SHIFT",

                            pressed =
                                KeyStateManager.KEY_LEFTSHIFT
                                        in pressedKeys ||
                                        KeyStateManager.KEY_RIGHTSHIFT
                                        in pressedKeys

                        )

                    }

                }


                /*
                 * 按键状态
                 */

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
                            "SHIFT: ${
                                if (
                                    KeyStateManager.KEY_LEFTSHIFT
                                    in pressedKeys ||
                                    KeyStateManager.KEY_RIGHTSHIFT
                                    in pressedKeys
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

}


/*
 * ============================================================
 * 设置页面
 * ============================================================
 */

@Composable
private fun SettingsPage(
    onBack: () -> Unit
) {

    Scaffold { innerPadding ->

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {


            /*
             * 顶部
             */

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                TextButton(
                    onClick = onBack
                ) {

                    Text(
                        "返回"
                    )

                }


                Text(

                    text =
                        "设置",

                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,

                    modifier =
                        Modifier.padding(
                            start = 8.dp
                        )

                )

            }


            /*
             * 悬浮窗
             */

            Button(

                onClick = {
                    // 暂时不实现
                },

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Column {

                    Text(
                        "悬浮窗"
                    )

                    Text(
                        "文字占位",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                }

            }


            /*
             * 样式
             */

            Button(

                onClick = {
                    // 暂时不实现
                },

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Column {

                    Text(
                        "样式"
                    )

                    Text(
                        "文字占位",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                }

            }


            /*
             * 按键布局
             */

            Button(

                onClick = {
                    // 暂时不实现
                },

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Column {

                    Text(
                        "按键布局"
                    )

                    Text(
                        "文字占位",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                }

            }


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            Text(

                text =
                    "尚未更新",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium

            )

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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold { innerPadding ->

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {


            /*
             * 顶部
             */

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                TextButton(
                    onClick = onBack
                ) {

                    Text(
                        "返回"
                    )

                }


                Text(

                    text =
                        "关于",

                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium,

                    modifier =
                        Modifier.padding(
                            start = 8.dp
                        )

                )

            }


            Spacer(
                modifier =
                    Modifier.height(32.dp)
            )


            /*
             * 项目名称
             */

            Text(

                text =
                    "Sth-Android-KeyStrokes",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge

            )


            Text(

                text =
                    "V1.4.4",

                style =
                    MaterialTheme
                        .typography
                        .titleMedium

            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            Text(

                text =
                    "一个Android\n" +
                            "按键显示UI",

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge

            )


            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )


            /*
             * GitHub
             */

            Button(

                onClick = {

                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            "https://github.com/something-sth/Sth-Android-KeyStrokes"
                        )
                    )

                    context.startActivity(intent)

                },

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    "GitHub 开源仓库"
                )

            }


            /*
             * License
             */

            Button(

                onClick = {

                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            "https://github.com/something-sth/Sth-Android-KeyStrokes/blob/main/LICENSE"
                        )
                    )

                    context.startActivity(intent)

                },

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Text(
                    "开源许可证 MIT License"
                )

            }


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            Text(

                text =
                    "开发者：something-sth",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium

            )


            Text(

                text =
                    "多多支持谢谢喵~",

                style =
                    MaterialTheme
                        .typography
                        .bodySmall

            )

        }

    }

}