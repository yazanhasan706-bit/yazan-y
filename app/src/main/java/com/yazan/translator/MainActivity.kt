package com.yazan.translator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    companion object {
        private const val SCREEN_CAPTURE_REQUEST_CODE = 1001
        const val START_PROJECTION_SERVICE = "START_PROJECTION_SERVICE"
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
    }

    private fun requestScreenCapture() {

        try {
            val captureIntent =
                mediaProjectionManager.createScreenCaptureIntent()

            startActivityForResult(
                captureIntent,
                SCREEN_CAPTURE_REQUEST_CODE
            )

        } catch (e: Exception) {

            val resultText =
                findViewById<TextView>(R.id.resultText)

            resultText.text =
                "حدث خطأ عند طلب التقاط الشاشة:\n${e.message}"
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
                    "تم السماح بالتقاط الشاشة ✅\n\nالخطوة التالية: التقاط الشاشة."

            } else {

                resultText.text =
                    "لم يتم السماح بالتقاط الشاشة."
            }
        }
    }
}