package com.anand.ctbuildfest.utils

import com.anand.ctbuildfest.EncryptedData
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptography helper for secure data handling
 * Provides AES-256 encryption, SHA-256 hashing, and RSA signatures
 */
object CryptoHelper {

    private const val TAG = "CryptoHelper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val KEY_ALIAS = "SanjeevaniAppKey"
    private const val GCM_TAG_LENGTH = 128

    /**
     * AES-256 Encryption for alert data
     */
    object AES256 {

        /**
         * Generate a new AES key
         */
        fun generateKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        /**
         * Encrypt data using AES-256-GCM
         */
        fun encrypt(plainText: String, secretKey: SecretKey): EncryptedData? {
            return try {
                val cipher = Cipher.getInstance(AES_ALGORITHM)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)

                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

                EncryptedData(
                    cipherText = Base64.encodeToString(encryptedBytes, Base64.DEFAULT),
                    iv = Base64.encodeToString(iv, Base64.DEFAULT)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Encryption failed", e)
                null
            }
        }

        /**
         * Decrypt data using AES-256-GCM
         */
        fun decrypt(encryptedData: EncryptedData, secretKey: SecretKey): String? {
            return try {
                val cipher = Cipher.getInstance(AES_ALGORITHM)
                val iv = Base64.decode(encryptedData.iv, Base64.DEFAULT)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                val encryptedBytes = Base64.decode(encryptedData.cipherText, Base64.DEFAULT)
                val decryptedBytes = cipher.doFinal(encryptedBytes)

                String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed", e)
                null
            }
        }
    }

    /**
     * SHA-256 Hashing for data integrity
     */
    object SHA256 {

        /**
         * Hash data using SHA-256
         */
        fun hash(data: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
                hashBytes.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Hashing failed", e)
                ""
            }
        }

        /**
         * Verify hash matches data
         */
        fun verify(data: String, expectedHash: String): Boolean {
            val actualHash = hash(data)
            return actualHash.equals(expectedHash, ignoreCase = true)
        }
    }

    /**
     * RSA Digital Signatures for authenticity
     */
    object RSA {

        /**
         * Generate RSA key pair
         */
        fun generateKeyPair(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            keyPairGenerator.initialize(2048) // 2048-bit key
            return keyPairGenerator.generateKeyPair()
        }

        /**
         * Sign data with private key
         */
        fun sign(data: String, privateKey: PrivateKey): String? {
            return try {
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initSign(privateKey)
                signature.update(data.toByteArray(Charsets.UTF_8))

                val signatureBytes = signature.sign()
                Base64.encodeToString(signatureBytes, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "Signing failed", e)
                null
            }
        }

        /**
         * Verify signature with public key
         */
        fun verify(data: String, signatureString: String, publicKey: PublicKey): Boolean {
            return try {
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initVerify(publicKey)
                signature.update(data.toByteArray(Charsets.UTF_8))

                val signatureBytes = Base64.decode(signatureString, Base64.DEFAULT)
                signature.verify(signatureBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Signature verification failed", e)
                false
            }
        }

        /**
         * Encrypt data with RSA public key (for small data only)
         */
        fun encrypt(data: String, publicKey: PublicKey): String? {
            return try {
                val cipher = Cipher.getInstance(RSA_ALGORITHM)
                cipher.init(Cipher.ENCRYPT_MODE, publicKey)

                val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
                Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "RSA encryption failed", e)
                null
            }
        }

        /**
         * Decrypt data with RSA private key
         */
        fun decrypt(encryptedData: String, privateKey: PrivateKey): String? {
            return try {
                val cipher = Cipher.getInstance(RSA_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, privateKey)

                val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
                val decryptedBytes = cipher.doFinal(encryptedBytes)

                String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "RSA decryption failed", e)
                null
            }
        }
    }

    /**
     * HMAC for message authentication
     */
    object HMAC {

        /**
         * Generate HMAC-SHA256
         */
        fun generate(data: String, key: String): String {
            return try {
                val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
                val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                mac.init(secretKeySpec)

                val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
                Base64.encodeToString(hmacBytes, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "HMAC generation failed", e)
                ""
            }
        }

        /**
         * Verify HMAC
         */
        fun verify(data: String, hmac: String, key: String): Boolean {
            val expectedHmac = generate(data, key)
            return expectedHmac == hmac
        }
    }

    fun generateRandomString(length: Int = 32): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Generate device fingerprint (unique identifier)
     */
    fun generateDeviceFingerprint(deviceId: String, timestamp: Long): String {
        val data = "$deviceId:$timestamp"
        return SHA256.hash(data)
    }
}



