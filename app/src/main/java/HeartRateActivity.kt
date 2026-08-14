package com.example.watchsepawv2.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper     // [CHANGED] ใช้ Looper กับ Handler
import android.widget.TextView
import com.example.watchsepawv2.R
import android.view.GestureDetector
import android.view.MotionEvent

class HeartRateActivity : Activity() {
    private lateinit var txtHeartRate: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtUnit: TextView
    private val handler = Handler(Looper.getMainLooper())   // [CHANGED]
    private lateinit var preferenceData: MyPreferenceData
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)
        txtHeartRate = findViewById(R.id.txtHeartRate)
        txtUnit = findViewById(R.id.txtUnit)
        //txtStatus = findViewById(R.id.txtStatus)
        preferenceData = MyPreferenceData(this)
        updateHeartRateDisplay()

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            // [CHANGED] e1 เป็น nullable ใน API 34
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (diffX < -100 && Math.abs(velocityX) > 100) {
                        // ปัดซ้าย → ไปหน้า Temperature
                        startActivity(Intent(this@HeartRateActivity, SkinTemperatureActivity::class.java))
                        return true
                    }
                }
                return false
            }
        })
    }

    // คงเดิม
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun updateHeartRateDisplay() {
        val runnable = object : Runnable {
            override fun run() {
                val rate = preferenceData.getHeartRate().toIntOrNull() ?: 0
                if (rate == 0) {
                    txtHeartRate.text = "--"
                    txtUnit.text = "กำลังวัดค่า..."
                } else {
                    txtHeartRate.text = rate.toString()
                    txtUnit.text = "ครั้งต่อนาที"
                }
                handler.postDelayed(this, 10_000)
            }
        }
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
