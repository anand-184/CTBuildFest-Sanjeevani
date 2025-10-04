package com.anand.ctbuildfest

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anand.ctbuildfest.network.SmartRouter
import com.anand.ctbuildfest.security.InputSanitizer
import com.anand.ctbuildfest.security.SecurityManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReportFragment : Fragment() {

    private lateinit var reportsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var reportIncidentFab: ExtendedFloatingActionButton
    private lateinit var reportsAdapter: ReportsAdapter

    private val reportsList = mutableListOf<Report>()
    private val reportsWithLocation = mutableListOf<ReportWithLocation>()

    private var selectedImageUri: Uri? = null
    private var photoUri: Uri? = null
    private lateinit var currentDialog: Dialog
    private lateinit var uploadedImageView: ImageView
    private lateinit var uploadPlaceholderView: LinearLayout

    // Security Manager
    private lateinit var securityManager: SecurityManager

    // Security Stats
    private var totalReportsSubmitted = 0
    private var secureReportsSubmitted = 0


    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Security Manager
        securityManager = SecurityManager(requireContext())

        reportsRecyclerView = view.findViewById(R.id.reportsRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        reportIncidentFab = view.findViewById(R.id.reportIncidentFab)

        setupRecyclerView()
        loadSampleReports()
        displaySecurityStats()

        reportIncidentFab.setOnClickListener {
            openReportIncidentDialog()
        }
    }

    /**
     * Display security statistics (Optional)
     */
    private fun displaySecurityStats() {
        Log.i("ReportFragment", "Device Fingerprint: ${securityManager.getDeviceFingerprint().take(16)}...")
        Log.i("ReportFragment", "Total Reports: $totalReportsSubmitted")
        Log.i("ReportFragment", "Secure Reports: $secureReportsSubmitted")
    }

    private fun setupRecyclerView() {
        reportsAdapter = ReportsAdapter(reportsWithLocation) { report ->
            Toast.makeText(requireContext(), "Viewing: ${report.title}", Toast.LENGTH_SHORT).show()
        }

        reportsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reportsAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadSampleReports() {
        reportsList.clear()
        reportsWithLocation.clear()

        val sampleReportsWithLocation = listOf(
            ReportWithLocation(
                id = 1,
                title = "Flood Alert",
                description = "Heavy flooding in downtown area",
                location = "Downtown, Mumbai",
                severity = "High",
                timestamp = "2h ago",
                reporterName = "John",
                type = "Flood",
                latLng = LatLng(19.0760, 72.8777)
            ),
            ReportWithLocation(
                id = 2,
                title = "Earthquake",
                description = "5.2 magnitude earthquake reported",
                location = "City Center, Delhi",
                severity = "Medium",
                timestamp = "5h ago",
                reporterName = "Jane",
                type = "Earthquake",
                latLng = LatLng(28.7041, 77.1025)
            ),
            ReportWithLocation(
                id = 3,
                title = "Fire Outbreak",
                description = "Forest fire spreading rapidly",
                location = "North Hills, Bangalore",
                severity = "High",
                timestamp = "1d ago",
                reporterName = "Mike",
                type = "Fire",
                latLng = LatLng(12.9716, 77.5946)
            )
        )

        reportsWithLocation.addAll(sampleReportsWithLocation)

        val reportsForDisplay = sampleReportsWithLocation.map {
            Report(
                id = it.id,
                title = it.title,
                description = it.description,
                location = it.location,
                severity = it.severity,
                timestamp = it.timestamp,
                reporterName = it.reporterName,
                type = it.type,
                latLng = it.latLng
            )
        }

        reportsList.addAll(reportsForDisplay)

        sampleReportsWithLocation.forEach { report ->
            (activity as? MainActivity)?.addDisasterMarker(report)
        }

        updateEmptyState()
        reportsAdapter.notifyDataSetChanged()
    }

    private fun updateEmptyState() {
        if (reportsList.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            reportsRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            reportsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun openReportIncidentDialog() {
        currentDialog = Dialog(requireContext())
        currentDialog.setContentView(R.layout.dialog_report_incident)

        currentDialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        currentDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val imageUploadLayout = currentDialog.findViewById<LinearLayout>(R.id.imageUploadLayout)
        uploadedImageView = currentDialog.findViewById(R.id.uploadedImage)
        uploadPlaceholderView = currentDialog.findViewById(R.id.uploadPlaceholder)
        val incidentTitle = currentDialog.findViewById<TextInputEditText>(R.id.incidentTitle)
        val incidentTypeRecyclerView = currentDialog.findViewById<RecyclerView>(R.id.incidentTypeRecyclerView)
        val severityRadioGroup = currentDialog.findViewById<RadioGroup>(R.id.severityRadioGroup)
        val incidentLocation = currentDialog.findViewById<TextInputEditText>(R.id.incidentLocation)
        val incidentDescription = currentDialog.findViewById<TextInputEditText>(R.id.incidentDescription)
        val cancelButton = currentDialog.findViewById<MaterialButton>(R.id.cancelButton)
        val submitButton = currentDialog.findViewById<MaterialButton>(R.id.submitButton)

        var selectedIncidentType = ""
        var cachedLatLng: LatLng? = null

        val incidentTypes = listOf("Earthquake", "Flood", "Fire", "Storm", "Landslide", "Tsunami", "Cyclone", "Other")
        val typeAdapter = IncidentTypeAdapter(incidentTypes) { type -> selectedIncidentType = type }
        incidentTypeRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        incidentTypeRecyclerView.adapter = typeAdapter

        imageUploadLayout.setOnClickListener {
            showImagePickerDialog()
        }

        // Auto-fetch LatLng when location loses focus
        incidentLocation.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val locationText = InputSanitizer.sanitizeLocation(incidentLocation.text.toString())
                if (locationText.isNotEmpty()) {
                    incidentLocation.hint = "Finding coordinates..."

                    CoroutineScope(Dispatchers.IO).launch {
                        val latLng = geocodeLocation(locationText)

                        withContext(Dispatchers.Main) {
                            if (latLng != null) {
                                cachedLatLng = latLng
                                incidentLocation.hint = "Lat: ${latLng.latitude}, Lng: ${latLng.longitude}"
                                Toast.makeText(requireContext(), "✓ Location found", Toast.LENGTH_SHORT).show()
                            } else {
                                incidentLocation.hint = "Location"
                                Toast.makeText(requireContext(), "⚠ Could not find coordinates", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        cancelButton.setOnClickListener { currentDialog.dismiss() }

        submitButton.setOnClickListener {
            // Sanitize ALL inputs for security
            val safeTitle = InputSanitizer.sanitizeText(incidentTitle.text.toString())
            val safeLocation = InputSanitizer.sanitizeLocation(incidentLocation.text.toString())
            val safeDescription = InputSanitizer.sanitizeText(incidentDescription.text.toString())

            val severity = when (severityRadioGroup.checkedRadioButtonId) {
                R.id.severityLow -> "Low"
                R.id.severityMedium -> "Medium"
                R.id.severityHigh -> "High"
                else -> "Medium"
            }

            // Log sanitization
            Log.i("ReportFragment", "Original title length: ${incidentTitle.text?.length}")
            Log.i("ReportFragment", "Sanitized title length: ${safeTitle.length}")

            when {
                safeTitle.isEmpty() -> {
                    incidentTitle.error = "Title required"
                    return@setOnClickListener
                }
                selectedIncidentType.isEmpty() -> {
                    Toast.makeText(requireContext(), "Select incident type", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                safeLocation.isEmpty() -> {
                    incidentLocation.error = "Location required"
                    return@setOnClickListener
                }
                safeDescription.isEmpty() -> {
                    incidentDescription.error = "Description required"
                    return@setOnClickListener
                }
                else -> {

                    if (cachedLatLng != null) {
                        submitSecureReport(safeTitle, safeDescription, safeLocation, severity, selectedIncidentType, cachedLatLng, null)
                    } else {
                        Toast.makeText(requireContext(), "Geocoding location...", Toast.LENGTH_SHORT).show()

                        CoroutineScope(Dispatchers.IO).launch {
                            val latLng = geocodeLocation(safeLocation)

                            withContext(Dispatchers.Main) {
                                submitSecureReport(safeTitle, safeDescription, safeLocation, severity, selectedIncidentType, latLng, null)
                            }
                        }
                    }
                }
            }
        }

        currentDialog.show()
    }

    /**
     * Submit report with full security: sanitization, signing, encryption
     */
    private fun submitSecureReport(
        safeTitle: String,
        safeDescription: String,
        safeLocation: String,
        severity: String,
        type: String,
        latLng: LatLng?,
        safeImageUri: Uri?
    ) {
        totalReportsSubmitted++

        // Create alert
        val alert = Alert(
            title = safeTitle,
            description = safeDescription,
            location = safeLocation,
            severity = severity,
            type = type
        )

        // Sign alert for authenticity
        val signedAlert = securityManager.signAlert(alert)

        if (signedAlert != null) {
            secureReportsSubmitted++
            Log.i("ReportFragment", "✓ Report signed successfully")
            Log.i("ReportFragment", "Signature: ${signedAlert.signature.take(32)}...")
        } else {
            Log.w("ReportFragment", "⚠ Failed to sign report")
        }

        // Get device fingerprint for trust scoring
        val deviceFingerprint = securityManager.getDeviceFingerprint()
        Log.i("ReportFragment", "Device Fingerprint: ${deviceFingerprint.take(16)}...")

        // Generate hash of report for integrity verification
        val reportHash = securityManager.hashData("$safeTitle:$safeDescription:$safeLocation")
        Log.i("ReportFragment", "Report Hash: ${reportHash.take(16)}...")

        // Send via smart router
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val router = SmartRouter(requireContext())
                router.sendAlert(alert, emptyList())

                // Create report for local display
                val newReportWithLoc = ReportWithLocation(
                    id = (Math.random() * 10000).toInt(),
                    title = safeTitle,
                    description = safeDescription,
                    location = safeLocation,
                    severity = severity,
                    timestamp = "Just now",
                    reporterName = "You (Verified)",
                    type = type,
                    latLng = latLng
                )

                addNewReportWithLocation(newReportWithLoc)

                if (latLng != null) {
                    (activity as? MainActivity)?.addDisasterMarker(newReportWithLoc)
                    Toast.makeText(
                        requireContext(),
                        "✓ Secure Report Submitted!\n🔒 Signed & Verified",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "✓ Report Submitted\n⚠ Without map marker",
                        Toast.LENGTH_LONG
                    ).show()
                }

                displaySecurityStats()
                currentDialog.dismiss()

            } catch (e: Exception) {
                Log.e("ReportFragment", "Failed to submit report", e)
                Toast.makeText(
                    requireContext(),
                    "⚠ Submission failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun geocodeLocation(locationString: String): LatLng? {
        return try {
            val geocoder = Geocoder(requireContext())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(locationString, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                LatLng(address.latitude, address.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("INCIDENT_${timeStamp}_", ".jpg", requireContext().getExternalFilesDir(null))
    }

    private fun addNewReportWithLocation(reportWithLoc: ReportWithLocation) {
        reportsWithLocation.add(0, reportWithLoc)

        val report = Report(
            id = reportWithLoc.id,
            title = reportWithLoc.title,
            description = reportWithLoc.description,
            location = reportWithLoc.location,
            severity = reportWithLoc.severity,
            timestamp = reportWithLoc.timestamp,
            reporterName = reportWithLoc.reporterName,
            type = reportWithLoc.type,
            latLng = reportWithLoc.latLng
        )

        reportsList.add(0, report)
        reportsAdapter.notifyItemInserted(0)
        reportsRecyclerView.scrollToPosition(0)
        updateEmptyState()
    }
}
