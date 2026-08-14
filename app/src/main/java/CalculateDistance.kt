package com.example.watchsepawv2.presentation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CalculateDistance (){
    fun getDistanceFromLatLonInKm(lat1 : Double, lon1 : Double, lat2 : Double , lon2 : Double) : Double{
        val radius = 6371  //radius of the earth in km
        val dLat = degreeToRadian(lat2-lat1)
        val dLon = degreeToRadian(lon2-lon1)
        val a =
            sin(dLat/2) * sin(dLat/2) +
                    cos(degreeToRadian(lat1)) * cos(degreeToRadian(lat2)) *
                    sin(dLon/2) * sin(dLon/2)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        val distance = radius * c //distance in km
        return distance
    }

    private fun degreeToRadian(deg :Double):Double{
        return deg * (Math.PI/180)
    }
}