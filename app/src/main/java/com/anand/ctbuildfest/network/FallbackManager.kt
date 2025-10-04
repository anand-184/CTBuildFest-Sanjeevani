package com.anand.ctbuildfest.network


import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.*
import androidx.work.WorkManager
import com.anand.ctbuildfest.NetworkMode
import com.anand.ctbuildfest.QueueStats
import com.anand.ctbuildfest.QueuedAlert
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Manages fallback mechanisms for alert delivery when primary methods fail
 * Handles store-and-forward, retry logic, and offline queueing
 */
class FallbackManager(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("alert_queue", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_QUEUE = "alert_queue"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_INTERVAL_MINUTES = 15L
        private const val ALERT_TTL_HOURS = 24L
    }

    /**
     * Store alert for later delivery when network becomes available
     */
    fun queueAlertForLater(alert: Alert, recipients: List<String>) {
        val queuedAlert = QueuedAlert(
            alert = alert,
            recipients = recipients,
            queuedAt = System.currentTimeMillis(),
            retryCount = 0
        )

        val alertId = "alert_${System.currentTimeMillis()}"
        val alertJson = gson.toJson(queuedAlert)

        sharedPrefs.edit()
            .putString(alertId, alertJson)
            .apply()

        Log.i("FallbackManager", "Alert queued: $alertId")

        // Schedule background work to retry delivery
        scheduleRetryWork(alertId)
    }

    /**
     * Get all queued alerts that haven't expired
     */
    fun getQueuedAlerts(): List<Pair<String, QueuedAlert>> {
        val alerts = mutableListOf<Pair<String, QueuedAlert>>()
        val allEntries = sharedPrefs.all

        allEntries.forEach { (key, value) ->
            if (key.startsWith("alert_") && value is String) {
                try {
                    val queuedAlert = gson.fromJson(value, QueuedAlert::class.java)

                    // Check if alert hasn't expired
                    val ageHours = (System.currentTimeMillis() - queuedAlert.queuedAt) / (1000 * 60 * 60)
                    if (ageHours < ALERT_TTL_HOURS) {
                        alerts.add(Pair(key, queuedAlert))
                    } else {
                        // Remove expired alert
                        removeQueuedAlert(key)
                        Log.i("FallbackManager", "Removed expired alert: $key")
                    }
                } catch (e: Exception) {
                    Log.e("FallbackManager", "Failed to parse alert: $key", e)
                }
            }
        }

        return alerts
    }

    /**
     * Attempt to deliver all queued alerts
     */
    suspend fun processQueuedAlerts() {
        val networkDetector = NetworkModeDetector(context)
        val currentMode = networkDetector.getCurrentMode()

        if (currentMode == NetworkMode.OFFLINE_ISOLATED) {
            Log.i("FallbackManager", "Still offline, skipping queue processing")
            return
        }

        val queuedAlerts = getQueuedAlerts()
        Log.i("FallbackManager", "Processing ${queuedAlerts.size} queued alerts")

        queuedAlerts.forEach { (alertId, queuedAlert) ->
            try {
                // Attempt delivery based on current network mode
                when (currentMode) {
                    NetworkMode.ONLINE -> {
                        deliverViaInternet(queuedAlert.alert)
                        removeQueuedAlert(alertId)
                        Log.i("FallbackManager", "Successfully delivered queued alert: $alertId")
                    }
                    NetworkMode.MESH_DENSE, NetworkMode.MESH_SPARSE -> {
                        deliverViaMesh(queuedAlert.alert, queuedAlert.recipients)
                        removeQueuedAlert(alertId)
                        Log.i("FallbackManager", "Delivered via mesh: $alertId")
                    }
                    else -> {
                        // Increment retry count
                        incrementRetryCount(alertId, queuedAlert)
                    }
                }
            } catch (e: Exception) {
                Log.e("FallbackManager", "Failed to deliver alert: $alertId", e)
                incrementRetryCount(alertId, queuedAlert)
            }
        }
    }

    /**
     * Send via cellular SMS as last resort
     */
    fun sendViaSMSFallback(alert: Alert, recipients: List<String>) {
        if (recipients.isEmpty()) {
            Log.w("FallbackManager", "No recipients for SMS fallback")
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            val message = """
                🚨 EMERGENCY ALERT
                ${alert.title}
                ${alert.description}
                Location: ${alert.location}
                Severity: ${alert.severity}
                
                (Sent via SMS - network unavailable)
            """.trimIndent()

            // Split long messages if needed
            val parts = smsManager.divideMessage(message)

            recipients.forEach { phoneNumber ->
                try {
                    if (parts.size == 1) {
                        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    } else {
                        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                    }
                    Log.i("FallbackManager", "SMS sent to $phoneNumber")
                } catch (e: Exception) {
                    Log.e("FallbackManager", "SMS failed for $phoneNumber", e)
                }
            }
        } catch (e: Exception) {
            Log.e("FallbackManager", "SMS Manager error", e)
        }
    }

    /**
     * Schedule periodic retry work using WorkManager
     */
    private fun scheduleRetryWork(alertId: String) {
        val workRequest = PeriodicWorkRequestBuilder<RetryAlertWorker>(
            RETRY_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf("alertId" to alertId))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "retry_alert_$alertId",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Remove successfully delivered alert from queue
     */
    private fun removeQueuedAlert(alertId: String) {
        sharedPrefs.edit().remove(alertId).apply()

        // Cancel associated work
        WorkManager.getInstance(context).cancelUniqueWork("retry_alert_$alertId")
    }

    /**
     * Increment retry count and remove if max attempts reached
     */
    private fun incrementRetryCount(alertId: String, queuedAlert: QueuedAlert) {
        val newRetryCount = queuedAlert.retryCount + 1

        if (newRetryCount >= MAX_RETRY_ATTEMPTS) {
            Log.w("FallbackManager", "Max retries reached for $alertId, removing from queue")
            removeQueuedAlert(alertId)
        } else {
            val updatedAlert = queuedAlert.copy(retryCount = newRetryCount)
            sharedPrefs.edit()
                .putString(alertId, gson.toJson(updatedAlert))
                .apply()
            Log.i("FallbackManager", "Incremented retry count: $newRetryCount for $alertId")
        }
    }

    /**
     * Deliver alert via internet (Firebase)
     */
    private suspend fun deliverViaInternet(alert: Alert) {
        val router = SmartRouter(context)
        router.sendAlert(alert, emptyList())
    }

    /**
     * Deliver alert via mesh network
     */
    private fun deliverViaMesh(alert: Alert, recipients: List<String>) {
        // TODO: Implement mesh delivery in Phase 3
        Log.i("FallbackManager", "Mesh delivery not yet implemented")
    }

    /**
     * Get queue statistics
     */
    fun getQueueStats(): QueueStats {
        val alerts = getQueuedAlerts()
        return QueueStats(
            totalQueued = alerts.size,
            oldestAlert = alerts.minByOrNull { it.second.queuedAt }?.second?.queuedAt,
            highestRetryCount = alerts.maxOfOrNull { it.second.retryCount } ?: 0
        )
    }
}



class RetryAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val fallbackManager = FallbackManager(applicationContext)
            fallbackManager.processQueuedAlerts()
            Result.success()
        } catch (e: Exception) {
            Log.e("RetryAlertWorker", "Retry failed", e)
            Result.retry()
        }
    }
}
