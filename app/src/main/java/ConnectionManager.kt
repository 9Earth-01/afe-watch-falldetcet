
package com.example.watchsepawv2.presentation
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerCapability
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.example.watchsepawv2.presentation.SkinTemperatureListener
import com.example.watchsepawv2.presentation.ConnectionObserver

class ConnectionManager(private val connectionObserver: ConnectionObserver) {

    private val TAG = "ConnectionManager"
    private var healthTrackingService: HealthTrackingService? = null

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Connected to Health Tracking Service")
            connectionObserver.onConnectionResult(1)

            // 🔁 เช็คความสามารถของ service หลังจาก connect
            healthTrackingService?.let { service ->
                val capability = service.trackingCapability
                val isAvailable = capability?.supportHealthTrackerTypes?.contains(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS) == true
                connectionObserver.onSkinTemperatureAvailability(isAvailable)
            } ?: run {
                Log.e(TAG, "Service is null when checking tracker availability")
                connectionObserver.onSkinTemperatureAvailability(false)
            }
        }

        override fun onConnectionEnded() {
            Log.i(TAG, "Disconnected from Health Tracking Service")
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            Log.e(TAG, "Could not connect to Health Tracking Service: ${e.message}")
            connectionObserver.onConnectionResult(2)
            connectionObserver.onSkinTemperatureAvailability(false)
        }
    }

    fun connect(context: Context) {
        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService?.connectService()
    }

    fun disconnect() {
        healthTrackingService?.disconnectService()
    }

    fun initSkinTemperature(skinTemperatureListener: SkinTemperatureListener) {
        healthTrackingService?.let {
            skinTemperatureListener.setSkinTemperatureTracker(it)
            skinTemperatureListener.setSkinTemperatureHandler(Handler(Looper.getMainLooper()))
        }
    }
}

