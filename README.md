Android KeyStrokes

KeyStrokes 是一个开源的 Android 按键可视化工具。

项目的主要用途是将外接键盘的实时按键状态显示在 Android 屏幕上的可移动悬浮窗中，整体显示效果参考 Minecraft 等游戏中的 KeyStrokes 模组。

该项目适用于游戏、投屏、远程桌面以及其他需要直观看到键盘输入状态的场景。

实现原理

Android 普通应用无法直接监听系统范围内的所有键盘输入，因此 KeyStrokes 没有采用普通的 "KeyEvent" 监听方式，而是直接从 Android 底层的 Linux Input 子系统获取输入事件。

外接键盘产生的输入事件通常会通过：

/dev/input/event*

进行传递。

KeyStrokes 通过 Root 权限或 Shizuku 提供的高权限环境访问这些输入设备，读取其中的 Linux Input Event，并对输入事件进行解析，从而判断具体的按键状态。

整体流程可以简单表示为：

外接键盘
   ↓
Linux Input Subsystem
   ↓
/dev/input/event*
   ↓
Root / Shizuku
   ↓
Input Event 解析
   ↓
按键状态
   ↓
悬浮窗显示

通过这种方式，应用可以在不依赖当前前台应用的情况下获取外接键盘产生的底层输入事件，并将其实时显示出来。

项目状态

KeyStrokes 目前仍处于持续开发阶段。

当前项目已经实现 Root 模式和 Shizuku 模式的输入监听，并能够将检测到的键盘输入实时显示在悬浮窗中。

项目后续将继续完善 Shizuku 模式、输入设备兼容性以及悬浮窗相关功能。

开源

KeyStrokes 是一个开源项目。

如果这个项目对你有所帮助，欢迎在 GitHub 上点一个 Star。

如果你发现了问题或有新的想法，也欢迎提交 Issue 或 Pull Request。

GitHub:
https://github.com/something-sth/Sth-Android-KeyStrokes

---

«KeyStrokes —— A simple Android keyboard visualizer based on Linux Input Events.»