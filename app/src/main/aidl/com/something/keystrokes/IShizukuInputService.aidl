package com.something.keystrokes;

import com.something.keystrokes.IShizukuInputListener;

interface IShizukuInputService {

    int getUid();

    String[] scanDevices();

    int start(in String[] eventPaths);

    void stop();

    String getStatus();

    void setListener(IShizukuInputListener listener);

    void destroy();
}