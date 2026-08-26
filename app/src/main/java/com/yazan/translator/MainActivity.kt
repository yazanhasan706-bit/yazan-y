package com.yazan.translator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val translateButton = findViewById<Button>(R.id.translateButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        translateButton.setOnClickListener {
            resultText.text = "تم الضغط! سنضيف ترجمة الشاشة في الخطوة التالية."
        }
    }
}