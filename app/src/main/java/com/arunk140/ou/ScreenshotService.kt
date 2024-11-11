package com.arunk140.ou

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class ScreenshotService : Service() {
    private var mediaProjection: MediaProjection? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ScreenshotService = this@ScreenshotService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    private fun startForeground() {
        val channelId = "screenshot_service_channel"
        val channelName = "Screenshot Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Taking Screenshot")
            .setContentText("Processing...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startProjection(resultCode: Int, data: Intent, callback: (Bitmap?) -> Unit) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)?.apply {
            // Register callback before creating virtual display
            registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    // Handle cleanup if needed
                }
            }, Handler(Looper.getMainLooper()))
        }
//        captureScreen(callback)
        Handler(Looper.getMainLooper()).postDelayed({
            captureScreen(callback)
        }, 1000)

    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreen(callback: (Bitmap?) -> Unit) {
        val metrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(WindowManager::class.java).currentWindowMetrics
        } else {
            @Suppress("DEPRECATION")
            val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            WindowMetrics(Rect(0, 0, metrics.widthPixels, metrics.heightPixels), WindowInsets.CONSUMED)
        }

        val width = metrics.bounds.width()
        val height = metrics.bounds.height()

        val imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, 1
        )

        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Screenshot",
            width, height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader.surface,
            null,
            null
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                val planes = it.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Cleanup
                image.close()
                virtualDisplay?.release()
                mediaProjection?.stop()
                stopSelf()

                Handler(Looper.getMainLooper()).post {
                    callback(bitmap)
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun onDestroy() {
        mediaProjection?.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 12345
    }
}