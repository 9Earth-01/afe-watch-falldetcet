package com.example.watchsepawv2.presentation

import android.content.Context
class MyPreferenceData (context: Context) {
    val PREFERENCE_NAME ="SharePreferenceExample"
    val PREFERENCE_LOGIN_STATUS ="LoginStatus"
    val PREFERENCE_USER_ID = "userId"
    val PREFERENCE_USER_PIN = "PIN"
    val PREFERENCE_LATITUDE = "lat"
    val PREFERENCE_LONGITUDE = "long"
    val PREFERENCE_RADIUS1 = "r1"
    val PREFERENCE_RADIUS2 ="r2"
    val PREFERENCE_TAKECARE_ID = "takecare_id"
    val PREFERENCE_DISTANCE = "DISTANCE"
//    ตั้งตัวแปรสำหรับ temperature 30/05
    val PREFERENCE_TEMPERATURE = "TEMPERATURE"
    val PREFERENCE_TEMPERATURE_STATUS = "TEMP_STATUS"
    private val PREFERENCE_MAX_TEMP = "MAX_TEMP"
    // ตั้งตัวแปรสำหรับ fallactive
    val PREFERENCE_FALL_ACTIVE = "FALL_ACTIVE"
    val PREFERENCE_X_AXIS = "x_axis"
    val PREFERENCE_Y_AXIS = "y_axis"
    val PREFERENCE_Z_AXIS = "z_axis"
    val PREFERENCE_FALL_STATUS = "FALL_STATUS"
    //ตั้งตัวแปรสำหรับ heart_rate
    val PREFERENCE_HEART_RATE ="HEART_RATE"
    val PREFERENCE_HEART_RATE_STATUS = "HEART_RATE_STATUS"
    val PREFERENCE_MIN_HEART_RATE = "MIN_HEART_RATE"
    val PREFERENCE_MAX_HEART_RATE = "MAX_HEART_RATE"
    //ตั้งตัวเเปรสำหรับ Gyro
    val PREFERENCE_GYRO_X = "GYRO_X"
    val PREFERENCE_GYRO_Y = "GYRO_Y"
    val PREFERENCE_GYRO_Z = "GYRO_Z"

    val preference = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    fun getLoginStatus(): Boolean{
        return preference.getBoolean(PREFERENCE_LOGIN_STATUS, false)
    }
    fun setLoginStatus(status:Boolean){
        val editor = preference.edit()
        editor.putBoolean(PREFERENCE_LOGIN_STATUS,status)
        editor.apply()
    }
    fun getUserId():String{
        return preference.getString(PREFERENCE_USER_ID,"0").toString()
    }
    fun setUserId(userId:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_USER_ID,userId)
        editor.apply()
    }

    fun getUserPin():String{
        return preference.getString(PREFERENCE_USER_PIN,"0").toString()
    }
    fun setUserPin(userPin:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_USER_PIN,userPin)
        editor.apply()
    }
    fun getLat():String{
        return preference.getString(PREFERENCE_LATITUDE,"0.0").toString()
    }
    fun setLat(lat:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_LATITUDE,lat).toString()
        editor.apply()
    }
    fun getLong():String{
        return preference.getString(PREFERENCE_LONGITUDE,"0.0").toString()
    }
    fun setLong(long:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_LONGITUDE,long).toString()
        editor.apply()
    }

    fun getR1():String{
        return preference.getString(PREFERENCE_RADIUS1,"0.0").toString()
    }
    fun setR1(r1:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_RADIUS1,r1).toString()
        editor.apply()
    }
    fun getR2():String{
        return preference.getString(PREFERENCE_RADIUS2,"0.0").toString()
    }
    fun setR2(r2:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_RADIUS2,r2).toString()
        editor.apply()
    }
    fun getTakecareId():String{
        return preference.getString(PREFERENCE_TAKECARE_ID,"0").toString()
    }
    fun setTakecareId(takecare_id:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_TAKECARE_ID,takecare_id).toString()
        editor.apply()
    }
    fun setDistance(distance:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_DISTANCE,distance).toString()
        editor.apply()
    }
    fun getDistance():String{
        return preference.getString(PREFERENCE_DISTANCE,"0").toString()
    }
//    function Temperature 30/05
    fun setTemperature(temp:String){
        val editor =preference.edit()
        editor.putString(PREFERENCE_TEMPERATURE,temp)
        editor.apply()
    }
    fun getTemperature():String{
        return preference.getString(PREFERENCE_TEMPERATURE,"0.0")?:"0.0"
    }
    fun setTemperatureStatus(status: Int){
        val editor = preference.edit()
        editor.putInt(PREFERENCE_TEMPERATURE_STATUS,status)
        editor.apply()
    }
    fun getTemperatureStatus():Int{
        return preference.getInt(PREFERENCE_TEMPERATURE_STATUS,0)
    }
    fun setMaxTemperature(temp:String){
        val editor = preference.edit()
        editor.putString(PREFERENCE_MAX_TEMP,temp)
        editor.apply()
    }
    fun getMaxTemperature():String{
        return preference.getString(PREFERENCE_MAX_TEMP,"37")?:"37"
    }

    //ฟังก์ชันสำหรับ การตรวจจับการล้ม
    fun setXAxis(x:Float){
        val editor = preference.edit()
        editor.putFloat(PREFERENCE_X_AXIS,x)
        editor.apply()
    }
    fun getXAxis():Float{
        return preference.getFloat(PREFERENCE_X_AXIS,0.0f)
    }
    fun setYAxis(y:Float){
        val editor = preference.edit()
        editor.putFloat(PREFERENCE_Y_AXIS,y)
        editor.apply()
    }
    fun getYAxis():Float{
        return preference.getFloat(PREFERENCE_Y_AXIS,0.0f)
    }
    fun setZAxis(z:Float){
        val editor = preference.edit()
        editor.putFloat(PREFERENCE_Z_AXIS,z)
        editor.apply()
    }
    fun getZAxis():Float{
        return preference.getFloat(PREFERENCE_Z_AXIS,0.0f)
    }
    fun getGyroX():Float{
        return preference.getFloat(PREFERENCE_GYRO_X,0.0f)
    }

    fun setGyroX(x:Float){
        val editor = preference.edit()
        editor.putFloat(PREFERENCE_GYRO_X,x)
        editor.apply()
    }
    fun getGyroY():Float{
        return preference.getFloat(PREFERENCE_GYRO_Y,0.0f)

    }
    fun setGyroY(y:Float){
        val editor = preference.edit()
        editor.putFloat(PREFERENCE_GYRO_Y,y)
        editor.apply()

    }
    fun getGyroZ():Float{
        return preference.getFloat(PREFERENCE_GYRO_Z,0.0f)

    }
    fun setGyroZ(z:Float){
        val editor = preference.edit()
        editor.putFloat(PREFERENCE_GYRO_Z,z)
        editor.apply()
    }
    fun setFallActive(active:Boolean){
        val editor = preference.edit()
        editor.putBoolean(PREFERENCE_FALL_ACTIVE,active)
        editor.apply()
    }

    fun getFallActive(): Boolean{
        return preference.getBoolean(PREFERENCE_FALL_ACTIVE,false)
    }

    fun setFallStatus(status: Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_FALL_STATUS, status)
        editor.apply()
    }

    fun getFallStatus(): Int {
        return preference.getInt(PREFERENCE_FALL_STATUS, 0)
    }

    //ฟังก์ชัน สำหรับ Heart Rate
    fun setHeartRate(rate: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_HEART_RATE, rate)
        editor.apply()
    }
    fun getHeartRate(): String {
        return preference.getString(PREFERENCE_HEART_RATE, "0") ?: "0"
    }
    fun setHeartRateStatus(status: Int) {
        val editor = preference.edit()
        editor.putInt(PREFERENCE_HEART_RATE_STATUS, status)
        editor.apply()
    }
    fun getHeartRateStatus(): Int {
        return preference.getInt(PREFERENCE_HEART_RATE_STATUS, 0)
    }
    fun setMaxHeartRate(rate: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_MAX_HEART_RATE, rate)
        editor.apply()
    }
    fun getMaxHeartRate(): String {
        return preference.getString(PREFERENCE_MAX_HEART_RATE, "120") ?: "120"
    }
    fun setMinHeartRate(rate: String) {
        val editor = preference.edit()
        editor.putString(PREFERENCE_MIN_HEART_RATE, rate)
        editor.apply()
    }
    fun getMinHeartRate(): String {
        return preference.getString(PREFERENCE_MIN_HEART_RATE, "50") ?: "50"
    }


}