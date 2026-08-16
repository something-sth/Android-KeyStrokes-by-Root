package com.something.keystrokes.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class KeyboardService : Service() {

    override fun onCreate() {
        super.onCreate()
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {


        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}