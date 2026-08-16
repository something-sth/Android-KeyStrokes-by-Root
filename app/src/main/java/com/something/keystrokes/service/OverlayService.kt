package com.something.keystrokes.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager

import com.something.keystrokes.input.OverlayState
import com.something.keystrokes.view.KeyOverlayView


class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var keyView: KeyOverlayView

    private lateinit var params: WindowManager.LayoutParams


    /*
     * ==========================
     * 按键监听
     * ==========================
     *
     * OverlayState 可能在后台线程
     * 调用这里。
     *
     * 所以必须通过 keyView.post
     * 回到 View 所在线程。
     */

    private val keyListener: (Set<Int>) -> Unit = { keys ->

        keyView.post {

            keyView.updateKeys(keys)

        }

    }


    /*
     * ==========================
     * 创建服务
     * ==========================
     */

    override fun onCreate() {

        super.onCreate()


        /*
         * ==========================
         * WindowManager
         * ==========================
         */

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        /*
         * ==========================
         * 创建真正的按键 UI
         * ==========================
         */

        keyView =
            KeyOverlayView(this)


        /*
         * ==========================
         * 悬浮窗尺寸
         * ==========================
         *
         * 固定 300 × 420。
         *
         * 不使用 WRAP_CONTENT，
         * 避免出现触摸区域异常变大的问题。
         */

        params =
            WindowManager.LayoutParams(

                300,

                420,

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                PixelFormat.TRANSLUCENT

            )


        params.gravity =
            Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL


        params.x =
            0

        params.y =
            200


        /*
         * ==========================
         * 悬浮窗拖动
         * ==========================
         */

        var startX =
            0

        var startY =
            0

        var touchStartX =
            0f

        var touchStartY =
            0f


        keyView.setOnTouchListener { _, event ->

            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {

                    startX =
                        params.x

                    startY =
                        params.y

                    touchStartX =
                        event.rawX

                    touchStartY =
                        event.rawY

                    true
                }


                MotionEvent.ACTION_MOVE -> {

                    params.x =
                        startX +
                                (
                                        event.rawX -
                                                touchStartX
                                        ).toInt()


                    params.y =
                        startY +
                                (
                                        event.rawY -
                                                touchStartY
                                        ).toInt()


                    windowManager.updateViewLayout(
                        keyView,
                        params
                    )

                    true
                }


                MotionEvent.ACTION_UP -> {

                    true
                }


                else -> {

                    true
                }

            }

        }


        /*
         * ==========================
         * 添加悬浮窗
         * ==========================
         */

        windowManager.addView(
            keyView,
            params
        )


        /*
         * ==========================
         * 注册按键监听
         * ==========================
         */

        OverlayState.addListener(
            keyListener
        )

    }


    /*
     * ==========================
     * 服务销毁
     * ==========================
     */

    override fun onDestroy() {

        /*
         * 先取消按键监听。
         */

        OverlayState.removeListener(
            keyListener
        )


        /*
         * 移除悬浮窗。
         */

        if (
            ::keyView.isInitialized
        ) {

            try {

                windowManager.removeView(
                    keyView
                )

            } catch (
                _: Exception
            ) {
            }

        }


        super.onDestroy()

    }


    /*
     * ==========================
     * Service Binder
     * ==========================
     */

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null

    }

}