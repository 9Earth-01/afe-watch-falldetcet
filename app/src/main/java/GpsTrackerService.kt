//package com.example.watchsepawv2.presentation
//import android.Manifest
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.location.Location
//import android.location.LocationListener
//import android.location.LocationManager
//import android.os.Bundle
//import android.os.IBinder
//import androidx.core.content.ContextCompat
//import android.util.Log
//class GpsTrackerService(private val context: Context) : Service(), LocationListener {
//    private lateinit var locationManager: LocationManager
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        // Start tracking when service is started
//        Log.d("GpsTrackerService", "GPS tracking started in background")
//        startTracking()  // Start location tracking
//        return START_STICKY  // Ensures service restarts if killed
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopTracking()
//    }
//
//    override fun onLocationChanged(location: Location) {
//        // Handle location updates
//    }
//
//    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
//        // Handle status changes
//    }
//
//    override fun onProviderEnabled(provider: String) {
//        // Handle provider enabled
//    }
//
//    override fun onProviderDisabled(provider: String) {
//        // Handle provider disabled
//    }
//
//    private fun startTracking() {
//        if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED
//        ) {
//            Log.d("GpsTrackerService", "Starting GPS tracking updates")
//            locationManager.requestLocationUpdates(
//                LocationManager.GPS_PROVIDER,
//                10000,  // 1 second
//                10f,  // Minimum distance of 10 meters
//                this
//            )
//        } else {
//            Log.d("GpsTrackerService", "Permission not granted for GPS tracking")
//        }
//    }
//
//    private fun stopTracking() {
//        locationManager.removeUpdates(this)
//    }
//}
