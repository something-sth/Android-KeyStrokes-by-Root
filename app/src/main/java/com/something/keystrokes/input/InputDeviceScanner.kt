package com.something.keystrokes.input

import java.io.BufferedReader
import java.io.InputStreamReader

object InputDeviceScanner {

    data class InputDeviceInfo(
        val eventName: String,
        val eventPath: String,
        val deviceName: String
    )

    data class ScanResult(
        val devices: List<InputDeviceInfo>,
        val message: String
    )

    fun scan(): ScanResult {
        return try {
            val command = "ls /dev/input/event* 2>/dev/null"

            val output = runRootCommand(command)

            if (output.isBlank()) {
                return ScanResult(
                    devices = emptyList(),
                    message = "Root 命令执行成功，但没有发现 /dev/input/event*。"
                )
            }

            val devices = mutableListOf<InputDeviceInfo>()

            output.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    val path = line.trim()
                    if (!path.startsWith("/dev/input/event")) {
                        return@forEach
                    }

                    val eventName = path.substringAfterLast("/")

                    devices += InputDeviceInfo(
                        eventName = eventName,
                        eventPath = path,
                        deviceName = path
                    )
                }

            // 按 event 编号数字排序
            devices.sortBy {
                it.eventPath
                    .substringAfter("event")
                    .toIntOrNull() ?: Int.MAX_VALUE
            }

            ScanResult(
                devices = devices,
                message = when {
                    devices.isNotEmpty() ->
                        "Root 正常，找到 ${devices.size} 个输入设备"
                    else ->
                        "Root 正常，没有找到输入设备"
                }
            )

        } catch (e: Exception) {
            ScanResult(
                devices = emptyList(),
                message = "Root 扫描失败：${e.message ?: e.javaClass.simpleName}"
            )
        }
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