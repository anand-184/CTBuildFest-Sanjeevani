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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    private val emergencyContacts = listOf(
        "+916280915449", // Replace with real numbers
        "+917986405288"
    )

    // Permission launcher (handles multiple at once)
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupListeners()
    }

    private fun setupListeners() {
        // SOS Button -> Show Confirmation Dialog
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

        // Feature Cards
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
        // Check permissions
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Permissions not granted!", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if location services are enabled
        if (!isLocationEnabled()) {
            promptEnableLocation()
            return
        }

        // Try to get last location first
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

    // Check if location services are enabled
    private fun isLocationEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Prompt user to enable location services
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

    // Send SOS with confirmed location
    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendSosWithLocation(location: Location) {
        val message = "🚨 EMERGENCY SOS!\n" +
                "I need help. Location:\n" +
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"

        sendSmsToContacts(message)
        shareViaWhatsApp(message)
        sendFcmSosAlert(message, location.latitude, location.longitude)

        Toast.makeText(requireContext(), "🚨 SOS Alert Sent!", Toast.LENGTH_LONG).show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendSmsToContacts(message: String) {
        // Check SMS permission first
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Get SmsManager based on API level
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                requireContext().getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Create PendingIntents for sent and delivery confirmation
            val sentIntent = PendingIntent.getBroadcast(
                requireContext(),
                0,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE
            )

            val deliveryIntent = PendingIntent.getBroadcast(
                requireContext(),
                0,
                Intent("SMS_DELIVERED"),
                PendingIntent.FLAG_IMMUTABLE
            )

            // Register broadcast receivers for SMS status
            requireContext().registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK ->
                            Toast.makeText(requireContext(), "✓ SMS Sent Successfully", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                            Toast.makeText(requireContext(), "✗ SMS Failed: Generic Error", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_NO_SERVICE ->
                            Toast.makeText(requireContext(), "✗ SMS Failed: No Service", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_NULL_PDU ->
                            Toast.makeText(requireContext(), "✗ SMS Failed: Null PDU", Toast.LENGTH_SHORT).show()
                        SmsManager.RESULT_ERROR_RADIO_OFF ->
                            Toast.makeText(requireContext(), "✗ SMS Failed: Radio Off", Toast.LENGTH_SHORT).show()
                    }
                    try {
                        requireContext().unregisterReceiver(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, IntentFilter("SMS_SENT"), Context.RECEIVER_NOT_EXPORTED)

            // Send SMS to each contact
            var sentCount = 0
            for (contact in emergencyContacts) {
                try {
                    // Split message if longer than 160 characters
                    val parts = smsManager.divideMessage(message)

                    if (parts.size > 1) {
                        // Send as multipart SMS
                        val sentIntents = ArrayList<PendingIntent>()
                        val deliveryIntents = ArrayList<PendingIntent>()
                        for (i in parts.indices) {
                            sentIntents.add(sentIntent)
                            deliveryIntents.add(deliveryIntent)
                        }
                        smsManager.sendMultipartTextMessage(contact, null, parts, sentIntents, deliveryIntents)
                    } else {
                        // Send single SMS
                        smsManager.sendTextMessage(contact, null, message, sentIntent, deliveryIntent)
                    }
                    sentCount++
                } catch (e: SecurityException) {
                    Toast.makeText(requireContext(), "SMS permission denied at runtime", Toast.LENGTH_SHORT).show()
                    return
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to send SMS to $contact: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            if (sentCount > 0) {
                Toast.makeText(requireContext(), "📱 Sending SMS to $sentCount contacts...", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "SMS sending failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }


    private fun shareViaWhatsApp(message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://api.whatsapp.com/send?text=${Uri.encode(message)}".toUri()
                setPackage("com.whatsapp")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFcmSosAlert(message: String, lat: Double, lon: Double) {
        val serverKey = "YOUR_FCM_SERVER_KEY"
        val json = """
        {
          "to": "/topics/emergency", 
          "notification": {
            "title": "🚨 SOS Alert",
            "body": "$message"
          },
          "data": {
            "latitude": "$lat",
            "longitude": "$lon"
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
                if (!response.isSuccessful) {
                    println("FCM Error: ${response.body?.string()}")
                } else {
                    println("FCM notification sent successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
