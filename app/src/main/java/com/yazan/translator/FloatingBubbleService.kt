package com.yazan.translator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class FloatingBubbleService : Service() {

private lateinit var windowManager: WindowManager
private lateinit var bubbleView: View

private var mediaProjection: MediaProjection? = null

companion object {
    private const val CHANNEL_ID = "screen_capture_channel"
    private const val NOTIFICATION_ID = 1001

    private const val START_PROJECTION_SERVICE =
        "START_PROJECTION_SERVICE"

    private const val RESULT_CODE = "RESULT_CODE"
    private const val RESULT_DATA = "RESULT_DATA"
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
                intent.getParcelableExtra(RESULT_DATA)
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

    createNotificationChannel()

    val notification =
        createNotification()

    if (Build.VERSION.SDK_INT >= 29) {

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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

        notificationManager.createNotificationChannel(
            channel
        )
    }
}

private fun createNotification(): Notification {

    return if (Build.VERSION.SDK_INT >= 26) {

        Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Yazan Translator")
            .setContentText("جاهز لالتقاط الشاشة")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

    } else {

        @Suppress("DEPRECATION")
        Notification.Builder(this)
            .setContentTitle("Yazan Translator")
            .setContentText("جاهز لالتقاط الشاشة")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}

private fun createBubble() {

    val textView =
        TextView(this)

    bubbleView = textView.apply {

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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

    params.gravity =
        Gravity.TOP or Gravity.START

    params.x = 30
    params.y = 200

    windowManager.addView(
        bubbleView,
        params
    )
}

override fun onDestroy() {

    mediaProjection?.stop()
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
                    event.rawX - initialTouchX

                val dy =
                    event.rawY - initialTouchY

                if (
                    abs(dx) > 10 ||
                    abs(dy) > 10
                ) {
                    moved = true
                }

                params.x =
                    initialX + dx.toInt()

                params.y =
                    initialY + dy.toInt()

                windowManager.updateViewLayout(
                    bubbleView,
                    params
                )

                return true
            }

            MotionEvent.ACTION_UP -> {

                if (!moved) {

                    if (mediaProjection != null) {

                        android.widget.Toast.makeText(
                            this@FloatingBubbleService,
                            "التقاط الشاشة جاهز",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        val intent =
                            Intent(
                                this@FloatingBubbleService,
                                MainActivity::class.java
                            )

                        intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )

                        intent.putExtra(
                            "START_SCREEN_CAPTURE",
                            true
                        )

                        startActivity(intent)
                    }
                }

                return true
            }
        }

        return false
    }
}

}