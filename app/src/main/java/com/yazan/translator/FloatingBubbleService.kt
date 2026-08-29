package com.yazan.translator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler =
        Handler(Looper.getMainLooper())

    companion object {

        private const val CHANNEL_ID =
            "screen_capture_channel"

        private const val NOTIFICATION_ID =
            1001

        private const val START_PROJECTION_SERVICE =
            "START_PROJECTION_SERVICE"

        private const val RESULT_CODE =
            "RESULT_CODE"

        private const val RESULT_DATA =
            "RESULT_DATA"
    }

    override fun onCreate() {
        super.onCreate()

        createBubble()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.getBooleanExtra(
                START_PROJECTION_SERVICE,
                false
            ) == true
        ) {

            val resultCode =
                intent.getIntExtra(
                    RESULT_CODE,
                    0
                )

            val resultData: Intent? =
                if (Build.VERSION.SDK_INT >= 33) {

                    intent.getParcelableExtra(
                        RESULT_DATA,
                        Intent::class.java
                    )

                } else {

                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(
                        RESULT_DATA
                    )
                }

            if (
                resultCode != 0 &&
                resultData != null
            ) {

                startProjection(
                    resultCode,
                    resultData
                )
            }
        }

        return START_STICKY
    }

    private fun startProjection(
        resultCode: Int,
        resultData: Intent
    ) {

        if (mediaProjection != null) {
            return
        }

        try {

            createNotificationChannel()

            val notification =
                createNotification()

            if (Build.VERSION.SDK_INT >= 29) {

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )

            } else {

                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                manager.getMediaProjection(
                    resultCode,
                    resultData
                )

            if (mediaProjection == null) {

                Toast.makeText(
                    this,
                    "تعذر تفعيل التقاط الشاشة",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "خطأ في خدمة التقاط الشاشة:\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun captureScreen() {

        val projection =
            mediaProjection

        if (projection == null) {

            Toast.makeText(
                this,
                "التقاط الشاشة غير جاهز",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (virtualDisplay != null) {
            return
        }

        try {

            val metrics =
                resources.displayMetrics

            val width =
                metrics.widthPixels

            val height =
                metrics.heightPixels

            val density =
                metrics.densityDpi

            if (
                width <= 0 ||
                height <= 0 ||
                density <= 0
            ) {

                Toast.makeText(
                    this,
                    "تعذر معرفة أبعاد الشاشة",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            imageReader =
                ImageReader.newInstance(
                    width,
                    height,
                    PixelFormat.RGBA_8888,
                    2
                )

            imageReader?.setOnImageAvailableListener(
                { reader ->

                    val image =
                        reader.acquireLatestImage()
                            ?: return@setOnImageAvailableListener

                    try {

                        val plane =
                            image.planes[0]

                        val buffer =
                            plane.buffer

                        val pixelStride =
                            plane.pixelStride

                        val rowStride =
                            plane.rowStride

                        val rowPadding =
                            rowStride -
                                    pixelStride * width

                        val bitmapWidth =
                            width +
                                    rowPadding /
                                    pixelStride

                        val bitmap =
                            Bitmap.createBitmap(
                                bitmapWidth,
                                height,
                                Bitmap.Config.ARGB_8888
                            )

                        buffer.rewind()

                        bitmap.copyPixelsFromBuffer(
                            buffer
                        )

                        val croppedBitmap =
                            Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                width,
                                height
                            )

                        bitmap.recycle()

                        saveScreenshot(
                            croppedBitmap
                        )

                        croppedBitmap.recycle()

                    } catch (e: Exception) {

                        handler.post {

                            Toast.makeText(
                                this,
                                "فشل التقاط الشاشة:\n${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } finally {

                        image.close()

                        releaseCaptureObjects()
                    }

                },
                handler
            )

            virtualDisplay =
                projection.createVirtualDisplay(
                    "YazanTranslatorCapture",
                    width,
                    height,
                    density,
                    DisplayManager
                        .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null,
                    handler
                )

        } catch (e: Exception) {

            releaseCaptureObjects()

            Toast.makeText(
                this,
                "تعذر إنشاء التقاط الشاشة:\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveScreenshot(
        bitmap: Bitmap
    ) {

        try {

            val file =
                File(
                    cacheDir,
                    "screen_capture.png"
                )

            FileOutputStream(file).use { output ->

                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    output
                )
            }

            handler.post {

                Toast.makeText(
                    this,
                    "تم التقاط الشاشة ✅",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {

            handler.post {

                Toast.makeText(
                    this,
                    "تعذر حفظ لقطة الشاشة",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun releaseCaptureObjects() {

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }

        imageReader = null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Screen Translation",
                    NotificationManager.IMPORTANCE_LOW
                )

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        return if (Build.VERSION.SDK_INT >= 26) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Yazan Translator"
                )
                .setContentText(
                    "جاهز لالتقاط الشاشة"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(
                    "Yazan Translator"
                )
                .setContentText(
                    "جاهز لالتقاط الشاشة"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()
        }
    }

    private fun createBubble() {

        val textView =
            TextView(this)

        bubbleView =
            textView.apply {

                text = "أ"

                textSize = 22f

                gravity = Gravity.CENTER

                setBackgroundResource(
                    android.R.drawable.btn_default
                )

                setOnTouchListener(
                    BubbleTouchListener()
                )
            }

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        val params =
            WindowManager.LayoutParams(
                120,
                120,
                WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.START

        params.x = 30
        params.y = 200

        try {

            windowManager.addView(
                bubbleView,
                params
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "تعذر إظهار الفقاعة:\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {

        releaseCaptureObjects()

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null

        if (::bubbleView.isInitialized) {

            try {

                windowManager.removeView(
                    bubbleView
                )

            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    private inner class BubbleTouchListener :
        View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0

        private var initialTouchX = 0f
        private var initialTouchY = 0f

        private var moved = false

        override fun onTouch(
            view: View?,
            event: MotionEvent
        ): Boolean {

            val params =
                bubbleView.layoutParams
                        as WindowManager.LayoutParams

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    initialX = params.x
                    initialY = params.y

                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    moved = false

                    return true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        event.rawX -
                                initialTouchX

                    val dy =
                        event.rawY -
                                initialTouchY

                    if (
                        abs(dx) > 10 ||
                        abs(dy) > 10
                    ) {
                        moved = true
                    }

                    params.x =
                        initialX +
                                dx.toInt()

                    params.y =
                        initialY +
                                dy.toInt()

                    try {

                        windowManager
                            .updateViewLayout(
                                bubbleView,
                                params
                            )

                    } catch (_: Exception) {
                    }

                    return true
                }

                MotionEvent.ACTION_UP -> {

                    if (!moved) {

                        captureScreen()
                    }

                    return true
                }
            }

            return false
        }
    }
}