package com.something.keystrokes;

import com.something.keystrokes.IShizukuInputListener;

interface IShizukuInputService {

    int getUid();

    int start();

    void stop();

    String getStatus();

    void setListener(IShizukuInputListener listener);

    void destroy();
}