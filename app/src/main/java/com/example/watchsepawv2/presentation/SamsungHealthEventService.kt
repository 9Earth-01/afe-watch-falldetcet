package com.example.watchsepawv2.presentation

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.HealthEvent

class SamsungHealthEventService : PassiveListenerService() {
    companion object {
        private const val TAG = "SamsungHealthEventService"
    }

    override fun onHealthEventReceived(event: HealthEvent) {
        super.onHealthEventReceived(event)
        Log.i(TAG, "Samsung Health event received: ${event.type}")
        if (event.type == HealthEvent.Type.FALL_DETECTED &&
            MyPreferenceData(this).getFallMode() == MyPreferenceData.FALL_MODE_SAMSUNG
        ) {
            FallAlertNotifier.show(this)
        }
    }
}
