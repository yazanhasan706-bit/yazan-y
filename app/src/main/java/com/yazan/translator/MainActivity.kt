package com.yazan.translator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    companion object {
        private const val SCREEN_CAPTURE_REQUEST_CODE = 1001
        const val START_PROJECTION_SERVICE = "START_PROJECTION_SERVICE"
        private const val RESULT_CODE = "RESULT_CODE"
        private const val RESULT_DATA = "RESULT_DATA"
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

        // تشغيل الفقاعة فقط إذا كانت صلاحية الظهور فوق التطبيقات مفعلة.
        // لا نشغّلها كـForeground Service من هنا.
        if (Settings.canDrawOverlays(this)) {
            startBubbleService()
        } else {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )

            startActivity(overlayIntent)
        }
    }

    override fun onResume() {
        super.onResume()

        if (Settings.canDrawOverlays(this)) {
            startBubbleService()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        setIntent(intent)
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

            showMessage(
                "حدث خطأ عند طلب التقاط الشاشة:\n${e.message}"
            )
        }
    }

    private fun startBubbleService() {

        try {

            val serviceIntent =
                Intent(
                    this,
                    FloatingBubbleService::class.java
                )

            // الخدمة هنا هدفها إظهار الفقاعة فقط.
            // لا نستخدم startForegroundService قبل موافقة MediaProjection.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

        } catch (e: Exception) {

            showMessage(
                "تعذر تشغيل الفقاعة:\n${e.message}"
            )
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

        if (requestCode != SCREEN_CAPTURE_REQUEST_CODE) {
            return
        }

        if (
            resultCode != Activity.RESULT_OK ||
            data == null
        ) {

            showMessage(
                "لم يتم السماح بالتقاط الشاشة."
            )

            return
        }

        try {

            val serviceIntent =
                Intent(
                    this,
                    FloatingBubbleService::class.java
                )

            serviceIntent.putExtra(
                START_PROJECTION_SERVICE,
                true
            )

            serviceIntent.putExtra(
                RESULT_CODE,
                resultCode
            )

            serviceIntent.putExtra(
                RESULT_DATA,
                data
            )

            // الآن فقط نستخدم Foreground Service،
            // بعد أن وافق المستخدم على مشاركة الشاشة.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(
                    serviceIntent
                )

            } else {

                startService(
                    serviceIntent
                )
            }

            showMessage(
                "تم السماح بالتقاط الشاشة ✅\n\nجاهز لالتقاط الشاشة."
            )

            finish()

        } catch (e: Exception) {

            showMessage(
                "تعذر تشغيل خدمة التقاط الشاشة:\n${e.message}"
            )
        }
    }

    private fun showMessage(message: String) {

        val resultText =
            findViewById<TextView>(R.id.resultText)

        resultText.text = message
    }
}