package com.example.watchsepawv2.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.handmonitor.sensorlib.v3.SensorFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SensorLibTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SensorLibTestScreen()
        }
    }
}

@Composable
fun SensorLibTestScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("ยังไม่เริ่ม") }
    var windowCount by remember { mutableStateOf(0) }
    var collectJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SensorLib v3 Test")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "สถานะ: $status")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "จำนวน window: $windowCount")
        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            if (collectJob != null) return@Button

            val sensorFlow = SensorFlow(
                context = context,
                samplingMs = 50L,
                windowSize = 40
            )

            status = "กำลังอ่าน sensor..."

            collectJob = scope.launch {
                try {
                    sensorFlow.asFlow().collect { window ->
                        windowCount++
                        status = "ได้รับ window แล้ว"

                        Log.d("SensorLibTest", "window #$windowCount = $window")
                    }
                } catch (e: Exception) {
                    status = "Error: ${e.message}"
                    Log.e("SensorLibTest", "collect error", e)
                }
            }
        }) {
            Text("Start")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            collectJob?.cancel()
            collectJob = null
            status = "หยุดแล้ว"
        }) {
            Text("Stop")
        }
    }
}