package com.anand.ctbuildfest

/**
 * Data class for encrypted data
 */
data class EncryptedData(
    val cipherText: String,
    val iv: String
)