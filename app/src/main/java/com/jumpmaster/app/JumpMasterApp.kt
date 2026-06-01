package com.jumpmaster.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class JumpMasterApp : Application() {

    companion object {
        const val CHANNEL_SCREEN_CAPTURE = "screen_capture"
        const val CHANNEL_FLOATING = "floating_window"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val captureChannel = NotificationChannel(
            CHANNEL_SCREEN_CAPTURE,
            "屏幕录制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "屏幕截图服务通知"
        }

        val floatingChannel = NotificationChannel(
            CHANNEL_FLOATING,
            "悬浮窗服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮窗控制服务通知"
        }

        manager.createNotificationChannel(captureChannel)
        manager.createNotificationChannel(floatingChannel)
    }
}
