# JumpMaster

微信跳一跳 Android 辅助工具，基于图像识别自动计算跳跃距离和按压时间。

## 算法来源

核心图像识别算法借鉴自 [wangshub/wechat_jump_game](https://github.com/wangshub/wechat_jump_game)，原项目为 Python 实现，本项目将其移植到 Android 平台。

## 功能特点

- **本地模式**：通过 MediaProjection 截屏 + AccessibilityService 执行触摸手势，无需电脑
- **ADB 模式**：通过无线 ADB 调试连接手机，支持远程控制
- **悬浮窗控制**：游戏内悬浮窗显示状态、跳跃次数、按压系数等信息
- **自动休息**：连续跳跃后随机休息，降低被检测风险

## 技术栈

- Kotlin + Jetpack Compose
- MediaProjection API（截屏）
- AccessibilityService（触摸手势）
- kadb（无线 ADB 连接）

## 构建

```bash
./gradlew assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/release/`。

## 使用说明

1. 安装 APK 并授予必要权限（悬浮窗、无障碍服务、截屏）
2. 打开 JumpMaster，选择本地模式或 ADB 模式
3. 切换到微信跳一跳游戏，点击开始
4. 程序会自动识别棋子和目标位置，计算并执行跳跃

## 致谢

- [wangshub/wechat_jump_game](https://github.com/wangshub/wechat_jump_game) - 原始 Python 算法

## 许可证

本项目仅供学习交流使用。
