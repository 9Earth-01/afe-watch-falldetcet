package com.example.watchsepawv2.presentation

//using for lock one page of app on allway
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
//import com.example.watchsepawv2.databinding.ActivityStandbymainBinding
//import okio.IOException

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import com.android.volley.RequestQueue
import com.example.watchsepawv2.R
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import java.time.Clock.systemDefaultZone
import java.time.Duration
import kotlin.math.roundToInt
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class standbymain : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider, TrackerObserver,
    ConnectionObserver {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val handler = Handler()
    private lateinit var requestQueue: RequestQueue
    private lateinit var txtTemperature: TextView
    private lateinit var txtTemperatureStatus: TextView
    private lateinit var txtLatResult: TextView
    private lateinit var txtAfeState: TextView
    private val appTag = "standbymain"

    //    private lateinit var skinTemperatureSubject: TrackerDataSubject
//    private lateinit var skinTemperatureListener: SkinTemperatureListener
//    private lateinit var connectionManager: ConnectionManager
    private lateinit var gestureDetector: GestureDetector

    // ===== เพิ่ม: request codes สำหรับ permissions =====
    private val REQ_BODY_SENSORS = 100
    private val REQ_BODY_SENSORS_BACKGROUND = 101
    private val REQ_POST_NOTIFICATIONS = 102
    private val REQ_FINE_LOCATION = 1           // คงหมายเลขเดิมของคุณไว้
    private val REQ_BACKGROUND_LOCATION = 103

    // [ADDED] ---------- Permission helpers & flow ----------
    private fun isGranted(p: String) =
        ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(Manifest.permission.POST_NOTIFICATIONS) else true

    private fun hasLocationPermissions(): Boolean {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true
        return fine && bg
    }

    private fun hasSensorsPermissions(): Boolean {
        val body = isGranted(Manifest.permission.BODY_SENSORS)
        val bg = if (Build.VERSION.SDK_INT >= 34)
            isGranted(Manifest.permission.BODY_SENSORS_BACKGROUND) else true
        return body && bg
    }

    private fun hasAllRuntimePermissions(): Boolean =
        hasPostNotifications() && hasLocationPermissions() && hasSensorsPermissions()

    // [ADDED] ขอทุกสิทธิ์ตามลำดับที่ Android อนุญาต (เรียกครั้งเดียวจบ flow)
    private fun requestAllRuntimePermissions() {
        // 1) POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS
            )
            return
        }

        // 2) BODY_SENSORS → BODY_SENSORS_BACKGROUND (Android 14+)
        if (!isGranted(Manifest.permission.BODY_SENSORS)) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BODY_SENSORS), REQ_BODY_SENSORS
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !isGranted(Manifest.permission.BODY_SENSORS_BACKGROUND)
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND),
                REQ_BODY_SENSORS_BACKGROUND
            )
            return
        }

        // 3) FINE → BACKGROUND LOCATION
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_FINE_LOCATION
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQ_BACKGROUND_LOCATION
            )
            return
        }

        Log.d(appTag, "✅ All runtime permissions granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.standbymain)

        txtLatResult = findViewById(R.id.txtLatResult)
        txtAfeState = findViewById(R.id.txtAfeState)
        updateLatResultText()
        updateAfeStateText()

//        txtTemperature = findViewById(R.id.txtTemperature)
//        txtTemperatureStatus = findViewById(R.id.txtTemperatureStatus)
        // ตั้งค่า gesture detector สำหรับปัดซ้ายไป Heart Rate
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (diffX < -SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        // ปัดซ้าย → ไปหน้า HeartRateActivity เท่านั้น
                        val intent = Intent(this@standbymain, HeartRateActivity::class.java)
                        startActivity(intent)
                        return true
                    }
                }
                return false
            }
        })

        // ===== [CHANGED]: รวมการขอสิทธิ์มาไว้ที่เดียว =====
        // เดิม: ขอ POST_NOTIFICATIONS, BODY_SENSORS(_BACKGROUND), FINE/BACKGROUND LOCATION แยกกัน
        // ใหม่: เรียก requestAllRuntimePermissions() ครั้งเดียวให้ระบบเด้งตามลำดับ
        requestAllRuntimePermissions()

        // [ADDED] ถ้ามี extra บังคับให้เริ่ม flow (เช่น Service สั่งมา) ก็เรียกซ้ำได้
        if (intent?.getBooleanExtra("REQUEST_ALL_PERMISSIONS", false) == true) {
            requestAllRuntimePermissions()
        }

        // เริ่มต้น BackgroundService
        // เริ่มต้น BackgroundService เป็น Foreground Service
        val serviceIntent = Intent(this, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // สำหรับ Android 8.0 (Oreo) ขึ้นไป
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            // สำหรับเวอร์ชันเก่า
            startService(serviceIntent)
        }
        val preferenceData = MyPreferenceData(this)
        // เริ่มต้นเซนเซอร์อุณหภูมิ
//        skinTemperatureSubject = TrackerDataSubject()
//        skinTemperatureSubject.addObserver(this)
//
//        skinTemperatureListener = SkinTemperatureListener(this)
//        skinTemperatureListener.setTrackerDataSubject(skinTemperatureSubject)
//
//        connectionManager = ConnectionManager(this)
//        connectionManager.connect(this)
//        // connectionManager.initSkinTemperature(skinTemperatureListener)
//        skinTemperatureListener.startTracker()

        val uId = preferenceData.getUserId()
        val safeZoneLat = preferenceData.getLat().toDouble()
        val safeZoneLong = preferenceData.getLong().toDouble()
        val r1 = preferenceData.getR1().toDouble()
        val r2 = preferenceData.getR2().toDouble()
        Log.d("Debug", "SafeZoneLat=$safeZoneLat, SafeZoneLong=$safeZoneLong, R1=$r1, R2=$r2")
        val btnSos = findViewById<Button>(R.id.btnSos)
        btnSos.setOnClickListener {
            Thread {
                val statusCodeSOS = requestSOS(uId)
                runOnUiThread {
                    if (statusCodeSOS == 200) {
                        Toast.makeText(
                            this,
                            "ขอความช่วยเหลือ ถึงผู้ดูแลรหัส ${uId} สำเร็จ",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("!!! แจ้งเตือน !!!")
                        builder.setMessage("ขอความช่วยเหลือ ถึง ${uId} ไม่สำเร็จ")
                        builder.setPositiveButton("OK") { _, _ -> }
                        builder.show()
                    }
                }
            }.start()
            val serviceIntent = Intent(this, BackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // สำหรับ Android 8.0 (Oreo) ขึ้นไป
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                // สำหรับเวอร์ชันเก่า
                startService(serviceIntent)
            }

            val ambientController = AmbientModeSupport.attach(this)
        }

        //Disconnect phone and show connect screen.
        val btnLogOff = findViewById<Button>(R.id.btnLogOff)
        btnLogOff.setOnClickListener {
            val preferenceData = MyPreferenceData(this)
            val uPin = preferenceData.getUserPin()
            val uId = preferenceData.getUserId()
            showInputDialog(this, "โปรดระบุ PIN") { inputText ->
                val textView = findViewById<TextView>(R.id.textView)
                if (uPin == inputText.trim()) {
                    goToLogOff(preferenceData)
                } else {
                    Toast.makeText(this, "รหัส PIN ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                }
            }
        }

        //Check GPS modules of this devices and run currentLocation and show message on screen.
        if (!hasGps()) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("!!! แจ้งเตือน !!!")
            builder.setMessage("ไม่พบ GPS บนบอร์ดของอุปกรณ์")
            builder.setPositiveButton("OK") { _, _ -> }
            builder.show()
        } else {
            //First time for open app.
            // getCurrentLocation()
            refreshDisplay(r1, r2)
            // requestOkHttpClient(preferenceData)
//            updateTemperatureDisplay(preferenceData)
//            sendTemperatureToServer(preferenceData)
        }

        val refreshIntervalMillis: Long = 10000 // 10 seconds
        val refreshRunnable = object : Runnable {
            override fun run() {
                //getCurrentLocation()
//                updateTemperatureDisplay(preferenceData)
//                sendTemperatureToServer(preferenceData)
                refreshDisplay(r1, r2)
                updateLatResultText()
                updateAfeStateText()
                //requestOkHttpClient(preferenceData)
                handler.postDelayed(this, refreshIntervalMillis)
            }
        }
        handler.postDelayed(refreshRunnable, refreshIntervalMillis)
    }

//    private fun updateTemperatureDisplay(preferenceData: MyPreferenceData) {
//        val temp = preferenceData.getTemperature()
//        val status = preferenceData.getTemperatureStatus()
//        val tempFormatted = temp.toFloatOrNull()?.let { "%.1f".format(it) } ?: "-"
//        txtTemperature.text = "อุณหภูมิร่างกาย: $tempFormatted °C"
////        txtTemperatureStatus.text = ""
//    }

//    private fun sendTemperatureToServer(preferenceData: MyPreferenceData) {
//        val client = OkHttpClient()
//        val url = "https://afe-thesis-production.up.railway.app/api/sentTemperature"
//
//
//        val body = """
//        {
//            "uId": "${preferenceData.getUserId()}",
//            "takecare_id": "${preferenceData.getTakecareId()}",
//            "temperature_value": "${preferenceData.getTemperature()}",
//            "status": "${preferenceData.getTemperatureStatus()}"
//        }
//        """.trimIndent().toRequestBody()
//
//        val request = Request.Builder()
//            .url(url)
//            .put(body)
//            .addHeader("Content-Type", "application/json")
//            .build()
//
//        Thread {
//            try {
//                client.newCall(request).enqueue(object : Callback {
//                    override fun onFailure(call: Call, e: IOException) {
//                        Log.d("TEMP_API", "❌ Error: ${e.message}")
//                    }
//
//                    override fun onResponse(call: Call, response: Response) {
//                        Log.d("TEMP_API", "✅ Sent: ${response.code}")
//                    }
//                })
//            } catch (e: IOException) {
//                Log.d("TEMP_API", "❌ IOException: ${e.message}")
//            }
//        }.start()
//    }

    private fun updateLatResultText() {
        if (!::txtLatResult.isInitialized) return

        val resultText = when (AfeDebugState.latResult) {
            1 -> "1 สนใจ"
            0 -> "0 ไม่สนใจ"
            else -> "-"
        }

        txtLatResult.text =
            "LAT: $resultText (${String.format("%.3f", AfeDebugState.latProbability)})"
    }

    private fun updateAfeStateText() {
        if (!::txtAfeState.isInitialized) return

        txtAfeState.text = AfeDebugState.afeStateText
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback {
        return MyAmbientCallback()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // ต้องเช็ค null ก่อนส่งเข้า gestureDetector
        return if (event != null) {
            gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
        } else {
            super.onTouchEvent(event)
        }
    }


    override fun onSkinTemperatureChanged(status: Int, wristSkinTemperature: Float) {
        runOnUiThread {
            val preferenceData = MyPreferenceData(this)
            val maxTemp = preferenceData.getMaxTemperature().toFloatOrNull() ?: 37.0f
            Log.d("TEMP_CHECK", "วัดได้ = $wristSkinTemperature | ตั้งไว้ = $maxTemp")

            // เปรียบเทียบอุณหภูมิกับค่าที่ตั้งไว้ใน Preferences
            val calculatedStatus = if (wristSkinTemperature > maxTemp) 1 else 0

            // แสดงเฉพาะสถานะอุณหภูมิ ไม่แสดงสถานะเซนเซอร์
            txtTemperature.text = "อุณหภูมิร่างกาย: %.1f °C".format(wristSkinTemperature)
//            txtTemperatureStatus.text = ""

            // บันทึกค่าลง SharedPreferences
            preferenceData.setTemperature(wristSkinTemperature.toString())
            preferenceData.setTemperatureStatus(calculatedStatus)
        }
    }

    override fun notifyTrackerError(errorResourceId: Int) {
        runOnUiThread {
            Toast.makeText(this, "เกิดข้อผิดพลาดจากเซนเซอร์: $errorResourceId", Toast.LENGTH_LONG)
                .show()
        }
    }

    /* Using for destroy interval refressh modules. on logout */
    override fun onDestroy() {

        val serviceIntent = Intent(this, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // สำหรับ Android 8.0 (Oreo) ขึ้นไป
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            // สำหรับเวอร์ชันเก่า
            startService(serviceIntent)
        }

        super.onDestroy()
        // Remove any pending callbacks to avoid memory leaks
        handler.removeCallbacksAndMessages(null)
    }

    private fun showInputDialog(context: Context, title: String, callback: (String) -> Unit) {
        val editText = EditText(context)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val inputText = editText.text.toString()
                callback(inputText)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    public fun refreshDisplay(r1: Double, r2: Double) {
        val textLatLng = findViewById<TextView>(R.id.txtLatLng)
        val textStatus = findViewById<TextView>(R.id.txtStatus)
//        val textDistance = findViewById<TextView>(R.id.distance)

        // แสดงค่าพิกัดปัจจุบัน
        textLatLng.text = "ละติจูด %.3f \nลองจิจูด %.3f".format(curLat, curLong)

        if (curLat != 0.0 && curLong != 0.0) {
            if (distance <= r1) {
                textStatus.text = "สถานะ: อยู่ในบ้าน"
                if (status != 0) controllSound(currentSong3[0])
                status = 0
            } else if (distance > r1 && distance <= r2) {
                textStatus.text = "สถานะ: กำลังออกนอกบ้าน"
                if (status != 1) controllSound(currentSong[0])
                status = 1
            } else if (distance > r2) {
                textStatus.text = "สถานะ: ออกนอกเขตปลอดภัย"
                if (status != 2) controllSound(currentSong2[0])
                status = 2
            }
        }

//        textDistance.text = "ระยะห่างจากจุดปลอดภัย\n$distanceKM"

        val batteryManager = applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager
        batLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        updateLatResultText()
        updateAfeStateText()
    }

    private fun controllSound(id: Int) {
        if (mediaPlayer == null) mediaPlayer = MediaPlayer.create(this, id)
        mediaPlayer?.start()
        mediaPlayer = null
        Thread.sleep(1000)
    }

    private fun hasGps(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    public fun getCurrentLocation() {
        val gpsTracker = GpsTracker(this)
        gpsTracker.getLocation { location: Location? ->
            if (location != null) {
                curLat = location.latitude
                curLong = location.longitude

                Log.d("GPS", "Latitude: $curLat, Longitude: $curLong")

                // คำนวณระยะห่าง
                val preferenceData = MyPreferenceData(this)
                safeZoneLat = preferenceData.getLat().toDouble()
                safeZoneLong = preferenceData.getLong().toDouble()

                if (curLat != 0.0 && curLong != 0.0) {
                    distance = (CalculateDistance().getDistanceFromLatLonInKm(
                        curLat, curLong, safeZoneLat, safeZoneLong
                    ) * 1000).roundToInt()
                    distanceKM = "%,d เมตร".format(distance)
                }
            } else {
                Toast.makeText(
                    this,
                    "!!! แจ้งเตือน !!! ไม่สามารถรับพิกัดจาก GPS ได้",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d("GPS", "Unable to get GPS location")
            }
        }
    }

    fun isAnimationHandlerEnabled(): Boolean {
        return false
    }

    public fun requestOkHttpClient(preferenceData: MyPreferenceData) {
        try {
            val client = OkHttpClient()
            val url = "https://afe-thesis-production.up.railway.app/api/sentlocation"
            //val mediaType = "application/json".toMediaType()
            val body = """
    {
        "uId": "${preferenceData.getUserId()}",
        "takecare_id": "${preferenceData.getTakecareId()}",
        "distance": "$distance",
        "latitude": "$curLat",
        "longitude": "$curLong",
        "battery": "$batLevel",
        "status": "$status"
    }
""".trimIndent()

            val encryptedBody = AesCrypto.encrypt(body).toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .put(encryptedBody)
                .addHeader("Content-Type", "application/json")
                .build()
            Thread {
                try {
                    //response = client.newCall(request).execute().code
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            // Handle failure
                            //println("Failed to make a request: ${e.message}")
                            Log.d("Failed to make a request:", e.message.toString())
                        }

                        override fun onResponse(call: Call, response: Response) {
                            // Handle response
                            val responseData = response.body?.string()
                            Log.d("Response code:", responseData.toString())
                            println("Response Code: ${response.code}")
                            println("Response Body: $responseData")
                            println("Response Headers: ${response.headers}")
                        }
                    })

                } catch (e: IOException) {
                    response = 403
                    Log.d("requestOkHttpClient response #1", e.toString())
                }
                runOnUiThread {
                    if (response != 200) {
                        Log.d("requestOkHttpClient response #2", "ไม่มีสัญญาณอินเทอร์เน็ต")
                    }
                }
            }.start()
        } catch (e: IOException) {
            Log.d("requestOkHttpClient IOException", e.toString())
            500
        }
    }

    private inner class MyAmbientCallback : AmbientModeSupport.AmbientCallback() {
        /**
         * If the display is low-bit in ambient mode. i.e. it requires anti-aliased fonts.
         */
        private var isLowBitAmbient = false

        /**
         * If the display requires burn-in protection in ambient mode, rendered pixels need to be
         * intermittently offset to avoid screen burn-in.
         */
        private var doBurnInProtection = false
        /**
         * Prepares the UI for ambient mode.
         */

        /**
         * Updates the display in ambient mode on the standard interval. Since we're using a custom
         * refresh cycle, this method does NOT update the data in the display. Rather, this method
         * simply updates the positioning of the data in the screen to avoid burn-in, if the display
         * requires it.
         */
        override fun onEnterAmbient(ambientDetails: Bundle?) {
            // Handle entering ambient mode
        }

        override fun onUpdateAmbient() {
            // Handle updating ambient mode content
        }

        override fun onExitAmbient() {
            // Handle exiting ambient mode
        }
    }
    //Private
    fun requestSOS(uId: String): Int {
        val client = OkHttpClient()
        val url = "https://afe-thesis-production.up.railway.app/api/requestSOS"
//        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
//            .addFormDataPart("uid",uId.toString())
//            .build()
        val body = """
              {
                "uid": "${uId}"
              }
              """.trimIndent().toRequestBody()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        return try {
            val response = client.newCall(request).execute()
            Log.d("SOS_API", "Response code: ${response.code}")
            response.code
        } catch (e: IOException) {
            Log.d("requestSOS", e.toString())
            500
        }
    }

    private fun goTostandbymain() {
        val intent = Intent(this, standbymain::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToLogin() {
        val intent = Intent(this, login::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToLogOff(preferenceData: MyPreferenceData) {
//        run_app = false
        preferenceData.setLoginStatus(false)
        val intent = Intent(this, login::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToStandby() {
//        run_app = false
        val intent = Intent(this, standbymain::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        var run_app = false
        private const val TAG = "standbymain"

        //private val calDis = CalculateDistance()
        lateinit var myPreferenceMain: MyPreferenceData
        private var uid: String = ""
        public var curLat: Double = 0.0
        public var curLong: Double = 0.0
        public var safeZoneLat: Double = 0.0
        public var safeZoneLong: Double = 0.0
        public var r1: Double = 0.0
        public var r2: Double = 0.0
        public var distance = 0
        public var distanceKM: String = ""
        public var response = 0
        public var statusCodeSOS: Int = 0
        public var status = 0
        public var batLevel = 0

        public var mediaPlayer: MediaPlayer? = null
        public var currentSong = mutableListOf(R.raw.homeout)
        public var currentSong2 = mutableListOf(R.raw.safezoneout)
        public var currentSong3 = mutableListOf(R.raw.safezonein)

        /**
         * Duration between updates while in active mode.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val ACTIVE_INTERVAL = Duration.ofSeconds(10)

        /**
         * Duration between updates while in ambient mode.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val AMBIENT_INTERVAL = Duration.ofSeconds(60)

        /**
         * Action for updating the display in ambient mode, per our custom refresh cycle.
         */
        const val AMBIENT_UPDATE_ACTION =
            "package com.example.watchsepawv2.presentation.action.AMBIENT_UPDATE"
        //"com.example.android.wearable.wear.alwayson.action.AMBIENT_UPDATE"

        /**
         * Number of pixels to offset the content rendered in the display to prevent screen burn-in.
         */
        private const val BURN_IN_OFFSET_PX = 10
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save your state information to the bundle
    }

    override fun onConnectionResult(stringResourceId: Int) {
        Log.d("ConnectionObserver", "onconnectionResult:$stringResourceId")
        Toast.makeText(this, "เชื่อมต่อบริการสุขภาพสำเร็จ: $stringResourceId", Toast.LENGTH_SHORT)
            .show()
    }

    override fun onSkinTemperatureAvailability(isAvailable: Boolean) {
        Log.d("SkinTempAvailable", "Available: $isAvailable")
//        if (isAvailable) {
//            // ✅ ตรงนี้ถึงค่อย init
//            connectionManager.initSkinTemperature(skinTemperatureListener)
//            skinTemperatureListener.startTracker()
//        } else {
//            Toast.makeText(this, "ไม่สามารถใช้งานเซ็นเซอร์อุณหภูมิได้", Toast.LENGTH_SHORT).show()
//        }
    }

    // ===== [CHANGED]: จัดการ callback ของ runtime permissions → วนต่อไปจนกว่าจะครบ =====
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        fun granted() = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            REQ_POST_NOTIFICATIONS -> {
                Log.d(appTag, "POST_NOTIFICATIONS granted=${granted()}")
            }
            REQ_BODY_SENSORS -> {
                Log.d(appTag, "BODY_SENSORS granted=${granted()}")
                // BODY_SENSORS_BACKGROUND จะถูกขอต่อใน requestAllRuntimePermissions()
            }
            REQ_BODY_SENSORS_BACKGROUND -> {
                Log.d(appTag, "BODY_SENSORS_BACKGROUND granted=${granted()}")
            }
            REQ_FINE_LOCATION -> {
                Log.d(appTag, "ACCESS_FINE_LOCATION granted=${granted()}")
                // BACKGROUND LOCATION จะถูกขอต่อใน requestAllRuntimePermissions()
            }
            REQ_BACKGROUND_LOCATION -> {
                Log.d(appTag, "ACCESS_BACKGROUND_LOCATION granted=${granted()}")
            }
        }

        // [ADDED] เรียกต่อเพื่อขอสิทธิ์ชุดถัดไป จนกว่าจะครบทั้งหมด
        requestAllRuntimePermissions()
    }
}

/**
 * The [Clock] driving the time information. Overridable only for testing.
 */
@RequiresApi(Build.VERSION_CODES.O)
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal var clock: Clock = systemDefaultZone()

/**
 * The dispatcher used for delaying in active mode. Overridable only for testing.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal var activeDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
