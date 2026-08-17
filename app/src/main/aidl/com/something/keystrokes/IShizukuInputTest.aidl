package com.something.keystrokes;

interface IShizukuInputTest {

    int getUid();

    int startInputTest();

    void stopInputTest();

    String getStatus();

    void destroy();
}