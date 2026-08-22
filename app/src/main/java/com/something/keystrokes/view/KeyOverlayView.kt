package com.something.keystrokes.view

import com.something.keystrokes.input.OverlayState
import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.roundToInt

class KeyOverlayView(
    context: Context
) : View(context) {

    private val TAG = "KeyOverlayView"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pressedKeys = emptySet<Int>()

    private var uiScale = 1f
    private var textScale = 1f
    private var opacity = 70
    private var animationEnabled = true
    private var cornerRadiusEnabled = false
    private var cornerRadiusPercent = 0f
    private var shiftKeyEnabled = false
    private var replaceSpaceDisplay = true
    private var mouseButtonsEnabled = true
    private var mouseCpsEnabled = false
    private var mouseCpsMode = 1

    /**
     * 每个按键当前的动画进度：
     * 0f = 未按下，1f = 按下。
     * 这样快速连续按键时，可以从当前画面状态继续过渡，而不是重新从 0 开始。
     */
    private val keyProgress = mutableMapOf<Int, Float>()
    private val keyAnimators = mutableMapOf<Int, ValueAnimator>()

    fun applyUiScale(scale: Float) {
        uiScale = scale.coerceIn(0.5f, 2f)
        requestLayout()
        invalidate()
    }

    fun applyTextScale(percent: Int) {
        textScale = percent.coerceIn(50, 150) / 100f
        invalidate()
    }

    fun applyOpacity(percent: Int) {
        opacity = percent.coerceIn(20, 100)
        invalidate()
    }

    fun applyCornerRadius(enabled: Boolean, percent: Float) {
        cornerRadiusEnabled = enabled
        cornerRadiusPercent = percent.coerceIn(0f, 50f)
        invalidate()
    }

    fun applyShiftKeyEnabled(enabled: Boolean) {
        shiftKeyEnabled = enabled
        invalidate()
    }

    fun applyReplaceSpaceDisplay(enabled: Boolean) {
        replaceSpaceDisplay = enabled
        invalidate()
    }

    fun applyMouseButtonsEnabled(enabled: Boolean) {
        mouseButtonsEnabled = enabled
        invalidate()
    }

    fun applyMouseCps(enabled: Boolean, mode: Int) {
        mouseCpsEnabled = enabled
        mouseCpsMode = mode.coerceIn(1, 3)
        invalidate()
    }

    fun applyAnimationEnabled(enabled: Boolean) {
        if (animationEnabled == enabled) return

        animationEnabled = enabled

        if (!enabled) {
            // 关闭动画后立即落到当前按键状态，不留下半截过渡。
            keyAnimators.values.forEach { it.cancel() }
            keyAnimators.clear()
            pressedKeys.forEach { keyProgress[it] = 1f }
            keyProgress.keys
                .filterNot { pressedKeys.contains(it) }
                .forEach { keyProgress[it] = 0f }
        }

        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (BASE_WIDTH * uiScale).roundToInt()
        val height = (getBaseHeight() * uiScale).roundToInt()
        setMeasuredDimension(width, height)
    }

    /** 根据当前 CPS 模式和 SHIFT 设置计算悬浮窗基础高度。 */
    fun getBaseHeight(): Int {
        return when {
            mouseCpsEnabled && mouseCpsMode == 2 -> {
                val spaceY = 370f
                val shiftBottom = if (shiftKeyEnabled) spaceY + 55f + 10f + 55f else spaceY + 55f
                kotlin.math.ceil(shiftBottom + 10f).toInt()
            }
            mouseCpsEnabled && mouseCpsMode == 3 -> {
                val mouseBottom = 190f + 110f
                val spaceY = mouseBottom + 10f
                val shiftBottom = if (shiftKeyEnabled) spaceY + 55f + 10f + 55f else spaceY + 55f
                kotlin.math.ceil(shiftBottom + 10f).toInt()
            }
            else -> BASE_HEIGHT
        }
    }

    private val keyMap = mapOf(
        17 to "W",
        30 to "A",
        31 to "S",
        32 to "D",
        57 to "SPACE",
        272 to "LMB",
        273 to "RMB"
    )

    fun updateKeys(keys: Set<Int>) {
        Log.d(TAG, "updateKeys=$keys")

        val oldKeys = pressedKeys
        val newKeys = keys.toSet()
        pressedKeys = newKeys

        if (!animationEnabled) {
            newKeys.forEach { keyProgress[it] = 1f }
            oldKeys.forEach { keyProgress[it] = if (newKeys.contains(it)) 1f else 0f }
            invalidate()
            return
        }

        val changedKeys = (oldKeys union newKeys)
            .filter { oldKeys.contains(it) != newKeys.contains(it) }

        changedKeys.forEach { keyCode ->
            animateKey(
                keyCode = keyCode,
                target = if (newKeys.contains(keyCode)) 1f else 0f
            )
        }

        postInvalidateOnAnimation()
    }

    private fun animateKey(keyCode: Int, target: Float) {
        val start = keyProgress[keyCode] ?: if (target > 0f) 0f else 1f

        keyAnimators[keyCode]?.cancel()

        if (start == target) {
            keyProgress[keyCode] = target
            invalidate()
            return
        }

        val animator = ValueAnimator.ofFloat(start, target).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = LinearInterpolator()

            addUpdateListener {
                keyProgress[keyCode] = it.animatedValue as Float
                postInvalidateOnAnimation()
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    if (keyAnimators[keyCode] === animation) {
                        keyAnimators.remove(keyCode)
                    }
                    keyProgress[keyCode] = target
                    invalidate()
                }

                override fun onAnimationCancel(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit
            })
        }

        keyAnimators[keyCode] = animator
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        Log.d(TAG, "onDraw")

        // All drawing coordinates remain based on the original 300 × 420 UI.
        // Scaling the canvas lets the configuration enlarge/reduce the complete UI,
        // including keys, gaps and text, without changing the original layout math.
        canvas.save()
        canvas.scale(uiScale, uiScale)

        val keySize = 80f
        val gap = 10f
        val center = BASE_WIDTH / 2f

        val alpha = (255f * opacity / 100f).roundToInt().coerceIn(0, 255)
        val normalColor = Color.argb(alpha, 0, 0, 0)
        val pressedColor = Color.argb(alpha, 255, 255, 255)
        val normalTextColor = Color.WHITE
        val pressedTextColor = Color.BLACK

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 35f * textScale
        paint.typeface = android.graphics.Typeface.DEFAULT

        /* W */
        drawKey(canvas, "W", center, 10f, keySize, keySize, 17, normalColor, pressedColor, normalTextColor, pressedTextColor)

        /* ASD */
        val asdY = keySize + gap + 10f

        drawKey(canvas, "A", center - keySize - gap, asdY, keySize, keySize, 30, normalColor, pressedColor, normalTextColor, pressedTextColor)
        drawKey(canvas, "S", center, asdY, keySize, keySize, 31, normalColor, pressedColor, normalTextColor, pressedTextColor)
        drawKey(canvas, "D", center + keySize + gap, asdY, keySize, keySize, 32, normalColor, pressedColor, normalTextColor, pressedTextColor)

        /* LMB / RMB */
        val mouseY = asdY + keySize + gap
        val mouseWidth = keySize * 1.5f + gap * 0.5f
        var spaceY = mouseY

        if (mouseButtonsEnabled) {
            val lmbCps = if (mouseCpsEnabled) OverlayState.getMouseCps(272) else 0
            val rmbCps = if (mouseCpsEnabled) OverlayState.getMouseCps(273) else 0

            val lmbLabel = if (mouseCpsEnabled && mouseCpsMode == 1 && lmbCps > 0) "LMB $lmbCps" else "LMB"
            val rmbLabel = if (mouseCpsEnabled && mouseCpsMode == 1 && rmbCps > 0) "RMB $rmbCps" else "RMB"

            val mouseHeight = if (mouseCpsEnabled && mouseCpsMode == 3) 110f else keySize

            if (mouseCpsEnabled && mouseCpsMode == 3) {
                drawKeyWithSecondaryText(
                    canvas, "LMB", "CPS: $lmbCps",
                    center - mouseWidth / 2f - gap / 2f, mouseY, mouseWidth, mouseHeight,
                    272, normalColor, pressedColor, normalTextColor, pressedTextColor
                )
                drawKeyWithSecondaryText(
                    canvas, "RMB", "CPS: $rmbCps",
                    center + mouseWidth / 2f + gap / 2f, mouseY, mouseWidth, mouseHeight,
                    273, normalColor, pressedColor, normalTextColor, pressedTextColor
                )
            } else {
                drawKey(canvas, lmbLabel, center - mouseWidth / 2f - gap / 2f, mouseY, mouseWidth, mouseHeight, 272, normalColor, pressedColor, normalTextColor, pressedTextColor)
                drawKey(canvas, rmbLabel, center + mouseWidth / 2f + gap / 2f, mouseY, mouseWidth, mouseHeight, 273, normalColor, pressedColor, normalTextColor, pressedTextColor)
            }

            when (mouseCpsMode) {
                2 -> {
                    val cpsY = mouseY + keySize + gap
                    drawStaticKey(canvas, "CPS: $lmbCps", center - mouseWidth / 2f - gap / 2f, cpsY, mouseWidth, keySize, normalColor, normalTextColor)
                    drawStaticKey(canvas, "CPS: $rmbCps", center + mouseWidth / 2f + gap / 2f, cpsY, mouseWidth, keySize, normalColor, normalTextColor)
                    spaceY = cpsY + keySize + gap
                }
                3 -> {
                    spaceY = mouseY + mouseHeight + gap
                }
                else -> {
                    spaceY = mouseY + keySize + gap
                }
            }
        }

        /* SPACE */
        val longWidth = keySize * 3 + gap * 2
        val spaceLabel = if (replaceSpaceDisplay) "————" else "SPACE"

        drawKey(
            canvas,
            spaceLabel,
            center,
            spaceY,
            longWidth,
            55f,
            57,
            normalColor,
            pressedColor,
            normalTextColor,
            pressedTextColor
        )

        /* SHIFT */
        if (shiftKeyEnabled) {
            val shiftY = spaceY + 55f + gap

            drawKey(
                canvas,
                "SHIFT",
                center,
                shiftY,
                longWidth,
                55f,
                42,
                normalColor,
                pressedColor,
                normalTextColor,
                pressedTextColor,
                54
            )
        }

        canvas.restore()

        // CPS 需要在没有新点击时也能从非零值回落到 0，Mode 1/2/3 都需要持续刷新。
        if (mouseCpsEnabled && mouseButtonsEnabled) {
            postInvalidateDelayed(CPS_REFRESH_MS)
        }
    }

    private fun drawStaticKey(
        canvas: Canvas,
        text: String,
        centerX: Float,
        topY: Float,
        w: Float,
        h: Float,
        normal: Int,
        textColor: Int
    ) {
        paint.color = normal

        val left = centerX - w / 2
        val right = centerX + w / 2
        val bottom = topY + h

        if (cornerRadiusEnabled && cornerRadiusPercent > 0f) {
            val radius = minOf(w, h) * (cornerRadiusPercent / 100f)
            canvas.drawRoundRect(left, topY, right, bottom, radius, radius, paint)
        } else {
            canvas.drawRect(left, topY, right, bottom, paint)
        }

        paint.color = textColor
        canvas.drawText(
            text,
            centerX,
            topY + h / 2 + 12,
            paint
        )
    }

    private fun drawKeyWithSecondaryText(
        canvas: Canvas,
        primaryText: String,
        secondaryText: String,
        centerX: Float,
        topY: Float,
        w: Float,
        h: Float,
        keyCode: Int,
        normal: Int,
        active: Int,
        normalText: Int,
        activeText: Int,
        vararg alternateKeyCodes: Int
    ) {
        val allKeyCodes = listOf(keyCode) + alternateKeyCodes
        val progress = allKeyCodes.maxOfOrNull { code ->
            keyProgress[code] ?: if (pressedKeys.contains(code)) 1f else 0f
        } ?: 0f

        paint.color = lerpColor(normal, active, progress)

        val left = centerX - w / 2
        val right = centerX + w / 2
        val bottom = topY + h

        if (cornerRadiusEnabled && cornerRadiusPercent > 0f) {
            val radius = minOf(w, h) * (cornerRadiusPercent / 100f)
            canvas.drawRoundRect(left, topY, right, bottom, radius, radius, paint)
        } else {
            canvas.drawRect(left, topY, right, bottom, paint)
        }

        paint.color = lerpColor(normalText, activeText, progress)

        val centerY = topY + h / 2f
        paint.textSize = 28f * textScale
        canvas.drawText(primaryText, centerX, centerY - 4f * textScale, paint)
        paint.textSize = 22f * textScale
        canvas.drawText(secondaryText, centerX, centerY + 24f * textScale, paint)

        paint.textSize = 35f * textScale
    }

    private fun drawKey(
        canvas: Canvas,
        text: String,
        centerX: Float,
        topY: Float,
        w: Float,
        h: Float,
        keyCode: Int,
        normal: Int,
        active: Int,
        normalText: Int,
        activeText: Int,
        vararg alternateKeyCodes: Int
    ) {
        val allKeyCodes = listOf(keyCode) + alternateKeyCodes
        val progress = allKeyCodes.maxOfOrNull { code ->
            keyProgress[code] ?: if (pressedKeys.contains(code)) 1f else 0f
        } ?: 0f

        paint.color = lerpColor(normal, active, progress)

        val left = centerX - w / 2
        val right = centerX + w / 2
        val bottom = topY + h

        if (cornerRadiusEnabled && cornerRadiusPercent > 0f) {
            val radius = minOf(w, h) * (cornerRadiusPercent / 100f)
            canvas.drawRoundRect(
                left,
                topY,
                right,
                bottom,
                radius,
                radius,
                paint
            )
        } else {
            canvas.drawRect(left, topY, right, bottom, paint)
        }

        paint.color = lerpColor(normalText, activeText, progress)

        canvas.drawText(
            text,
            centerX,
            topY + h / 2 + 12,
            paint
        )
    }

    private fun lerpColor(from: Int, to: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        val a = lerp(Color.alpha(from), Color.alpha(to), t)
        val r = lerp(Color.red(from), Color.red(to), t)
        val g = lerp(Color.green(from), Color.green(to), t)
        val b = lerp(Color.blue(from), Color.blue(to), t)
        return Color.argb(a, r, g, b)
    }

    private fun lerp(from: Int, to: Int, fraction: Float): Int {
        return (from + (to - from) * fraction).roundToInt()
    }

    override fun onDetachedFromWindow() {
        keyAnimators.values.forEach { it.cancel() }
        keyAnimators.clear()
        super.onDetachedFromWindow()
    }

    companion object {
        const val BASE_WIDTH = 300
        const val BASE_HEIGHT = 420
        private const val ANIMATION_DURATION_MS = 100L
        private const val CPS_REFRESH_MS = 100L
    }
}
