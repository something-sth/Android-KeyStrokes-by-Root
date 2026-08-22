package com.something.keystrokes.config

/**
 * KeyStrokes 的一份完整配置。
 *
 * 目前先把未来会被悬浮窗使用的基础参数集中起来，
 * 第一阶段只负责配置的创建、修改、切换和持久化，暂不让 Overlay 读取这些参数。
 */
data class KeyStrokesConfig(
    val id: String,
    var name: String,
    var description: String,
    val builtIn: Boolean = false,
    var overlayWidth: Int = 300,
    var overlayHeight: Int = 420,
    /**
     * 悬浮窗整体 UI 缩放百分比。100% 表示当前的原始大小。
     * 暂时只保存配置，不直接影响现有 Overlay。
     */
    var uiScalePercent: Int = 100,
    /** 按键字符相对于 UI 大小的额外缩放百分比。100% 为当前字符大小。 */
    var textScalePercent: Int = 100,
    var opacity: Int = 70,
    /** 是否启用按键按下/释放时的平滑颜色动画。 */
    var animationEnabled: Boolean = true,
    var keySize: Float = 80f,
    var keyGap: Float = 10f,
    var normalColor: Long = 0xB4000000,
    var pressedColor: Long = 0xB4FFFFFF,
    var textColor: Long = 0xFFFFFFFF,
    var pressedTextColor: Long = 0xFF000000,
    var cornerRadiusEnabled: Boolean = false,
    var cornerRadius: Float = 0f,
    /** 是否监听并显示 SHIFT 按键。 */
    var shiftKeyEnabled: Boolean = false,
    /** 是否将 SPACE 的显示替换为 Minecraft 风格的长横线。 */
    var replaceSpaceDisplay: Boolean = true,
    /** 是否显示鼠标左右键。 */
    var mouseButtonsEnabled: Boolean = true,
    /** 是否启用鼠标 CPS 显示（目前仅保存配置，尚未接入 Overlay）。 */
    var mouseCpsEnabled: Boolean = false,
    /** 鼠标 CPS 显示模式。1、2、3 为预留模式。 */
    var mouseCpsMode: Int = 1
) {

    fun copyAs(
        newId: String,
        newName: String,
        newDescription: String = description
    ): KeyStrokesConfig {
        return copy(
            id = newId,
            name = newName,
            description = newDescription,
            builtIn = false
        )
    }
}
