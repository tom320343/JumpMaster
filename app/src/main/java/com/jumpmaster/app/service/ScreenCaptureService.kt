package com.jumpmaster.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.jumpmaster.app.JumpMasterApp
import com.jumpmaster.app.MainActivity
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        const val ACTION_START = "com.jumpmaster.START_CAPTURE"
        const val ACTION_STOP = "com.jumpmaster.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private var instance: ScreenCaptureService? = null

        fun isRunning(): Boolean = instance != null

        fun captureScreen(): Bitmap? = instance?.captureOnce()

        fun setCaptureCallback(callback: ((Bitmap) -> Unit)?) {
            instance?.captureCallback = callback
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDpi = 320
    private var captureCallback: ((Bitmap) -> Unit)? = null
    private var lastBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "onCreate")
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDpi = metrics.densityDpi
            Log.i(TAG, "Screen: ${screenWidth}x${screenHeight} dpi=$screenDpi")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screen metrics", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                Log.d(TAG, "ACTION_START: resultCode=$resultCode, resultData=${resultData != null}")
                if (resultData != null) {
                    try {
                        val notification = createNotification()
                        Log.d(TAG, "Starting foreground with notification")
                        startForeground(1, notification)
                        Log.i(TAG, "Foreground started, beginning capture")
                        startCapture(resultCode, resultData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start foreground or capture", e)
                        stopSelf()
                    }
                } else {
                    Log.e(TAG, "No result data, stopping")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                Log.i(TAG, "ACTION_STOP")
                stopCapture()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        Log.d(TAG, "Creating notification, channel=${JumpMasterApp.CHANNEL_SCREEN_CAPTURE}")
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, JumpMasterApp.CHANNEL_SCREEN_CAPTURE)
            .setContentTitle("跳一跳助手")
            .setContentText("正在截屏分析...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        Log.i(TAG, "startCapture: getting MediaProjection")
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            Log.d(TAG, "MediaProjection obtained: ${mediaProjection != null}")

            // Android 14+ requires registering a callback before createVirtualDisplay
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    stopCapture()
                    stopSelf()
                }
            }, null)
            Log.d(TAG, "MediaProjection callback registered")

            val captureWidth = screenWidth
            val captureHeight = screenHeight
            Log.d(TAG, "Creating ImageReader: ${captureWidth}x${captureHeight}")

            imageReader = ImageReader.newInstance(
                captureWidth, captureHeight,
                PixelFormat.RGBA_8888, 2
            )

            Log.d(TAG, "Creating VirtualDisplay")
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "JumpMaster",
                captureWidth, captureHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, null
            )
            Log.i(TAG, "Capture started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "startCapture failed", e)
            stopSelf()
        }
    }

    fun captureOnce(): Bitmap? {
        try {
            val deadline = SystemClock.uptimeMillis() + 700
            var bitmap: Bitmap? = null

            while (SystemClock.uptimeMillis() < deadline) {
                val image = imageReader?.acquireLatestImage()
                if (image == null) {
                    SystemClock.sleep(50)
                    continue
                }

                try {
                    bitmap?.recycle()
                    bitmap = imageToBitmap(image)
                } finally {
                    image.close()
                }

                val currentBitmap = bitmap
                if (!isMostlyBlack(currentBitmap)) {
                    lastBitmap = currentBitmap
                    captureCallback?.invoke(currentBitmap)
                    return currentBitmap
                }
            }

            if (bitmap != null) {
                Log.w(TAG, "Only black frames were captured")
                lastBitmap = bitmap
                captureCallback?.invoke(bitmap)
            }
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "captureOnce failed", e)
            return null
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        buffer.rewind()

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var samples = 0
        var litSamples = 0

        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (red + green + blue > 36) litSamples++
                samples++
                x += stepX
            }
            y += stepY
        }

        return samples > 0 && litSamples < samples / 100
    }

    private fun stopCapture() {
        Log.i(TAG, "stopCapture")
        captureCallback = null
        try { virtualDisplay?.release() } catch (e: Exception) { Log.e(TAG, "release virtualDisplay", e) }
        virtualDisplay = null
        try { imageReader?.close() } catch (e: Exception) { Log.e(TAG, "close imageReader", e) }
        imageReader = null
        try { mediaProjection?.stop() } catch (e: Exception) { Log.e(TAG, "stop mediaProjection", e) }
        mediaProjection = null
        try { lastBitmap?.recycle() } catch (e: Exception) { Log.e(TAG, "recycle bitmap", e) }
        lastBitmap = null
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        stopCapture()
        instance = null
        super.onDestroy()
    }
}
