package com.anand.ctbuildfest.security

import com.anand.ctbuildfest.EncryptedData
import com.anand.ctbuildfest.SignedData
import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.anand.ctbuildfest.Alert
import com.anand.ctbuildfest.utils.CryptoHelper
import java.security.KeyPair
import java.security.PublicKey

/**
 * Central security manager for the app
 * Handles encryption, signing, and device fingerprinting
 */
class SecurityManager(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
    private var keyPair: KeyPair? = null

    companion object {
        private const val TAG = "SecurityManager"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
    }

    init {
        // Load or generate key pair on initialization
        loadOrGenerateKeyPair()
    }

    /**
     * Encrypt alert data before transmission
     */
    fun encryptAlert(alert: Alert): EncryptedData? {
        return try {
            val alertJson = alert.toJson()
            val secretKey = CryptoHelper.AES256.generateKey()
            CryptoHelper.AES256.encrypt(alertJson, secretKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt alert", e)
            null
        }
    }

    /**
     * Sign alert for authenticity verification
     */
    fun signAlert(alert: Alert): SignedData? {
        val privateKey = keyPair?.private ?: return null
        val alertJson = alert.toJson()
        val signature = CryptoHelper.RSA.sign(alertJson, privateKey) ?: return null

        val publicKeyString = Base64.encodeToString(keyPair?.public?.encoded, Base64.DEFAULT)

        return SignedData(
            data = alertJson,
            signature = signature,
            publicKey = publicKeyString
        )
    }

    /**
     * Verify signed alert from another user
     */
    fun verifyAlert(signedData: SignedData, publicKey: PublicKey): Boolean {
        return CryptoHelper.RSA.verify(signedData.data, signedData.signature, publicKey)
    }

    /**
     * Get device fingerprint (unique ID for trust scoring)
     */
    fun getDeviceFingerprint(): String {
        // Check if fingerprint already exists
        val existingFingerprint = sharedPrefs.getString(KEY_DEVICE_FINGERPRINT, null)
        if (existingFingerprint != null) {
            return existingFingerprint
        }

        // Generate new fingerprint
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val timestamp = System.currentTimeMillis()
        val fingerprint = CryptoHelper.generateDeviceFingerprint(deviceId, timestamp)

        // Save for future use
        sharedPrefs.edit().putString(KEY_DEVICE_FINGERPRINT, fingerprint).apply()

        return fingerprint
    }

    /**
     * Generate session token for authenticated requests
     */
    fun generateSessionToken(): String {
        return CryptoHelper.generateRandomString(32)
    }

    /**
     * Hash sensitive data (passwords, tokens)
     */
    fun hashData(data: String): String {
        return CryptoHelper.SHA256.hash(data)
    }

    /**
     * Verify hashed data
     */
    fun verifyHash(data: String, expectedHash: String): Boolean {
        return CryptoHelper.SHA256.verify(data, expectedHash)
    }

    /**
     * Load or generate RSA key pair
     */
    private fun loadOrGenerateKeyPair() {
        try {
            // Try to load existing keys
            val privateKeyString = sharedPrefs.getString(KEY_PRIVATE_KEY, null)
            val publicKeyString = sharedPrefs.getString(KEY_PUBLIC_KEY, null)

            if (privateKeyString != null && publicKeyString != null) {
                // Keys exist, load them
                Log.i(TAG, "Loading existing key pair")
                // Note: In production, use proper key deserialization
            } else {
                // Generate new key pair
                Log.i(TAG, "Generating new key pair")
                keyPair = CryptoHelper.RSA.generateKeyPair()

                // Save keys (Note: In production, use Android Keystore)
                val privateKeyEncoded = Base64.encodeToString(keyPair?.private?.encoded, Base64.DEFAULT)
                val publicKeyEncoded = Base64.encodeToString(keyPair?.public?.encoded, Base64.DEFAULT)

                sharedPrefs.edit()
                    .putString(KEY_PRIVATE_KEY, privateKeyEncoded)
                    .putString(KEY_PUBLIC_KEY, publicKeyEncoded)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load/generate keys", e)
        }
    }

    /**
     * Get public key for sharing with other devices
     */
    fun getPublicKey(): PublicKey? {
        return keyPair?.public
    }
}
