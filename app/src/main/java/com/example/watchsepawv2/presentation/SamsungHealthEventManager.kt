package com.example.watchsepawv2.presentation

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.HealthEvent
import androidx.health.services.client.data.PassiveListenerConfig

object SamsungHealthEventManager {
    private const val TAG = "SamsungHealthEventManager"

    fun register(context: Context) {
        val appContext = context.applicationContext
        val passiveMonitoringClient = HealthServices.getClient(appContext).passiveMonitoringClient
        val config = PassiveListenerConfig.builder()
            .setHealthEventTypes(setOf(HealthEvent.Type.FALL_DETECTED))
            .build()

        val future = passiveMonitoringClient.setPassiveListenerServiceAsync(
            SamsungHealthEventService::class.java,
            config
        )
        future.addListener(
            {
                try {
                    future.get()
                    Log.i(TAG, "Registered for Samsung Health fall events")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register Samsung Health fall events: ${e.message}", e)
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun unregister(context: Context) {
        val appContext = context.applicationContext
        val passiveMonitoringClient = HealthServices.getClient(appContext).passiveMonitoringClient
        val future = passiveMonitoringClient.clearPassiveListenerServiceAsync()
        future.addListener(
            {
                try {
                    future.get()
                    Log.i(TAG, "Unregistered Samsung Health fall events")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister Samsung Health fall events: ${e.message}", e)
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }
}
