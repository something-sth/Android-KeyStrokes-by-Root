package com.something.keystrokes;

interface IShizukuInputListener {

    void onKeyEvent(
        int type,
        int code,
        int value,
        String keyName,
        boolean down
    );
}