package com.arunk140.ou

import ScreenshotHelper
import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi

class OverlayOU : Activity() {
    private lateinit var screenshotHelper: ScreenshotHelper
    private var screenshotService: ScreenshotService? = null
    private var resultCodeCache: Int = 0
    private var dataCache: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the activity to appear as a dialog
        window.requestFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_overlay_ou)

        // Set window properties
        window.apply {
            setGravity(Gravity.CENTER)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Make the background semi-transparent
//            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//            attributes.dimAmount = 0.6f

            // Make sure this activity appears above the lockscreen
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        // Close when clicking outside
        setFinishOnTouchOutside(true)

        // Initialize screenshot helper
        screenshotHelper = ScreenshotHelper(this)

        // Setup screenshot button
        findViewById<Button>(R.id.screenshotButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                takeScreenshot()
            } else {
                Toast.makeText(this, "Screenshots require Android 11 or higher", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenshotService.LocalBinder
            screenshotService = binder.getService()
            // If we have cached screenshot data, process it
            if (dataCache != null) {
                screenshotService?.startProjection(resultCodeCache, dataCache!!) { bitmap ->
                    bitmap?.let {
                        // Show our overlay again
                        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                        window.decorView.alpha = 1f

                        saveBitmapToGallery(it)
                        Toast.makeText(this@OverlayOU,
                            "Screenshot saved!", Toast.LENGTH_SHORT).show()
                    }
                }
                dataCache = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenshotService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshot() {
        screenshotHelper.takeScreenshot { bitmap ->
            bitmap?.let {
                saveBitmapToGallery(it)
                Toast.makeText(this, "Screenshot saved!", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Failed to take screenshot", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ScreenshotHelper.SCREENSHOT_PERMISSION_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Cache the result and start binding to service
                resultCodeCache = resultCode
                dataCache = data
                startAndBindScreenshotService()
            } else {
                // Show our overlay again
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                window.decorView.alpha = 1f
                Toast.makeText(this, "Screenshot permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startAndBindScreenshotService() {
        val serviceIntent = Intent(this, ScreenshotService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind from service
        screenshotService?.let {
            unbindService(serviceConnection)
        }
    }



    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "Screenshot_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { imageUri ->
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
    }
}