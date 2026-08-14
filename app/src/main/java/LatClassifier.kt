package com.example.watchsepawv2.presentation

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class LatClassifier(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, "lat_model.tflite")
        interpreter = Interpreter(model)
        Log.d("LAT_MODEL", "Model loaded successfully")
    }

    private fun loadModelFile(context: Context, fileName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(fileName)
        val inputStream = assetFileDescriptor.createInputStream()
        val fileChannel = inputStream.channel

        return fileChannel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    fun predict(window: List<FloatArray>): Float {
        // input shape = [1, 40, 6]
        val input = Array(1) {
            Array(40) {
                FloatArray(6)
            }
        }

        for (i in 0 until 40) {
            for (j in 0 until 6) {
                input[0][i][j] = window[i][j]
            }
        }

        // output shape = [1, 1]
        val output = Array(1) {
            FloatArray(1)
        }

        interpreter.run(input, output)

        val probability = output[0][0]
        Log.d("LAT_MODEL", "Probability = $probability")

        return probability
    }

    fun close() {
        interpreter.close()
    }
}