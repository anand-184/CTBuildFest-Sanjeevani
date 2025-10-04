package com.anand.ctbuildfest.network

import Alert
import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.anand.ctbuildfest.NetworkMode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*



class SmartRouter(private val context: Context) {

    private val networkDetector = NetworkModeDetector(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun sendAlert(alert: Alert, recipients: List<String>) {
        val mode = networkDetector.getCurrentMode()

        Log.i("SmartRouter", "Sending alert via mode: $mode")

        when (mode) {
            NetworkMode.ONLINE -> sendViaInternet(alert)
            NetworkMode.MESH_DENSE -> sendViaMesh(alert, recipients)
            NetworkMode.MESH_SPARSE -> storeAndForward(alert, recipients)
            NetworkMode.OFFLINE_ISOLATED -> {
                // Use FallbackManager for store-and-forward
                val fallbackManager = FallbackManager(context)

                if (recipients.isNotEmpty()) {
                    // Send via SMS immediately
                    fallbackManager.sendViaSMSFallback(alert, recipients)
                }

                // Also queue for later delivery when network returns
                fallbackManager.queueAlertForLater(alert, recipients)
            }
        }
    }

    private suspend fun sendViaInternet(alert: Alert) {
        withContext(Dispatchers.IO) {
            try {
                firestore.collection("alerts")
                    .add(alert.toMap())
                    .addOnSuccessListener {
                        Log.i("SmartRouter", "Alert sent via internet")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("SmartRouter", "Internet send failed", exception)
                    }
            } catch (e: Exception) {
                Log.e("SmartRouter", "Internet error", e)
            }
        }
    }

    private fun sendViaMesh(alert: Alert, recipients: List<String>) {
        // Mesh implementation (Phase 3 - Future)
        Log.i("SmartRouter", "Sending via mesh to ${recipients.size} recipients")
        // TODO: Implement mesh networking in future phase
    }

    private fun storeAndForward(alert: Alert, recipients: List<String>) {
        // Store locally and forward opportunistically
        Log.i("SmartRouter", "Storing alert for later forwarding")
        saveToLocalQueue(alert, recipients)
    }

    private fun sendViaSMS(alert: Alert, recipients: List<String>) {
        try {
            val smsManager = SmsManager.getDefault()
            val message = """
                🚨 ${alert.title}
                ${alert.description}
                Location: ${alert.location}
            """.trimIndent()

            recipients.forEach { phoneNumber ->
                try {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    Log.i("SmartRouter", "SMS sent to $phoneNumber")
                } catch (e: Exception) {
                    Log.e("SmartRouter", "SMS failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SmartRouter", "SmsManager error", e)
        }
    }

    private fun saveToLocalQueue(alert: Alert, recipients: List<String>) {
        try {
            val sharedPrefs = context.getSharedPreferences("alert_queue", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            editor.putString("pending_alert_${System.currentTimeMillis()}", alert.toJson())
            editor.apply()
            Log.i("SmartRouter", "Alert saved to local queue")
        } catch (e: Exception) {
            Log.e("SmartRouter", "Failed to save to queue", e)
        }
    }
}
