package com.something.keystrokes

import android.os.IBinder
import android.os.Process

class KeyStrokesUserService : IKeyStrokesUserService.Stub() {

    override fun getServiceInfo(): String {

        return "KeyStrokes UserService 正常，UID=${Process.myUid()}"

    }
}