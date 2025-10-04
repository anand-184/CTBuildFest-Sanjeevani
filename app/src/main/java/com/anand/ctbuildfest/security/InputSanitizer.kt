package com.anand.ctbuildfest.security


import android.util.Log

object InputSanitizer {

    private const val MAX_TEXT_LENGTH = 500
    private const val MAX_LOCATION_LENGTH = 200

    fun sanitizeText(input: String): String {
        return input
            .replace(Regex("<[^>]*>"), "") // Remove HTML
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Remove control chars
            .replace(Regex("[\\\\\"';]"), "") // Remove SQL chars
            .trim()
            .take(MAX_TEXT_LENGTH)
            .also {
                if (it != input) {
                    Log.w("Security", "Input sanitized: removed ${input.length - it.length} chars")
                }
            }
    }

    fun sanitizeLocation(location: String): String {
        return location
            .replace(Regex("[^a-zA-Z0-9\\s,.-]"), "")
            .take(MAX_LOCATION_LENGTH)
    }

    fun validateEmail(email: String): Boolean {
        return Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$").matches(email)
    }

    fun validatePhoneNumber(phone: String): Boolean {
        return Regex("^[+]?[0-9]{10,15}\$").matches(phone)
    }
}
