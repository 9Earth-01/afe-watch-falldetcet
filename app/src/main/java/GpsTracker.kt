//package com.example.watchsepawv2.presentation
//
//import android.Manifest
//import android.content.Context
//import android.content.pm.PackageManager
//import android.location.Location
//import android.location.LocationListener
//import android.location.LocationManager
//import android.os.Bundle
//import android.util.Log
//import androidx.core.content.ContextCompat
//
//class GpsTracker (private var context: Context?): LocationListener {
//    val location: Location?
//        get() {
//            if (ContextCompat.checkSelfPermission(context!!, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//                return null
//            }
//            try {
//                val locationManager =
//                    context!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//                val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
//                val isNetworkEnabled =
//                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
//                if (isGPSEnabled == false && isNetworkEnabled == false) {
//                    Log.d("GPS", "GPS provider is not enabled")
//                    return null
//                } else if (isGPSEnabled == true) {
//                    locationManager.requestLocationUpdates(
//                        LocationManager.GPS_PROVIDER,
//                        1000,
//                        10f,
//                        this
//                    )
//                    return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//                }
//                if (isNetworkEnabled == true && isGPSEnabled == false) {
//                    return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
//                }
//            } catch (e: Exception){
//                e.printStackTrace()
//            }
//            return null
//        }
//    override fun onLocationChanged(location: Location) {}
//
//    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
//
//    override fun onProviderEnabled(provider: String) {}
//
////    override fun onProviderDisabled(provider: String) {}
//    override fun onProviderDisabled(provider: String) {}
//}
package com.example.watchsepawv2.presentation
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import android.util.Log
class GpsTracker(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun getLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("GpsTracker", "Permission not granted for location access")
            callback(null)
            return
        }

        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(10000)  // Update every 10 seconds
            .setFastestInterval(5000)  // 5 seconds

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult?) {
                    locationResult ?: return
                    val location = locationResult.lastLocation
                    callback(location)
                    Log.d("GpsTracker", "Location updated: Latitude=${location.latitude}, Longitude=${location.longitude}")
                }
            },
            Looper.getMainLooper()
        )
    }

}
