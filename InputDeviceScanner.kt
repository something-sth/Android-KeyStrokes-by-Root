package com.something.keystrokes.input

import java.io.BufferedReader
import java.io.InputStreamReader

object InputDeviceScanner {

    data class InputDeviceInfo(
        val eventName: String,
        val eventPath: String,
        val deviceName: String,
        val isKeyboard: Boolean
    )

    data class ScanResult(
        val devices: List<InputDeviceInfo>,
        val keyboard: InputDeviceInfo?,
        val message: String
    )

    fun scan(): ScanResult {
        return try {
            val command = """
                for f in /dev/input/event*; do
                    [ -e "${'$'}f" ] || continue

                    event=${'$'}(basename "${'$'}f")
                    name=${'$'}(cat "/sys/class/input/${'$'}event/device/name" 2>/dev/null)

                    echo "${'$'}event|${'$'}name"
                done
            """.trimIndent()

            val output = runRootCommand(command)

            if (output.isBlank()) {
                return ScanResult(
                    devices = emptyList(),
                    keyboard = null,
                    message = "Root 命令执行成功，但没有发现 /dev/input/event*。"
                )
            }

            val devices = mutableListOf<InputDeviceInfo>()

            output.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->

                    val parts = line.split("|", limit = 2)

                    if (parts.isEmpty()) {
                        return@forEach
                    }

                    val eventName = parts[0].trim()

                    if (!eventName.matches(Regex("event\\d+"))) {
                        return@forEach
                    }

                    val deviceName =
                        if (parts.size >= 2) {
                            parts[1].trim().ifEmpty { "(unknown)" }
                        } else {
                            "(unknown)"
                        }

                    val isKeyboard = looksLikeKeyboard(deviceName)

                    devices += InputDeviceInfo(
                        eventName = eventName,
                        eventPath = "/dev/input/$eventName",
                        deviceName = deviceName,
                        isKeyboard = isKeyboard
                    )
                }

            val keyboard = devices.firstOrNull { it.isKeyboard }

            ScanResult(
                devices = devices,
                keyboard = keyboard,
                message = when {
                    keyboard != null ->
                        "Root 正常，找到键盘：${keyboard.eventPath}"

                    devices.isNotEmpty() ->
                        "Root 正常，但暂时没有识别到键盘。"

                    else ->
                        "Root 正常，但没有找到输入设备。"
                }
            )

        } catch (e: Exception) {
            ScanResult(
                devices = emptyList(),
                keyboard = null,
                message = "Root 扫描失败：${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun looksLikeKeyboard(deviceName: String): Boolean {
        val lower = deviceName.lowercase()

        return lower.contains("keyboard") ||
                lower.contains("keypad") ||
                lower.contains("kbd")
    }

    private fun runRootCommand(command: String): String {
        val process = ProcessBuilder(
            "su",
            "-c",
            command
        )
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()

        BufferedReader(
            InputStreamReader(process.inputStream)
        ).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                output.appendLine(line)
            }
        }

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IllegalStateException(
                "su 返回错误码：$exitCode\n$output"
            )
        }

        return output.toString()
    }
}