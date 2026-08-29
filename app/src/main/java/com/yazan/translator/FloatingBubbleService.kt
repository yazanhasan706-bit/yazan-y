package com.yazan.translator

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingBubbleService : Service() {

private lateinit var windowManager: WindowManager
private lateinit var bubbleView: View

override fun onCreate() {
    super.onCreate()

    windowManager =
        getSystemService(WINDOW_SERVICE) as WindowManager

    bubbleView = TextView(this).apply {

        text = "أ"
        textSize = 22f
        gravity = Gravity.CENTER

        setBackgroundResource(android.R.drawable.btn_default)

        setOnTouchListener(BubbleTouchListener())
    }

    val params = WindowManager.LayoutParams(
        120,
        120,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    params.gravity = Gravity.TOP or Gravity.START
    params.x = 30
    params.y = 200

    windowManager.addView(bubbleView, params)
}

override fun onDestroy() {
    super.onDestroy()

    if (::bubbleView.isInitialized) {
        windowManager.removeView(bubbleView)
    }
}

override fun onBind(intent: Intent?): IBinder? {
    return null
}

private inner class BubbleTouchListener : View.OnTouchListener {

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
            bubbleView.layoutParams as WindowManager.LayoutParams

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

                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (kotlin.math.abs(dx) > 10 ||
                    kotlin.math.abs(dy) > 10
                ) {
                    moved = true
                }

                params.x = initialX + dx.toInt()
                params.y = initialY + dy.toInt()

                windowManager.updateViewLayout(
                    bubbleView,
                    params
                )

                return true
            }

            MotionEvent.ACTION_UP -> {

                if (!moved) {

                    val intent = Intent(
                        this@FloatingBubbleService,
                        MainActivity::class.java
                    )

                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )

                    startActivity(intent)
                }

                return true
            }
        }

        return false
    }
}

}