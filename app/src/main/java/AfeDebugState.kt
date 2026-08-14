package com.example.watchsepawv2.presentation

object AfeDebugState {
    var latProbability: Float = 0f
    var latResult: Int = -1
    var sensorSampleCount: Int = 0

    var afeStateText: String = "State: -"

    var temperature: String = "-"
    var heartRate: String = "-"
    var distance: String = "-"

    var svmA: Float = 0f
    var svmG: Float = 0f
    var pitch: Float = 0f
    var roll: Float = 0f
    var yaw: Float = 0f

    var lastUpdated: Long = 0L
}
