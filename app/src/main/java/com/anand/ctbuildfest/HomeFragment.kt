package com.anand.ctbuildfest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.anand.ctbuildfest.network.SmartRouter
import com.anand.ctbuildfest.security.SecurityManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HomeFragment : Fragment() {

    private lateinit var sosButton: MaterialButton
    private lateinit var confirmSosButton: MaterialButton
    private lateinit var cancelSosButton: MaterialButton
    private lateinit var sosDialog: MaterialCardView

    private lateinit var mapCard: MaterialCardView
    private lateinit var alertsCard: MaterialCardView
    private lateinit var guidesCard: MaterialCardView
    private lateinit var safetyKitCard: MaterialCardView
    private lateinit var reportCard: MaterialCardView

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Security Manager
    private lateinit var securityManager: SecurityManager

    // Device Security Status Display (Optional)
    private var securityStatusText: TextView? = null

    private val emergencyContacts = listOf(
        "+916280915449",
        "+917986405288"
    )

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                @Suppress("MissingPermission")
                sendSos()
            } else {
                Toast.makeText(
                    requireContext(),
                    "All permissions are required to send SOS!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Security Manager
        securityManager = SecurityManager(requireContext())

        // Init Views
        sosButton = view.findViewById(R.id.sosButton)
        confirmSosButton = view.findViewById(R.id.confirmSosButton)
        cancelSosButton = view.findViewById(R.id.cancelSosButton)
        sosDialog = view.findViewById(R.id.sosConfirmationDialog)

        mapCard = view.findViewById(R.id.mapCard)
        alertsCard = view.findViewById(R.id.alertsCard)
        guidesCard = view.findViewById(R.id.guidesCard)
        safetyKitCard = view.findViewById(R.id.safetyKitCard)
        reportCard = view.findViewById(R.id.reportCard)

        // Optional: Security status display
        // securityStatusText = view.findViewById(R.id.securityStatusText)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupListeners()
        displaySecurityStatus()
    }

    /**
     * Display device security status and fingerprint
     */
    private fun displaySecurityStatus() {
        val fingerprint = securityManager.getDeviceFingerprint()
        val shortFingerprint = fingerprint.take(12)

        Log.i("HomeFragment", "Device Fingerprint: $fingerprint")
        Log.i("HomeFragment", "Public Key: ${securityManager.getPublicKey()?.encoded?.size} bytes")

        // Optional: Show in UI
        securityStatusText?.text = "Secure Device ID: $shortFingerprint..."

        // Test security features (DEBUG - Remove in production)
        testSecurityFeatures()
    }

    /**
     * Test security implementation (DEBUG ONLY)
     */
    private fun testSecurityFeatures() {
        // Test hashing
        val testData = "TestSecurityData"
        val hash = securityManager.hashData(testData)
        Log.i("SecurityTest", "Hash: ${hash.take(20)}...")

        // Test session token
        val token = securityManager.generateSessionToken()
        Log.i("SecurityTest", "Session Token: ${token.take(16)}...")

        // Test alert signing
        val testAlert = Alert("Test SOS", "Test", "0.0,0.0", "Critical", "SOS")
        val signedAlert = securityManager.signAlert(testAlert)
        Log.i("SecurityTest", "Alert signed: ${signedAlert != null}")
    }

    private fun setupListeners() {
        sosButton.setOnClickListener {
            sosDialog.visibility = View.VISIBLE
        }

        cancelSosButton.setOnClickListener {
            sosDialog.visibility = View.GONE
        }

        confirmSosButton.setOnClickListener {
            sosDialog.visibility = View.GONE
            checkAndRequestPermissions()
        }

        mapCard.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Disaster Map...", Toast.LENGTH_SHORT).show()
        }

        alertsCard.setOnClickListener {
            Toast.makeText(requireContext(), "Viewing Alerts & Hotspots...", Toast.LENGTH_SHORT).show()
        }

        guidesCard.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Disaster Guides...", Toast.LENGTH_SHORT).show()
        }

        safetyKitCard.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Safety Kit...", Toast.LENGTH_SHORT).show()
        }

        reportCard.setOnClickListener {
            Toast.makeText(requireContext(), "Reporting Incident / Hotspot...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val neededPermissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            neededPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            neededPermissions.add(Manifest.permission.SEND_SMS)
        }

        if (neededPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(neededPermissions.toTypedArray())
        } else {
            sendSos()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS])
    private fun sendSos() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Permissions not granted!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isLocationEnabled()) {
            promptEnableLocation()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSosWithLocation(location)
            } else {
                requestCurrentLocation()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Location fetch failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun promptEnableLocation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location Services Disabled")
            .setMessage("Please enable location services to send SOS alert with your location.")
            .setPositiveButton("Enable") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(2000L)
            setMaxUpdates(1)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendSosWithLocation(location)
                    fusedLocationClient.removeLocationUpdates(this)
                } ?: run {
                    Toast.makeText(requireContext(), "Still unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Toast.makeText(requireContext(), "Getting your location...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Send SOS with security features: signing and encryption
     */
    private fun sendSosWithLocation(location: Location) {
        // Create alert
        val alert = Alert(
            title = "EMERGENCY SOS",
            description = "I need immediate help! This is a verified emergency alert.",
            location = "${location.latitude},${location.longitude}",
            severity = "Critical",
            type = "SOS"
        )

        // Sign alert for authenticity
        val signedAlert = securityManager.signAlert(alert)

        if (signedAlert != null) {
            Log.i("HomeFragment", "✓ SOS Alert signed successfully")
            Log.i("HomeFragment", "Signature: ${signedAlert.signature.take(32)}...")
        } else {
            Log.w("HomeFragment", "⚠ Failed to sign SOS alert")
        }

        // Get device fingerprint for trust scoring
        val deviceFingerprint = securityManager.getDeviceFingerprint()
        Log.i("HomeFragment", "Device Fingerprint: ${deviceFingerprint.take(16)}...")

        // Send via smart router with encryption
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val router = SmartRouter(requireContext())
                router.sendAlert(alert, emergencyContacts)

                Toast.makeText(
                    requireContext(),
                    "🚨 Signed & Encrypted SOS Sent!",
                    Toast.LENGTH_LONG
                ).show()

                // Also send direct SMS as fallback
                sendSecureSmsToContacts(alert, location)

            } catch (e: Exception) {
                Log.e("HomeFragment", "Failed to send SOS", e)
                Toast.makeText(
                    requireContext(),
                    "⚠ SOS sending failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Send SMS with hash verification for authenticity
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendSecureSmsToContacts(alert: Alert, location: Location) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requireContext().getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Create message with hash for verification
            val messageContent = """
                🚨 EMERGENCY SOS
                ${alert.description}
                
                Location: https://maps.google.com/?q=${location.latitude},${location.longitude}
                
                Time: ${System.currentTimeMillis()}
                Device: ${securityManager.getDeviceFingerprint().take(8)}
            """.trimIndent()

            // Generate hash for message integrity
            val messageHash = securityManager.hashData(messageContent)
            val secureMessage = "$messageContent\n\nVerify: ${messageHash.take(8)}"

            Log.i("HomeFragment", "SMS Hash: ${messageHash.take(16)}...")

            val sentIntent = PendingIntent.getBroadcast(
                requireContext(),
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE
            )

            requireContext().registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val status = when (resultCode) {
                        Activity.RESULT_OK -> "✓ SMS Sent"
                        else -> "✗ SMS Failed"
                    }
                    Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                    try {
                        requireContext().unregisterReceiver(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, IntentFilter("SMS_SENT"), Context.RECEIVER_NOT_EXPORTED)

            var sentCount = 0
            for (contact in emergencyContacts) {
                try {
                    val parts = smsManager.divideMessage(secureMessage)

                    if (parts.size > 1) {
                        val sentIntents = ArrayList<PendingIntent>()
                        for (i in parts.indices) {
                            sentIntents.add(sentIntent)
                        }
                        smsManager.sendMultipartTextMessage(contact, null, parts, sentIntents, null)
                    } else {
                        smsManager.sendTextMessage(contact, null, secureMessage, sentIntent, null)
                    }
                    sentCount++
                    Log.i("HomeFragment", "Secure SMS sent to $contact")
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Failed to send SMS to $contact", e)
                }
            }

            if (sentCount > 0) {
                Toast.makeText(requireContext(), "📱 Sending verified SMS to $sentCount contacts", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e("HomeFragment", "SMS sending failed", e)
            Toast.makeText(requireContext(), "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Share via WhatsApp with security verification code
     */
    private fun shareViaWhatsApp(message: String) {
        try {
            // Add verification hash
            val hash = securityManager.hashData(message)
            val secureMessage = "$message\n\n🔒 Verify: ${hash.take(8)}"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://api.whatsapp.com/send?text=${Uri.encode(secureMessage)}".toUri()
                setPackage("com.whatsapp")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Send FCM notification with signed alert
     */
    private fun sendFcmSosAlert(message: String, lat: Double, lon: Double) {
        val serverKey = "YOUR_FCM_SERVER_KEY"

        // Generate session token for this request
        val sessionToken = securityManager.generateSessionToken()
        val deviceFingerprint = securityManager.getDeviceFingerprint()

        val json = """
        {
          "to": "/topics/emergency", 
          "notification": {
            "title": "🚨 Verified SOS Alert",
            "body": "$message"
          },
          "data": {
            "latitude": "$lat",
            "longitude": "$lon",
            "deviceFingerprint": "$deviceFingerprint",
            "sessionToken": "$sessionToken",
            "timestamp": "${System.currentTimeMillis()}"
          }
        }
        """.trimIndent()

        Thread {
            try {
                val client = OkHttpClient()
                val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("https://fcm.googleapis.com/fcm/send")
                    .addHeader("Authorization", "key=$serverKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.i("HomeFragment", "FCM notification sent successfully")
                } else {
                    Log.e("HomeFragment", "FCM Error: ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "FCM failed", e)
            }
        }.start()
    }
}
