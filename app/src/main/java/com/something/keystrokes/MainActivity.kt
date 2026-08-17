package com.something.keystrokes

import android.content.Intent
import android.os.Bundle

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

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

import com.something.keystrokes.input.InputDeviceScanner
import com.something.keystrokes.input.KeyStateManager
import com.something.keystrokes.input.LinuxInputEvent
import com.something.keystrokes.input.OverlayState

import com.something.keystrokes.service.KeyboardService
import com.something.keystrokes.service.OverlayService

import com.something.keystrokes.ui.KeyButton
import com.something.keystrokes.ui.theme.KeyStrokesTheme

import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.Future


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KeyStrokesTheme {
                KeystrokesTestScreen()
            }
        }
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


    var readerFuture by remember {
        mutableStateOf<Future<*>?>(null)
    }


    /*
     * ========================================================
     * 生命周期
     * ========================================================
     */

    DisposableEffect(Unit) {

        onDispose {

            readerFuture?.cancel(true)

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

        readerFuture?.cancel(true)

        readerFuture = null


        keyStateManager.clear()

        pressedKeys = emptySet()


        status = "已停止"

        isListening = false

    }


    /*
     * ========================================================
     * 开始监听
     * ========================================================
     */

    fun startReader() {

        if (readerFuture != null) {

            status = "已经正在监听"

            return

        }


        isListening = true

        status = "正在扫描输入设备..."


        readerFuture =
            executor.submit {

                try {

                    /*
                     * 扫描 /dev/input/event*
                     */

                    val result =
                        InputDeviceScanner.scan()


                    devices =
                        result.devices


                    val keyboard =
                        result.keyboard


                    if (keyboard == null) {

                        status =
                            result.message

                        isListening =
                            false

                        readerFuture =
                            null

                        return@submit

                    }


                    val eventPath =
                        keyboard.eventPath


                    status =
                        "Root 正常，正在监听 $eventPath"


                    val reader =
                        RootKeyboardReader(
                            eventPath
                        )


                    reader.readEvents { event ->

                        /*
                         * EV_KEY = 1
                         */

                        if (event.type != EV_KEY) {

                            return@readEvents

                        }


                        keyStateManager.update(
                            event
                        )


                        pressedKeys =
                            keyStateManager
                                .getPressedKeys()


                        /*
                         * 更新悬浮窗
                         */

                        OverlayState.update(
                            pressedKeys
                        )


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
                            linuxKeyName(
                                event.code
                            )


                        val line =
                            "${event.timestamp}  $keyName  $action"


                        events =
                            (
                                    listOf(line) +
                                            events
                                    ).take(80)

                    }


                } catch (
                    e: InterruptedException
                ) {

                    /*
                     * 正常停止监听
                     */

                    status =
                        "已停止"


                } catch (
                    e: Exception
                ) {

                    status =
                        "监听失败：${e.message ?: e.javaClass.simpleName}"

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

                    context.startService(
                        Intent(
                            context,
                            KeyboardService::class.java
                        )
                    )

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
                            "Sth. Keystrokes V1.1",

                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium

                    )


                    Text(

                        text =
                            "通过Root权限来监听外接键盘输入",

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

                    Text(

                        text =
                            "${device.eventName} | " +
                                    "${device.deviceName} | " +
                                    if (device.isKeyboard) {
                                        "键盘"
                                    } else {
                                        "非键盘"
                                    },

                        modifier =
                            Modifier.padding(
                                vertical = 4.dp
                            )

                    )

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
                    "V1.1",

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
                    "在 Android 系统中使用的\n" +
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


/*
 * ============================================================
 * Linux input_event
 *
 * Android 16 / 常见 arm64 环境：
 *
 * struct input_event {
 *     struct timeval time;   // 16 bytes
 *     __u16 type;             // 2
 *     __u16 code;             // 2
 *     __s32 value;            // 4
 * }
 *
 * 总大小 = 24 bytes
 * ============================================================
 */

private class RootKeyboardReader(
    private val eventPath: String
) {

    @Volatile
    private var running = true


    fun readEvents(
        onEvent:
            (LinuxInputEvent) -> Unit
    ) {

        val process =
            ProcessBuilder(
                "su",
                "-c",
                "cat \"$eventPath\""
            )
                .redirectErrorStream(true)
                .start()


        try {

            BufferedInputStream(
                process.inputStream,
                8192
            ).use { input ->

                val buffer =
                    ByteArray(24)


                while (

                    running &&

                    !Thread
                        .currentThread()
                        .isInterrupted

                ) {

                    val read =
                        readFully(
                            input,
                            buffer
                        )


                    if (read < 24) {

                        break

                    }


                    val event =
                        parseInputEvent(
                            buffer
                        )


                    onEvent(
                        event
                    )

                }

            }

        } finally {

            running =
                false


            try {

                process.destroy()

            } catch (
                _: Exception
            ) {
            }

        }

    }


    private fun readFully(
        input:
        BufferedInputStream,

        buffer:
        ByteArray
    ): Int {

        var offset =
            0


        while (
            offset < buffer.size
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


            offset +=
                count

        }


        return offset

    }


    private fun parseInputEvent(
        buffer:
        ByteArray
    ): LinuxInputEvent {

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


        val timestamp =
            "$seconds.$microseconds"


        return LinuxInputEvent(

            timestamp =
                timestamp,

            type =
                type,

            code =
                code,

            value =
                value

        )

    }

}


/*
 * ============================================================
 * Linux Event
 * ============================================================
 */

private const val EV_KEY =
    0x01


/*
 * ============================================================
 * Linux Key Name
 * ============================================================
 */

private fun linuxKeyName(
    code: Int
): String {

    return when (code) {

        1 ->
            "KEY_ESC"


        2 ->
            "KEY_1"

        3 ->
            "KEY_2"

        4 ->
            "KEY_3"

        5 ->
            "KEY_4"

        6 ->
            "KEY_5"

        7 ->
            "KEY_6"

        8 ->
            "KEY_7"

        9 ->
            "KEY_8"

        10 ->
            "KEY_9"

        11 ->
            "KEY_0"


        16 ->
            "KEY_Q"

        17 ->
            "KEY_W"

        18 ->
            "KEY_E"

        19 ->
            "KEY_R"

        20 ->
            "KEY_T"

        21 ->
            "KEY_Y"

        22 ->
            "KEY_U"

        23 ->
            "KEY_I"

        24 ->
            "KEY_O"

        25 ->
            "KEY_P"


        30 ->
            "KEY_A"

        31 ->
            "KEY_S"

        32 ->
            "KEY_D"

        33 ->
            "KEY_F"

        34 ->
            "KEY_G"

        35 ->
            "KEY_H"

        36 ->
            "KEY_J"

        37 ->
            "KEY_K"

        38 ->
            "KEY_L"


        44 ->
            "KEY_Z"

        45 ->
            "KEY_X"

        46 ->
            "KEY_C"

        47 ->
            "KEY_V"

        48 ->
            "KEY_B"

        49 ->
            "KEY_N"

        50 ->
            "KEY_M"


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


        57 ->
            "KEY_SPACE"


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