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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val translateButton = findViewById<Button>(R.id.translateButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        translateButton.setOnClickListener {

            resultText.text = "جاري تجهيز قراءة الشاشة..."

            val captureIntent =
                mediaProjectionManager.createScreenCaptureIntent()

            startActivityForResult(
                captureIntent,
                SCREEN_CAPTURE_REQUEST_CODE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {

            if (resultCode == Activity.RESULT_OK && data != null) {

                findViewById<TextView>(R.id.resultText).text =
                    "تم السماح بالتقاط الشاشة.\n\nالخطوة التالية: قراءة النص من الشاشة."

            } else {

                findViewById<TextView>(R.id.resultText).text =
                    "لم يتم السماح بالتقاط الشاشة."
            }
        }
    }
}