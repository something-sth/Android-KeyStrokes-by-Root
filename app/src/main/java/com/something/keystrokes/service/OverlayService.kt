package com.something.keystrokes.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

import com.something.keystrokes.config.ConfigRepository
import com.something.keystrokes.input.OverlayState
import com.something.keystrokes.view.KeyOverlayView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var keyView: KeyOverlayView
    private lateinit var params: WindowManager.LayoutParams

    private val keyListener: (Set<Int>) -> Unit = { keys ->
        keyView.post {
            keyView.updateKeys(keys)
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        keyView = KeyOverlayView(this)

        params = WindowManager.LayoutParams(
            KeyOverlayView.BASE_WIDTH,
            KeyOverlayView.BASE_HEIGHT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 200

        setupDragListener()
        applyCurrentConfig()

        windowManager.addView(keyView, params)

        OverlayState.addListener(keyListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startService() while the service is already alive reaches this method too,
        // so re-read the active profile and apply it without requiring a service restart.
        if (::keyView.isInitialized) {
            applyCurrentConfig()
        }
        return START_NOT_STICKY
    }

    private fun applyCurrentConfig() {
        val configRepository = ConfigRepository(this)
        val configs = configRepository.loadConfigs()
        val activeId = configRepository.getActiveConfigId()
        val config = configs.firstOrNull { it.id == activeId }
            ?: configs.firstOrNull { it.id == ConfigRepository.DEFAULT_ID }
            ?: return

        val scale = config.uiScalePercent.coerceIn(50, 200) / 100f
        keyView.applyUiScale(scale)
        keyView.applyTextScale(config.textScalePercent)
        keyView.applyOpacity(config.opacity)
        keyView.applyAnimationEnabled(config.animationEnabled)
        keyView.applyCornerRadius(config.cornerRadiusEnabled, config.cornerRadius)
        keyView.applyShiftKeyEnabled(config.shiftKeyEnabled)
        keyView.applyReplaceSpaceDisplay(config.replaceSpaceDisplay)
        keyView.applyMouseButtonsEnabled(config.mouseButtonsEnabled)
        keyView.applyMouseCps(
            config.mouseButtonsEnabled && config.mouseCpsEnabled,
            config.mouseCpsMode
        )

        val targetWidth = (KeyOverlayView.BASE_WIDTH * scale).toInt()
        val targetHeight = (keyView.getBaseHeight() * scale).toInt()

        params.width = targetWidth
        params.height = targetHeight

        if (isViewAttached()) {
            windowManager.updateViewLayout(keyView, params)
        }
    }

    private fun isViewAttached(): Boolean = keyView.parent != null

    private fun setupDragListener() {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        keyView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchStartX).toInt()
                    params.y = startY + (event.rawY - touchStartY).toInt()

                    if (keyView.parent != null) {
                        windowManager.updateViewLayout(keyView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> true
                else -> true
            }
        }
    }

    override fun onDestroy() {
        OverlayState.removeListener(keyListener)

        if (::keyView.isInitialized) {
            try {
                windowManager.removeView(keyView)
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
