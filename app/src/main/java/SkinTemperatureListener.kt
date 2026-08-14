package com.example.watchsepawv2.presentation
/*
 * Copyright 2022 Samsung Electronics Co., Ltd. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.os.Handler
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

class SkinTemperatureListener(private val context: android.content.Context) {

    private var trackerDataSubject: TrackerDataSubject? = null
    private var skinTemperatureHandler: Handler? = null
    private var skinTemperatureTracker: HealthTracker? = null
    private var isHandlerRunning = false

    fun setTrackerDataSubject(subject: TrackerDataSubject) {
        trackerDataSubject = subject
    }

    fun setSkinTemperatureTracker(service: HealthTrackingService) {
        skinTemperatureTracker = service.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
    }

    fun setSkinTemperatureHandler(handler: Handler) {
        skinTemperatureHandler = handler
    }

    fun startTracker() {
        if (!isHandlerRunning && skinTemperatureTracker != null && skinTemperatureHandler != null) {
            skinTemperatureHandler!!.post {
                skinTemperatureTracker!!.setEventListener(object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(list: List<DataPoint>) {
                        for (data in list) updateSkinTemperature(data)
                    }

                    override fun onFlushCompleted() {
                        Log.i("SkinTemperatureListener", "Flush completed")
                    }

                    override fun onError(trackerError: HealthTracker.TrackerError) {
                        Log.e("SkinTemperatureListener", "Error: $trackerError")
                        when (trackerError) {
                            HealthTracker.TrackerError.PERMISSION_ERROR -> trackerDataSubject?.notifyError(1)
                            HealthTracker.TrackerError.SDK_POLICY_ERROR -> trackerDataSubject?.notifyError(2)
                            else -> {}
                        }
                    }
                })
                Log.i("SkinTemperatureListener", "Started tracker")
            }
            isHandlerRunning = true
        }
    }

    fun stopTracker() {
        skinTemperatureTracker?.unsetEventListener()
        skinTemperatureHandler?.removeCallbacksAndMessages(null)
        isHandlerRunning = false
    }

    private fun updateSkinTemperature(data: DataPoint) {
        try {
            val status = data.getValue(ValueKey.SkinTemperatureSet.STATUS)
            val temp = data.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)

            Log.d("SkinTemperatureListener", "Temp: $temp, Status: $status")

            // ✅ บันทึกลง MyPreferenceData
            val pref = MyPreferenceData(context)
            pref.setTemperature(temp.toString())
            pref.setTemperatureStatus(status)

            // ✅ แจ้ง observer ถ้ามี
            trackerDataSubject?.notifySkinTemperatureTrackerObservers(status, temp)

        } catch (e: Exception) {
            Log.e("SkinTemperatureListener", "Parse error: ${e.message}")
        }
    }
}
