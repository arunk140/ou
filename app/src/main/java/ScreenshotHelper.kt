import android.app.Activity
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
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class ScreenshotHelper(private val activity: Activity) {
    private var screenshotCallback: ((Bitmap?) -> Unit)? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    init {
        mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        screenshotCallback = callback

        // Hide our overlay before taking screenshot
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        activity.window.decorView.alpha = 0f

        // Launch screenshot intent
        activity.startActivityForResult(
            mediaProjectionManager?.createScreenCaptureIntent(),
            SCREENSHOT_PERMISSION_CODE
        )
    }

    companion object {
        const val SCREENSHOT_PERMISSION_CODE = 100
    }
}