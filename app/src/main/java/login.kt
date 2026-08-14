package com.example.watchsepawv2.presentation

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.watchsepawv2.R


class login : ComponentActivity() {
    private lateinit var requestQueue: RequestQueue
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)
        val permission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this,permission,0)
        //When on click btnConnect
        val preferenceData = MyPreferenceData(this)
        if(preferenceData.getLoginStatus() == true ) {
            goTostandbymain()
        }
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        btnConnect.setOnClickListener{
            val uId = findViewById<EditText>(R.id.inputID).text.toString()
            val uPin = findViewById<EditText>(R.id.inputPIN).text.toString()
            requestQueue = Volley.newRequestQueue(this)
            connectPhoneApp(uId, uPin)
        }

        val btnSkipLogin = findViewById<Button>(R.id.btnSkipLogin)
        btnSkipLogin.setOnClickListener {
            skipLogin()
        }
    }

    private fun connectPhoneApp(uId: String, uPin:String){
        val url = "https://afe-thesis-production.up.railway.app/api/watchconnphone?uId=$uId&uPin=$uPin"
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val res = response.getString("status").toString()
                val lat = response.getString("lat").toString()
                val long = response.getString("long").toString()
                val r1 = response.getString("r1").toString()
                val r2 = response.getString("r2").toString()
                val takecare_id = response.getString("takecare_id").toString()

                if (res == "true"){
                    resLogin(res.toBoolean(), uId ,uPin, lat,long,r1,r2,takecare_id)
                    Toast.makeText(this, "การเชื่อมต่อกับ ผู้ดูแลรหัส $uId สำเร็จ", Toast.LENGTH_SHORT).show()
                    goTostandbymain()
                } else {
                    resLogin(res.toBoolean(), uId ,uPin,lat,long,r1,r2,takecare_id)
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("!!! แจ้งเตือน !!!")
                    builder.setMessage("การเชื่อมต่อผิดพลาด โปรดตรวสอบ ID: $uId หรือ PIN: $uPin อีกครั้ง")
                    builder.setPositiveButton("OK") { _, _ -> }
                    builder.show()
                }
            },
            { error ->
                val errorMessage = error.message
                val builder = AlertDialog.Builder(this)
                builder.setTitle("!!! แจ้งเตือน !!!")
                builder.setMessage("การเชื่อมต่อผิดพลาด โปรดตรวจสอบ สัญญาณอินเทอร์เน็ต: $errorMessage")
                builder.setPositiveButton("OK") { _, _ -> }
                builder.show()
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    private fun resLogin (res:Boolean, uId: String,uPin: String, lat:String,long:String,r1:String,r2:String,takecare_id:String){
        val myPreference = MyPreferenceData(this)
        myPreference.setLoginStatus(res)
        myPreference.setUserId(uId)
        myPreference.setUserPin(uPin)
        myPreference.setLat(lat)
        myPreference.setLong(long)
        myPreference.setR1(r1)
        myPreference.setR2(r2)
        myPreference.setTakecareId(takecare_id)
    }

    private fun skipLogin() {
        val myPreference = MyPreferenceData(this)
        myPreference.setUserId("0")
        myPreference.setUserPin("0")
        myPreference.setLat("0.0")
        myPreference.setLong("0.0")
        myPreference.setR1("0.0")
        myPreference.setR2("0.0")
        myPreference.setTakecareId("0")
        Toast.makeText(this, "Skip login", Toast.LENGTH_SHORT).show()
        goTostandbymain()
    }

    private fun goTostandbymain(){
        val intent = Intent(this, standbymain::class.java)
        startActivity(intent)
        finish()
    }
}

