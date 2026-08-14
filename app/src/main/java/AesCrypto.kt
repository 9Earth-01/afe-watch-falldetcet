package com.example.watchsepawv2.presentation

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesCrypto {
    // ใส่ Key 32 ตัวที่ได้จาก PowerShell ตรงนี้
    private const val KEY_HEX = "6c28fd956a4353458ff7c6a85c59f885"

    private val keyBytes: ByteArray by lazy {
        KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /**
     * เข้ารหัส plaintext -> คืน JSON { iv, ciphertext } (base64)
     */
    fun encrypt(plaintext: String): JSONObject {
        val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
    }
}
