package com.something.keystrokes.view

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
    private var opacity = 70
    private var animationEnabled = true
    private var cornerRadiusEnabled = false
    private var cornerRadiusPercent = 0f

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

    fun applyOpacity(percent: Int) {
        opacity = percent.coerceIn(20, 100)
        invalidate()
    }

    fun applyCornerRadius(enabled: Boolean, percent: Float) {
        cornerRadiusEnabled = enabled
        cornerRadiusPercent = percent.coerceIn(0f, 50f)
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
        val height = (BASE_HEIGHT * uiScale).roundToInt()
        setMeasuredDimension(width, height)
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
        paint.textSize = 35f
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

        drawKey(canvas, "LMB", center - mouseWidth / 2f - gap / 2f, mouseY, mouseWidth, keySize, 272, normalColor, pressedColor, normalTextColor, pressedTextColor)
        drawKey(canvas, "RMB", center + mouseWidth / 2f + gap / 2f, mouseY, mouseWidth, keySize, 273, normalColor, pressedColor, normalTextColor, pressedTextColor)

        /* SPACE */
        val longWidth = keySize * 3 + gap * 2
        val spaceY = mouseY + keySize + gap

        drawKey(canvas, "————", center, spaceY, longWidth, 55f, 57, normalColor, pressedColor, normalTextColor, pressedTextColor)

        canvas.restore()
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
        activeText: Int
    ) {
        val progress = keyProgress[keyCode] ?: if (pressedKeys.contains(keyCode)) 1f else 0f

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
    }
}
