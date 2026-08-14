package com.example.watchsepawv2.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import com.example.watchsepawv2.R
import kotlin.math.abs

class SkinTemperatureActivity : Activity() {

    private lateinit var txtTemperature: TextView
    private lateinit var preferenceData: MyPreferenceData
    private val handler = Handler(Looper.getMainLooper())
    private val refreshIntervalMillis: Long = 10_000

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_temperature)

        txtTemperature = findViewById(R.id.txtTemperatureValue)
        preferenceData = MyPreferenceData(this)

        gestureDetector = GestureDetector(this, SwipeGestureListener())

        updateTemperatureDisplay()
        handler.postDelayed(refreshRunnable, refreshIntervalMillis)
    }

    private fun updateTemperatureDisplay() {
        val temp = preferenceData.getTemperature()
        val tempFormatted = temp.toFloatOrNull()?.let { "%.1f".format(it) } ?: "-"
        txtTemperature.text = "อุณหภูมิ: $tempFormatted °C"

        AfeDebugState.temperature = "$tempFormatted °C"
        AfeDebugState.lastUpdated = System.currentTimeMillis()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateTemperatureDisplay()
            handler.postDelayed(this, refreshIntervalMillis)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val swipeThreshold = 40
        private val swipeVelocityThreshold = 40

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (
                abs(diffX) > abs(diffY) &&
                abs(diffX) > swipeThreshold &&
                abs(velocityX) > swipeVelocityThreshold
            ) {
                if (diffX > 0) {
                    // ปัดขวา = กลับไปหน้า Heart Rate
                    startActivity(
                        Intent(
                            this@SkinTemperatureActivity,
                            HeartRateActivity::class.java
                        )
                    )
                    overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                    finish()
                }

                return true
            }

            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}