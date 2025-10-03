package com.anand.ctbuildfest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var mapLoadingProgress: ProgressBar
    private lateinit var locationFab: FloatingActionButton
    private lateinit var emergencyFab: ExtendedFloatingActionButton
    private lateinit var offlineIndicator: MaterialCardView
    private lateinit var bottomSheet: MaterialCardView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>

    // Bottom sheet views
    private lateinit var locationTitle: TextView
    private lateinit var severityText: TextView
    private lateinit var locationDescription: TextView
    private lateinit var navigateButton: MaterialButton
    private lateinit var shareButton: MaterialButton

    // Filter chips
    private lateinit var earthquakeChip: Chip
    private lateinit var floodChip: Chip
    private lateinit var fireChip: Chip
    private lateinit var stormChip: Chip
    private lateinit var safeZoneChip: Chip

    private val disasterMarkers = mutableListOf<Marker>()
    private val activeFilters = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupMap()
        setupListeners()
    }

    private fun initViews(view: View) {
        mapLoadingProgress = view.findViewById(R.id.mapLoadingProgress)
        locationFab = view.findViewById(R.id.locationFab)
        emergencyFab = view.findViewById(R.id.emergencyFab)
        offlineIndicator = view.findViewById(R.id.offlineIndicator)
        bottomSheet = view.findViewById(R.id.bottomSheet)

        // Bottom sheet views
        locationTitle = view.findViewById(R.id.locationTitle)
        severityText = view.findViewById(R.id.severityText)
        locationDescription = view.findViewById(R.id.locationDescription)
        navigateButton = view.findViewById(R.id.navigateButton)
        shareButton = view.findViewById(R.id.shareButton)

        // Filter chips
        earthquakeChip = view.findViewById(R.id.earthquakeChip)
        floodChip = view.findViewById(R.id.floodChip)
        fireChip = view.findViewById(R.id.fireChip)
        stormChip = view.findViewById(R.id.stormChip)
        safeZoneChip = view.findViewById(R.id.safeZoneChip)

        // Setup bottom sheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun setupMap() {
        mapLoadingProgress.visibility = View.VISIBLE
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapLoadingProgress.visibility = View.GONE

        // Configure map
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }

        // Enable location if permission granted
        enableMyLocation()

        // Set initial position (India)
        val india = LatLng(20.5937, 78.9629)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(india, 5f))

        // Add sample disaster markers
        addSampleDisasterMarkers()

        // Marker click listener
        googleMap.setOnMarkerClickListener { marker ->
            showBottomSheetForMarker(marker)
            true
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun addSampleDisasterMarkers() {
        val disasters = listOf(
            DisasterData("Earthquake", LatLng(28.7041, 77.1025), "High", "Delhi NCR",
                "Magnitude 5.2 earthquake reported. Avoid high-rise buildings."),
            DisasterData("Flood", LatLng(19.0760, 72.8777), "Medium", "Mumbai",
                "Heavy flooding in low-lying areas. Avoid coastal roads."),
            DisasterData("Fire", LatLng(12.9716, 77.5946), "High", "Bangalore",
                "Forest fire spreading rapidly. Evacuate immediately."),
            DisasterData("Storm", LatLng(22.5726, 88.3639), "Low", "Kolkata",
                "Severe storm warning. Stay indoors."),
            DisasterData("SafeZone", LatLng(13.0827, 80.2707), "Safe", "Chennai",
                "Safe shelter available with food and medical supplies.")
        )

        disasters.forEach { disaster ->
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(disaster.position)
                    .title(disaster.type)
                    .snippet(disaster.location)
                    .icon(getMarkerIcon(disaster.type, disaster.severity))
            )
            marker?.tag = disaster
            disasterMarkers.add(marker!!)
        }
    }

    private fun getMarkerIcon(type: String, severity: String): BitmapDescriptor {
        val color = when (severity) {
            "High" -> BitmapDescriptorFactory.HUE_RED
            "Medium" -> BitmapDescriptorFactory.HUE_ORANGE
            "Low" -> BitmapDescriptorFactory.HUE_YELLOW
            "Safe" -> BitmapDescriptorFactory.HUE_GREEN
            else -> BitmapDescriptorFactory.HUE_BLUE
        }
        return BitmapDescriptorFactory.defaultMarker(color)
    }

    private fun setupListeners() {
        // Location FAB
        locationFab.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap.animateCamera(CameraUpdateFactory.zoomTo(15f))
            } else {
                Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
            }
        }

        // Emergency FAB
        emergencyFab.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Report Incident...", Toast.LENGTH_SHORT).show()
            // Navigate to report incident
        }

        // Filter chips
        earthquakeChip.setOnCheckedChangeListener { _, isChecked ->
            toggleFilter("Earthquake", isChecked)
        }
        floodChip.setOnCheckedChangeListener { _, isChecked ->
            toggleFilter("Flood", isChecked)
        }
        fireChip.setOnCheckedChangeListener { _, isChecked ->
            toggleFilter("Fire", isChecked)
        }
        stormChip.setOnCheckedChangeListener { _, isChecked ->
            toggleFilter("Storm", isChecked)
        }
        safeZoneChip.setOnCheckedChangeListener { _, isChecked ->
            toggleFilter("SafeZone", isChecked)
        }

        // Bottom sheet buttons
        navigateButton.setOnClickListener {
            Toast.makeText(requireContext(), "Opening navigation...", Toast.LENGTH_SHORT).show()
        }

        shareButton.setOnClickListener {
            Toast.makeText(requireContext(), "Sharing location...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFilter(type: String, isEnabled: Boolean) {
        if (isEnabled) {
            activeFilters.add(type)
        } else {
            activeFilters.remove(type)
        }
        applyFilters()
    }

    private fun applyFilters() {
        disasterMarkers.forEach { marker ->
            val disaster = marker.tag as? DisasterData
            if (disaster != null) {
                marker.isVisible = activeFilters.isEmpty() || activeFilters.contains(disaster.type)
            }
        }
    }

    private fun showBottomSheetForMarker(marker: Marker) {
        val disaster = marker.tag as? DisasterData ?: return

        locationTitle.text = "${disaster.type} - ${disaster.severity} Risk"
        severityText.text = "${disaster.severity} Severity"
        locationDescription.text = disaster.description

        // Set severity color
        val severityColor = when (disaster.severity) {
            "High" -> ContextCompat.getColor(requireContext(), R.color.error)
            "Medium" -> ContextCompat.getColor(requireContext(), R.color.primary)
            "Low" -> Color.YELLOW
            else -> Color.GREEN
        }
        severityText.setTextColor(severityColor)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheet.visibility = View.VISIBLE
    }

    // Add this method inside MapFragment class
    fun addUserReportedMarker(report: ReportWithLocation) {
        if (report.latLng != null && ::googleMap.isInitialized) {
            val disaster = DisasterData(
                type = report.type,
                position = report.latLng,
                severity = report.severity,
                location = report.location,
                description = report.description
            )

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(report.latLng)
                    .title(report.type)
                    .snippet(report.location)
                    .icon(getMarkerIcon(report.type, report.severity))
            )
            marker?.tag = disaster
            marker?.let { disasterMarkers.add(it) }

            // Animate camera to new marker
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(report.latLng, 12f)
            )

            // Show bottom sheet for new marker
            marker?.let { showBottomSheetForMarker(it) }
        }
    }



}
