package com.example.watchsepawv2.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.handmonitor.sensorlib.v3.SensorFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.google.android.gms.tasks.CancellationTokenSource

// 🟢 เพิ่ม TrackerObserver เข้ามาเพื่อรับค่าอุณหภูมิ
class BackgroundService : Service(), ConnectionObserver, TrackerObserver{

    // --------- Service infra ---------
    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "GPS_Tracking_Channel"
    private val FALL_ALERT_CHANNEL_ID = "FALL_ALERT_CHANNEL"
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var connectionManager: ConnectionManager
    private lateinit var skinTemperatureListener: SkinTemperatureListener
    private lateinit var trackerDataSubject: TrackerDataSubject

    // 🟢 ตัวแปรอินเทอร์เน็ต สร้างครั้งเดียวใช้ตลอดกาล (ลดอาการแอปค้าง/Memory Leak)
    private val httpClient = OkHttpClient()

    // flags สำหรับควบคุมการเริ่ม tracker
    private var isHealthConnected = false
    private var isSkinTempAvailable = false
    private var isSkinTempStarted = false

    // 🟢 ตัวแปรสำหรับเช็คว่าเปิด HR ไปหรือยัง (แก้บัคชีพจรค้าง)
    private var isHrStarted = false

    // --------- GPS continuous updates ---------
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_INTERVAL_MS = 10_000L

    // --------- Sensors / fall detection ---------
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isFallDetected = false

    private lateinit var heartRateListener: HeartRateListener

    private var GyroX = 0f
    private var GyroY = 0f
    private var GyroZ = 0f

    private var svmA = 0f
    private var svmG = 0f
    private var pitchDeg = 0f
    private var rollDeg = 0f
    private var yawDeg = 0f

    private val IMPACT_A_THR = 47.40f
    private val IMPACT_G_THR = 9.20f
    private val IMPACT_PAIRING_MS = 300L

    private val EULER_DELTA_THR_DEG = 71.19f
    private val POSTURE_WINDOW_MS = 1500L
    private val COOLDOWN_MS = 10_000L

    private enum class State { IDLE, IMPACT, POSTURE, COOLDOWN }
    private var state = State.IDLE
    private var tImpact = 0L
    private var tStateEntered = 0L

    private var pitchAtImpact = 0f
    private var rollAtImpact = 0f
    private var yawAtImpact = 0f
    private var aPeak = 0f
    private var gPeak = 0f
    private var dPitchMax = 0f
    private var dRollMax = 0f
    private var dYawMax = 0f

    private val TAG_RAW = "FALL_RAW"
    private val TAG_STATE = "FALL_STATE"
    private val TAG_EVT = "FALL_EVT"
    private val LOG_RAW_EVERY_MS = 250L
    private var lastRawLog = 0L

    // --------- SensorLib ---------
    private lateinit var sensorFlow: SensorFlow
    private val sensorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --------- LAT Model ---------
    private lateinit var latClassifier: LatClassifier
    private val LAT_WINDOW_SIZE = 40
    private val LAT_FEATURE_SIZE = 6
    private val LAT_THRESHOLD = 0.5f

    // 🟢 pool ที่สร้างครั้งเดียว reuse ตลอดอายุ service
    // ⚠️ ปลอดภัยเพราะ handleSensorWindow ถูกเรียกทีละครั้งจาก coroutine เดียว (ไม่ concurrent)
    private val rawWindowPool = Array(LAT_WINDOW_SIZE) { FloatArray(LAT_FEATURE_SIZE) }
    private val normalizedWindowPool = Array(LAT_WINDOW_SIZE) { FloatArray(LAT_FEATURE_SIZE) }

    // --------- LAT Normalization values ---------
    private val LAT_MEAN = floatArrayOf(
        0.10759267f, -4.56684160f, 1.42213023f, -0.01850153f, -0.01588123f, 0.00818511f
    )
    private val LAT_STD = floatArrayOf(
        6.97592974f, 4.38509321f, 4.52552319f, 1.57675147f, 1.07482123f, 1.24845302f
    )

    // --------- AFE 3-State Sensor Control ---------
    private enum class AfeControlState {
        STATE_1_HOME,      // Home area / low-power sensor mode
        STATE_2_TRACKING,  // GPS verification and tracking
        STATE_3_ALERT      // Outside safe zone sensor mode
    }

    private var currentAfeControlState: AfeControlState? = null
    private var latestDistance: Double? = null

    private val latBuffer = ArrayDeque<Int>()
    private var isCollectingLat = false
    private val LAT_BUFFER_SIZE = 10
    private val LAT_TRIGGER_COUNT = 6

    private val GPS_STATE_2_INTERVAL_MS = 3 * 60 * 1000L // 3 นาที
    private val GPS_STATE_3_INTERVAL_MS = 3 * 1000L      // 3 วินาที
    private var currentGpsIntervalMs: Long? = null

    // 🟢 ระบบเล่นเสียง
    private var serviceMediaPlayer: android.media.MediaPlayer? = null

    private fun playAlertSoundInService(soundResId: Int) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)

            serviceMediaPlayer?.release()
            serviceMediaPlayer = android.media.MediaPlayer.create(this, soundResId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
                serviceMediaPlayer?.setAudioAttributes(audioAttributes)
            }

            serviceMediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                serviceMediaPlayer = null
            }
            serviceMediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("SOUND_ERROR", "เล่นเสียงไม่ได้: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GPS Tracking"
            val descriptionText = "Tracking location and temperature in the background"
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackgroundService::WakeLock")
            wakeLock.acquire(10 * 60 * 1000L)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS & Temp Tracking")
            .setContentText("Tracking in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Health connections
        connectionManager = ConnectionManager(this)
        skinTemperatureListener = SkinTemperatureListener(this)
        trackerDataSubject = TrackerDataSubject()

        //ผูกตัวแปรเพื่อรอรับค่าอุณหภูมิเข้า Service (แก้ปัญหา Temp เป็น 0.0)
        trackerDataSubject.addObserver(this)

        skinTemperatureListener.setTrackerDataSubject(trackerDataSubject)
        connectionManager.connect(applicationContext)

        // ---- Sensors ----
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { acc ->
            sensorManager.registerListener(
                fallSensorListener,
                acc,
                SensorManager.SENSOR_DELAY_GAME,
                0
            )
        } ?: Log.e("Sensor", "Accelerometer not available on this device")

        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroSensor != null) {
            sensorManager.registerListener(
                gyroListener,
                gyroSensor,
                SensorManager.SENSOR_DELAY_GAME,
                0
            )
        } else {
            Log.e("Sensor", "Gyroscope not available on this device")
        }

        //LAT Model
        try {
            latClassifier = LatClassifier(this)
        } catch (e: Exception) {
            Log.e("LAT_MODEL", "LatClassifier init failed: ${e.message}")
        }

        sensorFlow = SensorFlow(context = this, samplingMs = 20L, windowSize = 100)
        sensorScope.launch {
            sensorFlow.asFlow().collect { window -> handleSensorWindow(window) }
        }

        heartRateListener = HeartRateListener(this)
        updateAfeControlState(latTriggered = false)
    }

    private fun normalizeLatWindowInPlace() {
        for (i in 0 until LAT_WINDOW_SIZE) {
            for (j in 0 until LAT_FEATURE_SIZE) {
                val std = if (LAT_STD[j] == 0f) 1f else LAT_STD[j]
                normalizedWindowPool[i][j] = (rawWindowPool[i][j] - LAT_MEAN[j]) / std
            }
        }
    }

    private fun updateLatTrigger(latResult: Int): Boolean {
        if (!isCollectingLat && latResult == 0) return false
        if (latResult == 1) isCollectingLat = true
        latBuffer.addLast(latResult)

        if (latBuffer.size >= LAT_BUFFER_SIZE) {
            val countOne = latBuffer.count { it == 1 }
            latBuffer.clear()
            isCollectingLat = false
            return countOne >= LAT_TRIGGER_COUNT
        }
        return false
    }

    private fun updateSafeZoneStatus(distance: Double, r1: Double, r2: Double) {
        val newStatus = when {
            distance > r2 -> 2
            distance > r1 -> 1
            else -> 0
        }

        // 🟢 ตรวจสอบว่ามีการเปลี่ยนเขตจริงๆ ถึงจะเล่นเสียง (แก้เสียงหลอนรัวๆ)
        if (newStatus != standbymain.status) {
            when (newStatus) {
                0 -> playAlertSoundInService(com.example.watchsepawv2.R.raw.safezonein)
                1 -> if (standbymain.status == 0) playAlertSoundInService(com.example.watchsepawv2.R.raw.homeout)
                2 -> playAlertSoundInService(com.example.watchsepawv2.R.raw.safezoneout)
            }
            standbymain.status = newStatus
        }
        Log.d("SAFEZONE_STATUS", "distance=$distance, r1=$r1, r2=$r2, status=${standbymain.status}")
    }

    private fun updateAfeControlState(latTriggered: Boolean) {
        handler.post {
            val preferenceData = MyPreferenceData(this@BackgroundService)
            val r1 = preferenceData.getR1().toDoubleOrNull() ?: 0.0
            val r2 = preferenceData.getR2().toDoubleOrNull() ?: 0.0
            val distance = latestDistance

            if (distance != null) {
                updateSafeZoneStatus(distance, r1, r2)
            }

            // 🟢 การขยับตัว (LAT) ทำแค่หาพิกัดเงียบๆ ไม่เอาไปเปลี่ยน State!
            if (latTriggered) {
                Log.d("AFE_STATE", "พบการขยับตัว (LAT) -> แอบเช็ค GPS เงียบๆ")
                getCurrentLocation()
            }

            // 🟢 การเปลี่ยน State อิงจากระยะทาง GPS ล้วนๆ
            val nextState = when {
                distance == null -> AfeControlState.STATE_2_TRACKING
                distance > r2 -> AfeControlState.STATE_3_ALERT
                distance > r1 -> AfeControlState.STATE_2_TRACKING
                else -> AfeControlState.STATE_1_HOME
            }

            if (currentAfeControlState != nextState) {
                currentAfeControlState = nextState
                applyAfeControlState(nextState)
            }
        }
    }

    private fun applyAfeControlState(state: AfeControlState) {
        when (state) {
            AfeControlState.STATE_1_HOME -> {
                Log.d("AFE_STATE", "STATE 1 HOME: GPS=OFF, HR=ON, TEMP=ON")
                AfeDebugState.afeStateText = "State 1: Home"

                stopLocationUpdates()

                // 🟢 เช็คตัวแปรจำก่อนเปิดชีพจร (แก้บัคชีพจรไม่อ่านค่า)
                if (::heartRateListener.isInitialized && !isHrStarted) {
                    heartRateListener.startListening()
                    isHrStarted = true
                }
                tryStartSkinTemperature()
            }
            AfeControlState.STATE_2_TRACKING -> {
                Log.d("AFE_STATE", "STATE 2 TRACKING: GPS=ON every 3 mins, HR=ON, TEMP=ON")
                AfeDebugState.afeStateText = "State 2: Tracking"

                getCurrentLocation()
                restartLocationUpdates(GPS_STATE_2_INTERVAL_MS)

                if (::heartRateListener.isInitialized && !isHrStarted) {
                    heartRateListener.startListening()
                    isHrStarted = true
                }
                tryStartSkinTemperature()
            }
            AfeControlState.STATE_3_ALERT -> {
                Log.d("AFE_STATE", "STATE 3 ALERT: GPS=ON every 3 sec, HR=OFF, TEMP=OFF")
                AfeDebugState.afeStateText = "State 3: Alert"

                getCurrentLocation()
                restartLocationUpdates(GPS_STATE_3_INTERVAL_MS)

                if (::heartRateListener.isInitialized && isHrStarted) {
                    heartRateListener.stopListening()
                    isHrStarted = false
                }
                if (::skinTemperatureListener.isInitialized) {
                    skinTemperatureListener.stopTracker()
                    isSkinTempStarted = false
                }
            }
        }
    }

    // SensorLib -> LAT
    private fun handleSensorWindow(window: Any) {
        val floats = extractFloatList(window)
        if (floats.isEmpty()) return
        val chunkSize = LAT_FEATURE_SIZE
        val sampleCount = floats.size / chunkSize
        if (sampleCount == 0) return

        if (::latClassifier.isInitialized && sampleCount >= LAT_WINDOW_SIZE) {
            for (i in 0 until LAT_WINDOW_SIZE) {
                val base = i * chunkSize
                rawWindowPool[i][0] = floats[base]
                rawWindowPool[i][1] = floats[base + 1]
                rawWindowPool[i][2] = floats[base + 2]
                rawWindowPool[i][3] = floats[base + 3]
                rawWindowPool[i][4] = floats[base + 4]
                rawWindowPool[i][5] = floats[base + 5]
            }

            try {
                normalizeLatWindowInPlace()
                // 🟢 asList() ไม่ copy ข้อมูล เบากว่า toList()
                val probability = latClassifier.predict(normalizedWindowPool.asList())
                val result = if (probability >= LAT_THRESHOLD) 1 else 0

                AfeDebugState.latProbability = probability
                AfeDebugState.latResult = result
                AfeDebugState.sensorSampleCount = sampleCount
                AfeDebugState.lastUpdated = System.currentTimeMillis()

                val latTriggered = updateLatTrigger(result)
                updateAfeControlState(latTriggered)
            } catch (e: Exception) {
                Log.e("LAT_RESULT", "predict failed: ${e.message}")
            }
        } else {
            Log.d("LAT_RESULT", "skip predict: initialized=${::latClassifier.isInitialized}")
        }
    }

    private fun extractFloatList(window: Any): List<Float> {
        return when (window) {
            is List<*> -> window.filterIsInstance<Float>()
            is Array<*> -> window.filterIsInstance<Float>()
            else -> emptyList()
        }
    }

    private fun restartLocationUpdates(intervalMs: Long) {
        if (currentGpsIntervalMs == intervalMs) return
        stopLocationUpdates()
        startLocationUpdatesWithInterval(intervalMs)
        currentGpsIntervalMs = intervalMs
    }

    private fun startLocationUpdatesWithInterval(intervalMs: Long) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val req = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = intervalMs
            fastestInterval = intervalMs
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                updateLocationData(loc.latitude, loc.longitude)
            }
        }
        fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun updateLocationData(latitude: Double, longitude: Double) {
        standbymain.curLat = latitude
        standbymain.curLong = longitude
        val pref = MyPreferenceData(this@BackgroundService)
        standbymain.safeZoneLat = pref.getLat().toDouble()
        standbymain.safeZoneLong = pref.getLong().toDouble()

        if (standbymain.curLat != 0.0 && standbymain.curLong != 0.0) {
            standbymain.distance = (CalculateDistance().getDistanceFromLatLonInKm(
                standbymain.curLat, standbymain.curLong, standbymain.safeZoneLat, standbymain.safeZoneLong
            ) * 1000).roundToInt()
            standbymain.distanceKM = "%,d เมตร".format(standbymain.distance)
            latestDistance = standbymain.distance.toDouble()
            val r1 = pref.getR1().toDoubleOrNull() ?: 0.0
            val r2 = pref.getR2().toDoubleOrNull() ?: 0.0
            updateSafeZoneStatus(latestDistance ?: 0.0, r1, r2)
        }
        updateAfeControlState(latTriggered = false)
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        currentGpsIntervalMs = null
    }

    private fun tryStartSkinTemperature() {
        if (isSkinTempStarted || currentAfeControlState == AfeControlState.STATE_3_ALERT) return
        if (!isHealthConnected || !isSkinTempAvailable) return
        try {
            connectionManager.initSkinTemperature(skinTemperatureListener)
            skinTemperatureListener.startTracker()
            isSkinTempStarted = true
        } catch (e: Exception) {
            Log.e("SkinTemp", "start failed: ${e.message}")
        }
    }
    // ---------- Accelerometer ----------
    private val fallSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val ax = it.values[0]
                val ay = it.values[1]
                val az = it.values[2]

                svmA = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()

                val denomPitch = sqrt((ay * ay + az * az).toDouble()).toFloat()
                val denomRoll  = sqrt((ax * ax + az * az).toDouble()).toFloat()
                val denomYaw   = sqrt((ax * ax + ay * ay).toDouble()).toFloat()

                val pRatio = if (denomPitch == 0f) 0f else ax / denomPitch
                val rRatio = if (denomRoll  == 0f) 0f else ay / denomRoll
                val yRatio = if (denomYaw   == 0f) 0f else az / denomYaw

                pitchDeg = Math.toDegrees(atan(pRatio.toDouble())).toFloat()
                rollDeg  = Math.toDegrees(atan(rRatio.toDouble())).toFloat()
                yawDeg   = Math.toDegrees(atan(yRatio.toDouble())).toFloat()

                val preferenceData = MyPreferenceData(this@BackgroundService)
                preferenceData.setXAxis(ax)
                preferenceData.setYAxis(ay)
                preferenceData.setZAxis(az)
                preferenceData.setGyroX(GyroX)
                preferenceData.setGyroY(GyroY)
                preferenceData.setGyroZ(GyroZ)

                val now = System.currentTimeMillis()
                handleLogic(now)
                logRaw(now)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ---------- Gyroscope ----------
    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                GyroX = it.values[0]
                GyroY = it.values[1]
                GyroZ = it.values[2]
                val gyroMagnitude = sqrt((GyroX * GyroX + GyroY * GyroY + GyroZ * GyroZ).toDouble()).toFloat()
                svmG = gyroMagnitude

                val now = System.currentTimeMillis()
                handleLogic(now)
                logRaw(now)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    // ---------- Core logic: IDLE → IMPACT → POSTURE → COOLDOWN ----------
    private fun handleLogic(now: Long) {
        when (state) {
            State.IDLE -> {
                if (svmA > IMPACT_A_THR && svmG > IMPACT_G_THR && !isFallDetected) {
                    tImpact = now; pitchAtImpact = pitchDeg; rollAtImpact = rollDeg; yawAtImpact = yawDeg
                    aPeak = svmA; gPeak = svmG
                    dPitchMax = 0f; dRollMax = 0f; dYawMax = 0f
                    transition(State.IMPACT)
                    Log.d(TAG_EVT, "IMPACT start: A=${"%.2f".format(svmA)} m/s^2, G=${"%.2f".format(svmG)} rad/s, pitch=${"%.1f".format(pitchDeg)}, roll=${"%.1f".format(rollDeg)}, yaw=${"%.1f".format(yawDeg)}")
                }
            }
            State.IMPACT -> {
                aPeak = max(aPeak, svmA); gPeak = max(gPeak, svmG)
                if (now - tImpact > IMPACT_PAIRING_MS) transition(State.POSTURE)
            }
            State.POSTURE -> {
                val dPitch = abs(pitchDeg - pitchAtImpact)
                val dRoll = abs(rollDeg - rollAtImpact)
                val dYaw = abs(yawDeg - yawAtImpact)
                dPitchMax = max(dPitchMax, dPitch)
                dRollMax = max(dRollMax, dRoll)
                dYawMax = max(dYawMax, dYaw)
                val deltaEuler = max(dPitch, max(dRoll, dYaw))
                if (deltaEuler >= EULER_DELTA_THR_DEG) {
                    onConfirmedFall()
                    transition(State.COOLDOWN)
                } else if (now - tStateEntered > POSTURE_WINDOW_MS) {
                    transition(State.IDLE)
                }
            }
            State.COOLDOWN -> {}
        }
    }

    private fun onConfirmedFall() {
        val deltaMax = max(dPitchMax, max(dRollMax, dYawMax))
        Log.i(TAG_EVT, "CONFIRMED: A_peak=${"%.2f".format(aPeak)} m/s^2, G_peak=${"%.2f".format(gPeak)} rad/s, ΔPitch=${"%.1f".format(dPitchMax)}, ΔRoll=${"%.1f".format(dRollMax)}, ΔYaw=${"%.1f".format(dYawMax)}, ΔMax=${"%.1f".format(deltaMax)}")
        showFallAlertFullScreen()
        isFallDetected = true
        val preferenceData = MyPreferenceData(this)
        sendFallToServer(preferenceData, 1)
    }

    private fun transition(newState: State) {
        state = newState; tStateEntered = System.currentTimeMillis()
        if (newState == State.COOLDOWN) {
            handler.postDelayed({ isFallDetected = false; transition(State.IDLE) }, COOLDOWN_MS)
        }
    }

    private fun logRaw(now: Long) {
        if (now - lastRawLog >= LOG_RAW_EVERY_MS) {
            lastRawLog = now
            Log.d(
                TAG_RAW,
                "t=$now state=$state " +
                        "A=${"%.2f".format(svmA)} m/s^2 " +
                        "G=${"%.2f".format(svmG)} rad/s " +
                        "pitch=${"%.1f".format(pitchDeg)} " +
                        "roll=${"%.1f".format(rollDeg)} " +
                        "yaw=${"%.1f".format(yawDeg)}"
            )
        }
    }

    private fun showFallAlertFullScreen() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val vibratePattern = longArrayOf(0, 600, 250, 600, 250, 800)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(FALL_ALERT_CHANNEL_ID, "Fall Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = vibratePattern
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                @Suppress("DEPRECATION")
                pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "BackgroundService:FallWake").apply { acquire(3_000); release() }
            }
        } catch (_: Exception) {}

        val fullScreenIntent = Intent(this, HelpActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val okPI = PendingIntent.getBroadcast(this, 10, Intent(this, FallActionReceiver::class.java).setAction(FallActionReceiver.ACTION_OK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notOkPI = PendingIntent.getBroadcast(this, 11, Intent(this, FallActionReceiver::class.java).setAction(FallActionReceiver.ACTION_NOT_OK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val okAction = NotificationCompat.Action.Builder(0, "โอเค", okPI).build()
        val notOkAction = NotificationCompat.Action.Builder(0, "ไม่โอเค", notOkPI).build()

        val builder = NotificationCompat.Builder(this, FALL_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("พบการล้ม")
            .setContentText("แตะเพื่อยืนยันความปลอดภัย")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(okAction).addAction(notOkAction)
            .setVibrate(vibratePattern)
            .extend(NotificationCompat.WearableExtender().addAction(okAction).addAction(notOkAction))

        nm.notify(FallActionReceiver.NOTIF_ID, builder.build())
        try {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(android.os.VibrationEffect.createWaveform(vibratePattern, -1))
            else @Suppress("DEPRECATION") vib.vibrate(vibratePattern, -1)
        } catch (_: Exception) {}
        try { startActivity(fullScreenIntent) } catch (_: Exception) {}
    }

    // ------------------- API parts -------------------
    private fun sendFallToServer(preferenceData: MyPreferenceData, fallStatus: Int) {
        val url = "https://afe-thesis-production.up.railway.app/api/sentFall"
        val jsonBody = """
            {
                "users_id": "${preferenceData.getUserId()}", 
                "takecare_id": "${preferenceData.getTakecareId()}",
                "x_axis": "${preferenceData.getXAxis()}", 
                "y_axis": "${preferenceData.getYAxis()}", 
                "z_axis": "${preferenceData.getZAxis()}",
                "fall_status": "$fallStatus", 
                "latitude": "${standbymain.curLat}", 
                "longitude": "${standbymain.curLong}"
            }
        """.trimIndent().toRequestBody()
        val request = Request.Builder().url(url).put(jsonBody).addHeader("Content-Type", "application/json").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.d("FALL_API", "❌ Error: ${e.message}") }
            override fun onResponse(call: Call, response: Response) { response.use { Log.d("FALL_API", "✅ Sent: ${it.code}") } }
        })
    }

    private fun sendHeartRateToServer(preferenceData: MyPreferenceData) {
        val url = "https://afe-thesis-production.up.railway.app/api/sentHeartRate"
        val body = """
            {
                "uId": "${preferenceData.getUserId()}", 
                "takecare_id": "${preferenceData.getTakecareId()}",
                "bpm": "${preferenceData.getHeartRate()}", 
                "status": "${preferenceData.getHeartRateStatus()}"
            }
        """.trimIndent().toRequestBody()
        val request = Request.Builder().url(url).put(body).addHeader("Content-Type", "application/json").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.d("HR_API", "❌ Error: ${e.message}") }
            override fun onResponse(call: Call, response: Response) { response.use { Log.d("HR_API", "✅ Sent: ${it.code}") } }
        })
    }



    private fun sendTemperatureToServer(preferenceData: MyPreferenceData) {
        val url = "https://afe-thesis-production.up.railway.app/api/sentTemperature"
        val body = """
            {
                "uId": "${preferenceData.getUserId()}", 
                "takecare_id": "${preferenceData.getTakecareId()}",
                "temperature_value": "${preferenceData.getTemperature()}", 
                "status": "${preferenceData.getTemperatureStatus()}"
            }
        """.trimIndent().toRequestBody()
        val request = Request.Builder().url(url).put(body).addHeader("Content-Type", "application/json").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.d("TEMP_API", "❌ Error: ${e.message}") }
            override fun onResponse(call: Call, response: Response) { response.use { Log.d("TEMP_API", "✅ SentBackground: ${it.code}") } }
        })
    }

    private fun requestOkHttpClient(preferenceData: MyPreferenceData) {
        val url = "https://afe-thesis-production.up.railway.app/api/sentlocation"
        val body = """
            {
                "uId": "${preferenceData.getUserId()}", 
                "takecare_id": "${preferenceData.getTakecareId()}",
                "distance": "${standbymain.distance}", 
                "latitude": "${standbymain.curLat}",
                "longitude": "${standbymain.curLong}", 
                "battery": "${standbymain.batLevel}", 
                "status": "${standbymain.status}"
            }
        """.trimIndent().toRequestBody()
        val request = Request.Builder().url(url).put(body).addHeader("Content-Type", "application/json").build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { standbymain.response = 403; Log.d("LOC_API", "❌ Error: ${e.message}") }
            override fun onResponse(call: Call, response: Response) {
                response.use { standbymain.response = it.code; Log.d("LOC_API", "✅ Sent: ${it.code}") }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferenceData = MyPreferenceData(this)
        val refreshIntervalMillis: Long = 10_000
        var lastState2GpsSentTime = 0L

        val refreshRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                if (currentAfeControlState != AfeControlState.STATE_3_ALERT) {
                    if (isHealthConnected && isSkinTempAvailable && !isSkinTempStarted) tryStartSkinTemperature()
                }

                var shouldSendGps = false
                if (currentAfeControlState == AfeControlState.STATE_1_HOME && AfeDebugState.latResult == 1) {
                    shouldSendGps = true
                } else if (currentAfeControlState == AfeControlState.STATE_2_TRACKING) {
                    if (now - lastState2GpsSentTime >= 3 * 60 * 1000L) {
                        shouldSendGps = true
                        lastState2GpsSentTime = now
                    }
                } else if (currentAfeControlState == AfeControlState.STATE_3_ALERT) {
                    shouldSendGps = true
                }
                if (shouldSendGps) requestOkHttpClient(preferenceData)

                if (currentAfeControlState != AfeControlState.STATE_3_ALERT) {
                    sendTemperatureToServer(preferenceData)
                    sendHeartRateToServer(preferenceData)
                }
                handler.postDelayed(this, refreshIntervalMillis)
            }
        }
        handler.postDelayed(refreshRunnable, refreshIntervalMillis)
        return START_STICKY
    }

    override fun onConnectionResult(stringResourceId: Int) {
        isHealthConnected = (stringResourceId == 1)
        if (isHealthConnected && isSkinTempAvailable) tryStartSkinTemperature()
    }

    override fun onSkinTemperatureAvailability(isAvailable: Boolean) {
        isSkinTempAvailable = isAvailable
        if (isHealthConnected && isSkinTempAvailable && !isSkinTempStarted) tryStartSkinTemperature()
    }

    // รับค่าอุณหภูมิ (แก้ให้อ่านค่าได้จริง)
    override fun onSkinTemperatureChanged(status: Int, wristSkinTemperature: Float) {
        val preferenceData = MyPreferenceData(this)
        val maxTemp = preferenceData.getMaxTemperature().toFloatOrNull() ?: 37.0f
        val calculatedStatus = if (wristSkinTemperature > maxTemp) 1 else 0

        preferenceData.setTemperature(wristSkinTemperature.toString())
        preferenceData.setTemperatureStatus(calculatedStatus)
        Log.d("TEMP_UPDATE", "วัดอุณหภูมิได้: $wristSkinTemperature °C (สถานะ: $calculatedStatus)")
    }
    override fun notifyTrackerError(errorResourceId: Int) {
        Log.e("TEMP_ERROR", "Error: $errorResourceId")
    }

    fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token).addOnSuccessListener { location ->
            if (location != null) updateLocationData(location.latitude, location.longitude)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, BackgroundService::class.java)
        val pi = PendingIntent.getService(this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pi)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        if (::skinTemperatureListener.isInitialized) skinTemperatureListener.stopTracker()
        if (::connectionManager.isInitialized) connectionManager.disconnect()
        sensorScope.cancel()
        if (::heartRateListener.isInitialized) heartRateListener.stopListening()
        if (::latClassifier.isInitialized) latClassifier.close()
        try { wakeLock.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}