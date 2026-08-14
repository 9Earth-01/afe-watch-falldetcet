package com.example.watchsepawv2.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class HeartRateListener(private val context: Context) : SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null

    fun startListening() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartRateSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val heartRate = event.values[0]
            val pref = MyPreferenceData(context)
            pref.setHeartRate(heartRate.toInt().toString())

            // ตรวจสอบค่าสูงสุด/ต่ำสุด
            val max = pref.getMaxHeartRate().toIntOrNull() ?: 120
            val min = pref.getMinHeartRate().toIntOrNull() ?: 50
            Log.d("HR_TEST", "Heart Rate = $heartRate")

            val status = when {
                heartRate > max -> 1 // สูง
                heartRate < min -> -1 // ต่ำ
                else -> 0 // ปกติ
            }
            pref.setHeartRateStatus(status)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
