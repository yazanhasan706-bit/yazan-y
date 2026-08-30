package com.yazan.translator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private val handler =
        Handler(Looper.getMainLooper())

    private var translator: Translator? = null

    private val translationViews =
        mutableListOf<View>()

    private var dismissTouchView: View? = null

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

        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        createBubble()
        createTranslator()
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

                showToast(
                    "تعذر تفعيل التقاط الشاشة"
                )

                return
            }

            projectionCallback =
                object : MediaProjection.Callback() {

                    override fun onStop() {

                        handler.post {

                            releaseCaptureObjects()

                            mediaProjection = null

                            removeAllTranslations()

                            showToast(
                                "تم إيقاف مشاركة الشاشة"
                            )
                        }
                    }
                }

            mediaProjection?.registerCallback(
                projectionCallback!!,
                handler
            )

            showToast(
                "تم تفعيل التقاط الشاشة ✅"
            )

        } catch (e: Exception) {

            showToast(
                "خطأ في خدمة التقاط الشاشة:\n${e.message}"
            )
        }
    }

    private fun captureScreen() {

        val projection =
            mediaProjection

        if (projection == null) {

            showToast(
                "التقاط الشاشة غير جاهز"
            )

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

                showToast(
                    "تعذر معرفة أبعاد الشاشة"
                )

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

                        val cropped =
                            Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                width,
                                height
                            )

                        bitmap.recycle()

                        processImage(
                            cropped
                        )

                    } catch (e: Exception) {

                        showToast(
                            "فشل تجهيز الصورة:\n${e.message}"
                        )

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

            showToast(
                "جارٍ التقاط الشاشة..."
            )

        } catch (e: Exception) {

            releaseCaptureObjects()

            showToast(
                "تعذر إنشاء التقاط الشاشة:\n${e.message}"
            )
        }
    }

    private fun processImage(
        bitmap: Bitmap
    ) {

        handler.post {

            showToast(
                "جارٍ قراءة النص..."
            )
        }

        val inputImage =
            InputImage.fromBitmap(
                bitmap,
                0
            )

        val recognizer =
            TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

        recognizer.process(
            inputImage
        )
            .addOnSuccessListener { result ->

                val words =
                    mutableListOf<WordData>()

                for (block in result.textBlocks) {

                    for (line in block.lines) {

                        for (
                            element in line.elements
                        ) {

                            val text =
                                element.text.trim()

                            val box =
                                element.boundingBox

                            if (
                                !text.isNullOrEmpty() &&
                                box != null &&
                                containsEnglish(text)
                            ) {

                                words.add(
                                    WordData(
                                        text,
                                        Rect(box)
                                    )
                                )
                            }
                        }
                    }
                }

                bitmap.recycle()

                if (words.isEmpty()) {

                    showToast(
                        "لم يتم العثور على كلمات إنجليزية"
                    )

                    return@addOnSuccessListener
                }

                translateWords(words)
            }
            .addOnFailureListener { e ->

                bitmap.recycle()

                showToast(
                    "فشل OCR:\n${e.message}"
                )
            }
    }

    private fun containsEnglish(
        text: String
    ): Boolean {

        for (character in text) {

            if (
                character in 'A'..'Z' ||
                character in 'a'..'z'
            ) {

                return true
            }
        }

        return false
    }

    private fun translateWords(
        words: List<WordData>
    ) {

        val currentTranslator =
            translator

        if (currentTranslator == null) {

            showToast(
                "محرك الترجمة غير جاهز"
            )

            return
        }

        val conditions =
            DownloadConditions.Builder()
                .build()

        currentTranslator
            .downloadModelIfNeeded(
                conditions
            )
            .addOnSuccessListener {

                val translatedWords =
                    mutableListOf<TranslatedWord>()

                fun translateNext(
                    index: Int
                ) {

                    if (
                        index >= words.size
                    ) {

                        showTranslations(
                            translatedWords
                        )

                        return
                    }

                    val word =
                        words[index]

                    currentTranslator
                        .translate(
                            word.text
                        )
                        .addOnSuccessListener { arabic ->

                            val translated =
                                arabic.trim()

                            if (
                                translated.isNotEmpty()
                            ) {

                                translatedWords.add(
                                    TranslatedWord(
                                        word,
                                        translated
                                    )
                                )
                            }

                            translateNext(
                                index + 1
                            )
                        }
                        .addOnFailureListener {

                            translateNext(
                                index + 1
                            )
                        }
                }

                translateNext(0)
            }
            .addOnFailureListener { e ->

                showToast(
                    "تعذر تحميل نموذج الترجمة:\n${e.message}"
                )
            }
    }

    private fun showTranslations(
        translations:
            List<TranslatedWord>
    ) {

        handler.post {

            removeAllTranslations()

            if (translations.isEmpty()) {

                showToast(
                    "لم تنجح الترجمة"
                )

                return@post
            }

            for (
                item in translations
            ) {

                addTranslationView(
                    item.word.box,
                    item.translation
                )
            }

            createDismissTouchLayer()

            showToast(
                "تمت الترجمة ✅"
            )
        }
    }

    private fun addTranslationView(
        box: Rect,
        translation: String
    ) {

        val textView =
            TextView(this)

        textView.text =
            translation

        textView.gravity =
            Gravity.CENTER

        textView.setTextColor(
            Color.WHITE
        )

        textView.setBackgroundColor(
            Color.argb(
                225,
                0,
                0,
                0
            )
        )

        textView.setPadding(
            3,
            0,
            3,
            0
        )

        val width =
            box.width()
                .coerceAtLeast(45)

        val height =
            box.height()
                .coerceAtLeast(30)

        val textSize =
            (
                box.height() * 0.65f
            ).coerceIn(
                10f,
                28f
            )

        textView.textSize =
            textSize

        val params =
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams
                    .FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or
            Gravity.START

        params.x =
            box.left

        params.y =
            box.top

        try {

            windowManager.addView(
                textView,
                params
            )

            translationViews.add(
                textView
            )

        } catch (e: Exception) {

            showToast(
                "تعذر إظهار الترجمة:\n${e.message}"
            )
        }
    }

    /*
     * طبقة شفافة تستقبل أول لمسة على الشاشة.
     *
     * عند أول لمسة:
     * 1. تحذف جميع الترجمات.
     * 2. تحذف طبقة اللمس.
     * 3. تعود الشاشة للوضع الطبيعي.
     */
    private fun createDismissTouchLayer() {

        removeDismissTouchLayer()

        val touchView =
            View(this)

        touchView.setBackgroundColor(
            Color.TRANSPARENT
        )

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or
            Gravity.START

        touchView.setOnTouchListener { _, event ->

            if (
                event.action ==
                MotionEvent.ACTION_DOWN
            ) {

                removeAllTranslations()

                removeDismissTouchLayer()

                true

            } else {

                true
            }
        }

        try {

            windowManager.addView(
                touchView,
                params
            )

            dismissTouchView =
                touchView

        } catch (e: Exception) {

            showToast(
                "تعذر تفعيل لمس الإخفاء:\n${e.message}"
            )
        }
    }

    private fun removeDismissTouchLayer() {

        val view =
            dismissTouchView
                ?: return

        try {

            windowManager.removeView(
                view
            )

        } catch (_: Exception) {
        }

        dismissTouchView = null
    }

    private fun removeAllTranslations() {

        removeDismissTouchLayer()

        val views =
            translationViews.toList()

        translationViews.clear()

        for (view in views) {

            try {

                windowManager.removeView(
                    view
                )

            } catch (_: Exception) {
            }
        }
    }

    private fun createTranslator() {

        translator =
            Translation.getClient(
                com.google.mlkit.nl.translate.TranslatorOptions
                    .Builder()
                    .setSourceLanguage(
                        TranslateLanguage.ENGLISH
                    )
                    .setTargetLanguage(
                        TranslateLanguage.ARABIC
                    )
                    .build()
            )
    }

    private fun createBubble() {

        val textView =
            TextView(this)

        bubbleView =
            textView.apply {

                text = "أ"

                textSize = 22f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setBackgroundResource(
                    android.R.drawable.btn_default
                )

                setOnTouchListener(
                    BubbleTouchListener()
                )
            }

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
            Gravity.TOP or
            Gravity.START

        params.x = 30
        params.y = 200

        try {

            windowManager.addView(
                bubbleView,
                params
            )

        } catch (e: Exception) {

            showToast(
                "تعذر إظهار الفقاعة:\n${e.message}"
            )
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

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification():
        Notification {

        return if (
            Build.VERSION.SDK_INT >= 26
        ) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Yazan Translator"
                )
                .setContentText(
                    "جاهز لترجمة الشاشة"
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
                    "جاهز لترجمة الشاشة"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()
        }
    }

    private fun showToast(
        message: String
    ) {

        handler.post {

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {

        removeAllTranslations()

        releaseCaptureObjects()

        try {

            if (
                mediaProjection != null &&
                projectionCallback != null
            ) {

                mediaProjection
                    ?.unregisterCallback(
                        projectionCallback!!
                    )
            }

        } catch (_: Exception) {
        }

        projectionCallback = null

        try {

            mediaProjection?.stop()

        } catch (_: Exception) {
        }

        mediaProjection = null

        translator?.close()

        translator = null

        if (
            ::bubbleView.isInitialized
        ) {

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

    private inner class
        BubbleTouchListener :
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

            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {

                    initialX =
                        params.x

                    initialY =
                        params.y

                    initialTouchX =
                        event.rawX

                    initialTouchY =
                        event.rawY

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

                        removeAllTranslations()

                        captureScreen()
                    }

                    return true
                }
            }

            return false
        }
    }

    private data class WordData(
        val text: String,
        val box: Rect
    )

    private data class TranslatedWord(
        val word: WordData,
        val translation: String
    )
}