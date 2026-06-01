package com.jumpmaster.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that performs touch gestures on behalf of the app.
 * Replaces the ADB "input swipe" command from the original Python tool.
 * Uses GestureDescription API (Android 7.0+) for precise long-press simulation.
 */
class TouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchAccessibility"

        private var instance: TouchAccessibilityService? = null
        private var activePackageName: String? = null

        fun isRunning(): Boolean = instance != null

        fun getActivePackageName(): String? = activePackageName

        /**
         * Perform a long press at the given screen coordinates.
         * @param x X coordinate
         * @param y Y coordinate
         * @param durationMs Duration of the press in milliseconds
         * @param onComplete Called when the gesture completes (or fails)
         */
        fun longPress(x: Int, y: Int, durationMs: Long, onComplete: ((Boolean) -> Unit)? = null) {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Service not running, cannot perform gesture")
                onComplete?.invoke(false)
                return
            }
            service.performLongPress(x, y, durationMs, onComplete)
        }

        fun captureScreenshot(onComplete: (Bitmap?) -> Unit) {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Service not running, cannot take screenshot")
                onComplete(null)
                return
            }
            service.takeServiceScreenshot(onComplete)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.let { activePackageName = it.toString() }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    /**
     * Perform a long press gesture using the GestureDescription API.
     * This simulates the ADB "input swipe x y x y duration" from the Python tool.
     */
    private fun performLongPress(x: Int, y: Int, durationMs: Long, onComplete: ((Boolean) -> Unit)?) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val strokeDescription = GestureDescription.StrokeDescription(
            path, 0, durationMs
        )

        val gesture = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture completed: long press ($x, $y) for ${durationMs}ms")
                handler.post { onComplete?.invoke(true) }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled: long press ($x, $y)")
                handler.post { onComplete?.invoke(false) }
            }
        }, handler)
    }

    private fun takeServiceScreenshot(onComplete: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Accessibility screenshot requires Android 11+")
            onComplete(null)
            return
        }

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                screenshot.colorSpace
                            )?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            Log.i(TAG, "Accessibility screenshot: ${bitmap?.width}x${bitmap?.height}")
                            onComplete(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Accessibility screenshot conversion failed", e)
                            onComplete(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Accessibility screenshot failed: code=$errorCode")
                        onComplete(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot failed", e)
            onComplete(null)
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
