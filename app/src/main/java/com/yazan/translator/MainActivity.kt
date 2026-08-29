package com.yazan.translator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

private lateinit var mediaProjectionManager: MediaProjectionManager

companion object {
    private const val SCREEN_CAPTURE_REQUEST_CODE = 1001
    private const val START_SCREEN_CAPTURE = "START_SCREEN_CAPTURE"
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    mediaProjectionManager =
        getSystemService(MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

    val translateButton =
        findViewById<Button>(R.id.translateButton)

    translateButton.setOnClickListener {
        requestScreenCapture()
    }

    if (Settings.canDrawOverlays(this)) {
        startBubbleService()
    } else {
        val overlayIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        startActivity(overlayIntent)
    }

    handleBubbleIntent(intent)
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)

    setIntent(intent)

    handleBubbleIntent(intent)
}

private fun handleBubbleIntent(intent: Intent?) {

    if (
        intent?.getBooleanExtra(
            START_SCREEN_CAPTURE,
            false
        ) == true
    ) {

        requestScreenCapture()

        intent.removeExtra(START_SCREEN_CAPTURE)
    }
}

private fun requestScreenCapture() {

    val captureIntent =
        mediaProjectionManager.createScreenCaptureIntent()

    startActivityForResult(
        captureIntent,
        SCREEN_CAPTURE_REQUEST_CODE
    )
}

private fun startBubbleService() {

    val serviceIntent =
        Intent(this, FloatingBubbleService::class.java)

    startService(serviceIntent)
}

override fun onResume() {
    super.onResume()

    if (Settings.canDrawOverlays(this)) {
        startBubbleService()
    }
}

@Deprecated("Deprecated in Java")
override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
) {
    super.onActivityResult(
        requestCode,
        resultCode,
        data
    )

    if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {

        val resultText =
            findViewById<TextView>(R.id.resultText)

        if (
            resultCode == Activity.RESULT_OK &&
            data != null
        ) {

            resultText.text =
                "تم السماح بالتقاط الشاشة ✅\n\nجاهز لالتقاط الشاشة."
        } else {

            resultText.text =
                "لم يتم السماح بالتقاط الشاشة."
        }
    }
}

}